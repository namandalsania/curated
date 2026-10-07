package com.curated.app.features.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.AuthRepository
import com.curated.app.core.data.BlockedAccounts
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.core.legal.LegalLinks
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.HairlineCard
import com.curated.app.designsystem.components.HairlineDivider
import kotlinx.coroutines.launch

class SettingsViewModel(private val authRepository: AuthRepository) : ViewModel() {

    val email: String? get() = authRepository.currentEmail()

    /** Signing out flips the auth gate, which shows the sign-in screen. */
    fun signOut() {
        viewModelScope.launch {
            BlockedAccounts.clear()
            authRepository.signOut()
        }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer { SettingsViewModel(AuthRepository(SupabaseProvider.client(context.applicationContext))) }
        }
    }
}

/** Account settings, reached from your profile's menu. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenBlockedAccounts: () -> Unit, onDeleteAccount: () -> Unit) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(context))
    SettingsContent(
        email = viewModel.email,
        onBack = onBack,
        onOpenBlockedAccounts = onOpenBlockedAccounts,
        onDeleteAccount = onDeleteAccount,
        onSignOut = viewModel::signOut
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    email: String?,
    onBack: () -> Unit,
    onOpenBlockedAccounts: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSignOut: () -> Unit
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    // Opens in the browser; the pages are on the web so they can be linked from Play too.
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xl)
        ) {
            SettingsSection("Privacy and safety") {
                SettingsRow(
                    icon = Icons.Outlined.Block,
                    title = "Blocked accounts",
                    subtitle = "People you've blocked, and unblocking them",
                    onClick = onOpenBlockedAccounts
                )
            }

            SettingsSection("About") {
                SettingsRow(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "Privacy Policy",
                    subtitle = "What we collect and who can see it",
                    onClick = { uriHandler.openUri(LegalLinks.PRIVACY_POLICY) },
                    trailing = Icons.AutoMirrored.Outlined.OpenInNew
                )
                HairlineDivider()
                SettingsRow(
                    icon = Icons.Outlined.Description,
                    title = "Terms of Use",
                    subtitle = "The rules for using Curated",
                    onClick = { uriHandler.openUri(LegalLinks.TERMS_OF_USE) },
                    trailing = Icons.AutoMirrored.Outlined.OpenInNew
                )
            }

            SettingsSection("Account") {
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = "Sign out",
                    subtitle = email?.let { "Signed in as $it" },
                    onClick = { confirmSignOut = true },
                    showChevron = false
                )
                HairlineDivider()
                SettingsRow(
                    icon = Icons.Outlined.DeleteOutline,
                    title = "Delete account",
                    subtitle = "Permanently remove your account and everything in it",
                    onClick = onDeleteAccount,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("You'll need your email and password to sign back in.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    onSignOut()
                }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.sm)
    )
    HairlineCard(modifier = Modifier.padding(horizontal = Spacing.md).fillMaxWidth()) { content() }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    /** Replaces the chevron, e.g. for rows that leave the app. */
    trailing: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showChevron) {
            Icon(
                trailing ?: Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- Previews ----------------------------------------------------------------

@Preview(showBackground = true, heightDp = 640, name = "Settings")
@Composable
private fun SettingsPreview() {
    CuratedTheme {
        SettingsContent(
            email = "maya@example.com",
            onBack = {},
            onOpenBlockedAccounts = {},
            onDeleteAccount = {},
            onSignOut = {}
        )
    }
}
