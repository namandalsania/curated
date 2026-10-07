package com.curated.app.features.settings

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AccountRepository
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.BlockedAccounts
import com.curated.app.core.data.DeleteAccountOutcome
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing
import io.github.jan.supabase.auth.exception.AuthRestException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What has to be typed, exactly, before the button wakes up. */
const val DELETE_CONFIRMATION_WORD = "DELETE"

data class DeleteAccountState(
    val password: String = "",
    val confirmation: String = "",
    val isDeleting: Boolean = false,
    val error: String? = null
) {
    val canDelete: Boolean get() = canDeleteAccount(password, confirmation) && !isDeleting
}

/** The password is filled in and the word is typed exactly (spaces around it forgiven). */
fun canDeleteAccount(password: String, confirmation: String): Boolean =
    password.isNotEmpty() && confirmation.trim() == DELETE_CONFIRMATION_WORD

/** What to tell the person after a failed attempt. Null means there's nothing to say. */
fun deleteAccountMessage(outcome: DeleteAccountOutcome): String? = when (outcome) {
    DeleteAccountOutcome.DELETED -> null
    DeleteAccountOutcome.REAUTH_REQUIRED -> "Please enter your password again."
    DeleteAccountOutcome.NOT_SIGNED_IN -> "Your session has ended. Sign in again, then delete your account."
    DeleteAccountOutcome.FAILED ->
        "Couldn't finish deleting your account. Try again - it picks up where it stopped."
}

class DeleteAccountViewModel(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DeleteAccountState())
    val state: StateFlow<DeleteAccountState> = _state

    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }
    fun setConfirmation(value: String) = _state.update { it.copy(confirmation = value, error = null) }

    /**
     * Checks the password by signing in again - which also makes the sign-in
     * recent, as the function requires - then asks the function to delete the
     * account. On success the session is dropped and the auth gate shows the
     * sign-in screen.
     */
    fun delete() {
        val current = _state.value
        if (!current.canDelete) return
        val email = authRepository.currentEmail() ?: run {
            _state.update { it.copy(error = deleteAccountMessage(DeleteAccountOutcome.NOT_SIGNED_IN)) }
            return
        }
        _state.update { it.copy(isDeleting = true, error = null) }
        viewModelScope.launch {
            try {
                authRepository.signIn(email, current.password)
            } catch (e: AuthRestException) {
                _state.update { it.copy(isDeleting = false, error = "That password isn't right.") }
                return@launch
            } catch (e: Exception) {
                Log.w(TAG, "Re-authentication failed", e)
                _state.update {
                    it.copy(isDeleting = false, error = "Couldn't check your password. Check your connection and try again.")
                }
                return@launch
            }

            val outcome = try {
                accountRepository.deleteAccount()
            } catch (e: Exception) {
                Log.w(TAG, "delete-account call failed", e)
                DeleteAccountOutcome.FAILED
            }

            if (outcome == DeleteAccountOutcome.DELETED) {
                BlockedAccounts.clear()
                runCatching { authRepository.signOutLocally() }
                    .onFailure { Log.w(TAG, "Local sign-out after deletion failed", it) }
                // The gate takes over from here; nothing more to show.
            } else {
                _state.update { it.copy(isDeleting = false, error = deleteAccountMessage(outcome)) }
            }
        }
    }

    companion object {
        private const val TAG = "DeleteAccountVM"

        fun factory(context: Context) = viewModelFactory {
            initializer {
                val client = SupabaseProvider.client(context.applicationContext)
                DeleteAccountViewModel(AuthRepository(client), AccountRepository(client))
            }
        }
    }
}

@Composable
fun DeleteAccountScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: DeleteAccountViewModel = viewModel(factory = DeleteAccountViewModel.factory(context))
    val state by viewModel.state.collectAsState()
    DeleteAccountContent(
        state = state,
        onBack = onBack,
        onPasswordChange = viewModel::setPassword,
        onConfirmationChange = viewModel::setConfirmation,
        onDelete = viewModel::delete
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteAccountContent(
    state: DeleteAccountState,
    onBack: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Delete account") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isDeleting) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                "This permanently deletes your Curated account. It can't be undone.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Heading("What's removed")
            Bullets(
                "Your profile, username and photo",
                "Your trips - live, finished and drafts - with their days, places and photos",
                "Your comments, likes and saved places",
                "Your plans, and your place in plans others shared with you",
                "Your follows, notifications, and trips sent to or by you",
                "People you blocked, and reports you filed"
            )

            Heading("What stays")
            Bullets(
                "Places other people saved from your trips stay in their lists, without your name or photos",
                "Reports others made about your content are kept for safety review, marked as deleted"
            )

            Heading("Confirm")
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text("Your password") },
                singleLine = true,
                enabled = !state.isDeleting,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.confirmation,
                onValueChange = onConfirmationChange,
                label = { Text("Type $DELETE_CONFIRMATION_WORD to confirm") },
                singleLine = true,
                enabled = !state.isDeleting,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = onDelete,
                enabled = state.canDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
            ) {
                if (state.isDeleting) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.padding(end = Spacing.sm).size(16.dp)
                    )
                    Text("Deleting…")
                } else {
                    Text("Delete my account")
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.sm)
    )
}

@Composable
private fun Bullets(vararg lines: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Text("•  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// --- Previews ----------------------------------------------------------------

@Preview(showBackground = true, heightDp = 900, name = "Delete account")
@Composable
private fun DeleteAccountPreview() {
    CuratedTheme {
        DeleteAccountContent(
            state = DeleteAccountState(password = "secret", confirmation = "DELETE"),
            onBack = {}, onPasswordChange = {}, onConfirmationChange = {}, onDelete = {}
        )
    }
}
