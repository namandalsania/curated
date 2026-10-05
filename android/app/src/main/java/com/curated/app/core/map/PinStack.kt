package com.curated.app.core.map

/** Pins closer than this (in degrees, ~10 m) count as the same spot. */
const val SAME_SPOT_DEGREES = 0.0001

/**
 * Whether every point sits in one spot. Such a cluster can't be split by
 * zooming - the clustering groups pins by on-screen distance, and theirs is
 * zero at every zoom - so tapping it has to show its pins some other way.
 */
fun allAtSameSpot(points: List<Pair<Double, Double>>): Boolean {
    val first = points.firstOrNull() ?: return false
    return points.all {
        kotlin.math.abs(it.first - first.first) < SAME_SPOT_DEGREES &&
            kotlin.math.abs(it.second - first.second) < SAME_SPOT_DEGREES
    }
}

/** Of [stackIds], the one that comes first in [rankedIds]; null if none of them are there. */
fun firstRanked(stackIds: Collection<String>, rankedIds: List<String>): String? =
    rankedIds.firstOrNull { it in stackIds }
