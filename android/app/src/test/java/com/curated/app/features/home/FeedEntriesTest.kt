package com.curated.app.features.home

import com.curated.app.core.data.LikeSummary
import com.curated.app.core.data.PostedLiveDay
import com.curated.app.core.model.Trip
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class FeedEntriesTest {

    private val now = Instant.parse("2026-10-08T12:00:00Z")

    private fun trip(id: String, publishedDaysAgo: Int?, createdDaysAgo: Int = 30, destination: String = "Lisbon, Portugal") = Trip(
        id = id, authorId = "a", title = id, destination = destination,
        startDate = LocalDate(2026, 9, 1), endDate = LocalDate(2026, 9, 3),
        createdAt = now - createdDaysAgo.days, updatedAt = now,
        completedAt = publishedDaysAgo?.let { now - it.days }
    )

    private fun feed(trip: Trip) = FeedItem(trip, stopCount = 1, likeSummary = LikeSummary(0, false), isSaved = false, commentCount = 0)

    private fun day(id: String, daysAgo: Int, index: Int = 1, destination: String = "Bangkok, Thailand") = LiveDayItem(
        PostedLiveDay(
            dayId = id, dayIndex = index, publishedAt = now - daysAgo.days,
            trip = trip("live-$id", null, destination = destination), stopNames = emptyList(), coverUrl = null
        )
    )

    @Test
    fun `trips and days interleave by time, newest first`() {
        val merged = mergeFeed(
            trips = listOf(feed(trip("t1", 1)), feed(trip("t3", 3))),
            days = listOf(day("d2", 2), day("d0", 0)),
            tripsComplete = true
        )
        assertEquals(listOf("day-d0", "trip-t1", "day-d2", "trip-t3"), merged.map { it.key })
    }

    @Test
    fun `a day older than the loaded trips waits for the next page`() {
        val trips = listOf(feed(trip("t1", 1)), feed(trip("t2", 2)))
        val days = listOf(day("old", 5))
        assertEquals(listOf("trip-t1", "trip-t2"), mergeFeed(trips, days, tripsComplete = false).map { it.key })
        assertEquals(listOf("trip-t1", "trip-t2", "day-old"), mergeFeed(trips, days, tripsComplete = true).map { it.key })
    }

    @Test
    fun `with no trips at all, the days are the feed`() {
        assertEquals(listOf("day-a", "day-b"), mergeFeed(emptyList(), listOf(day("b", 2), day("a", 1)), tripsComplete = false).map { it.key })
    }

    @Test
    fun `a trip's time is when it was published, not when its draft started`() {
        val oldDraftPublishedToday = trip("draft", publishedDaysAgo = 0, createdDaysAgo = 60)
        val legacy = trip("legacy", publishedDaysAgo = null, createdDaysAgo = 3)
        assertEquals(now, oldDraftPublishedToday.publishedTime)
        assertEquals(now - 3.days, legacy.publishedTime)
    }

    @Test
    fun `a live day reads as its number and city`() {
        assertEquals("Day 3 · Bangkok", day("x", 0, index = 3).title)
        assertEquals("Day 1 · Kyoto", day("y", 0, destination = "Kyoto").title)
    }
}
