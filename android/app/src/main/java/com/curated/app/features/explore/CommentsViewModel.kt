package com.curated.app.features.explore

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.CommentRepository
import com.curated.app.core.data.NotificationRepository
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.core.model.StopComment
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val COMMENT_MAX_LENGTH = 1000

data class CommentsState(
    val isLoading: Boolean = true,
    val comments: List<StopComment> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val error: String? = null,
    val myUserId: String? = null,
    /** The trip's author can delete anyone's comment on their own trip. */
    val isTripAuthor: Boolean = false
) {
    val canSend: Boolean get() = draft.isNotBlank() && !isSending
}

/** The comment thread for one place. */
class CommentsViewModel(
    private val stopId: String,
    private val tripId: String,
    private val tripAuthorId: String,
    private val authRepository: AuthRepository,
    private val commentRepository: CommentRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CommentsState())
    val state: StateFlow<CommentsState> = _state

    private var channel: RealtimeChannel? = null

    init {
        val me = authRepository.currentUserId()
        _state.update { it.copy(myUserId = me, isTripAuthor = me == tripAuthorId) }
        load()
        watchForComments()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.comments.isEmpty(), error = null) }
            try {
                val comments = commentRepository.fetchForStop(stopId)
                _state.update { it.copy(isLoading = false, comments = comments) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load comments for stop $stopId", e)
                _state.update { it.copy(isLoading = false, error = "Couldn't load comments.") }
            }
        }
    }

    private fun watchForComments() {
        val open = commentRepository.openStopChannel(stopId)
        channel = open
        viewModelScope.launch {
            commentRepository.observeComments(open, stopId).collect { load() }
        }
        viewModelScope.launch { runCatching { open.subscribe() } }
    }

    override fun onCleared() {
        val open = channel ?: return
        channel = null
        // viewModelScope is cancelled before this runs, so close on our own scope.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { commentRepository.closeChannel(open) }
    }

    fun setDraft(value: String) = _state.update { it.copy(draft = value.take(COMMENT_MAX_LENGTH)) }

    fun send() {
        val me = _state.value.myUserId ?: return
        val body = _state.value.draft.trim()
        if (body.isEmpty() || _state.value.isSending) return
        _state.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            try {
                commentRepository.add(stopId, tripId, me, body)
                runCatching {
                    notificationRepository.notifyStopComment(
                        actorId = me,
                        recipientId = tripAuthorId,
                        tripId = tripId,
                        stopId = stopId
                    )
                }
                _state.update { it.copy(isSending = false, draft = "") }
                // Reload rather than appending: it fills in the author profile.
                load()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't post comment on stop $stopId", e)
                _state.update { it.copy(isSending = false, error = "Couldn't post that comment. Try again.") }
            }
        }
    }

    fun canDelete(comment: StopComment): Boolean =
        comment.authorId == _state.value.myUserId || _state.value.isTripAuthor

    fun delete(comment: StopComment) {
        if (!canDelete(comment)) return
        _state.update { it.copy(comments = it.comments - comment) }
        viewModelScope.launch {
            try {
                commentRepository.delete(comment.id)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't delete comment ${comment.id}", e)
                _state.update { it.copy(error = "Couldn't delete that comment.") }
                load()
            }
        }
    }

    companion object {
        private const val TAG = "CommentsViewModel"

        fun factory(context: Context, stopId: String, tripId: String, tripAuthorId: String) = viewModelFactory {
            initializer {
                val client = SupabaseProvider.client(context.applicationContext)
                CommentsViewModel(
                    stopId = stopId,
                    tripId = tripId,
                    tripAuthorId = tripAuthorId,
                    authRepository = AuthRepository(client),
                    commentRepository = CommentRepository(client),
                    notificationRepository = NotificationRepository(client)
                )
            }
        }
    }
}
