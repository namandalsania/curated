package com.curated.app.features.moderation

import com.curated.app.core.data.LikeSummary
import com.curated.app.core.data.REPORT_NOTE_MAX_LENGTH
import com.curated.app.core.data.ReportReason
import com.curated.app.core.data.ReportTarget
import com.curated.app.core.data.TripMapPin
import com.curated.app.core.model.Notification
import com.curated.app.core.model.NotificationType
import com.curated.app.core.model.Trip
import com.curated.app.core.model.TripShare
import com.curated.app.features.explore.ExploreUiState
import com.curated.app.features.explore.withoutAuthors
import com.curated.app.features.home.FeedItem
import com.curated.app.features.home.HomeUiState
import com.curated.app.features.home.InboxState
import com.curated.app.features.home.withoutAuthors
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.time.Instant

class ModerationTest {

    private val at = Instant.parse("2026-10-01T00:00:00Z")

    private fun trip(id: String, author: String) = Trip(
        id = id, authorId = author, title = id, destination = "D",
        startDate = LocalDate(2026, 9, 1), endDate = LocalDate(2026, 9, 2),
        createdAt = at, updatedAt = at
    )

    private fun feedItem(id: String, author: String) =
        FeedItem(trip(id, author), stopCount = 1, likeSummary = LikeSummary(0, false), isSaved = false, commentCount = 0)

    // --- The app's values must be exactly what the table's checks accept ---

    @Test
    fun `report targets match reports_target_type`() {
        assertEquals(listOf("trip", "stop", "comment", "profile"), ReportTarget.entries.map { it.dbValue })
    }

    @Test
    fun `report reasons match reports_reason`() {
        assertEquals(
            listOf("spam", "inappropriate", "harassment", "misleading", "other"),
            ReportReason.entries.map { it.dbValue }
        )
    }

    @Test
    fun `the note limit matches the table's`() {
        assertEquals(500, REPORT_NOTE_MAX_LENGTH)
    }

    // --- Client-side filtering of blocked accounts ---

    @Test
    fun `home drops blocked authors' trips and notifications, and keeps everyone else's`() {
        val state = HomeUiState(
            feed = listOf(feedItem("t1", "blocked"), feedItem("t2", "friend")),
            notifications = listOf(
                Notification(id = "n1", recipientId = "me", actorId = "blocked", type = NotificationType.FOLLOW, createdAt = at),
                Notification(id = "n2", recipientId = "me", actorId = "friend", type = NotificationType.FOLLOW, createdAt = at),
                Notification(id = "n3", recipientId = "me", actorId = null, type = NotificationType.NEW_TRIP, createdAt = at)
            )
        )
        val filtered = state.withoutAuthors(setOf("blocked"))
        assertEquals(listOf("t2"), filtered.feed.map { it.trip.id })
        assertEquals(listOf("n2", "n3"), filtered.notifications.map { it.id })
    }

    @Test
    fun `the inbox drops what blocked accounts sent`() {
        val state = InboxState(
            isLoading = false,
            shares = listOf(
                TripShare(id = "s1", tripId = "t", senderId = "blocked", recipientId = "me", createdAt = at),
                TripShare(id = "s2", tripId = "t", senderId = "friend", recipientId = "me", createdAt = at)
            )
        )
        assertEquals(listOf("s2"), state.withoutAuthors(setOf("blocked")).shares.map { it.id })
    }

    @Test
    fun `explore drops blocked authors from the list and the map`() {
        val state = ExploreUiState(
            listItems = listOf(feedItem("t1", "blocked"), feedItem("t2", "friend")),
            tripPins = listOf(TripMapPin(trip("t1", "blocked"), 0.0, 0.0), TripMapPin(trip("t2", "friend"), 1.0, 1.0))
        )
        val filtered = state.withoutAuthors(setOf("blocked"))
        assertEquals(listOf("t2"), filtered.listItems.map { it.trip.id })
        assertEquals(listOf("t2"), filtered.tripPins.map { it.trip.id })
    }

    @Test
    fun `with nobody blocked, the state is passed through untouched`() {
        val state = ExploreUiState(listItems = listOf(feedItem("t1", "a")))
        assertSame(state, state.withoutAuthors(emptySet()))
    }
}
