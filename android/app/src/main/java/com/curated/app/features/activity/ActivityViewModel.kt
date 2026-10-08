package com.curated.app.features.activity

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.NotificationRepository
import com.curated.app.core.data.SocialRepository
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.core.model.Notification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityState(
    val isLoading: Boolean = true,
    val notifications: List<Notification> = emptyList(),
    /** Unread when this screen opened - kept after marking read, so the tint stays while you look. */
    val unreadIds: Set<String> = emptySet(),
    /** Who you follow, for Follow back. */
    val following: Set<String> = emptySet(),
    val error: String? = null,
    val actionError: String? = null
)

/** Your notifications, for the Activity tab. */
class ActivityViewModel(
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
    private val socialRepository: SocialRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityState())
    val state: StateFlow<ActivityState> = _state

    fun load() {
        val me = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.notifications.isEmpty(), error = null) }
            try {
                val notifications = notificationRepository.fetchNotifications(me)
                val following = runCatching { socialRepository.fetchFollowingIds(me).toSet() }.getOrDefault(emptySet())
                // Capture what's unread first, then mark it read.
                val newlyUnread = notifications.filter { it.readAt == null }.map { it.id }.toSet()
                _state.update {
                    it.copy(
                        isLoading = false,
                        notifications = notifications,
                        unreadIds = it.unreadIds + newlyUnread,
                        following = following
                    )
                }
                if (newlyUnread.isNotEmpty()) {
                    runCatching { notificationRepository.markAllRead(me) }
                        .onFailure { Log.w(TAG, "Couldn't mark notifications read", it) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load activity", e)
                _state.update { it.copy(isLoading = false, error = "Couldn't load your activity.") }
            }
        }
    }

    /** Follows [userId] back from their follow row. The button goes as soon as it's tapped. */
    fun followBack(userId: String) {
        val me = authRepository.currentUserId() ?: return
        _state.update { it.copy(following = it.following + userId, actionError = null) }
        viewModelScope.launch {
            try {
                socialRepository.follow(me, userId)
            } catch (e: Exception) {
                Log.w(TAG, "Follow back failed", e)
                // Neutral on purpose: a follow refused because of a block reads like any other failure.
                _state.update { it.copy(following = it.following - userId, actionError = "Couldn't follow this account.") }
            }
        }
    }

    companion object {
        private const val TAG = "ActivityViewModel"

        fun factory(context: Context) = viewModelFactory {
            initializer {
                val client = SupabaseProvider.client(context.applicationContext)
                ActivityViewModel(AuthRepository(client), NotificationRepository(client), SocialRepository(client))
            }
        }
    }
}
