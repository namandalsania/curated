package com.curated.app.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class NotificationType {
    @SerialName("follow") FOLLOW,
    @SerialName("new_trip") NEW_TRIP,
    @SerialName("plan_invite") PLAN_INVITE,
    @SerialName("stop_comment") STOP_COMMENT,
    @SerialName("trip_share") TRIP_SHARE
}

@Serializable
data class Notification(
    val id: String,
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("actor_id") val actorId: String? = null,
    val type: NotificationType,
    @SerialName("trip_id") val tripId: String? = null,
    @SerialName("plan_id") val planId: String? = null,
    @SerialName("stop_id") val stopId: String? = null,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("read_at") val readAt: Instant? = null,
    @Transient val actor: User? = null,
    @Transient val trip: Trip? = null,
    @Transient val plan: Plan? = null
)
