package com.curated.app.features.create

import com.curated.app.core.data.TripDaySection
import com.curated.app.core.model.Day
import com.curated.app.core.model.Stop
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The decisions behind posting a live trip, kept free of Android and Supabase so
 * they can be tested on their own.
 */
object LiveTripRules {

    /**
     * How many days a live trip shows: every day up to today, and never fewer
     * than the days that already hold something (a trip whose clock was wrong
     * shouldn't hide what's on it). At least one.
     */
    fun dayCount(startDate: LocalDate, today: LocalDate, days: List<Day>, sections: List<TripDaySection>): Int {
        val elapsed = (today.toEpochDays() - startDate.toEpochDays() + 1).toInt()
        val used = maxOf(days.maxOfOrNull { it.dayIndex } ?: 0, sections.maxOfOrNull { it.dayIndex ?: 0 } ?: 0)
        return maxOf(elapsed, used, 1)
    }

    /** Why a day can't be posted yet, or null when it can. */
    sealed interface PostProblem {
        data object NoStops : PostProblem

        /** Stops that would be posted but aren't attached to a day row. */
        data class Unassigned(val stops: List<Stop>) : PostProblem
    }

    /**
     * Every stop being posted must carry a day_id. The database already hides a
     * day-less stop from everyone but its author, so posting one would publish a
     * day that looks complete to you and is missing places for everyone else.
     */
    fun postProblem(stops: List<Stop>): PostProblem? = when {
        stops.isEmpty() -> PostProblem.NoStops
        stops.any { it.dayId == null } -> PostProblem.Unassigned(stops.filter { it.dayId == null })
        else -> null
    }

    /**
     * Splits picked photos into those taken on [date] and those that weren't, so
     * posting Day 3 can't quietly pull in a photo from Day 1. A photo with no
     * timestamp can't be placed at all, so it's given the benefit of the doubt.
     */
    fun <T> splitByDay(photos: List<T>, date: LocalDate, zone: TimeZone, takenAt: (T) -> Instant?): Pair<List<T>, List<T>> =
        photos.partition { photo ->
            val at = takenAt(photo) ?: return@partition true
            at.toLocalDateTime(zone).date == date
        }

    /** Unposted days that End trip will publish: the ones with at least one stop. */
    fun daysEndTripWillPublish(days: List<Day>, sections: List<TripDaySection>): List<Int> {
        val withStops = sections.filter { it.stops.isNotEmpty() }.mapNotNull { it.dayIndex }.toSet()
        val posted = days.filter { it.isPublished }.map { it.dayIndex }.toSet()
        return withStops.filter { it !in posted }.sorted()
    }

    /** The trip ends on the last day that has anything on it. */
    fun endDate(startDate: LocalDate, sections: List<TripDaySection>): LocalDate {
        val lastDay = sections.filter { it.stops.isNotEmpty() }.mapNotNull { it.dayIndex }.maxOrNull() ?: 1
        return LocalDate.fromEpochDays(startDate.toEpochDays() + lastDay - 1)
    }
}
