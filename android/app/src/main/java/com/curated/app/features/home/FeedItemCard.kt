package com.curated.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.curated.app.core.format.Noun
import com.curated.app.core.format.countText
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.LikeButton
import com.curated.app.designsystem.pressScale
import com.curated.app.features.trip.StopPreviewRow
import com.curated.app.features.trip.stopPreview
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

/** Gap between an icon button's edge and its 24dp glyph: (36dp - 24dp) / 2. */
private val IconInset = 6.dp

private val CardCorner = 16.dp
private val AvatarSize = 32.dp

/** Landscape: tall enough to carry a photograph, short enough to scroll past. */
private const val IMAGE_ASPECT_RATIO = 4f / 3f

/** Stops named before the preview switches to "+N". */
/**
 * One trip in the feed: who posted it, their photograph, and what the trip was.
 *
 * The title sits under the image rather than over it. Text on the photo looked
 * striking on a dark cover and became unreadable on a bright one, and it meant
 * the image could never be looked at on its own terms.
 */
@Composable
fun FeedItemCard(
    item: FeedItem,
    onClick: () -> Unit,
    onAuthorClick: () -> Unit,
    onLikeToggle: () -> Unit,
    onSaveToggle: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now()
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(CardCorner)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .pressScale(interactionSource)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        AuthorRow(item = item, now = now, onAuthorClick = onAuthorClick)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(IMAGE_ASPECT_RATIO)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (item.trip.coverPhotoUrl != null) {
                AsyncImage(
                    model = item.trip.coverPhotoUrl,
                    contentDescription = item.trip.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm + 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                item.trip.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                tripSubtitle(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.stopNames.isNotEmpty()) {
                StopPreviewRow(stopPreview(item.stopNames, item.stopCount))
            }

            ActionRow(item = item, onLikeToggle = onLikeToggle, onSaveToggle = onSaveToggle)
        }
    }
}

@Composable
private fun AuthorRow(item: FeedItem, now: Instant, onAuthorClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAuthorClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        val author = item.trip.author
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (author?.avatarUrl != null) {
                AsyncImage(
                    model = author.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // An initial beats a grey circle when someone hasn't set a photo.
                Text(
                    author?.displayName?.firstOrNull()?.uppercase().orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            author?.displayName ?: "someone",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            relativeTime(item.trip.createdAt, now),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionRow(item: FeedItem, onLikeToggle: () -> Unit, onSaveToggle: () -> Unit) {
    // The icon buttons center a 24dp glyph in a 36dp touch target, so the heart
    // sat 6dp right of the text above. Pull the row left by that inset so its
    // glyph lines up with the text's left edge.
    Row(
        modifier = Modifier.offset(x = -IconInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        LikeButton(
            liked = item.likeSummary.likedByMe,
            onToggle = onLikeToggle,
            unlikedTint = MaterialTheme.colorScheme.onSurface
        )
        // A zero tells you nothing; the empty heart already says "no likes yet".
        if (item.likeSummary.likeCount > 0) {
            Text(
                "${item.likeSummary.likeCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Comments live on individual places inside the trip, so this is a
        // count, not a button: opening the trip is how you get to them.
        Icon(
            Icons.AutoMirrored.Outlined.Comment,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = Spacing.sm).size(18.dp)
        )
        if (item.commentCount > 0) {
            Text(
                "${item.commentCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        IconButton(
            onClick = onSaveToggle,
            modifier = Modifier.size(36.dp).padding(start = Spacing.sm)
        ) {
            Icon(
                if (item.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (item.isSaved) "Saved" else "Save",
                tint = if (item.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** "Lisbon, Portugal · 3 days · 10 stops" */
private fun tripSubtitle(item: FeedItem): String {
    val days = tripDays(item.trip.startDate, item.trip.endDate)
    return buildString {
        append(item.trip.destination)
        append(" · ")
        append(countText(days, Noun.DAY))
        append(" · ")
        append(countText(item.stopCount, Noun.STOP))
    }
}

/** Inclusive of both ends: a trip that starts and ends the same day is one day. */
internal fun tripDays(start: LocalDate, end: LocalDate): Int =
    (end.toEpochDays() - start.toEpochDays() + 1).toInt().coerceAtLeast(1)

/** Compact age, the way a feed states it: "2d", "5h", "just now". */
internal fun relativeTime(posted: Instant, now: Instant): String {
    val elapsed = now - posted
    val minutes = elapsed.inWholeMinutes
    val hours = elapsed.inWholeHours
    val days = elapsed.inWholeDays
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        days < 365 -> "${days / 7}w"
        else -> "${days / 365}y"
    }
}
