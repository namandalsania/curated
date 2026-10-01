package com.curated.app.core.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A trip's lifecycle. Orthogonal to [TripVisibility]: a live trip can be private,
 * a completed one unlisted.
 *
 * [LIVE] is a trip being posted a day at a time while it happens - which days are
 * public is per-day state on `days.published_at`, not something this says.
 */
@Serializable
enum class TripStatus {
    @SerialName("draft") DRAFT,
    @SerialName("live") LIVE,
    @SerialName("completed") COMPLETED
}

@Serializable
enum class TripVisibility {
    @SerialName("public") PUBLIC,
    @SerialName("unlisted") UNLISTED,
    @SerialName("private") PRIVATE
}

@Serializable
enum class BudgetTag {
    @SerialName("budget") BUDGET,
    @SerialName("mid_range") MID_RANGE,
    @SerialName("luxury") LUXURY
}

@Serializable
enum class SeasonTag {
    @SerialName("spring") SPRING,
    @SerialName("summer") SUMMER,
    @SerialName("fall") FALL,
    @SerialName("winter") WINTER
}

@Serializable
data class Trip(
    val id: String,
    @SerialName("author_id") val authorId: String,
    val title: String,
    val destination: String,
    @SerialName("start_date") val startDate: LocalDate,
    @SerialName("end_date") val endDate: LocalDate,
    @SerialName("cover_photo_url") val coverPhotoUrl: String? = null,
    @SerialName("budget_tag") val budgetTag: BudgetTag? = null,
    @SerialName("season_tag") val seasonTag: SeasonTag? = null,
    val status: TripStatus = TripStatus.DRAFT,
    val visibility: TripVisibility = TripVisibility.PUBLIC,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
    @SerialName("completed_at") val completedAt: Instant? = null,
    @Transient val stopCount: Int? = null,
    @Transient val author: User? = null
)
