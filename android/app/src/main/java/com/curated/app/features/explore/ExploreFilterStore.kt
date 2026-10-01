package com.curated.app.features.explore

import android.content.Context

private const val PREFS_NAME = "explore_filters"
private const val KEY_LENGTH = "trip_length"
private const val KEY_BUDGET = "budget_tag"
private const val KEY_SEASON = "season_tag"
private const val KEY_SCOPE = "follow_scope"

/** Remembers the last-used Explore filter selection on-device — never sent to Supabase. */
class ExploreFilterStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): ExploreFilters = ExploreFilters(
        tripLength = prefs.getString(KEY_LENGTH, null)?.toEnumOrNull(),
        budgetTag = prefs.getString(KEY_BUDGET, null)?.toEnumOrNull(),
        seasonTag = prefs.getString(KEY_SEASON, null)?.toEnumOrNull(),
        followScope = prefs.getString(KEY_SCOPE, null)?.toEnumOrNull() ?: FollowScope.EVERYONE
    )

    fun save(filters: ExploreFilters) {
        prefs.edit()
            .putString(KEY_LENGTH, filters.tripLength?.name)
            .putString(KEY_BUDGET, filters.budgetTag?.name)
            .putString(KEY_SEASON, filters.seasonTag?.name)
            .putString(KEY_SCOPE, filters.followScope.name)
            .apply()
    }
}

private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
    runCatching { enumValueOf<T>(this) }.getOrNull()
