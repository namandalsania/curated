package com.curated.app.features.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.ShareRepository
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.core.model.TripShare
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InboxState(
    val isLoading: Boolean = true,
    val shares: List<TripShare> = emptyList(),
    /** Unread when this opened - kept after marking read, so the tint stays while you look. */
    val unreadIds: Set<String> = emptySet(),
    val error: String? = null
)

/** Trips other people sent you. */
class InboxViewModel(
    private val authRepository: AuthRepository,
    private val shareRepository: ShareRepository
) : ViewModel() {

    private val _state = MutableStateFlow(InboxState())
    val state: StateFlow<InboxState> = _state

    fun load() {
        val me = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.shares.isEmpty(), error = null) }
            try {
                val shares = shareRepository.fetchInbox(me)
                // Capture what's unread first, then mark it read: opening is reading.
                val newlyUnread = shares.filter { it.readAt == null }.map { it.id }.toSet()
                _state.update { it.copy(isLoading = false, shares = shares, unreadIds = it.unreadIds + newlyUnread) }
                if (newlyUnread.isNotEmpty()) runCatching { shareRepository.markAllRead(me) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load the inbox", e)
                _state.update { it.copy(isLoading = false, error = "Couldn't load what people sent you.") }
            }
        }
    }

    fun remove(share: TripShare) {
        _state.update { it.copy(shares = it.shares - share) }
        viewModelScope.launch {
            runCatching { shareRepository.delete(share.id) }
                .onFailure {
                    Log.w(TAG, "Couldn't remove share ${share.id}", it)
                    load()
                }
        }
    }

    companion object {
        private const val TAG = "InboxViewModel"

        fun factory(context: Context) = viewModelFactory {
            initializer {
                val client = SupabaseProvider.client(context.applicationContext)
                InboxViewModel(AuthRepository(client), ShareRepository(client))
            }
        }
    }
}

/** This state without what [authorIds] sent. */
internal fun InboxState.withoutAuthors(authorIds: Set<String>): InboxState =
    if (authorIds.isEmpty()) this else copy(shares = shares.filter { it.senderId !in authorIds })
