package com.curated.app.core.data

import com.curated.app.core.model.Notification
import com.curated.app.core.model.NotificationType
import com.curated.app.core.model.Plan
import com.curated.app.core.model.Trip
import com.curated.app.core.model.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class NotificationRepository(private val client: SupabaseClient) {

    private val postgrest get() = client.postgrest
    private val decodeJson = Json { ignoreUnknownKeys = true }

    suspend fun fetchNotifications(userId: String): List<Notification> {
        val rows = postgrest.from("notifications")
            .select {
                filter {
                    eq("recipient_id", userId)
                    // Only types this build knows: a type added later would
                    // otherwise fail to decode and take the whole list with it.
                    isIn("type", NotificationType.entries.map { it.dbValue })
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Notification>()
        if (rows.isEmpty()) return rows

        val actorIds = rows.mapNotNull { it.actorId }.distinct()
        val tripIds = rows.mapNotNull { it.tripId }.distinct()
        val planIds = rows.mapNotNull { it.planId }.distinct()

        val actors = if (actorIds.isNotEmpty()) {
            postgrest.from("users").select { filter { isIn("id", actorIds) } }.decodeList<User>()
        } else emptyList()
        val trips = if (tripIds.isNotEmpty()) {
            postgrest.from("trips").select { filter { isIn("id", tripIds) } }.decodeList<Trip>()
        } else emptyList()

        // An invitee can read a plan they're invited to; a plan that's since
        // been deleted takes its notification with it (on delete cascade).
        val plans = if (planIds.isNotEmpty()) {
            postgrest.from("plans").select { filter { isIn("id", planIds) } }.decodeList<Plan>()
        } else emptyList()

        val actorById = actors.associateBy { it.id }
        val tripById = trips.associateBy { it.id }
        val planById = plans.associateBy { it.id }

        return rows.map { row ->
            row.copy(
                actor = row.actorId?.let(actorById::get),
                trip = row.tripId?.let(tripById::get),
                plan = row.planId?.let(planById::get)
            )
        }
    }

    /** Marks what's still unread as read. Rows read earlier keep their read_at. */
    suspend fun markAllRead(userId: String) {
        postgrest.from("notifications")
            .update(mapOf("read_at" to Clock.System.now().toString())) {
                filter {
                    eq("recipient_id", userId)
                    exact("read_at", null)
                }
            }
    }

    /**
     * Opens (but does not subscribe) a fresh realtime channel for the caller to manage.
     *
     * The client caches channels by topic and hands back the same one on the next
     * call - already joined, and a joined channel refuses new postgresChangeFlow
     * bindings. A second Home screen in the same process (leaving Home and coming
     * back, or signing out and in) crashed on exactly that, so any cached channel
     * for this topic is removed first.
     */
    suspend fun openNotificationsChannel(userId: String): RealtimeChannel {
        val channelId = "notifications-$userId"
        client.realtime.subscriptions.values
            .filter { it.topic.endsWith(":$channelId") }
            .forEach { client.realtime.removeChannel(it) }
        return client.channel(channelId)
    }

    fun observeInserts(channel: RealtimeChannel, recipientId: String): Flow<Notification> =
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "notifications"
        }.mapNotNull { action ->
            runCatching { decodeJson.decodeFromJsonElement(NotificationRow.serializer(), action.record) }
                .getOrNull()
                ?.takeIf { it.recipientId == recipientId }
                ?.toNotification()
        }
}

@Serializable
private data class NotificationRow(
    val id: String,
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("actor_id") val actorId: String? = null,
    val type: NotificationType,
    @SerialName("trip_id") val tripId: String? = null,
    @SerialName("plan_id") val planId: String? = null,
    @SerialName("stop_id") val stopId: String? = null,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("read_at") val readAt: Instant? = null
) {
    fun toNotification() = Notification(
        id = id,
        recipientId = recipientId,
        actorId = actorId,
        type = type,
        tripId = tripId,
        planId = planId,
        stopId = stopId,
        createdAt = createdAt,
        readAt = readAt
    )
}
