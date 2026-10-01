package com.curated.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.curated.app.core.data.LikeSummary
import com.curated.app.core.model.Trip
import com.curated.app.core.model.TripStatus
import com.curated.app.core.model.TripVisibility
import com.curated.app.core.model.User
import com.curated.app.designsystem.CuratedTheme
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * The card with no network: no cover photo and no avatar, so the placeholder
 * states are what you actually see. Rendering is identical with images loaded.
 */
@Preview(name = "Feed card", showBackground = true)
@Composable
private fun FeedItemCardPreview() {
    CuratedTheme {
        FeedItemCard(
            item = sampleFeedItem,
            onClick = {},
            onAuthorClick = {},
            onLikeToggle = {},
            onSaveToggle = {},
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 16.dp),
            // Fixed, so the relative time reads "2d" every time rather than drifting.
            now = PREVIEW_NOW
        )
    }
}

/**
 * The awkward cases in one card: a repeated stop that must collapse, names long
 * enough to ellipsize, and zero counts that should hide rather than show "0".
 */
@Preview(name = "Feed card · long names, repeats, no counts", showBackground = true)
@Composable
private fun FeedItemCardEdgeCasesPreview() {
    CuratedTheme {
        FeedItemCard(
            item = sampleFeedItem.copy(
                trip = sampleFeedItem.trip.copy(title = "Seventeen stops around Lisbon"),
                likeSummary = LikeSummary(likeCount = 0, likedByMe = false),
                commentCount = 0,
                isSaved = true,
                stopCount = 17,
                stopNames = listOf(
                    "Rossio Station",
                    "Rossio Station",
                    "Miradouro de São Pedro de Alcântara",
                    "Mosteiro dos Jerónimos"
                )
            ),
            onClick = {},
            onAuthorClick = {},
            onLikeToggle = {},
            onSaveToggle = {},
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 16.dp),
            now = PREVIEW_NOW
        )
    }
}

private val PREVIEW_NOW = Instant.parse("2026-09-25T12:00:00Z")

private val sampleFeedItem = FeedItem(
    trip = Trip(
        id = "preview-trip",
        authorId = "preview-author",
        title = "Three days along the Sintra coast",
        destination = "Lisbon, Portugal",
        startDate = LocalDate(2026, 7, 28),
        endDate = LocalDate(2026, 7, 30),
        coverPhotoUrl = null,
        status = TripStatus.COMPLETED,
        visibility = TripVisibility.PUBLIC,
        createdAt = Instant.parse("2026-09-23T12:00:00Z"),
        updatedAt = Instant.parse("2026-09-23T12:00:00Z"),
        author = User(
            id = "preview-author",
            username = "sofia",
            displayName = "Sofia Almeida",
            avatarUrl = null,
            createdAt = Instant.parse("2026-01-01T00:00:00Z")
        )
    ),
    stopCount = 5,
    likeSummary = LikeSummary(likeCount = 24, likedByMe = true),
    isSaved = false,
    commentCount = 3,
    stopNames = listOf(
        "Pastéis de Belém",
        "Quinta da Regaleira",
        "Cabo da Roca",
        "Praia da Ursa",
        "Castelo dos Mouros"
    )
)
