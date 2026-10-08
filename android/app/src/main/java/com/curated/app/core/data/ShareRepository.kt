package com.curated.app.core.data

import com.curated.app.core.model.Trip
import com.curated.app.core.model.TripShare
import com.curated.app.core.model.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
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

    /**
     * Sends [tripId] to each recipient. Sending a trip someone already has from
     * you is a success, not an error: the table allows one share per sender,
     * recipient and trip, so the repeat is refused as a duplicate and ignored.
     * One insert per recipient, so one repeat doesn't sink the others.
     */
    suspend fun send(tripId: String, senderId: String, recipientIds: List<String>, note: String?) {
        val clean = note?.trim()?.takeIf { it.isNotEmpty() }
        recipientIds.distinct().filter { it != senderId }.forEach { recipientId ->
            try {
                postgrest.from("trip_shares").insert(
                    NewShareRow(tripId = tripId, senderId = senderId, recipientId = recipientId, note = clean)
                )
            } catch (e: PostgrestRestException) {
                if (e.code != UNIQUE_VIOLATION) throw e
            }
        }
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

/** Postgres unique_violation, as PostgREST reports it. */
private const val UNIQUE_VIOLATION = "23505"

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
