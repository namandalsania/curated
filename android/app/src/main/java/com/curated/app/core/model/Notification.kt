package com.curated.app.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * What a notification is about. The database creates them (see
 * 20261008_notifications_from_triggers.sql); [dbValue] is the `type` column.
 */
@Serializable
enum class NotificationType(val dbValue: String) {
    @SerialName("follow") FOLLOW("follow"),
    @SerialName("new_trip") NEW_TRIP("new_trip"),
    @SerialName("new_day") NEW_DAY("new_day"),
    @SerialName("like") LIKE("like"),
    @SerialName("plan_invite") PLAN_INVITE("plan_invite"),
    @SerialName("stop_comment") STOP_COMMENT("stop_comment"),
    @SerialName("trip_share") TRIP_SHARE("trip_share")
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
