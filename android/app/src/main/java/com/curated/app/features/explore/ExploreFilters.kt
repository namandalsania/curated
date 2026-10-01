package com.curated.app.features.explore

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

fun Trip.lengthInDays(): Int =
    (endDate.toEpochDays() - startDate.toEpochDays() + 1).toInt().coerceAtLeast(1)

/** Simple, fixed thresholds: 1-3 days short, 4-7 medium, 8+ long. */
fun Trip.tripLength(): TripLength = when {
    lengthInDays() <= 3 -> TripLength.SHORT
    lengthInDays() <= 7 -> TripLength.MEDIUM
    else -> TripLength.LONG
}
