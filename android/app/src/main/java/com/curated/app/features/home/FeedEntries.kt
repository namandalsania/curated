package com.curated.app.features.home

import com.curated.app.core.data.PostedLiveDay
import com.curated.app.core.model.Trip
import kotlin.time.Instant

/** A day posted on a live trip, as a Following feed item: "Day 3 · Bangkok", with a Live tag. */
data class LiveDayItem(val day: PostedLiveDay) {
    val trip: Trip get() = day.trip
    /** "Day 3 · Bangkok" - the destination's first part, which is the city. */
    val title: String get() = "Day ${day.dayIndex} · ${trip.destination.substringBefore(",").trim()}"
}

/** One row of the Home feed. */
sealed interface FeedEntry {
    val key: String
    val time: Instant

    data class TripEntry(val item: FeedItem) : FeedEntry {
        override val key: String get() = "trip-${item.trip.id}"
        override val time: Instant get() = item.trip.publishedTime
    }

    data class DayEntry(val item: LiveDayItem) : FeedEntry {
        override val key: String get() = "day-${item.day.dayId}"
        override val time: Instant get() = item.day.publishedAt
    }
}

/** When a finished trip went public: completed_at, or created_at for rows from before it existed. */
val Trip.publishedTime: Instant get() = completedAt ?: createdAt

/**
 * Trips and live days in one list, newest first. Trips arrive a page at a time,
 * so a day older than the oldest trip loaded so far waits: it may belong after
 * trips not loaded yet. Once the trips run out ([tripsComplete]) every day shows.
 */
fun mergeFeed(trips: List<FeedItem>, days: List<LiveDayItem>, tripsComplete: Boolean): List<FeedEntry> {
    val oldestTrip = trips.minOfOrNull { it.trip.publishedTime }
    val shownDays = days.filter { tripsComplete || oldestTrip == null || it.day.publishedAt >= oldestTrip }
    return (trips.map { FeedEntry.TripEntry(it) } + shownDays.map { FeedEntry.DayEntry(it) })
        .sortedByDescending { it.time }
}
