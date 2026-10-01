package com.curated.app.core.data

import com.curated.app.core.model.BudgetTag
import com.curated.app.core.model.Day
import com.curated.app.core.model.DayPublishSource
import com.curated.app.core.model.SeasonTag
import com.curated.app.core.model.Stop
import com.curated.app.core.model.StopCategory
import com.curated.app.core.model.StopPhoto
import com.curated.app.core.model.Trip
import com.curated.app.core.model.TripStatus
import com.curated.app.core.model.TripVisibility
import com.curated.app.core.model.User
import com.curated.app.core.trip.deriveCover
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.time.Clock

/** Columns matching [Stop] exactly, so the PostGIS `location` geography column never enters the decode path. */
private val STOP_RETURN_COLUMNS = Columns.raw(
    "id,day_id,trip_id,name,category,latitude,longitude,order_in_day,caption,cost,tips,place_name,arrival_time,created_at"
)

class TripRepository(private val client: SupabaseClient) {

    private val postgrest get() = client.postgrest

    suspend fun fetchPublishedTrips(): List<Trip> =
        postgrest.from("trips")
            .select {
                filter {
                    eq("status", "completed")
                    eq("visibility", "public")
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()

    suspend fun fetchTripMapPins(): List<TripMapPin> = tripMapPins(fetchPublishedTrips())

    suspend fun fetchTripMapPinsForAuthor(authorId: String): List<TripMapPin> =
        tripMapPins(fetchTripsByAuthor(authorId))

    /** Map pins for an already-fetched, already-filtered set of trips (e.g. Explore search results). */
    suspend fun fetchTripMapPinsFor(trips: List<Trip>): List<TripMapPin> = tripMapPins(trips)

    /** Every stop's position across these trips - the profile groups them into cities. */
    suspend fun fetchStopPointsFor(trips: List<Trip>): List<StopPoint> =
        fetchStopLocations(trips).map { StopPoint(tripId = it.tripId, latitude = it.latitude, longitude = it.longitude) }

    private suspend fun fetchStopLocations(trips: List<Trip>): List<StopLocationRow> {
        if (trips.isEmpty()) return emptyList()
        return postgrest.from("stops")
            .select(columns = Columns.raw("trip_id,latitude,longitude,order_in_day")) {
                filter { isIn("trip_id", trips.map { it.id }) }
            }
            .decodeList()
    }

    private suspend fun tripMapPins(trips: List<Trip>): List<TripMapPin> {
        if (trips.isEmpty()) return emptyList()

        val firstStopByTrip = fetchStopLocations(trips)
            .groupBy { it.tripId }
            .mapValues { (_, rows) -> rows.minByOrNull { it.orderInDay } }

        return trips.mapNotNull { trip ->
            firstStopByTrip[trip.id]?.let { loc ->
                TripMapPin(trip = trip, latitude = loc.latitude, longitude = loc.longitude)
            }
        }
    }

    /**
     * Every completed trip by one author, most recent first — used for profile grids.
     *
     * Public only unless [includeNonPublic]: RLS lets anyone read an unlisted trip
     * (that's what makes its link work), so keeping it off someone else's profile
     * is this filter's job. Pass true only for the signed-in user's own trips.
     */
    suspend fun fetchTripsByAuthor(authorId: String, includeNonPublic: Boolean = false): List<Trip> =
        postgrest.from("trips")
            .select {
                filter {
                    eq("author_id", authorId)
                    eq("status", "completed")
                    if (!includeNonPublic) eq("visibility", "public")
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()

    /** Completed trips from a set of authors, most recent first — the Home feed. */
    suspend fun fetchTripsByAuthors(authorIds: List<String>, limit: Long = 50, offset: Long = 0): List<Trip> {
        if (authorIds.isEmpty()) return emptyList()
        return postgrest.from("trips")
            .select {
                filter {
                    isIn("author_id", authorIds)
                    eq("status", "completed")
                    eq("visibility", "public")
                }
                order("created_at", Order.DESCENDING)
                range(offset, offset + limit - 1)
            }
            .decodeList()
    }

    /** Everyone's newest completed trips: the Trending tab, and the cold-start feed. */
    suspend fun fetchRecentPublicTrips(limit: Long = 20, offset: Long = 0): List<Trip> =
        postgrest.from("trips")
            .select {
                filter {
                    eq("status", "completed")
                    eq("visibility", "public")
                }
                order("created_at", Order.DESCENDING)
                range(offset, offset + limit - 1)
            }
            .decodeList()

    /**
     * Explore's search + filters. Everything that maps to a real column (status,
     * visibility, budget_tag, season_tag, author_id, destination/title text) is filtered
     * server-side; trip length is derived from dates and filtered by the caller.
     */
    suspend fun searchTrips(params: TripSearchParams): List<Trip> =
        postgrest.from("trips")
            .select {
                filter {
                    eq("status", "completed")
                    eq("visibility", "public")
                    params.budgetTag?.let { eq("budget_tag", it.dbValue()) }
                    params.seasonTag?.let { eq("season_tag", it.dbValue()) }
                    params.authorIds?.let { isIn("author_id", it) }
                    val query = params.query
                    if (!query.isNullOrBlank()) {
                        or {
                            ilike("destination", "%$query%")
                            ilike("title", "%$query%")
                        }
                    }
                }
                order("created_at", Order.DESCENDING)
                limit(params.limit)
            }
            .decodeList()

    /** Stop count + how many of those stops have a caption, per trip — feeds Explore's ranking. */
    suspend fun fetchTripCompleteness(tripIds: List<String>): Map<String, TripCompleteness> {
        if (tripIds.isEmpty()) return emptyMap()
        return postgrest.from("stops")
            .select(columns = Columns.raw("trip_id,caption,name,order_in_day")) {
                filter { isIn("trip_id", tripIds) }
            }
            .decodeList<StopCaptionRow>()
            .groupBy { it.tripId }
            .mapValues { (_, rows) ->
                TripCompleteness(
                    stopCount = rows.size,
                    captionedStopCount = rows.count { !it.caption.isNullOrBlank() },
                    stopNames = rows.sortedBy { it.orderInDay }.map { it.name }
                )
            }
    }

    /**
     * Stop count and the stops in order, for the feed's one-line preview.
     *
     * This is the old fetchStopCounts with two more columns on the same select -
     * the names come back in the round trip the count already cost.
     */
    suspend fun fetchStopSummaries(tripIds: List<String>): Map<String, StopSummary> {
        if (tripIds.isEmpty()) return emptyMap()
        return postgrest.from("stops")
            .select(columns = Columns.raw("trip_id,name,order_in_day")) {
                filter { isIn("trip_id", tripIds) }
            }
            .decodeList<StopNameRow>()
            .groupBy { it.tripId }
            .mapValues { (_, rows) ->
                StopSummary(count = rows.size, names = rows.sortedBy { it.orderInDay }.map { it.name })
            }
    }

    suspend fun fetchTrip(tripId: String): Trip =
        postgrest.from("trips")
            .select { filter { eq("id", tripId) } }
            .decodeSingle()

    /**
     * Everything Trip Detail renders, in reading order: stops sorted by day
     * then position within the day, each with its photos already resolved to
     * loadable URLs.
     */
    suspend fun fetchTripDetail(tripId: String): TripDetail {
        val trip = fetchTrip(tripId)
        val author = postgrest.from("users")
            .select { filter { eq("id", trip.authorId) } }
            .decodeSingleOrNull<User>()
        val days = postgrest.from("days")
            .select { filter { eq("trip_id", tripId) } }
            .decodeList<Day>()
        val dayById = days.associateBy { it.id }
        val stops = fetchStopsForTrip(tripId).sortedWith(
            compareBy({ dayById[it.dayId]?.dayIndex ?: Int.MAX_VALUE }, { it.orderInDay })
        )

        val photosByStop = if (stops.isEmpty()) {
            emptyMap()
        } else {
            postgrest.from("stop_photos")
                .select {
                    filter { isIn("stop_id", stops.map { it.id }) }
                    order("order_index", Order.ASCENDING)
                }
                .decodeList<StopPhoto>()
                .groupBy({ it.stopId }, { resolvePhotoUrl(client, it.storagePath) })
        }

        val sections = stops
            .groupBy { it.dayId }
            .map { (dayId, dayStops) ->
                val day = dayById[dayId]
                TripDaySection(
                    dayIndex = day?.dayIndex,
                    date = day?.date ?: day?.let { trip.startDate.plus(it.dayIndex - 1, DateTimeUnit.DAY) },
                    stops = dayStops.map { StopWithPhotos(it, photosByStop[it.id].orEmpty()) }
                )
            }

        return TripDetail(trip = trip.copy(author = author, stopCount = stops.size), days = sections)
    }

    suspend fun fetchStopsForTrip(tripId: String): List<Stop> =
        postgrest.from("stops")
            .select(columns = STOP_RETURN_COLUMNS) {
                filter { eq("trip_id", tripId) }
                order("order_in_day", Order.ASCENDING)
            }
            .decodeList()

    /**
     * Publishes a whole trip in one shot: creates the trip row, one day per
     * distinct day index used by [stops], every stop, uploads + inserts every
     * photo, then backfills cover_photo_url from the first stop's first photo.
     */
    // --- Drafts -------------------------------------------------------------
    //
    // A trip exists as rows from the first screen of the create flow: the
    // builder writes stops and photos as you go, and publishing flips the
    // status. That's what makes a half-finished trip survive closing the app -
    // photo URIs from the picker stop being readable once the process dies, so
    // an in-memory draft couldn't be resumed.

    suspend fun createDraftTrip(
        authorId: String,
        title: String,
        destination: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Trip = insertTrip(
        NewTripRow(
            id = UUID.randomUUID().toString(),
            authorId = authorId,
            title = title,
            destination = destination,
            startDate = startDate,
            endDate = endDate,
            status = TripStatus.DRAFT
        )
    )

    /**
     * Inserts, then reads the row back in a second request rather than with
     * `insert ... returning`. The trips read policy goes through
     * private.trip_is_visible(), a STABLE function that can't see a row inserted
     * by the same statement - so returning it fails as an RLS violation, and no
     * trip could be created. The id is made here so the read-back can find it.
     */
    private suspend fun insertTrip(row: NewTripRow): Trip {
        postgrest.from("trips").insert(row)
        return fetchTrip(row.id)
    }

    suspend fun updateTripBasics(
        tripId: String,
        title: String,
        destination: String,
        startDate: LocalDate,
        endDate: LocalDate
    ) {
        postgrest.from("trips")
            .update(TripBasicsRow(title, destination, startDate, endDate)) { filter { eq("id", tripId) } }
    }

    /**
     * A trip that starts now and is posted a day at a time.
     *
     * end_date is required by the schema but unknown until the trip ends, so it
     * starts equal to [startDate] and [endTrip] sets the real one. Nothing about
     * the trip is visible to anyone else until a day is posted - the days policy
     * enforces that, not this.
     */
    suspend fun createLiveTrip(authorId: String, title: String, destination: String, startDate: LocalDate): Trip =
        insertTrip(
            NewTripRow(
                id = UUID.randomUUID().toString(),
                authorId = authorId,
                title = title,
                destination = destination,
                startDate = startDate,
                endDate = startDate,
                status = TripStatus.LIVE
            )
        )

    /** Your trips that are under way, newest first. */
    suspend fun fetchLiveTrips(authorId: String): List<Trip> =
        postgrest.from("trips")
            .select {
                filter {
                    eq("author_id", authorId)
                    eq("status", "live")
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()

    /** A trip's day rows, with their publish state. */
    suspend fun fetchDays(tripId: String): List<Day> =
        postgrest.from("days")
            .select {
                filter { eq("trip_id", tripId) }
                order("day_index", Order.ASCENDING)
            }
            .decodeList()

    /**
     * Makes one day of a live trip public. Only an unposted day is touched, so
     * posting twice can't move a day's published_at - and with it its place in
     * the feed. Editing a posted day never comes through here.
     */
    suspend fun postDay(dayId: String) {
        postgrest.from("days")
            .update(DayPublishRow(publishedAt = Clock.System.now().toString(), publishedVia = DayPublishSource.POST_DAY)) {
                filter {
                    eq("id", dayId)
                    exact("published_at", null)
                }
            }
    }

    /**
     * Finishes a live trip: every unposted day that has stops goes public as
     * 'end_trip' (so it never becomes a feed item of its own), then the trip
     * becomes completed with its real end date and a cover.
     *
     * Days first: if the trip update then fails, the trip is still live with a
     * few more days showing, which ending again completes. The other order could
     * leave a completed trip with days nobody can see.
     */
    suspend fun endTrip(tripId: String, endDate: LocalDate): Trip {
        val detail = fetchTripDetail(tripId)
        val stops = detail.days.flatMap { it.stops }
        require(stops.isNotEmpty()) { "A trip needs at least one place before it can end" }

        val daysWithStops = stops.mapNotNull { it.stop.dayId }.toSet()
        val toPublish = fetchDays(tripId).filter { !it.isPublished && it.id in daysWithStops }.map { it.id }
        if (toPublish.isNotEmpty()) {
            postgrest.from("days")
                .update(DayPublishRow(publishedAt = Clock.System.now().toString(), publishedVia = DayPublishSource.END_TRIP)) {
                    filter {
                        isIn("id", toPublish)
                        exact("published_at", null)
                    }
                }
        }

        postgrest.from("trips")
            .update(
                EndTripRow(
                    status = TripStatus.COMPLETED,
                    completedAt = Clock.System.now().toString(),
                    endDate = endDate,
                    coverPhotoUrl = detail.trip.coverPhotoUrl ?: deriveCover(stops)
                )
            ) { filter { eq("id", tripId) } }
        return fetchTrip(tripId)
    }

    /** Unfinished trips, newest first. */
    suspend fun fetchDrafts(authorId: String): List<Trip> =
        postgrest.from("trips")
            .select {
                filter {
                    eq("author_id", authorId)
                    eq("status", "draft")
                }
                order("updated_at", Order.DESCENDING)
            }
            .decodeList()

    suspend fun deleteTrip(tripId: String) {
        postgrest.from("trips").delete { filter { eq("id", tripId) } }
    }

    /** The day row for [dayIndex], created on demand. */
    suspend fun ensureDay(tripId: String, dayIndex: Int): String {
        val existing = postgrest.from("days")
            .select {
                filter {
                    eq("trip_id", tripId)
                    eq("day_index", dayIndex)
                }
            }
            .decodeSingleOrNull<Day>()
        if (existing != null) return existing.id
        return postgrest.from("days")
            .insert(NewDayRow(tripId = tripId, dayIndex = dayIndex)) { select() }
            .decodeSingle<Day>()
            .id
    }

    suspend fun addStop(
        tripId: String,
        dayIndex: Int,
        orderInDay: Int,
        name: String,
        category: StopCategory,
        latitude: Double,
        longitude: Double,
        caption: String = "",
        tips: String = "",
        arrivalTime: LocalTime? = null
    ): Stop =
        postgrest.from("stops")
            .insert(
                NewStopRow(
                    dayId = ensureDay(tripId, dayIndex),
                    tripId = tripId,
                    name = name,
                    category = category,
                    location = "SRID=4326;POINT($longitude $latitude)",
                    orderInDay = orderInDay,
                    caption = caption.ifBlank { null },
                    tips = tips.ifBlank { null },
                    arrivalTime = arrivalTime,
                    placeName = name
                )
            ) { select(columns = STOP_RETURN_COLUMNS) }
            .decodeSingle()

    suspend fun updateStopDetails(
        stopId: String,
        name: String,
        category: StopCategory,
        caption: String,
        tips: String,
        arrivalTime: LocalTime?
    ) {
        postgrest.from("stops")
            .update(
                StopDetailsRow(
                    name = name,
                    placeName = name,
                    category = category,
                    caption = caption.ifBlank { null },
                    tips = tips.ifBlank { null },
                    arrivalTime = arrivalTime
                )
            ) { filter { eq("id", stopId) } }
    }

    /** Where a stop sits: which day, and its position within that day. */
    suspend fun setStopPlacement(tripId: String, stopId: String, dayIndex: Int, orderInDay: Int) {
        postgrest.from("stops")
            .update(StopPlacementRow(dayId = ensureDay(tripId, dayIndex), orderInDay = orderInDay)) {
                filter { eq("id", stopId) }
            }
    }

    suspend fun deleteStop(stopId: String) {
        postgrest.from("stops").delete { filter { eq("id", stopId) } }
    }

    /** Uploads photos for a stop right away, so a draft keeps them. Returns how many landed. */
    suspend fun addStopPhotos(
        tripId: String,
        stopId: String,
        startIndex: Int,
        photos: List<Pair<android.net.Uri, Instant?>>,
        photoStorage: PhotoStorageRepository
    ): Int {
        var added = 0
        photos.forEachIndexed { index, (uri, takenAt) ->
            val storagePath = photoStorage.upload(tripId, stopId, startIndex + index, uri)
            postgrest.from("stop_photos").insert(
                NewStopPhotoRow(
                    stopId = stopId,
                    storagePath = storagePath,
                    takenAt = takenAt,
                    orderIndex = startIndex + index
                )
            )
            added++
        }
        return added
    }

    suspend fun deleteStopPhoto(photoId: String) {
        postgrest.from("stop_photos").delete { filter { eq("id", photoId) } }
    }

    /**
     * Publishes a draft as a finished trip: every day goes public as 'import',
     * status flips to completed, completed_at is stamped, and [deriveCover]
     * picks the cover if there isn't one.
     *
     * This is the import-a-finished-trip path. A live trip reaches completed by
     * being ended instead, which publishes its remaining days on the way.
     */
    suspend fun publishDraft(tripId: String, photoStorage: PhotoStorageRepository): Trip {
        val detail = fetchTripDetail(tripId)
        val stops = detail.days.flatMap { it.stops }
        require(stops.isNotEmpty()) { "A trip needs at least one stop to publish" }

        // Every day goes public with the trip, marked 'import' so none of them
        // turns into a day feed item. Without this the days policy hides them -
        // and every stop on them - from everyone but the author.
        postgrest.from("days")
            .update(DayPublishRow(publishedAt = Clock.System.now().toString(), publishedVia = DayPublishSource.IMPORT)) {
                filter {
                    eq("trip_id", tripId)
                    exact("published_at", null)
                }
            }

        val cover = detail.trip.coverPhotoUrl ?: deriveCover(stops)
        postgrest.from("trips")
            .update(
                PublishRow(
                    status = TripStatus.COMPLETED,
                    coverPhotoUrl = cover,
                    completedAt = Clock.System.now().toString()
                )
            ) {
                filter { eq("id", tripId) }
            }
        return fetchTrip(tripId)
    }

}

data class TripDetail(
    val trip: Trip,
    val days: List<TripDaySection>
)

/** One day's stops. [dayIndex] is null only for stops whose day was deleted. */
data class TripDaySection(
    val dayIndex: Int?,
    val date: LocalDate?,
    val stops: List<StopWithPhotos>
)

data class StopWithPhotos(
    val stop: Stop,
    val photoUrls: List<String>
)

data class TripMapPin(
    val trip: Trip,
    val latitude: Double,
    val longitude: Double
)

data class StopPoint(
    val tripId: String,
    val latitude: Double,
    val longitude: Double
)

data class TripCompleteness(
    val stopCount: Int,
    val captionedStopCount: Int,
    /** In itinerary order; the feed shows the first few. */
    val stopNames: List<String> = emptyList()
)

/** What the feed needs to know about a trip's stops. */
data class StopSummary(
    val count: Int,
    /** In itinerary order. */
    val names: List<String> = emptyList()
)

data class TripSearchParams(
    val query: String? = null,
    val budgetTag: BudgetTag? = null,
    val seasonTag: SeasonTag? = null,
    /** Non-null restricts results to these authors (the "Following" scope). */
    val authorIds: List<String>? = null,
    val limit: Long = 200
)

private fun BudgetTag.dbValue(): String = when (this) {
    BudgetTag.BUDGET -> "budget"
    BudgetTag.MID_RANGE -> "mid_range"
    BudgetTag.LUXURY -> "luxury"
}

private fun SeasonTag.dbValue(): String = when (this) {
    SeasonTag.SPRING -> "spring"
    SeasonTag.SUMMER -> "summer"
    SeasonTag.FALL -> "fall"
    SeasonTag.WINTER -> "winter"
}

@Serializable
private data class StopCaptionRow(
    @SerialName("trip_id") val tripId: String,
    val caption: String? = null,
    val name: String = "",
    @SerialName("order_in_day") val orderInDay: Int = 0
)

@Serializable
private data class StopNameRow(
    @SerialName("trip_id") val tripId: String,
    val name: String = "",
    @SerialName("order_in_day") val orderInDay: Int = 0
)

@Serializable
private data class StopLocationRow(
    @SerialName("trip_id") val tripId: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("order_in_day") val orderInDay: Int
)

@Serializable
private data class NewTripRow(
    val id: String,
    @SerialName("author_id") val authorId: String,
    val title: String,
    val destination: String,
    @SerialName("start_date") val startDate: LocalDate,
    @SerialName("end_date") val endDate: LocalDate,
    val status: TripStatus = TripStatus.DRAFT,
    val visibility: TripVisibility = TripVisibility.PUBLIC
)

@Serializable
private data class NewDayRow(
    @SerialName("trip_id") val tripId: String,
    @SerialName("day_index") val dayIndex: Int
)

@Serializable
private data class NewStopRow(
    @SerialName("day_id") val dayId: String,
    @SerialName("trip_id") val tripId: String,
    val name: String,
    val category: StopCategory,
    val location: String,
    @SerialName("order_in_day") val orderInDay: Int,
    val caption: String? = null,
    val tips: String? = null,
    @SerialName("arrival_time") val arrivalTime: LocalTime? = null,
    @SerialName("place_name") val placeName: String? = null
)

@Serializable
private data class NewStopPhotoRow(
    @SerialName("stop_id") val stopId: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("taken_at") val takenAt: Instant? = null,
    @SerialName("order_index") val orderIndex: Int
)

@Serializable
private data class TripBasicsRow(
    val title: String,
    val destination: String,
    @SerialName("start_date") val startDate: LocalDate,
    @SerialName("end_date") val endDate: LocalDate
)

@Serializable
private data class StopDetailsRow(
    val name: String,
    @SerialName("place_name") val placeName: String,
    val category: StopCategory,
    val caption: String?,
    val tips: String?,
    @SerialName("arrival_time") val arrivalTime: LocalTime?
)

@Serializable
private data class StopPlacementRow(
    @SerialName("day_id") val dayId: String,
    @SerialName("order_in_day") val orderInDay: Int
)

@Serializable
private data class DayPublishRow(
    @SerialName("published_at") val publishedAt: String,
    @SerialName("published_via") val publishedVia: DayPublishSource
)

@Serializable
private data class EndTripRow(
    val status: TripStatus,
    @SerialName("completed_at") val completedAt: String,
    @SerialName("end_date") val endDate: LocalDate,
    @SerialName("cover_photo_url") val coverPhotoUrl: String?
)

@Serializable
private data class PublishRow(
    val status: TripStatus,
    @SerialName("cover_photo_url") val coverPhotoUrl: String?,
    @SerialName("completed_at") val completedAt: String
)
