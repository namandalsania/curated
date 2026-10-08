package com.curated.app.features.activity

import com.curated.app.core.model.Notification
import com.curated.app.core.model.NotificationType
import com.curated.app.core.model.Plan
import com.curated.app.core.model.Trip
import com.curated.app.core.model.User
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Where a row sits in the Activity list. */
enum class ActivitySection(val title: String) {
    TODAY("Today"),
    THIS_WEEK("This week"),
    EARLIER("Earlier")
}

/** What tapping a row opens. */
sealed interface ActivityTarget {
    data class Profile(val userId: String) : ActivityTarget
    data class Trip(val tripId: String, val dayId: String? = null) : ActivityTarget
    data object Plans : ActivityTarget
    /** A trip the viewer can't open - private, deleted, blocked, or a live trip with nothing posted. */
    data object Unavailable : ActivityTarget
}

/**
 * One row: a single notification, or several of the same kind folded
 * together ("Diego and 2 others started following you").
 */
data class ActivityItem(
    val key: String,
    val type: NotificationType,
    /** Newest first, one entry per person. */
    val actors: List<User>,
    val actorIds: List<String>,
    val trip: Trip?,
    val tripId: String?,
    val plan: Plan?,
    val dayId: String?,
    val latestAt: Instant,
    val isUnread: Boolean
) {
    val firstActor: User? get() = actors.firstOrNull()

    /** "Diego Martins", "Diego Martins and Sofia Almeida", "Diego Martins and 2 others". */
    val actorsLabel: String
        get() {
            val first = nameOf(firstActor)
            val others = actorIds.size - 1
            return when {
                others <= 0 -> first
                others == 1 -> "$first and ${nameOf(actors.getOrNull(1))}"
                else -> "$first and $others others"
            }
        }

    /** What they did, after the names. */
    val action: String
        get() {
            val title = trip?.title
            return when (type) {
                NotificationType.FOLLOW -> "started following you"
                NotificationType.LIKE -> "liked ${title ?: "a trip"}"
                NotificationType.STOP_COMMENT -> "commented on a place in ${title ?: "a trip"}"
                NotificationType.TRIP_SHARE -> "sent you ${title ?: "a trip"}"
                NotificationType.NEW_TRIP -> "published ${title ?: "a trip"}"
                NotificationType.NEW_DAY -> "posted a new day of ${title ?: "a trip"}"
                NotificationType.PLAN_INVITE -> "invited you to plan ${plan?.title ?: "a trip"}"
            }
        }

    val target: ActivityTarget
        get() = when (type) {
            NotificationType.FOLLOW -> actorIds.firstOrNull()?.let { ActivityTarget.Profile(it) } ?: ActivityTarget.Unavailable
            NotificationType.PLAN_INVITE -> ActivityTarget.Plans
            NotificationType.NEW_DAY ->
                if (trip != null && tripId != null) ActivityTarget.Trip(tripId, dayId) else ActivityTarget.Unavailable
            else -> if (trip != null && tripId != null) ActivityTarget.Trip(tripId) else ActivityTarget.Unavailable
        }

    /** Trip rows show its cover on the right. */
    val thumbnailUrl: String? get() = trip?.coverPhotoUrl

    /** Only a single follower gets an inline Follow back, and only if you don't follow them yet. */
    fun canFollowBack(following: Set<String>): Boolean =
        type == NotificationType.FOLLOW && actorIds.size == 1 && actorIds.first() !in following
}

/**
 * Display name, or the handle when there isn't one - shown as the handle
 * really is, never re-cased.
 */
fun nameOf(user: User?): String =
    user?.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: user?.username?.let { "@$it" } ?: "Someone"

fun sectionOf(at: Instant, now: Instant, zone: TimeZone): ActivitySection {
    val today = now.toLocalDateTime(zone).date
    val day = at.toLocalDateTime(zone).date
    return when {
        day >= today -> ActivitySection.TODAY
        now - at < 7.days -> ActivitySection.THIS_WEEK
        else -> ActivitySection.EARLIER
    }
}

/**
 * Notifications as Activity rows, grouped by when they happened. Within a
 * section, follows fold into one row and likes fold per trip; everything else
 * is a row of its own. [unreadIds] is what was unread when the screen opened,
 * captured before marking read, so the tint survives the mark.
 */
fun buildActivity(
    notifications: List<Notification>,
    unreadIds: Set<String>,
    now: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault()
): List<Pair<ActivitySection, List<ActivityItem>>> =
    notifications
        .sortedByDescending { it.createdAt }
        .groupBy { sectionOf(it.createdAt, now, zone) }
        .toSortedMap()
        .map { (section, inSection) ->
            val rows = inSection
                .groupBy { foldKey(it) }
                .map { (key, group) -> toItem("${section.name}:$key", group, unreadIds) }
                .sortedByDescending { it.latestAt }
            section to rows
        }

private fun foldKey(n: Notification): String = when (n.type) {
    NotificationType.FOLLOW -> "follow"
    NotificationType.LIKE -> "like:${n.tripId}"
    else -> n.id
}

/** [group] is newest first. */
private fun toItem(key: String, group: List<Notification>, unreadIds: Set<String>): ActivityItem {
    val newest = group.first()
    val byActor = group.filter { it.actorId != null }.distinctBy { it.actorId }
    return ActivityItem(
        key = key,
        type = newest.type,
        actors = byActor.mapNotNull { it.actor },
        actorIds = byActor.mapNotNull { it.actorId },
        trip = newest.trip,
        tripId = newest.tripId,
        plan = newest.plan,
        dayId = newest.dayId,
        latestAt = newest.createdAt,
        isUnread = group.any { it.id in unreadIds }
    )
}
