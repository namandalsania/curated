package com.curated.app.core.data

import com.curated.app.core.model.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SocialRepository(private val client: SupabaseClient) {

    private val postgrest get() = client.postgrest

    suspend fun fetchUser(userId: String): User? =
        postgrest.from("users")
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull()

    suspend fun fetchUsers(userIds: List<String>): List<User> {
        if (userIds.isEmpty()) return emptyList()
        return postgrest.from("users")
            .select { filter { isIn("id", userIds) } }
            .decodeList()
    }

    suspend fun searchUsers(query: String): List<User> {
        if (query.isBlank()) return emptyList()
        return postgrest.from("users")
            .select { filter { ilike("username", "%$query%") } }
            .decodeList()
    }

    suspend fun isFollowing(followerId: String, followingId: String): Boolean =
        postgrest.from("follows")
            .select(columns = Columns.raw("follower_id")) {
                filter {
                    eq("follower_id", followerId)
                    eq("following_id", followingId)
                }
            }
            .decodeList<FollowerIdRow>()
            .isNotEmpty()

    suspend fun follow(followerId: String, followingId: String) {
        if (followerId == followingId) return
        postgrest.from("follows").insert(
            NewFollowRow(followerId = followerId, followingId = followingId)
        )
    }

    suspend fun unfollow(followerId: String, followingId: String) {
        postgrest.from("follows").delete {
            filter {
                eq("follower_id", followerId)
                eq("following_id", followingId)
            }
        }
    }

    suspend fun fetchFollowerIds(userId: String): List<String> =
        postgrest.from("follows")
            .select(columns = Columns.raw("follower_id")) {
                filter { eq("following_id", userId) }
            }
            .decodeList<FollowerIdRow>()
            .map { it.followerId }

    suspend fun fetchFollowingIds(userId: String): List<String> =
        postgrest.from("follows")
            .select(columns = Columns.raw("following_id")) {
                filter { eq("follower_id", userId) }
            }
            .decodeList<FollowingIdRow>()
            .map { it.followingId }

    suspend fun followerCount(userId: String): Int = fetchFollowerIds(userId).size

    suspend fun followingCount(userId: String): Int = fetchFollowingIds(userId).size
}

@Serializable
private data class FollowerIdRow(@SerialName("follower_id") val followerId: String)

@Serializable
private data class FollowingIdRow(@SerialName("following_id") val followingId: String)

@Serializable
private data class NewFollowRow(
    @SerialName("follower_id") val followerId: String,
    @SerialName("following_id") val followingId: String
)
