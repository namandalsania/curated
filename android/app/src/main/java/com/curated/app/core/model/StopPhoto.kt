package com.curated.app.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StopPhoto(
    val id: String,
    @SerialName("stop_id") val stopId: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("taken_at") val takenAt: Instant? = null,
    @SerialName("order_index") val orderIndex: Int,
    @SerialName("created_at") val createdAt: Instant
)
