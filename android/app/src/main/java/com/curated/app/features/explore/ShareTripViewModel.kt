package com.curated.app.features.explore

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.NotificationRepository
import com.curated.app.core.data.ShareRepository
import com.curated.app.core.data.SocialRepository
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.core.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val SHARE_NOTE_MAX_LENGTH = 280

data class ShareTripState(
    val isLoading: Boolean = true,
    /** People you follow or who follow you - the default list to send to. */
    val people: List<User> = emptyList(),
    val searchResults: List<User> = emptyList(),
    val query: String = "",
    val selected: Set<String> = emptySet(),
    val note: String = "",
    val isSending: Boolean = false,
    val error: String? = null,
    val sentTo: Int? = null
) {
    val shown: List<User> get() = if (query.isBlank()) people else searchResults
    val canSend: Boolean get() = selected.isNotEmpty() && !isSending
}

/** Sending a trip to other people's inboxes. */
class ShareTripViewModel(
    private val tripId: String,
    private val authRepository: AuthRepository,
    private val socialRepository: SocialRepository,
    private val shareRepository: ShareRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ShareTripState())
    val state: StateFlow<ShareTripState> = _state

    init {
        load()
    }

    private fun load() {
        val me = authRepository.currentUserId() ?: return
        viewModelScope.launch {
            try {
                // Both directions: people you follow, and people who follow you.
                val ids = (socialRepository.fetchFollowingIds(me) + socialRepository.fetchFollowerIds(me))
                    .distinct()
                    .filter { it != me }
                val people = socialRepository.fetchUsers(ids).sortedBy { it.displayName.lowercase() }
                _state.update { it.copy(isLoading = false, people = people) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load people to share with", e)
                _state.update { it.copy(isLoading = false, error = "Couldn't load your people.") }
            }
        }
    }

    fun search(query: String) {
        _state.update { it.copy(query = query) }
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        val me = authRepository.currentUserId()
        viewModelScope.launch {
            val results = runCatching { socialRepository.searchUsers(query.trim()) }.getOrElse { emptyList() }
            _state.update { state ->
                if (state.query != query) state // a newer search won
                else state.copy(searchResults = results.filter { it.id != me })
            }
        }
    }

    fun toggle(userId: String) = _state.update {
        it.copy(selected = if (userId in it.selected) it.selected - userId else it.selected + userId)
    }

    fun setNote(value: String) = _state.update { it.copy(note = value.take(SHARE_NOTE_MAX_LENGTH)) }

    fun send() {
        val me = authRepository.currentUserId() ?: return
        val recipients = _state.value.selected.toList()
        if (recipients.isEmpty() || _state.value.isSending) return
        _state.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            try {
                shareRepository.send(tripId, me, recipients, _state.value.note)
                runCatching { notificationRepository.notifyTripShare(me, recipients, tripId) }
                _state.update { it.copy(isSending = false, sentTo = recipients.size) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't share trip $tripId", e)
                _state.update { it.copy(isSending = false, error = "Couldn't send that. Try again.") }
            }
        }
    }

    /**
     * Clears a finished send. This view model outlives the sheet (it's keyed to
     * the trip), so without this the next open still saw sentTo set and closed
     * itself straight away - a trip could only be shared once per visit.
     */
    fun consumeSent() = _state.update {
        it.copy(sentTo = null, selected = emptySet(), note = "", query = "", searchResults = emptyList())
    }

    companion object {
        private const val TAG = "ShareTripViewModel"

        fun factory(context: Context, tripId: String) = viewModelFactory {
            initializer {
                val client = SupabaseProvider.client(context.applicationContext)
                ShareTripViewModel(
                    tripId = tripId,
                    authRepository = AuthRepository(client),
                    socialRepository = SocialRepository(client),
                    shareRepository = ShareRepository(client),
                    notificationRepository = NotificationRepository(client)
                )
            }
        }
    }
}
