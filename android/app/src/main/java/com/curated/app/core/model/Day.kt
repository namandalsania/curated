package com.curated.app.core.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a day became public. Null until it is. */
@Serializable
enum class DayPublishSource {
    /** Posted by its author while the trip was live — these become feed items. */
    @SerialName("post_day") POST_DAY,

    /** Swept up when the trip ended. Deliberately not a feed item. */
    @SerialName("end_trip") END_TRIP,

    /** A day of a trip written in one go, or one that predates live posting. */
    @SerialName("import") IMPORT
}

@Serializable
data class Day(
    val id: String,
    @SerialName("trip_id") val tripId: String,
    @SerialName("day_index") val dayIndex: Int,
    val date: LocalDate? = null,
    @SerialName("published_at") val publishedAt: Instant? = null,
    @SerialName("published_via") val publishedVia: DayPublishSource? = null
) {
    /** Non-owners can only see a day once this is true; the database enforces it. */
    val isPublished: Boolean get() = publishedAt != null
}
