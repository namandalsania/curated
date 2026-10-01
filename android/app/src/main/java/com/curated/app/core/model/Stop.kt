package com.curated.app.core.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class StopCategory {
    @SerialName("food") FOOD,
    @SerialName("sight") SIGHT,
    @SerialName("hotel") HOTEL,
    @SerialName("transport") TRANSPORT,
    @SerialName("other") OTHER
}

@Serializable
data class Stop(
    val id: String,
    @SerialName("day_id") val dayId: String? = null,
    @SerialName("trip_id") val tripId: String,
    val name: String,
    val category: StopCategory,
    val latitude: Double,
    val longitude: Double,
    @SerialName("order_in_day") val orderInDay: Int,
    val caption: String? = null,
    val cost: Double? = null,
    val tips: String? = null,
    @SerialName("place_name") val placeName: String? = null,
    /** Local wall-clock arrival time at the stop (Postgres `time`, no zone). */
    @SerialName("arrival_time") val arrivalTime: LocalTime? = null,
    @SerialName("created_at") val createdAt: Instant
)
