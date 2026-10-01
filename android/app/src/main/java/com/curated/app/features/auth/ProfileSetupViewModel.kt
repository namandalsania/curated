package com.curated.app.features.auth

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.AvatarStorageRepository
import com.curated.app.core.data.SupabaseProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileSetupState(
    val avatarUri: Uri? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val complete: Boolean = false,
    /** What was picked at sign-up, when writing it automatically failed (e.g. the username got taken). */
    val prefillUsername: String = "",
    val prefillDisplayName: String = ""
)

class ProfileSetupViewModel(
    private val authRepository: AuthRepository,
    private val avatarStorageRepository: AvatarStorageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        authRepository.pendingProfile()
            ?.let { (username, displayName) -> ProfileSetupState(prefillUsername = username, prefillDisplayName = displayName) }
            ?: ProfileSetupState()
    )
    val state: StateFlow<ProfileSetupState> = _state

    fun setAvatar(uri: Uri?) {
        _state.update { it.copy(avatarUri = uri) }
    }

    fun submit(username: String, displayName: String) {
        val userId = authRepository.currentUserId()
        if (userId == null) {
            _state.update { it.copy(error = "Not signed in") }
            return
        }
        if (username.isBlank() || displayName.isBlank()) {
            _state.update { it.copy(error = "Username and display name are required.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            try {
                val avatarUrl = _state.value.avatarUri?.let { uri ->
                    avatarStorageRepository.upload(userId, uri)
                }
                authRepository.createProfile(
                    userId = userId,
                    username = username.trim(),
                    displayName = displayName.trim(),
                    avatarUrl = avatarUrl
                )
                _state.update { it.copy(isSubmitting = false, complete = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isSubmitting = false, error = e.message ?: "Failed to save profile") }
            }
        }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val client = SupabaseProvider.client(appContext)
                ProfileSetupViewModel(
                    authRepository = AuthRepository(client),
                    avatarStorageRepository = AvatarStorageRepository(client, appContext)
                )
            }
        }
    }
}
