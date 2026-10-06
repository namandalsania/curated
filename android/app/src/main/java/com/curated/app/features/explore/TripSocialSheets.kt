package com.curated.app.features.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.curated.app.core.data.BlockedAccounts
import com.curated.app.core.data.ReportTarget
import com.curated.app.core.format.Noun
import com.curated.app.core.format.countText
import com.curated.app.core.format.shortDayText
import com.curated.app.core.model.StopComment
import com.curated.app.core.model.User
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.PrimaryButton
import com.curated.app.features.moderation.ModerationDialogs
import com.curated.app.features.moderation.ModerationMenu
import com.curated.app.features.moderation.ModerationSubject
import com.curated.app.features.moderation.rememberModerationViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Comments on one place. Anyone who can see the trip can read and add them;
 * you can delete your own, and the trip's author can delete any on their trip.
 */
@Composable
fun CommentsSheet(
    stopId: String,
    stopName: String,
    tripId: String,
    tripAuthorId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: CommentsViewModel = viewModel(
        key = "comments-$stopId",
        factory = CommentsViewModel.factory(context, stopId, tripId, tripAuthorId)
    )
    val state by viewModel.state.collectAsState()
    val blocked by BlockedAccounts.ids.collectAsState()
    val comments = state.comments.filter { it.authorId !in blocked }
    val moderation = rememberModerationViewModel(key = "comments-$stopId")
    val moderationState by moderation.state.collectAsState()
    // The comment is already filtered out; reload so the count is right too.
    LaunchedEffect(moderationState.blockedUserId) {
        if (moderationState.blockedUserId != null) {
            moderation.consumeBlocked()
            viewModel.load()
        }
    }
    ModerationDialogs(moderation)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = CuratedCornerRadius, topEnd = CuratedCornerRadius),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().imePadding().padding(bottom = Spacing.md)) {
            Text(
                stopName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.md)
            )
            Text(
                if (comments.isEmpty()) "No comments yet" else countText(comments.size, Noun.COMMENT),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )

            when {
                state.isLoading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(Spacing.lg)
                )
                comments.isEmpty() -> Text(
                    "Ask about opening times, what to order, whether it's worth the queue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                )
                else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(comments, key = { it.id }) { comment ->
                        CommentRow(
                            comment = comment,
                            canDelete = viewModel.canDelete(comment),
                            onDelete = { viewModel.delete(comment) },
                            moderation = if (comment.authorId != state.myUserId) {
                                {
                                    ModerationMenu(
                                        subject = ModerationSubject(
                                            target = ReportTarget.COMMENT,
                                            targetId = comment.id,
                                            userId = comment.authorId,
                                            userName = comment.author?.let { "@${it.username}" } ?: "this person"
                                        ),
                                        viewModel = moderation,
                                        iconSize = 20.dp
                                    )
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }

            state.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = viewModel::setDraft,
                    placeholder = { Text("Add a comment") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = viewModel::send, enabled = state.canSend) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Post comment",
                        tint = if (state.canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: StopComment,
    canDelete: Boolean,
    onDelete: () -> Unit,
    moderation: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Avatar(comment.author)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    comment.author?.displayName ?: "Someone",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    comment.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date.shortDayText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(comment.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        moderation?.invoke()
        if (canDelete) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Delete comment",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Send this trip to people you follow, or who follow you, with an optional note. */
@Composable
fun ShareTripSheet(tripId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val viewModel: ShareTripViewModel = viewModel(
        key = "share-$tripId",
        factory = ShareTripViewModel.factory(context, tripId)
    )
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.sentTo) {
        if (state.sentTo != null) {
            viewModel.consumeSent()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = CuratedCornerRadius, topEnd = CuratedCornerRadius),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().imePadding().padding(bottom = Spacing.md)) {
            Text(
                "Send to",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.md)
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::search,
                placeholder = { Text("Search by username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)
            )

            when {
                state.isLoading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(Spacing.lg)
                )
                state.shown.isEmpty() -> Text(
                    if (state.query.isBlank()) {
                        "Follow someone, or search for a username, to send this to them."
                    } else {
                        "Nobody found with that username."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md)
                )
                else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(state.shown, key = { it.id }) { user ->
                        val checked = user.id in state.selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggle(user.id) }
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Avatar(user)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    user.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "@${user.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { viewModel.toggle(user.id) },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                placeholder = { Text("Add a note (optional)") },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)
            )

            state.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                )
            }

            PrimaryButton(
                onClick = viewModel::send,
                enabled = state.canSend,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Text(
                    when (state.selected.size) {
                        0 -> "Choose people"
                        1 -> "Send"
                        else -> "Send to ${state.selected.size}"
                    }
                )
            }
        }
    }
}

@Composable
private fun Avatar(user: User?) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (user?.avatarUrl != null) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                user?.displayName?.firstOrNull()?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
