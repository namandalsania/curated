package com.curated.app.navigation

import com.curated.app.features.activity.ActivityTab
import com.curated.app.features.profile.FollowListKind

object AppRoutes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val TRIP_DETAIL_PATTERN = "trip_detail/{tripId}?day={day}"
    const val PROFILE_PATTERN = "profile/{userId}"
    const val FOLLOW_LIST_PATTERN = "follow_list/{userId}/{kind}"
    const val EDIT_PROFILE = "edit_profile"
    const val ACTIVITY_PATTERN = "activity/{tab}"
    const val SAVED_PLACES = "saved_places"
    const val PLANS = "plans"
    const val BLOCKED_ACCOUNTS = "blocked_accounts"
    const val SETTINGS = "settings"
    const val DELETE_ACCOUNT = "delete_account"
    const val PLAN_EDITOR_PATTERN = "plan/{planId}"

    /** savedStateHandle key Edit profile sets on the profile entry to make it refresh. */
    const val PROFILE_RELOAD_KEY = "profile_reload"

    fun tripDetail(tripId: String) = "trip_detail/$tripId"
    fun tripDetailAtDay(tripId: String, dayIndex: Int) = "trip_detail/$tripId?day=$dayIndex"
    fun activity(tab: ActivityTab) = "activity/${tab.name}"
    fun profile(userId: String) = "profile/$userId"
    fun followList(userId: String, kind: FollowListKind) = "follow_list/$userId/${kind.name}"
    fun planEditor(planId: String) = "plan/$planId"
}
