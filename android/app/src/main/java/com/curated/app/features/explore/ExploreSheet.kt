package com.curated.app.features.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.curated.app.core.data.LikeSummary
import com.curated.app.core.model.Trip
import com.curated.app.core.model.TripStatus
import com.curated.app.core.model.User
import com.curated.app.designsystem.CuratedCornerRadius
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing
import com.curated.app.features.home.FeedItem
import com.curated.app.features.home.FeedItemCard
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Tall enough for the drag handle, the "N trips" line and one row of cards. */
val ExploreSheetPeekHeight = 290.dp

private val SheetCardWidth = 168.dp

/**
 * Explore's bottom sheet. Peeking: the trips whose pins are in view, as a row
 * of small cards. Pulled up: every trip that matches, ranked, as full cards -
 * what the old list view showed.
 */
@Composable
fun ExploreSheetContent(
    inView: List<FeedItem>,
    all: List<FeedItem>,
    isLoading: Boolean,
    hasFilters: Boolean,
    query: String,
    onClearFilters: () -> Unit,
    onClearSearch: () -> Unit,
    selectedTripId: String?,
    rowState: LazyListState,
    onTripClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onLikeToggle: (String) -> Unit,
    onSaveToggle: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        if (all.isEmpty() && !isLoading) {
            item(key = "empty") {
                NoMatches(hasFilters, query, onClearFilters, onClearSearch)
            }
            return@LazyColumn
        }
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    inViewHeadline(inView.size, isLoading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        item(key = "row") {
            if (inView.isEmpty()) {
                Text(
                    "Move the map to see trips somewhere else.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md).height(SheetCardWidth)
                )
            } else {
                LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(inView, key = { it.trip.id }) { item ->
                        TripSheetCard(
                            item = item,
                            isSelected = item.trip.id == selectedTripId,
                            onClick = { onTripClick(item.trip.id) }
                        )
                    }
                }
            }
        }
        if (all.isNotEmpty()) {
            item(key = "all") {
                Text(
                    if (all.size == 1) "1 trip" else "All ${all.size} trips",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                )
            }
            items(all, key = { "all-${it.trip.id}" }) { item ->
                FeedItemCard(
                    item = item,
                    onClick = { onTripClick(item.trip.id) },
                    onAuthorClick = { onAuthorClick(item.trip.authorId) },
                    onLikeToggle = { onLikeToggle(item.trip.id) },
                    onSaveToggle = { onSaveToggle(item.trip.id) }
                )
            }
        }
    }
}

/** Nothing matches: say why, quietly, and offer the one way back. */
@Composable
private fun NoMatches(hasFilters: Boolean, query: String, onClearFilters: () -> Unit, onClearSearch: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            when {
                hasFilters -> "No trips match these filters"
                query.isNotBlank() -> "No trips match \u201c${query.trim()}\u201d"
                else -> "No trips yet"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        when {
            hasFilters -> TextButton(onClick = onClearFilters, contentPadding = PaddingValues(0.dp)) { Text("Clear filters") }
            query.isNotBlank() -> TextButton(onClick = onClearSearch, contentPadding = PaddingValues(0.dp)) { Text("Clear search") }
        }
    }
}

private fun inViewHeadline(count: Int, isLoading: Boolean): String = when {
    isLoading && count == 0 -> "Finding trips…"
    count == 1 -> "1 trip in this area"
    else -> "$count trips in this area"
}

/** A trip in the peek row: 4:3 cover, title, "City · N days · N stops", author. */
@Composable
fun TripSheetCard(item: FeedItem, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(CuratedCornerRadius)
    Column(
        modifier = modifier
            .width(SheetCardWidth)
            .clip(shape)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape) else Modifier
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box {
            AsyncImage(
                model = item.trip.coverPhotoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            AsyncImage(
                model = item.trip.author?.avatarUrl,
                contentDescription = item.trip.author?.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.xs)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
        Column(modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs)) {
            Text(
                item.trip.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                sheetCardMeta(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** "Lisbon · 3 days · 7 stops" - the city alone, since the card is narrow. */
internal fun sheetCardMeta(item: FeedItem): String {
    val days = item.trip.lengthInDays()
    return listOf(
        item.trip.destination.substringBefore(',').trim(),
        if (days == 1) "1 day" else "$days days",
        if (item.stopCount == 1) "1 stop" else "${item.stopCount} stops"
    ).filter { it.isNotBlank() }.joinToString(" · ")
}

// --- Previews ----------------------------------------------------------------

private val PreviewTime = Instant.parse("2026-09-15T10:00:00Z")

private val previewItem = FeedItem(
    trip = Trip(
        id = "t1",
        authorId = "u1",
        title = "Three days in Lisbon",
        destination = "Lisbon, Portugal",
        startDate = LocalDate(2026, 9, 12),
        endDate = LocalDate(2026, 9, 14),
        status = TripStatus.COMPLETED,
        createdAt = PreviewTime,
        updatedAt = PreviewTime,
        author = User(id = "u1", username = "wanderlust_maya", displayName = "Maya Chen", createdAt = PreviewTime)
    ),
    stopCount = 7,
    likeSummary = LikeSummary(12, false),
    isSaved = false,
    commentCount = 3
)

@Preview(showBackground = true, name = "Sheet card")
@Composable
private fun TripSheetCardPreview() {
    CuratedTheme {
        Row(modifier = Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TripSheetCard(item = previewItem, isSelected = false, onClick = {})
            TripSheetCard(item = previewItem, isSelected = true, onClick = {})
        }
    }
}
