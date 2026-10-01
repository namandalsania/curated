package com.curated.app.features.explore

import com.curated.app.core.data.TripCompleteness
import com.curated.app.core.model.Trip
import kotlin.time.Clock

private const val RECENCY_WEIGHT = 0.6
private const val COMPLETENESS_WEIGHT = 0.4
private const val MAX_STOPS_FOR_SCORE = 10

/**
 * A basic weighted sort, not a recommendation engine: newer trips and more
 * "filled-in" trips (more stops, more of them captioned) rank higher.
 */
fun rankingScore(trip: Trip, completeness: TripCompleteness): Double {
    val daysSinceCreated = (Clock.System.now() - trip.createdAt).inWholeDays.coerceAtLeast(0)
    val recencyScore = 1.0 / (1.0 + daysSinceCreated)

    val stopScore = completeness.stopCount.coerceAtMost(MAX_STOPS_FOR_SCORE).toDouble() / MAX_STOPS_FOR_SCORE
    val captionRatio = if (completeness.stopCount == 0) {
        0.0
    } else {
        completeness.captionedStopCount.toDouble() / completeness.stopCount
    }
    val completenessScore = (stopScore * 0.5) + (captionRatio * 0.5)

    return RECENCY_WEIGHT * recencyScore + COMPLETENESS_WEIGHT * completenessScore
}
