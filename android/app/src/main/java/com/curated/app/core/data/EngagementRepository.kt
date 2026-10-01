package com.curated.app.core.data

import com.curated.app.core.model.Trip
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class LikeSummary(val likeCount: Int, val likedByMe: Boolean)

/** Likes + bookmarks: the light engagement layer on top of trips. */
class EngagementRepository(private val client: SupabaseClient) {

    private val postgrest get() = client.postgrest

    suspend fun fetchLikeSummaries(tripIds: List<String>, currentUserId: String): Map<String, LikeSummary> {
        if (tripIds.isEmpty()) return emptyMap()
        val rows = postgrest.from("likes")
            .select(columns = Columns.raw("trip_id,user_id")) {
                filter { isIn("trip_id", tripIds) }
            }
            .decodeList<LikeRow>()

        return rows.groupBy { it.tripId }.mapValues { (_, likes) ->
            LikeSummary(
                likeCount = likes.size,
                likedByMe = likes.any { it.userId == currentUserId }
            )
        }
    }

    suspend fun like(userId: String, tripId: String) {
        postgrest.from("likes").insert(NewLikeRow(userId = userId, tripId = tripId))
    }

    suspend fun unlike(userId: String, tripId: String) {
        postgrest.from("likes").delete {
            filter {
                eq("user_id", userId)
                eq("trip_id", tripId)
            }
        }
    }

    suspend fun isSaved(userId: String, tripId: String): Boolean =
        postgrest.from("bookmarks")
            .select(columns = Columns.raw("trip_id")) {
                filter {
                    eq("user_id", userId)
                    eq("trip_id", tripId)
                }
            }
            .decodeList<TripIdRow>()
            .isNotEmpty()

    suspend fun save(userId: String, tripId: String) {
        postgrest.from("bookmarks").insert(NewBookmarkRow(userId = userId, tripId = tripId))
    }

    suspend fun unsave(userId: String, tripId: String) {
        postgrest.from("bookmarks").delete {
            filter {
                eq("user_id", userId)
                eq("trip_id", tripId)
            }
        }
    }

    suspend fun fetchSavedTrips(userId: String): List<Trip> {
        val savedTripIds = postgrest.from("bookmarks")
            .select(columns = Columns.raw("trip_id")) {
                filter { eq("user_id", userId) }
            }
            .decodeList<TripIdRow>()
            .map { it.tripId }
        if (savedTripIds.isEmpty()) return emptyList()

        return postgrest.from("trips")
            .select { filter { isIn("id", savedTripIds) } }
            .decodeList()
    }
}

@Serializable
private data class LikeRow(
    @SerialName("trip_id") val tripId: String,
    @SerialName("user_id") val userId: String
)

@Serializable
private data class TripIdRow(@SerialName("trip_id") val tripId: String)

@Serializable
private data class NewLikeRow(
    @SerialName("user_id") val userId: String,
    @SerialName("trip_id") val tripId: String
)

@Serializable
private data class NewBookmarkRow(
    @SerialName("user_id") val userId: String,
    @SerialName("trip_id") val tripId: String
)
