package com.curated.app.features.create

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.cluster.PhotoCluster
import com.curated.app.core.cluster.StopClusteringEngine
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.NotificationRepository
import com.curated.app.core.data.PhotoStorageRepository
import com.curated.app.core.data.SocialRepository
import com.curated.app.core.data.StopWithPhotos
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.core.data.TripDaySection
import com.curated.app.core.data.TripRepository
import com.curated.app.core.geocode.GeocodingService
import com.curated.app.core.model.Day
import com.curated.app.core.model.StopCategory
import com.curated.app.core.model.Trip
import com.curated.app.core.model.TripVisibility
import com.curated.app.core.photo.PhotoExifData
import com.curated.app.core.photo.PhotoExifReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class CreateWizardState(
    /** Null until the draft row exists - the first screen creates it. */
    val tripId: String? = null,
    val title: String = "",
    val destination: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    /** New trips start public, as they always have. */
    val visibility: TripVisibility = TripVisibility.PUBLIC,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isImportingPhotos: Boolean = false,
    val importSummary: String? = null,
    /** Reading picked photos before the trip exists; see [CreateTripViewModel.analyzePhotos]. */
    val isAnalyzingPhotos: Boolean = false,
    /** What the photos said, waiting on the review screen to become a trip. */
    val importReview: ImportReview? = null,
    /** Stops per day, straight from the draft rows. */
    val days: List<TripDaySection> = emptyList(),
    val isPublishing: Boolean = false,
    val error: String? = null,
    val publishedTrip: Trip? = null,
    /** Posted a day at a time rather than built and published in one go. */
    val isLive: Boolean = false,
    /** A live trip's day rows, for their publish state. */
    val liveDays: List<Day> = emptyList(),
    /** Days a live trip shows - up to today, see [LiveTripRules.dayCount]. */
    val liveDayCount: Int = 1,
    val postingDay: Int? = null,
    /** Set when a day goes public; the Post Day screen closes on it. */
    val justPostedDay: Int? = null,
    /** Why the last Post Day was refused, shown inline on that screen. */
    val postProblem: LiveTripRules.PostProblem? = null,
    val isEnding: Boolean = false,
    val endedTrip: Trip? = null
) {
    val dayCount: Int
        get() {
            if (isLive) return liveDayCount
            val start = startDate
            val end = endDate
            return if (start != null && end != null) {
                (end.toEpochDays() - start.toEpochDays() + 1).toInt().coerceAtLeast(1)
            } else {
                1
            }
        }

    fun stopsOn(dayIndex: Int): List<StopWithPhotos> =
        days.firstOrNull { it.dayIndex == dayIndex }?.stops.orEmpty()

    fun dateOf(dayIndex: Int): LocalDate? = startDate?.plusDays(dayIndex - 1)

    val stopCount: Int get() = days.sumOf { it.stops.size }

    val canPublish: Boolean get() = stopCount > 0 && !isPublishing

    fun dayRow(dayIndex: Int): Day? = liveDays.firstOrNull { it.dayIndex == dayIndex }

    fun isPosted(dayIndex: Int): Boolean = dayRow(dayIndex)?.isPublished == true

    /** Stops whose day was deleted out from under them. They can't be posted until moved. */
    val unassignedStops: List<StopWithPhotos> get() = days.filter { it.dayIndex == null }.flatMap { it.stops }

    val daysEndTripWillPublish: List<Int> get() = LiveTripRules.daysEndTripWillPublish(liveDays, days)
}

/**
 * Picked photos, read but not yet saved: the trip they suggest, for the review
 * screen to show pre-filled. Blank or null where the photos didn't say.
 */
data class ImportReview(
    val photoCount: Int,
    val stopCount: Int,
    val withoutLocation: Int,
    val destination: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?
)

/** A group of photos that will become one stop, already named. */
private class NamedCluster(val cluster: PhotoCluster, val name: String)

private fun LocalDate.plusDays(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() + days)

/**
 * The create flow, backed by a real draft trip: rows are written as you build,
 * so closing the app doesn't lose the trip and photos survive (their local URIs
 * wouldn't). Publishing flips the status.
 */
