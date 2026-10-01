package com.curated.app.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Follow(
    @SerialName("follower_id") val followerId: String,
    @SerialName("following_id") val followingId: String,
    @SerialName("created_at") val createdAt: Instant
)
