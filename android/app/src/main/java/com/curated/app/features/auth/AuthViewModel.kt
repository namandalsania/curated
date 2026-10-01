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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SIGN_IN, SIGN_UP }

/** Which auth screen is up. The reset screens replace the form rather than stacking on it. */
enum class AuthPage { SIGN_IN, SIGN_UP, RESET_REQUEST, RESET_VERIFY }

sealed interface UsernameStatus {
    data object Idle : UsernameStatus
    data object Checking : UsernameStatus
    data object Available : UsernameStatus
    data object Taken : UsernameStatus
    data class Invalid(val reason: String) : UsernameStatus
}

data class AuthUiState(
    val page: AuthPage = AuthPage.SIGN_IN,
    /** Shared by every page, so "Forgot password?" arrives with the email already filled in. */
    val email: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    /** Neutral confirmations: "If an account exists, we sent a code." */
    val info: String? = null,
    /** Sign-in was refused only because the email is unconfirmed - offer to resend. */
    val emailNotConfirmed: Boolean = false,
    /** Sign-up worked, and the account now needs its email confirmed before signing in. */
    val awaitingConfirmation: Boolean = false,
    val usernameStatus: UsernameStatus = UsernameStatus.Idle,
    /** Seconds until "Resend code" works again. */
    val resendCooldown: Int = 0,
    /** The recovery code was accepted; only saving the new password is left. */
    val codeVerified: Boolean = false
) {
    val mode: AuthMode get() = if (page == AuthPage.SIGN_UP) AuthMode.SIGN_UP else AuthMode.SIGN_IN
}

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    private var usernameJob: Job? = null
    private var cooldownJob: Job? = null

    fun setEmail(email: String) = _state.update { it.copy(email = email) }

    fun goTo(page: AuthPage) {
        // Leaving a reset half way, after the code already signed us in: undo that.
        if (_state.value.codeVerified && page != AuthPage.RESET_VERIFY) abandonRecovery()
        _state.update {
            it.copy(
                page = page,
                error = null,
                info = null,
                emailNotConfirmed = false,
                awaitingConfirmation = false,
                codeVerified = if (page == AuthPage.RESET_VERIFY) it.codeVerified else false
            )
        }
    }

    // --- Sign in / sign up ------------------------------------------------------

    fun signIn(password: String) {
        val email = _state.value.email.trim()
        if (!AuthRules.isEmailPlausible(email) || password.isEmpty()) {
            _state.update { it.copy(error = "Enter your email and password.") }
            return
        }
        launchSubmit(AuthMode.SIGN_IN) { authRepository.signIn(email, password) }
    }

    /** Validates everything first; nothing is sent while a field is still wrong. */
    fun signUp(displayName: String, rawUsername: String, password: String) {
        val email = _state.value.email.trim()
        val username = AuthRules.normalizeUsername(rawUsername)
        val problem = when {
            displayName.isBlank() -> "Add your name."
            AuthRules.usernameProblem(username) != null -> "Username: ${AuthRules.usernameProblem(username)}"
            _state.value.usernameStatus == UsernameStatus.Taken -> "That username is taken."
            !AuthRules.isEmailPlausible(email) -> "That doesn't look like a valid email address."
            AuthRules.passwordProblem(password) != null -> "Password: ${AuthRules.passwordProblem(password)}"
            else -> null
        }
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }
        launchSubmit(AuthMode.SIGN_UP) {
            // Checked once more at submit: the as-you-type answer may be stale.
            if (!authRepository.isUsernameAvailable(username)) {
                _state.update { it.copy(usernameStatus = UsernameStatus.Taken) }
                throw UsernameTakenException()
            }
            val signedIn = authRepository.signUp(email, password, username, displayName.trim())
            if (!signedIn) _state.update { it.copy(awaitingConfirmation = true) }
        }
    }

    /** Called on every keystroke; checks availability once typing pauses. */
    fun onUsernameChanged(raw: String) {
        usernameJob?.cancel()
        val username = AuthRules.normalizeUsername(raw)
        if (username.isEmpty()) {
            _state.update { it.copy(usernameStatus = UsernameStatus.Idle) }
            return
        }
        AuthRules.usernameProblem(username)?.let { reason ->
            _state.update { it.copy(usernameStatus = UsernameStatus.Invalid(reason)) }
            return
        }
        _state.update { it.copy(usernameStatus = UsernameStatus.Checking) }
        usernameJob = viewModelScope.launch {
            delay(USERNAME_DEBOUNCE_MS)
            val status = runCatching { authRepository.isUsernameAvailable(username) }
                .map { if (it) UsernameStatus.Available else UsernameStatus.Taken }
                // Can't tell: don't block sign-up on it, the submit re-checks.
                .getOrDefault(UsernameStatus.Idle)
            _state.update { it.copy(usernameStatus = status) }
        }
    }

    fun resendConfirmation() {
        val email = _state.value.email.trim()
        if (email.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            runCatching { authRepository.resendSignUpConfirmation(email) }
                .onSuccess { _state.update { it.copy(isSubmitting = false, info = "Sent. Check your inbox for the confirmation link.") } }
                .onFailure { e -> _state.update { it.copy(isSubmitting = false, error = authErrorMessage(e, AuthMode.SIGN_IN)) } }
        }
    }

    private fun launchSubmit(mode: AuthMode, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null, info = null, emailNotConfirmed = false) }
            try {
                block()
                _state.update { it.copy(isSubmitting = false) }
            } catch (e: UsernameTakenException) {
                _state.update { it.copy(isSubmitting = false, error = "That username is taken.") }
            } catch (e: Exception) {
                Log.w(TAG, "Auth failed ($mode)", e)
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        error = authErrorMessage(e, mode),
                        emailNotConfirmed = isEmailNotConfirmed(e)
                    )
                }
            }
        }
    }

    // --- Password reset ---------------------------------------------------------

    /** Screen 1: send the code. The answer is the same whether or not the account exists. */
    fun requestResetCode() {
        val email = _state.value.email.trim()
        if (!AuthRules.isEmailPlausible(email)) {
            _state.update { it.copy(error = "Enter the email you signed up with.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null, info = null) }
            try {
                authRepository.sendPasswordResetCode(email)
                _state.update {
                    it.copy(isSubmitting = false, page = AuthPage.RESET_VERIFY, info = NEUTRAL_SENT, codeVerified = false)
                }
                startCooldown()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't send a reset code", e)
                _state.update { it.copy(isSubmitting = false, error = resetErrorMessage(e, ResetStep.SEND_CODE)) }
            }
        }
    }

    fun resendResetCode() {
        if (_state.value.resendCooldown > 0 || _state.value.isSubmitting) return
        val email = _state.value.email.trim()
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null, info = null) }
            try {
                authRepository.sendPasswordResetCode(email)
                _state.update { it.copy(isSubmitting = false, info = NEUTRAL_RESENT) }
                startCooldown()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't resend a reset code", e)
                _state.update { it.copy(isSubmitting = false, error = resetErrorMessage(e, ResetStep.SEND_CODE)) }
            }
        }
    }

    /**
     * Screen 2: verify the code, then save the new password.
     *
     * Verifying signs in, so [PasswordRecovery] holds the auth gate until the
     * password is saved. If saving fails the code is spent, so a retry only
     * repeats the save.
     */
    fun confirmReset(code: String, newPassword: String, confirmPassword: String) {
        val problem = when {
            !_state.value.codeVerified && !AuthRules.isCodeComplete(code) -> "Enter the ${AuthRules.CODE_LENGTH}-digit code from the email."
            AuthRules.passwordProblem(newPassword) != null -> "Password: ${AuthRules.passwordProblem(newPassword)}"
            newPassword != confirmPassword -> "The two passwords don't match."
            else -> null
        }
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }
        val email = _state.value.email.trim()
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null, info = null) }
            PasswordRecovery.inProgress.value = true
            if (!_state.value.codeVerified) {
                try {
                    authRepository.verifyRecoveryCode(email, code)
                    _state.update { it.copy(codeVerified = true) }
                } catch (e: Exception) {
                    Log.w(TAG, "Recovery code rejected", e)
                    PasswordRecovery.inProgress.value = false
                    _state.update { it.copy(isSubmitting = false, error = resetErrorMessage(e, ResetStep.VERIFY_CODE)) }
                    return@launch
                }
            }
            try {
                authRepository.updatePassword(newPassword)
                _state.update { AuthUiState(email = email) }
                // Releasing the gate is what moves into the app: the session is the new one.
                PasswordRecovery.inProgress.value = false
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save the new password", e)
                _state.update { it.copy(isSubmitting = false, error = resetErrorMessage(e, ResetStep.SAVE_PASSWORD)) }
            }
        }
    }

    /** The code signed in but no new password was saved: sign out rather than leave a half-reset session. */
    private fun abandonRecovery() {
        viewModelScope.launch {
            runCatching { authRepository.signOut() }
            PasswordRecovery.inProgress.value = false
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (left in AuthRules.RESEND_COOLDOWN_SECONDS downTo 0) {
                _state.update { it.copy(resendCooldown = left) }
                if (left > 0) delay(1_000)
            }
        }
    }

    override fun onCleared() {
        if (_state.value.codeVerified) PasswordRecovery.inProgress.value = false
    }

    private class UsernameTakenException : Exception()

    companion object {
        private const val TAG = "AuthViewModel"
        private const val USERNAME_DEBOUNCE_MS = 400L
        private const val NEUTRAL_SENT = "If an account exists for that email, we sent it a code."
        private const val NEUTRAL_RESENT = "If an account exists for that email, we sent it a new code."

        fun factory(context: Context) = viewModelFactory {
            initializer {
                AuthViewModel(AuthRepository(SupabaseProvider.client(context.applicationContext)))
            }
        }
    }
}
