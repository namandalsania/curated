package com.curated.app.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** A comment on one place inside a trip - "is this still open on Mondays?". */
@Serializable
data class StopComment(
    val id: String,
    @SerialName("stop_id") val stopId: String,
    @SerialName("trip_id") val tripId: String,
    @SerialName("author_id") val authorId: String,
    val body: String,
    @SerialName("created_at") val createdAt: Instant,
    @Transient val author: User? = null
)

/** A trip someone sent to another user's inbox. */
@Serializable
data class TripShare(
    val id: String,
    @SerialName("trip_id") val tripId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    val note: String? = null,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("read_at") val readAt: Instant? = null,
    @Transient val sender: User? = null,
    @Transient val trip: Trip? = null
)
