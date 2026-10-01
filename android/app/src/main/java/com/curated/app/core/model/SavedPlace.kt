package com.curated.app.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A place someone saved from a trip - a stay, a castle, a pizza place.
 * What it shows is copied from the stop when saved, so it outlives the
 * source trip; [stopId] and [sourceTripId] go null if that trip is deleted.
 */
@Serializable
data class SavedPlace(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("stop_id") val stopId: String? = null,
    @SerialName("source_trip_id") val sourceTripId: String? = null,
    @SerialName("source_trip_title") val sourceTripTitle: String? = null,
    @SerialName("source_author_name") val sourceAuthorName: String? = null,
    val name: String,
    val category: StopCategory,
    val latitude: Double,
    val longitude: Double,
    @SerialName("place_name") val placeName: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val city: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("country_name") val countryName: String? = null,
    @SerialName("created_at") val createdAt: Instant
)

/** A private, day-by-day plan assembled from saved places. */
@Serializable
data class Plan(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    @SerialName("day_count") val dayCount: Int,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant
)

/**
 * A place in a plan. It carries its own copy of the place, so everyone on a
 * shared plan can see it - and it stays put if whoever added it later
 * unsaves the place. [savedPlaceId] only records which saved place it came from.
 */
@Serializable
data class PlanItem(
    val id: String,
    @SerialName("plan_id") val planId: String,
    @SerialName("saved_place_id") val savedPlaceId: String? = null,
    @SerialName("day_number") val dayNumber: Int,
    val position: Int,
    @SerialName("stop_id") val stopId: String? = null,
    @SerialName("source_trip_id") val sourceTripId: String? = null,
    @SerialName("source_trip_title") val sourceTripTitle: String? = null,
    @SerialName("source_author_name") val sourceAuthorName: String? = null,
    val name: String,
    val category: StopCategory,
    /** Null for a place someone just typed in - a name is enough to plan around. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("place_name") val placeName: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val city: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("country_name") val countryName: String? = null,
    @SerialName("added_by") val addedBy: String? = null
) {
    /** What makes two items "the same place": the source stop if there is one. */
    val placeKey: String get() = stopId ?: savedPlaceId ?: id
}

/** A copy of this saved place, ready to add to [planId] and credited to [addedBy]. */
fun SavedPlace.toPlanItem(id: String, planId: String, dayNumber: Int, addedBy: String) = PlanItem(
    id = id,
    planId = planId,
    savedPlaceId = this.id,
    dayNumber = dayNumber,
    position = 0,
    stopId = stopId,
    sourceTripId = sourceTripId,
    sourceTripTitle = sourceTripTitle,
    sourceAuthorName = sourceAuthorName,
    name = name,
    category = category,
    latitude = latitude,
    longitude = longitude,
    placeName = placeName,
    photoUrl = photoUrl,
    city = city,
    countryCode = countryCode,
    countryName = countryName,
    addedBy = addedBy
)

/** The same place identity as [PlanItem.placeKey], for "is this saved place already in the plan?". */
val SavedPlace.placeKey: String get() = stopId ?: id

/**
 * What someone can do with a plan. The owner is the plan's creator; everyone
 * else joins by invite as a viewer or an editor.
 */
@Serializable
enum class PlanRole {
    @SerialName("owner") OWNER,
    @SerialName("editor") EDITOR,
    @SerialName("viewer") VIEWER;

    val canEdit: Boolean get() = this == OWNER || this == EDITOR

    val label: String get() = when (this) {
        OWNER -> "Owner"
        EDITOR -> "Editor"
        VIEWER -> "Viewer"
    }
}

@Serializable
enum class InviteStatus {
    @SerialName("pending") PENDING,
    @SerialName("accepted") ACCEPTED
}

/** Someone invited to a plan. The owner has no row; see [Plan.userId]. */
@Serializable
data class PlanMember(
    @SerialName("plan_id") val planId: String,
    @SerialName("user_id") val userId: String,
    val role: PlanRole,
    val status: InviteStatus,
    @SerialName("invited_by") val invitedBy: String? = null,
    @SerialName("created_at") val createdAt: Instant,
    @Transient val user: User? = null
)
