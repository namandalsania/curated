package com.curated.app.core.trip

/** Where a stop sits in its trip, for picking the trip's first stop. */
data class StopPlacement(
    val stopId: String,
    val tripId: String,
    /** The day's day_index; null when the stop isn't on a day. */
    val dayIndex: Int?,
    val orderInDay: Int,
    val latitude: Double,
    val longitude: Double
)

/**
 * Reading order: day, then position in the day, then stop id. order_in_day
 * restarts every day, so it means nothing across days on its own; the id
 * makes ties (two stops at one position) come out the same on every load.
 * Stops not on a day go last.
 */
val READING_ORDER: Comparator<StopPlacement> =
    compareBy<StopPlacement>({ it.dayIndex ?: Int.MAX_VALUE }, { it.orderInDay }, { it.stopId })

/**
 * Each trip's first stop: the first stop of its earliest day.
 *
 * A stop that isn't on a day is hidden from everyone but the author on a
 * published trip, so where [daylessAllowed] says no for a trip, those stops
 * can't be its first stop. A trip with no eligible stop is left out.
 */
fun firstStopByTrip(
    stops: List<StopPlacement>,
    daylessAllowed: (tripId: String) -> Boolean
): Map<String, StopPlacement> =
    stops
        .filter { it.dayIndex != null || daylessAllowed(it.tripId) }
        .groupBy { it.tripId }
        .mapValues { (_, tripStops) -> tripStops.minWith(READING_ORDER) }