class CreateTripViewModel(
    private val authRepository: AuthRepository,
    private val tripRepository: TripRepository,
    private val geocodingService: GeocodingService,
    private val photoStorageRepository: PhotoStorageRepository,
    private val socialRepository: SocialRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreateWizardState())
    val state: StateFlow<CreateWizardState> = _state

    /** One write at a time, so reorders can't race each other. */
    private val writeLock = Mutex()

    /** The stops [analyzePhotos] found, written once the review screen creates the trip. */
    private var pendingStops: List<NamedCluster> = emptyList()

    /** Opens an existing draft from Profile - Drafts. */
    fun resumeDraft(tripId: String) {
        if (_state.value.tripId == tripId && _state.value.days.isNotEmpty()) return
        _state.update { it.copy(tripId = tripId, isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val detail = tripRepository.fetchTripDetail(tripId)
                _state.update {
                    it.copy(
                        isLoading = false,
                        tripId = tripId,
                        title = detail.trip.title,
                        destination = detail.trip.destination,
                        startDate = detail.trip.startDate,
                        endDate = detail.trip.endDate,
                        visibility = detail.trip.visibility,
                        days = detail.days
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't open draft $tripId", e)
                _state.update { it.copy(isLoading = false, error = "Couldn't open this draft.") }
            }
        }
    }

    private fun reload() {
        val tripId = _state.value.tripId ?: return
        viewModelScope.launch {
            runCatching {
                val detail = tripRepository.fetchTripDetail(tripId)
                val days = if (_state.value.isLive) tripRepository.fetchDays(tripId) else null
                detail to days
            }
                .onSuccess { (detail, days) ->
                    _state.update {
                        if (days == null) {
                            it.copy(days = detail.days)
                        } else {
                            it.copy(
                                days = detail.days,
                                liveDays = days,
                                liveDayCount = LiveTripRules.dayCount(detail.trip.startDate, today(), days, detail.days)
                            )
                        }
                    }
                }
                .onFailure { Log.w(TAG, "Couldn't reload the draft", it) }
        }
    }

    /**
     * Reads EXIF for every picked photo, groups the geo-tagged ones into stops,
     * and writes them into the draft - each on the day its photos were taken.
     */
    fun importPhotos(context: Context, uris: List<Uri>) {
        val tripId = _state.value.tripId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isImportingPhotos = true, importSummary = null, error = null) }
            try {
                val exifData = withContext(Dispatchers.Default) { uris.map { PhotoExifReader.read(context, it) } }
                val stopsAdded = addClusteredStops(tripId, nameClusters(exifData)) { photos -> dayIndexFor(photos) }
                _state.update {
                    it.copy(isImportingPhotos = false, importSummary = importSummary(stopsAdded, exifData.count { !it.hasLocation }, otherDays = 0))
                }
                reload()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't import photos", e)
                _state.update { it.copy(isImportingPhotos = false, error = "Couldn't read those photos. Try again.") }
            }
        }
    }

    /**
     * Post Day's photo import: the same clustering, but only photos taken on
     * [dayIndex]'s date are used, and every stop lands on that day.
     */
    fun importPhotosForDay(context: Context, uris: List<Uri>, dayIndex: Int) {
        val tripId = _state.value.tripId ?: return
        val date = _state.value.dateOf(dayIndex) ?: return
        viewModelScope.launch {
            _state.update { it.copy(isImportingPhotos = true, importSummary = null, error = null, postProblem = null) }
            try {
                val exifData = withContext(Dispatchers.Default) { uris.map { PhotoExifReader.read(context, it) } }
                val (sameDay, otherDays) = LiveTripRules.splitByDay(exifData, date, TimeZone.currentSystemDefault()) { it.takenAt }
                val stopsAdded = addClusteredStops(tripId, nameClusters(sameDay)) { dayIndex }
                _state.update {
                    it.copy(isImportingPhotos = false, importSummary = importSummary(stopsAdded, sameDay.count { !it.hasLocation }, otherDays.size, dayIndex))
                }
                reload()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't import photos for day $dayIndex", e)
                _state.update { it.copy(isImportingPhotos = false, error = "Couldn't read those photos. Try again.") }
            }
        }
    }

    /**
     * The first half of "Build it from your photos", before any trip exists:
     * reads the photos, groups them into stops and names them, and works out
     * the destination and dates for [ImportReview]. Nothing is written until
     * [createImportedTrip].
     */
    fun analyzePhotos(context: Context, uris: List<Uri>) {
        pendingStops = emptyList()
        _state.update { CreateWizardState(isAnalyzingPhotos = true) }
        viewModelScope.launch {
            try {
                val exifData = withContext(Dispatchers.Default) { uris.map { PhotoExifReader.read(context, it) } }
                val named = nameClusters(exifData)
                val places = sampleForDestination(named.map { it.cluster }).mapNotNull {
                    geocodingService.reverseGeocodePlace(it.centerLatitude, it.centerLongitude)
                }
                val dates = TripSuggestions.dateRange(exifData.map { it.takenAt }, TimeZone.currentSystemDefault())
                pendingStops = named
                _state.update {
                    it.copy(
                        isAnalyzingPhotos = false,
                        importReview = ImportReview(
                            photoCount = exifData.size,
                            stopCount = named.size,
                            withoutLocation = exifData.count { photo -> !photo.hasLocation },
                            destination = TripSuggestions.destination(places),
                            startDate = dates?.first,
                            endDate = dates?.second
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read the picked photos", e)
                _state.update { it.copy(isAnalyzingPhotos = false, error = "Couldn't read those photos. Try again.") }
            }
        }
    }

    /**
     * Creates the draft from the review screen, then writes the stops
     * [analyzePhotos] found - each on the day its photos were taken.
     */
    fun createImportedTrip(
        title: String,
        destination: String,
        startDate: LocalDate,
        endDate: LocalDate,
        visibility: TripVisibility,
        onReady: () -> Unit
    ) {
        val authorId = authRepository.currentUserId() ?: return
        val review = _state.value.importReview ?: return
        _state.update {
            it.copy(
                title = title,
                destination = destination,
                startDate = startDate,
                endDate = endDate,
                visibility = visibility,
                isSaving = true,
                error = null
            )
        }
        viewModelScope.launch {
            try {
                val trip = tripRepository.createDraftTrip(authorId, title, destination, startDate, endDate, visibility)
                _state.update { it.copy(tripId = trip.id) }
                val stopsAdded = addClusteredStops(trip.id, pendingStops) { photos -> dayIndexFor(photos) }
                pendingStops = emptyList()
                _state.update {
                    it.copy(
                        isSaving = false,
                        importReview = null,
                        importSummary = importSummary(stopsAdded, review.withoutLocation, otherDays = 0)
                    )
                }
                reload()
                onReady()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't create the trip from photos", e)
                _state.update { it.copy(isSaving = false, error = "Couldn't save this trip. Try again.") }
            }
        }
    }

    /** Groups the geo-tagged photos into stops, each named from where it is. */
    private suspend fun nameClusters(exifData: List<PhotoExifData>): List<NamedCluster> {
        val located = exifData.filter { it.hasLocation }
        val clusters = withContext(Dispatchers.Default) { StopClusteringEngine.cluster(located) }
        return clusters.map { cluster ->
            val name = geocodingService.reverseGeocode(cluster.centerLatitude, cluster.centerLongitude) ?: "Unnamed stop"
            NamedCluster(cluster, name)
        }
    }

    /** Writes named stops into the trip, each on the day [dayFor] picks. */
    private suspend fun addClusteredStops(
        tripId: String,
        clusters: List<NamedCluster>,
        dayFor: (List<PhotoExifData>) -> Int
    ): Int {
        writeLock.withLock {
            // Counted as we go: several clusters can land on the same day before
            // the state reloads.
            val addedPerDay = mutableMapOf<Int, Int>()
            for (named in clusters) {
                val cluster = named.cluster
                val dayIndex = dayFor(cluster.photos)
                val alreadyAdded = addedPerDay.getOrDefault(dayIndex, 0)
                val stop = tripRepository.addStop(
                    tripId = tripId,
                    dayIndex = dayIndex,
                    orderInDay = _state.value.stopsOn(dayIndex).size + alreadyAdded,
                    name = named.name,
                    category = StopCategory.OTHER,
                    latitude = cluster.centerLatitude,
                    longitude = cluster.centerLongitude,
                    arrivalTime = cluster.photos.firstNotNullOfOrNull { photo -> photo.takenAt?.localTime() }
                )
                addedPerDay[dayIndex] = alreadyAdded + 1
                tripRepository.addStopPhotos(
                    tripId = tripId,
                    stopId = stop.id,
                    startIndex = 0,
                    photos = cluster.photos.map { it.uri to it.takenAt },
                    photoStorage = photoStorageRepository
                )
            }
        }
        return clusters.size
    }

    private fun importSummary(stopsAdded: Int, withoutLocation: Int, otherDays: Int, dayIndex: Int? = null): String =
        buildString {
            append(if (stopsAdded == 1) "Added 1 stop" else "Added $stopsAdded stops")
            if (withoutLocation > 0) {
                append(". ")
                append(
                    if (withoutLocation == 1) "1 photo had no location, so add that place yourself."
                    else "$withoutLocation photos had no location, so add those places yourself."
                )
            }
            if (otherDays > 0 && dayIndex != null) {
                append(". ")
                append(
                    if (otherDays == 1) "1 photo was taken on another day, so it wasn't added to Day $dayIndex."
                    else "$otherDays photos were taken on other days, so they weren't added to Day $dayIndex."
                )
            }
        }

    // --- Live trips -----------------------------------------------------------

    /**
     * Creates a live trip starting today, titled from where and when
     * ("Lisbon · October 2026") - [renameTrip] changes it later. Nothing is
     * public until a day is posted.
     */
    fun startLiveTrip(destination: String, onReady: (String) -> Unit) {
        val authorId = authRepository.currentUserId() ?: return
        val place = destination.trim()
        if (place.isEmpty()) return
        val startDate = today()
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val title = TripSuggestions.title(place, startDate)
                val trip = tripRepository.createLiveTrip(authorId, title, place, startDate)
                _state.update {
                    CreateWizardState(
                        tripId = trip.id,
                        title = trip.title,
                        destination = trip.destination,
                        startDate = trip.startDate,
                        endDate = trip.endDate,
                        visibility = trip.visibility,
                        isLive = true,
                        liveDayCount = LiveTripRules.dayCount(trip.startDate, today(), emptyList(), emptyList())
                    )
                }
                onReady(trip.id)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't start a live trip", e)
                _state.update { it.copy(isSaving = false, error = "Couldn't start this trip. Try again.") }
            }
        }
    }

    /** Changes who can see the trip; before the trip exists it's only remembered. */
    fun setVisibility(visibility: TripVisibility) {
        if (visibility == _state.value.visibility) return
        _state.update { it.copy(visibility = visibility) }
        val tripId = _state.value.tripId ?: return
        write { tripRepository.updateTripVisibility(tripId, visibility) }
    }

    /** Renames the trip. A blank title is ignored rather than saved. */
    fun renameTrip(title: String) {
        val tripId = _state.value.tripId ?: return
        val trimmed = title.trim()
        if (trimmed.isEmpty() || trimmed == _state.value.title) return
        _state.update { it.copy(title = trimmed) }
        write { tripRepository.updateTripTitle(tripId, trimmed) }
    }

    /** Opens a live trip, from Profile or straight after starting it. */
    fun openLiveTrip(tripId: String) {
        if (_state.value.tripId == tripId && _state.value.isLive && !_state.value.isLoading) {
            reload()
            return
        }
        _state.update { CreateWizardState(tripId = tripId, isLive = true, isLoading = true) }
        viewModelScope.launch {
            try {
                val detail = tripRepository.fetchTripDetail(tripId)
                val days = tripRepository.fetchDays(tripId)
                _state.update {
                    it.copy(
                        isLoading = false,
                        title = detail.trip.title,
                        destination = detail.trip.destination,
                        startDate = detail.trip.startDate,
                        endDate = detail.trip.endDate,
                        visibility = detail.trip.visibility,
                        days = detail.days,
                        liveDays = days,
                        liveDayCount = LiveTripRules.dayCount(detail.trip.startDate, today(), days, detail.days)
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't open live trip $tripId", e)
                _state.update { it.copy(isLoading = false, error = "Couldn't open this trip.") }
            }
        }
    }

    /**
     * Makes [dayIndex] public. Refused, with the reason in [CreateWizardState.postProblem],
     * when the day is empty or any of its stops isn't attached to a day row.
     */
    fun postDay(dayIndex: Int) {
        val current = _state.value
        val problem = LiveTripRules.postProblem(current.stopsOn(dayIndex).map { it.stop })
        val dayId = current.dayRow(dayIndex)?.id
        if (problem != null || dayId == null) {
            _state.update { it.copy(postProblem = problem ?: LiveTripRules.PostProblem.NoStops) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(postingDay = dayIndex, postProblem = null, error = null) }
            try {
                writeLock.withLock { tripRepository.postDay(dayId) }
                _state.update { it.copy(postingDay = null, justPostedDay = dayIndex) }
                reload()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't post day $dayIndex", e)
                _state.update { it.copy(postingDay = null, error = "Couldn't post Day $dayIndex. Try again.") }
            }
        }
    }

    fun consumeJustPosted() = _state.update { it.copy(justPostedDay = null) }

    fun clearPostProblem() = _state.update { it.copy(postProblem = null, importSummary = null) }

    /** Ends the trip: unposted days with stops go public as 'end_trip', the trip becomes completed. */
    fun endTrip() {
        val tripId = _state.value.tripId ?: return
        val authorId = authRepository.currentUserId() ?: return
        val start = _state.value.startDate ?: return
        if (_state.value.stopCount == 0) {
            _state.update { it.copy(error = "Add at least one place before ending the trip.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isEnding = true, error = null) }
            try {
                val trip = writeLock.withLock {
                    tripRepository.endTrip(tripId, LiveTripRules.endDate(start, _state.value.days))
                }
                // Same as publishing a finished trip: followers hear about the completed trip once.
                if (trip.visibility == TripVisibility.PUBLIC) runCatching {
                    val followerIds = socialRepository.fetchFollowerIds(authorId)
                    notificationRepository.notifyNewTrip(actorId = authorId, tripId = trip.id, followerIds = followerIds)
                }
                _state.update { it.copy(isEnding = false, endedTrip = trip) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't end trip $tripId", e)
                _state.update { it.copy(isEnding = false, error = "Couldn't end this trip. Try again.") }
            }
        }
    }

    private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    /**
     * Up to [DESTINATION_SAMPLES] stops spread across the trip - enough to tell
     * where it was without a geocoder lookup for every stop.
     */
    private fun sampleForDestination(clusters: List<PhotoCluster>): List<PhotoCluster> {
        if (clusters.size <= DESTINATION_SAMPLES) return clusters
        return List(DESTINATION_SAMPLES) { i -> clusters[i * clusters.size / DESTINATION_SAMPLES] }
    }

    /** Which day a cluster belongs on, from when its photos were taken. */
    private fun dayIndexFor(photos: List<PhotoExifData>): Int {
        val start = _state.value.startDate ?: return 1
        val takenOn = photos.firstNotNullOfOrNull { it.takenAt }
            ?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
            ?: return 1
        val offset = (takenOn.toEpochDays() - start.toEpochDays()).toInt()
        return (offset + 1).coerceIn(1, _state.value.dayCount)
    }

    fun addPlace(
        dayIndex: Int,
        placeName: String,
        latitude: Double,
        longitude: Double,
        category: StopCategory = StopCategory.OTHER
    ) {
        val tripId = _state.value.tripId ?: return
        write {
            tripRepository.addStop(
                tripId = tripId,
                dayIndex = dayIndex,
                orderInDay = _state.value.stopsOn(dayIndex).size,
                name = placeName,
                category = category,
                latitude = latitude,
                longitude = longitude
            )
        }
    }

    fun updateStop(
        stopId: String,
        name: String,
        category: StopCategory,
        caption: String,
        tips: String,
        arrivalTime: LocalTime?
    ) = write {
        tripRepository.updateStopDetails(stopId, name, category, caption, tips, arrivalTime)
    }

    fun deleteStop(stopId: String) = write { tripRepository.deleteStop(stopId) }

    fun addPhotosToStop(stopId: String, context: Context, uris: List<Uri>) {
        val tripId = _state.value.tripId ?: return
        if (uris.isEmpty()) return
        val existing = _state.value.days.flatMap { it.stops }.firstOrNull { it.stop.id == stopId }?.photoUrls?.size ?: 0
        write {
            val exif = withContext(Dispatchers.Default) { uris.map { PhotoExifReader.read(context, it) } }
            tripRepository.addStopPhotos(
                tripId = tripId,
                stopId = stopId,
                startIndex = existing,
                photos = exif.map { it.uri to it.takenAt },
                photoStorage = photoStorageRepository
            )
        }
    }

    fun moveStop(dayIndex: Int, stopId: String, offset: Int) {
        val tripId = _state.value.tripId ?: return
        val ordered = _state.value.stopsOn(dayIndex).toMutableList()
        val from = ordered.indexOfFirst { it.stop.id == stopId }
        val to = from + offset
        if (from < 0 || to !in ordered.indices) return
        ordered.add(to, ordered.removeAt(from))
        write {
            ordered.forEachIndexed { index, item ->
                tripRepository.setStopPlacement(tripId, item.stop.id, dayIndex, index)
            }
        }
    }

    fun moveStopToDay(stopId: String, fromDay: Int, toDay: Int) {
        val tripId = _state.value.tripId ?: return
        if (fromDay == toDay) return
        val remaining = _state.value.stopsOn(fromDay).filterNot { it.stop.id == stopId }
        val target = _state.value.stopsOn(toDay)
        write {
            tripRepository.setStopPlacement(tripId, stopId, toDay, target.size)
            remaining.forEachIndexed { index, item ->
                tripRepository.setStopPlacement(tripId, item.stop.id, fromDay, index)
            }
        }
    }

    fun publish() {
        val tripId = _state.value.tripId ?: return
        val authorId = authRepository.currentUserId() ?: return
        if (_state.value.stopCount == 0) {
            _state.update { it.copy(error = "Add at least one place before publishing.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isPublishing = true, error = null) }
            try {
                val trip = tripRepository.publishDraft(tripId, photoStorageRepository)
                // An unlisted or private trip isn't announced: the notification
                // would point followers at something they can't find or open.
                if (trip.visibility == TripVisibility.PUBLIC) runCatching {
                    val followerIds = socialRepository.fetchFollowerIds(authorId)
                    notificationRepository.notifyNewTrip(actorId = authorId, tripId = trip.id, followerIds = followerIds)
                }
                _state.update { it.copy(isPublishing = false, publishedTrip = trip) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't publish trip $tripId", e)
                _state.update { it.copy(isPublishing = false, error = e.message ?: "Couldn't publish this trip.") }
            }
        }
    }

    /** Throws the draft away, rows and all. */
    fun discardDraft(onDone: () -> Unit) {
        val tripId = _state.value.tripId
        viewModelScope.launch {
            if (tripId != null) runCatching { tripRepository.deleteTrip(tripId) }
            _state.update { CreateWizardState() }
            onDone()
        }
    }

    /** Clears everything so the next "New trip" starts fresh, keeping the draft on the server. */
    fun reset() = _state.update { CreateWizardState() }

    fun clearImportSummary() = _state.update { it.copy(importSummary = null) }

    /** Runs a write in order, then reloads the draft so the screen matches the rows. */
    private fun write(block: suspend () -> Unit) {
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                writeLock.withLock { block() }
                _state.update { it.copy(isSaving = false) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save a change to the draft", e)
                _state.update { it.copy(isSaving = false, error = "Couldn't save that change. Try again.") }
            }
            reload()
        }
    }

    companion object {
        private const val TAG = "CreateTripViewModel"
        private const val DESTINATION_SAMPLES = 8

        fun factory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val client = SupabaseProvider.client(appContext)
                CreateTripViewModel(
                    authRepository = AuthRepository(client),
                    tripRepository = TripRepository(client),
                    geocodingService = GeocodingService(appContext),
                    photoStorageRepository = PhotoStorageRepository(client, appContext),
                    socialRepository = SocialRepository(client),
                    notificationRepository = NotificationRepository(client)
                )
            }
        }
    }
}

private fun kotlinx.datetime.Instant.localTime(): LocalTime =
    toLocalDateTime(TimeZone.currentSystemDefault()).time
