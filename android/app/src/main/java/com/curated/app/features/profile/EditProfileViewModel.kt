package com.curated.app.features.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.AvatarStorageRepository
import com.curated.app.core.data.SocialRepository
import com.curated.app.core.data.SupabaseProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val BIO_MAX_LENGTH = 160

data class EditProfileState(
    val isLoading: Boolean = true,
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    /** The avatar already saved on the profile. */
    val currentAvatarUrl: String? = null,
    /** A newly picked avatar, not uploaded until Save. */
    val pickedAvatarUri: Uri? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
) {
    val canSave: Boolean get() = !isLoading && !isSaving && displayName.isNotBlank()
}

class EditProfileViewModel(
    private val authRepository: AuthRepository,
    private val socialRepository: SocialRepository,
    private val avatarStorageRepository: AvatarStorageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EditProfileState())
    val state: StateFlow<EditProfileState> = _state

    init {
        load()
    }

    fun load() {
        val userId = authRepository.currentUserId() ?: run {
            _state.update { it.copy(isLoading = false, error = "You're signed out. Sign in again.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val user = socialRepository.fetchUser(userId) ?: error("No profile row for $userId")
                _state.update {
                    it.copy(
                        isLoading = false,
                        username = user.username,
                        displayName = user.displayName,
                        bio = user.bio.orEmpty(),
                        currentAvatarUrl = user.avatarUrl
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load profile for editing", e)
                _state.update { it.copy(isLoading = false, error = "Couldn't load your profile.") }
            }
        }
    }

    fun setDisplayName(value: String) = _state.update { it.copy(displayName = value, error = null) }

    fun setBio(value: String) = _state.update { it.copy(bio = value.take(BIO_MAX_LENGTH), error = null) }

    fun setAvatar(uri: Uri?) {
        if (uri != null) _state.update { it.copy(pickedAvatarUri = uri, error = null) }
    }

    fun save() {
        val userId = authRepository.currentUserId() ?: return
        val current = _state.value
        if (!current.canSave) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                val avatarUrl = current.pickedAvatarUri
                    ?.let { avatarStorageRepository.upload(userId, it) }
                    ?: current.currentAvatarUrl
                authRepository.updateProfile(
                    userId = userId,
                    displayName = current.displayName.trim(),
                    bio = current.bio.trim().ifEmpty { null },
                    avatarUrl = avatarUrl
                )
                _state.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save profile", e)
                _state.update { it.copy(isSaving = false, error = "Couldn't save your changes. Try again.") }
            }
        }
    }

    companion object {
        private const val TAG = "EditProfileViewModel"

        fun factory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val client = SupabaseProvider.client(appContext)
                EditProfileViewModel(
                    authRepository = AuthRepository(client),
                    socialRepository = SocialRepository(client),
                    avatarStorageRepository = AvatarStorageRepository(client, appContext)
                )
            }
        }
    }
}
