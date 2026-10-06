package com.curated.app.features.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.NotificationRepository
import com.curated.app.core.data.SocialRepository
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.core.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FollowListKind { FOLLOWERS, FOLLOWING }

data class FollowListUiState(
    val isLoading: Boolean = true,
    val users: List<User> = emptyList(),
    /** Ids the signed-in viewer follows — drives each row's button, whoever's list this is. */
    val viewerFollowingIds: Set<String> = emptySet(),
    val viewerId: String? = null,
    val error: String? = null,
    /** A follow/unfollow failed - shown above the list, which stays up. */
    val actionError: String? = null
)

class FollowListViewModel(
    private val authRepository: AuthRepository,
    private val socialRepository: SocialRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FollowListUiState())
    val state: StateFlow<FollowListUiState> = _state

    fun load(userId: String, kind: FollowListKind) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val viewerId = authRepository.currentUserId()
                val ids = when (kind) {
                    FollowListKind.FOLLOWERS -> socialRepository.fetchFollowerIds(userId)
                    FollowListKind.FOLLOWING -> socialRepository.fetchFollowingIds(userId)
                }
                val users = socialRepository.fetchUsers(ids).sortedBy { it.displayName.lowercase() }
                val viewerFollowing = viewerId?.let { socialRepository.fetchFollowingIds(it).toSet() } ?: emptySet()
                _state.update {
                    it.copy(
                        isLoading = false,
                        users = users,
                        viewerFollowingIds = viewerFollowing,
                        viewerId = viewerId
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to load list") }
            }
        }
    }

    /** Optimistic follow/unfollow for one row; reverts if the write fails. */
    fun toggleFollow(targetId: String) {
        val viewerId = _state.value.viewerId ?: return
        val wasFollowing = targetId in _state.value.viewerFollowingIds
        setFollowing(targetId, !wasFollowing)
        _state.update { it.copy(actionError = null) }

        viewModelScope.launch {
            try {
                if (wasFollowing) {
                    socialRepository.unfollow(viewerId, targetId)
                } else {
                    socialRepository.follow(viewerId, targetId)
                    runCatching { notificationRepository.notifyFollow(actorId = viewerId, recipientId = targetId) }
                }
            } catch (e: Exception) {
                setFollowing(targetId, wasFollowing)
                // Neutral on purpose: a follow refused because of a block must
                // read exactly like any other failure.
                _state.update { it.copy(actionError = if (wasFollowing) "Couldn't unfollow this account." else "Couldn't follow this account.") }
            }
        }
    }

    private fun setFollowing(targetId: String, following: Boolean) {
        _state.update {
            it.copy(
                viewerFollowingIds = if (following) it.viewerFollowingIds + targetId else it.viewerFollowingIds - targetId
            )
        }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer {
                val client = SupabaseProvider.client(context.applicationContext)
                FollowListViewModel(
                    authRepository = AuthRepository(client),
                    socialRepository = SocialRepository(client),
                    notificationRepository = NotificationRepository(client)
                )
            }
        }
    }
}
