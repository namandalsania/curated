package com.curated.app.features.explore

import com.curated.app.core.data.TripSearchParams
import com.curated.app.core.model.BudgetTag
import com.curated.app.core.model.SeasonTag
import com.curated.app.core.model.Trip

enum class TripLength { SHORT, MEDIUM, LONG }

enum class FollowScope { EVERYONE, FOLLOWING }

enum class ExploreViewMode { MAP, LIST }

data class ExploreFilters(
    val tripLength: TripLength? = null,
    val budgetTag: BudgetTag? = null,
    val seasonTag: SeasonTag? = null,
    val followScope: FollowScope = FollowScope.EVERYONE
) {
    /**
     * How many filters are narrowing the results. Shown on Explore's search pill,
     * which is now the only place a filter is visible from - the bar itself lives
     * inside the sheet.
     */
    val activeCount: Int
        get() = listOf(
            tripLength != null,
            budgetTag != null,
            seasonTag != null,
            followScope != FollowScope.EVERYONE
        ).count { it }
}

/**
 * What the trips query asks for. Budget, season, the search text and the
 * Following scope narrow it in the database; trip length isn't a column, so
 * [keeps] applies it to what comes back.
 */
fun ExploreFilters.toSearchParams(query: String, authorIds: List<String>?): TripSearchParams =
    TripSearchParams(
        query = query.trim().ifBlank { null },
        budgetTag = budgetTag,
        seasonTag = seasonTag,
        authorIds = authorIds
    )

/** Whether a trip the query returned passes the filters applied after it - trip length. */
fun ExploreFilters.keeps(trip: Trip): Boolean = tripLength == null || trip.tripLength() == tripLength

/** Identifies a result set: the same text and filters give the same key. */
fun ExploreFilters.resultsKey(query: String): String = "${query.trim()}|$tripLength|$budgetTag|$seasonTag|$followScope"

fun Trip.lengthInDays(): Int =
    (endDate.toEpochDays() - startDate.toEpochDays() + 1).toInt().coerceAtLeast(1)

/** Simple, fixed thresholds: 1-3 days short, 4-7 medium, 8+ long. */
fun Trip.tripLength(): TripLength = when {
    lengthInDays() <= 3 -> TripLength.SHORT
    lengthInDays() <= 7 -> TripLength.MEDIUM
    else -> TripLength.LONG
}
