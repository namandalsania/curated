package com.curated.app.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Like(
    @SerialName("user_id") val userId: String,
    @SerialName("trip_id") val tripId: String,
    @SerialName("created_at") val createdAt: Instant
)
