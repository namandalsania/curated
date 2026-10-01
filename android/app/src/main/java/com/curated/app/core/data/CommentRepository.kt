package com.curated.app.core.data

import com.curated.app.core.model.StopComment
import com.curated.app.core.model.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Comments on individual places. Anyone who can see the trip can read and add them. */
class CommentRepository(private val client: SupabaseClient) {

    private val postgrest get() = client.postgrest

    /** How many comments each place on a trip has, for the counts on the stop cards. */
    suspend fun countsForTrip(tripId: String): Map<String, Int> =
        postgrest.from("stop_comments")
            .select(columns = Columns.raw("stop_id")) { filter { eq("trip_id", tripId) } }
            .decodeList<StopIdOnlyRow>()
            .groupingBy { it.stopId }
            .eachCount()

    /** Comment counts per trip, for the feed cards. */
    suspend fun countsForTrips(tripIds: List<String>): Map<String, Int> {
        if (tripIds.isEmpty()) return emptyMap()
        return postgrest.from("stop_comments")
            .select(columns = Columns.raw("trip_id")) { filter { isIn("trip_id", tripIds) } }
            .decodeList<CommentTripIdRow>()
            .groupingBy { it.tripId }
            .eachCount()
    }

    suspend fun fetchForStop(stopId: String): List<StopComment> {
        val comments = postgrest.from("stop_comments")
            .select {
                filter { eq("stop_id", stopId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList<StopComment>()
        if (comments.isEmpty()) return comments
        val authors = postgrest.from("users")
            .select { filter { isIn("id", comments.map { it.authorId }.distinct()) } }
            .decodeList<User>()
            .associateBy { it.id }
        return comments.map { it.copy(author = authors[it.authorId]) }
    }

    suspend fun add(stopId: String, tripId: String, authorId: String, body: String): StopComment =
        postgrest.from("stop_comments")
            .insert(NewCommentRow(stopId = stopId, tripId = tripId, authorId = authorId, body = body.trim())) { select() }
            .decodeSingle()

    /** Allowed for the comment's author, and for the trip's author moderating. */
    suspend fun delete(commentId: String) {
        postgrest.from("stop_comments").delete { filter { eq("id", commentId) } }
    }

    fun openStopChannel(stopId: String): RealtimeChannel = client.channel("stop-comments-$stopId")

    /** Fires when anyone comments on this place while the thread is open. */
    fun observeComments(channel: RealtimeChannel, stopId: String): Flow<Unit> =
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "stop_comments"
            filter("stop_id", FilterOperator.EQ, stopId)
        }.map { }

    suspend fun closeChannel(channel: RealtimeChannel) {
        runCatching { channel.unsubscribe() }
    }
}

@Serializable
private data class StopIdOnlyRow(@SerialName("stop_id") val stopId: String)

@Serializable
private data class CommentTripIdRow(@SerialName("trip_id") val tripId: String)

@Serializable
private data class NewCommentRow(
    @SerialName("stop_id") val stopId: String,
    @SerialName("trip_id") val tripId: String,
    @SerialName("author_id") val authorId: String,
    val body: String
)
