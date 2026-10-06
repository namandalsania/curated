package com.curated.app.features.moderation

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.curated.app.core.data.ModerationRepository
import com.curated.app.core.data.REPORT_NOTE_MAX_LENGTH
import com.curated.app.core.data.ReportReason
import com.curated.app.core.data.ReportTarget
import com.curated.app.core.data.SupabaseProvider
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.PrimaryButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Something that can be reported, and whose author can be blocked. */
data class ModerationSubject(
    val target: ReportTarget,
    val targetId: String,
    /** The person behind it - who gets blocked. */
    val userId: String,
    /** "@maya" or a display name, for the block confirmation. */
    val userName: String
)

data class ModerationState(
    val reporting: ModerationSubject? = null,
    val isSending: Boolean = false,
    val reported: Boolean = false,
    val confirmingBlock: ModerationSubject? = null,
    val isBlocking: Boolean = false,
    /** Set once a block lands; the screen leaves or reloads on it. */
    val blockedUserId: String? = null,
    val isUnblocking: Boolean = false,
    val error: String? = null
)

/** Report and Block for one screen. */
class ModerationViewModel(private val repository: ModerationRepository) : ViewModel() {

    private val _state = MutableStateFlow(ModerationState())
    val state: StateFlow<ModerationState> = _state

    fun startReport(subject: ModerationSubject) = _state.update { ModerationState(reporting = subject) }

    fun startBlock(subject: ModerationSubject) = _state.update { ModerationState(confirmingBlock = subject) }

    fun dismiss() = _state.update { ModerationState() }

    fun submitReport(reason: ReportReason, note: String) {
        val subject = _state.value.reporting ?: return
        _state.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            try {
                repository.report(subject.target, subject.targetId, reason, note)
                _state.update { it.copy(isSending = false, reported = true) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't file a report on ${subject.target} ${subject.targetId}", e)
                _state.update { it.copy(isSending = false, error = "Couldn't send that. Try again.") }
            }
        }
    }

    fun confirmBlock() {
        val subject = _state.value.confirmingBlock ?: return
        _state.update { it.copy(isBlocking = true, error = null) }
        viewModelScope.launch {
            try {
                repository.block(subject.userId)
                _state.update { ModerationState(blockedUserId = subject.userId) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't block ${subject.userId}", e)
                _state.update { it.copy(isBlocking = false, error = "Couldn't block ${subject.userName}. Try again.") }
            }
        }
    }

    fun consumeBlocked() = _state.update { it.copy(blockedUserId = null) }

    /** Lifts a block from the blocked profile itself. [BlockedAccounts] updates, so the screen follows. */
    fun unblock(userId: String) {
        _state.update { it.copy(isUnblocking = true, error = null) }
        viewModelScope.launch {
            try {
                repository.unblock(userId)
                _state.update { it.copy(isUnblocking = false) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't unblock $userId", e)
                _state.update { it.copy(isUnblocking = false, error = "Couldn't unblock. Try again.") }
            }
        }
    }

    companion object {
        private const val TAG = "ModerationViewModel"

        fun factory(context: Context) = viewModelFactory {
            initializer {
                ModerationViewModel(ModerationRepository(SupabaseProvider.client(context.applicationContext)))
            }
        }
    }
}

/**
 * The screen's moderation view model, for [ModerationMenu] and [ModerationDialogs].
 *
 * [key] is namespaced: a screen's own view model may already use the same
 * string (CommentsViewModel uses "comments-<stop>"), and two view models under
 * one key evict each other on every recomposition.
 */
@Composable
fun rememberModerationViewModel(key: String): ModerationViewModel {
    val context = LocalContext.current
    return viewModel(key = "moderation:$key", factory = ModerationViewModel.factory(context))
}

/**
 * The "⋯" button with Report and Block. Callers show it only on other people's
 * content - there's nothing to report or block about your own.
 */
@Composable
fun ModerationMenu(
    subject: ModerationSubject,
    viewModel: ModerationViewModel,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = "More options", modifier = Modifier.size(iconSize))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(reportLabel(subject.target)) },
                leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                onClick = {
                    open = false
                    viewModel.startReport(subject)
                }
            )
            DropdownMenuItem(
                text = { Text("Block ${subject.userName}") },
                leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                onClick = {
                    open = false
                    viewModel.startBlock(subject)
                }
            )
        }
    }
}

