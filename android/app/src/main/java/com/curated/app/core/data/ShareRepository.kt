package com.curated.app.core.data

import com.curated.app.core.model.Trip
import com.curated.app.core.model.TripShare
import com.curated.app.core.model.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/** Trips people have sent each other inside the app. */
class ShareRepository(private val client: SupabaseClient) {

    private val postgrest get() = client.postgrest

    /** Someone's inbox: trips sent to them, newest first, with sender and trip filled in. */
    suspend fun fetchInbox(userId: String): List<TripShare> {
        val shares = postgrest.from("trip_shares")
            .select {
                filter { eq("recipient_id", userId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<TripShare>()
        if (shares.isEmpty()) return shares

        val senders = postgrest.from("users")
            .select { filter { isIn("id", shares.map { it.senderId }.distinct()) } }
            .decodeList<User>()
            .associateBy { it.id }
        // A trip that's since been deleted or made private simply drops out.
        val trips = postgrest.from("trips")
            .select { filter { isIn("id", shares.map { it.tripId }.distinct()) } }
            .decodeList<Trip>()
            .associateBy { it.id }

        return shares.map { it.copy(sender = senders[it.senderId], trip = trips[it.tripId]) }
    }

    /** Unread = never opened the inbox since it arrived (read_at is null). */
    suspend fun unreadCount(userId: String): Int =
        postgrest.from("trip_shares")
            .select(columns = Columns.raw("id")) {
                filter {
                    eq("recipient_id", userId)
                    exact("read_at", null)
                }
            }
            .decodeList<IdOnlyRow>()
            .size

    suspend fun send(tripId: String, senderId: String, recipientIds: List<String>, note: String?) {
        val clean = note?.trim()?.takeIf { it.isNotEmpty() }
        val rows = recipientIds.distinct().filter { it != senderId }.map {
            NewShareRow(tripId = tripId, senderId = senderId, recipientId = it, note = clean)
        }
        if (rows.isEmpty()) return
        postgrest.from("trip_shares").insert(rows)
    }

    suspend fun markAllRead(userId: String) {
        postgrest.from("trip_shares")
            .update(ReadRow(Clock.System.now().toString())) {
                filter {
                    eq("recipient_id", userId)
                    exact("read_at", null)
                }
            }
    }

    suspend fun delete(shareId: String) {
        postgrest.from("trip_shares").delete { filter { eq("id", shareId) } }
    }
}

@Serializable
private data class IdOnlyRow(val id: String)

@Serializable
private data class NewShareRow(
    @SerialName("trip_id") val tripId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    val note: String?
)

@Serializable
private data class ReadRow(@SerialName("read_at") val readAt: String)
