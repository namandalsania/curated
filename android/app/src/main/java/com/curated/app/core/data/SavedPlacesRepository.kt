package com.curated.app.core.data

import com.curated.app.core.geocode.PlaceRef
import com.curated.app.core.model.SavedPlace
import com.curated.app.core.model.StopCategory
import com.curated.app.core.model.Trip
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Places a user saved from anyone's trips. Private: RLS only ever returns the caller's rows. */
class SavedPlacesRepository(private val client: SupabaseClient) {

    private val postgrest get() = client.postgrest

    suspend fun fetchAll(userId: String): List<SavedPlace> =
        postgrest.from("saved_places")
            .select {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()

    /** Which of [stopIds] this user has saved - drives the save icons on a trip. */
    suspend fun savedStopIds(userId: String, stopIds: List<String>): Set<String> {
        if (stopIds.isEmpty()) return emptySet()
        return postgrest.from("saved_places")
            .select(columns = Columns.raw("stop_id")) {
                filter {
                    eq("user_id", userId)
                    isIn("stop_id", stopIds)
                }
            }
            .decodeList<StopIdRow>()
            .mapNotNullTo(HashSet()) { it.stopId }
    }

    /** Copies what the saved place needs from the stop, so it outlives the source trip. */
    suspend fun save(userId: String, trip: Trip, stop: StopWithPhotos, place: PlaceRef?): SavedPlace =
        postgrest.from("saved_places")
            .insert(
                NewSavedPlaceRow(
                    userId = userId,
                    stopId = stop.stop.id,
                    sourceTripId = trip.id,
                    sourceTripTitle = trip.title,
                    sourceAuthorName = trip.author?.displayName,
                    name = stop.stop.name,
                    category = stop.stop.category,
                    latitude = stop.stop.latitude,
                    longitude = stop.stop.longitude,
                    placeName = stop.stop.placeName,
                    photoUrl = stop.photoUrls.firstOrNull(),
                    city = place?.city,
                    countryCode = place?.country?.code,
                    countryName = place?.country?.name
                )
            ) { select() }
            .decodeSingle()

    suspend fun unsaveStop(userId: String, stopId: String) {
        postgrest.from("saved_places").delete {
            filter {
                eq("user_id", userId)
                eq("stop_id", stopId)
            }
        }
    }

    suspend fun delete(savedPlaceId: String) {
        postgrest.from("saved_places").delete { filter { eq("id", savedPlaceId) } }
    }
}

@Serializable
private data class StopIdRow(@SerialName("stop_id") val stopId: String? = null)

@Serializable
private data class NewSavedPlaceRow(
    @SerialName("user_id") val userId: String,
    @SerialName("stop_id") val stopId: String,
    @SerialName("source_trip_id") val sourceTripId: String,
    @SerialName("source_trip_title") val sourceTripTitle: String,
    @SerialName("source_author_name") val sourceAuthorName: String?,
    val name: String,
    val category: StopCategory,
    val latitude: Double,
    val longitude: Double,
    @SerialName("place_name") val placeName: String?,
    @SerialName("photo_url") val photoUrl: String?,
    val city: String?,
    @SerialName("country_code") val countryCode: String?,
    @SerialName("country_name") val countryName: String?
)
