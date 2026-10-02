package com.curated.app.features.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.PasswordRecovery
import com.curated.app.core.data.SupabaseProvider
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthGatePhase { LOADING, SIGNED_OUT, NEEDS_PROFILE, READY }

data class AuthGateState(
    val phase: AuthGatePhase = AuthGatePhase.LOADING,
    val userId: String? = null
)

/**
 * What the gate shows while the session is settling - Initializing, or a
 * refresh that failed - rather than known to be signed in or out.
 *
 * Only the first launch waits on it. Once the app or profile setup is showing,
 * it stays: auth-kt sets Initializing every time the app goes to the
 * background with auto-refresh running (file picker, camera, Home button), and
 * swapping in the spinner threw away the whole navigation stack, along with
 * any result the app was waiting on.
 */
internal fun phaseWhileSettling(current: AuthGatePhase): AuthGatePhase =
    when (current) {
        AuthGatePhase.READY, AuthGatePhase.NEEDS_PROFILE -> current
        AuthGatePhase.LOADING, AuthGatePhase.SIGNED_OUT -> AuthGatePhase.LOADING
    }

/** Drives which top-level flow (auth / profile setup / main app) is shown. */
class AuthGateViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthGateState())
    val state: StateFlow<AuthGateState> = _state

    init {
        viewModelScope.launch {
            // A password reset signs in when its code is verified; stay on the auth
            // screens until the new password is saved.
            combine(authRepository.sessionStatus, PasswordRecovery.inProgress) { status, recovering -> status to recovering }
                .collect { (status, recovering) ->
                    when {
                        recovering -> _state.update { AuthGateState(phase = AuthGatePhase.SIGNED_OUT, userId = null) }
                        status is SessionStatus.Authenticated -> {
                            // Coming back to the foreground re-announces the same session.
                            // The app is already open for this user; checking again only
                            // risks a network failure undoing that.
                            val current = _state.value
                            if (current.phase != AuthGatePhase.READY || current.userId != authRepository.currentUserId()) {
                                evaluateProfile()
                            }
                        }
                        status is SessionStatus.NotAuthenticated -> _state.update {
                            AuthGateState(phase = AuthGatePhase.SIGNED_OUT, userId = null)
                        }
                        else -> _state.update { it.copy(phase = phaseWhileSettling(it.phase)) }
                    }
                }
        }
    }

    private suspend fun evaluateProfile() {
        val userId = authRepository.currentUserId()
        if (userId == null) {
            _state.update { AuthGateState(phase = AuthGatePhase.SIGNED_OUT, userId = null) }
            return
        }
        val hasProfile = authRepository.fetchProfile(userId) != null || createProfileFromSignUp(userId)
        _state.update {
            AuthGateState(
                phase = if (hasProfile) AuthGatePhase.READY else AuthGatePhase.NEEDS_PROFILE,
                userId = userId
            )
        }
    }

    /**
     * Sign-up asks for a username and display name, but with email confirmation
     * on there's no session to write the profile with until the first sign-in -
     * so it's written here. If the username was taken in the meantime this fails
     * quietly and profile setup asks again, prefilled.
     */
    private suspend fun createProfileFromSignUp(userId: String): Boolean {
        val (username, displayName) = authRepository.pendingProfile() ?: return false
        return runCatching { authRepository.createProfile(userId, username, displayName, avatarUrl = null) }
            .onFailure { Log.w(TAG, "Couldn't create the profile from sign-up details", it) }
            .isSuccess
    }

    /** Call after profile setup completes to move past the NEEDS_PROFILE gate. */
    fun recheckProfile() {
        viewModelScope.launch { evaluateProfile() }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    companion object {
        private const val TAG = "AuthGateViewModel"

        fun factory(context: Context) = viewModelFactory {
            initializer {
                AuthGateViewModel(AuthRepository(SupabaseProvider.client(context.applicationContext)))
            }
        }
    }
}