private fun reportLabel(target: ReportTarget) = when (target) {
    ReportTarget.TRIP -> "Report trip"
    ReportTarget.STOP -> "Report place"
    ReportTarget.COMMENT -> "Report comment"
    ReportTarget.PROFILE -> "Report profile"
}

/** The report sheet and the block confirmation, whichever is open. */
@Composable
fun ModerationDialogs(viewModel: ModerationViewModel) {
    val state by viewModel.state.collectAsState()
    state.reporting?.let { subject ->
        ReportSheet(
            target = subject.target,
            isSending = state.isSending,
            reported = state.reported,
            error = state.error,
            onSubmit = viewModel::submitReport,
            onDismiss = viewModel::dismiss
        )
    }
    state.confirmingBlock?.let { subject ->
        BlockDialog(
            userName = subject.userName,
            isBlocking = state.isBlocking,
            error = state.error,
            onConfirm = viewModel::confirmBlock,
            onDismiss = viewModel::dismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(
    target: ReportTarget,
    isSending: Boolean,
    reported: Boolean,
    error: String?,
    onSubmit: (ReportReason, String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = CuratedCornerRadius, topEnd = CuratedCornerRadius),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        if (reported) ReportSent(onDone = onDismiss)
        else ReportForm(target, isSending, error, onSubmit)
    }
}

@Composable
private fun ReportForm(
    target: ReportTarget,
    isSending: Boolean,
    error: String?,
    onSubmit: (ReportReason, String) -> Unit
) {
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var note by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = Spacing.md).padding(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(reportLabel(target), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Reports are private. The person isn't told who sent one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.selectableGroup()) {
            ReportReason.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = option == reason, role = Role.RadioButton, onClick = { reason = option })
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    RadioButton(selected = option == reason, onClick = null)
                    Text(option.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(REPORT_NOTE_MAX_LENGTH) },
            label = { Text("Anything else? (optional)") },
            supportingText = { Text("${note.length} / $REPORT_NOTE_MAX_LENGTH") },
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        PrimaryButton(
            onClick = { reason?.let { onSubmit(it, note) } },
            enabled = reason != null && !isSending,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSending) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            } else {
                Text("Send report")
            }
        }
    }
}

/** The quiet confirmation once a report is in. */
@Composable
private fun ReportSent(onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text("Thanks for telling us", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "We'll take a look. If you'd rather not see this person at all, you can block them from the same menu.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.End)) { Text("Done") }
    }
}

@Composable
private fun BlockDialog(
    userName: String,
    isBlocking: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block $userName?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(blockExplanation(userName))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isBlocking) { Text(if (isBlocking) "Blocking…" else "Block") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isBlocking) { Text("Cancel") } }
    )
}

internal fun blockExplanation(userName: String) =
    "You won't see each other's trips, comments or notifications, and any follows between you are removed. " +
        "$userName isn't told. You can unblock them from your profile under Blocked accounts."

// --- Previews ----------------------------------------------------------------

@Preview(showBackground = true, heightDp = 640, name = "Report sheet")
@Composable
private fun ReportFormPreview() {
    CuratedTheme { ReportForm(target = ReportTarget.TRIP, isSending = false, error = null, onSubmit = { _, _ -> }) }
}

@Preview(showBackground = true, name = "Report sent")
@Composable
private fun ReportSentPreview() {
    CuratedTheme { ReportSent(onDone = {}) }
}

@Preview(showBackground = true, name = "Block dialog")
@Composable
private fun BlockDialogPreview() {
    CuratedTheme { BlockDialog(userName = "@wanderer_sofia", isBlocking = false, error = null, onConfirm = {}, onDismiss = {}) }
}
