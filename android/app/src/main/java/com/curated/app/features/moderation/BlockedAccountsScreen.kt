package com.curated.app.features.moderation

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.curated.app.core.data.ModerationRepository
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.core.model.User
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.EmptyState
import com.curated.app.designsystem.components.HairlineDivider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

data class BlockedAccountsState(
    val isLoading: Boolean = true,
    val users: List<User> = emptyList(),
    val unblocking: Set<String> = emptySet(),
    val error: String? = null
)

class BlockedAccountsViewModel(private val repository: ModerationRepository) : ViewModel() {

    private val _state = MutableStateFlow(BlockedAccountsState())
    val state: StateFlow<BlockedAccountsState> = _state

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.users.isEmpty(), error = null) }
            try {
                val users = repository.fetchBlockedUsers()
                _state.update { it.copy(isLoading = false, users = users) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load blocked accounts", e)
                _state.update { it.copy(isLoading = false, error = "Couldn't load your blocked accounts.") }
            }
        }
    }

    fun unblock(user: User) {
        _state.update { it.copy(unblocking = it.unblocking + user.id, error = null) }
        viewModelScope.launch {
            try {
                repository.unblock(user.id)
                _state.update { it.copy(users = it.users - user, unblocking = it.unblocking - user.id) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't unblock ${user.id}", e)
                _state.update {
                    it.copy(unblocking = it.unblocking - user.id, error = "Couldn't unblock ${user.displayName}. Try again.")
                }
            }
        }
    }

    companion object {
        private const val TAG = "BlockedAccountsVM"

        fun factory(context: Context) = viewModelFactory {
            initializer {
                BlockedAccountsViewModel(ModerationRepository(SupabaseProvider.client(context.applicationContext)))
            }
        }
    }
}

/** Everyone you've blocked, each with Unblock. Reached from Settings. */
@Composable
fun BlockedAccountsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: BlockedAccountsViewModel = viewModel(factory = BlockedAccountsViewModel.factory(context))
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    BlockedAccountsContent(state = state, onBack = onBack, onUnblock = viewModel::unblock)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedAccountsContent(state: BlockedAccountsState, onBack: () -> Unit, onUnblock: (User) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
                state.users.isEmpty() -> EmptyState(
                    headline = "No one blocked",
                    body = "Block someone from the ⋯ menu on their trip, comment or profile. They aren't told.",
                    icon = Icons.Outlined.Block,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item(key = "intro") {
                        Text(
                            "You and these accounts can't see each other's trips, comments or notifications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        )
                    }
                    state.error?.let { error ->
                        item(key = "error") {
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = Spacing.md)
                            )
                        }
                    }
                    items(state.users, key = { it.id }) { user ->
                        BlockedRow(user = user, isUnblocking = user.id in state.unblocking, onUnblock = { onUnblock(user) })
                        HairlineDivider(modifier = Modifier.padding(start = Spacing.md))
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedRow(user: User, isUnblocking: Boolean, onUnblock: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onUnblock, enabled = !isUnblocking) { Text(if (isUnblocking) "Unblocking…" else "Unblock") }
    }
}

// --- Previews ----------------------------------------------------------------

@Preview(showBackground = true, heightDp = 400, name = "Blocked accounts")
@Composable
private fun BlockedAccountsPreview() {
    val at = Instant.parse("2026-09-01T00:00:00Z")
    CuratedTheme {
        BlockedAccountsContent(
            state = BlockedAccountsState(
                isLoading = false,
                users = listOf(
                    User(id = "1", username = "wanderer_sofia", displayName = "Sofia Almeida", createdAt = at),
                    User(id = "2", username = "foodie_travels_kai", displayName = "Kai Nakamura", createdAt = at)
                )
            ),
            onBack = {},
            onUnblock = {}
        )
    }
}
