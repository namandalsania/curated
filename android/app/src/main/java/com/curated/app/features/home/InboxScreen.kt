package com.curated.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.curated.app.core.model.TripShare
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.EmptyState
import com.curated.app.designsystem.components.ErrorState
import com.curated.app.designsystem.components.HairlineCard
import com.curated.app.features.activity.nameOf
import kotlin.time.Clock
import kotlin.time.Instant

/** The neutral line for any trip that can't be opened - private, deleted, blocked or not posted yet. */
const val TRIP_UNAVAILABLE = "This trip isn't available."

/**
 * Trips people sent you: the "Sent to you" tab of Activity. Rows that were
 * unread when it opened keep a tint; a trip you can't open says so, the same
 * way whatever the reason.
 */
@Composable
fun SentToYouList(
    state: InboxState,
    onRetry: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onUnavailable: () -> Unit,
    onRemove: (TripShare) -> Unit,
    modifier: Modifier = Modifier
) {
    val now = Clock.System.now()
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
            state.error != null -> ErrorState(
                message = state.error,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center)
            )
            state.shares.isEmpty() -> EmptyState(
                headline = "Nothing sent to you yet",
                body = "When someone sends you a trip, it lands here.",
                icon = Icons.AutoMirrored.Outlined.Send,
                modifier = Modifier.align(Alignment.Center)
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(state.shares, key = { it.id }) { share ->
                    ShareCard(
                        share = share,
                        isUnread = share.id in state.unreadIds,
                        now = now,
                        onOpen = { if (share.trip != null) onOpenTrip(share.tripId) else onUnavailable() },
                        onRemove = { onRemove(share) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareCard(share: TripShare, isUnread: Boolean, now: Instant, onOpen: () -> Unit, onRemove: () -> Unit) {
    val trip = share.trip
    HairlineCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isUnread) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)) else Modifier)
                .clickable(onClick = onOpen)
                .padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(CuratedCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                trip?.coverPhotoUrl?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isUnread) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                    Text(
                        "${nameOf(share.sender)} sent you · ${relativeTime(share.createdAt, now)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    trip?.title ?: TRIP_UNAVAILABLE,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (trip != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                share.note?.let {
                    Text(
                        "“$it”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Remove from your inbox",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
