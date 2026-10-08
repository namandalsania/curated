package com.curated.app.features.activity

import com.curated.app.core.model.Notification
import com.curated.app.core.model.NotificationType
import com.curated.app.core.model.Trip
import com.curated.app.core.model.User
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ActivityItemsTest {

    private val zone = TimeZone.UTC
    private val now = Instant.parse("2026-10-08T15:00:00Z")
    private val epoch = Instant.parse("2026-01-01T00:00:00Z")

    private fun user(id: String, name: String = id.replaceFirstChar { it.uppercase() }) =
        User(id = id, username = id, displayName = name, createdAt = epoch)

    private val tokyo = Trip(
        id = "tokyo", authorId = "me", title = "Tokyo Escape", destination = "Tokyo",
        startDate = LocalDate(2026, 9, 1), endDate = LocalDate(2026, 9, 5),
        createdAt = epoch, updatedAt = epoch, coverPhotoUrl = "https://example.invalid/tokyo.jpg"
    )

    private var seq = 0
    private fun n(type: NotificationType, actor: String?, at: Instant, trip: Trip? = null, tripId: String? = trip?.id) =
        Notification(
            id = "n${seq++}", recipientId = "me", actorId = actor, type = type,
            tripId = tripId, createdAt = at, actor = actor?.let { user(it) }, trip = trip
        )

    @Test
    fun `sections are today, this week and earlier`() {
        assertEquals(ActivitySection.TODAY, sectionOf(now - 2.hours, now, zone))
        assertEquals(ActivitySection.THIS_WEEK, sectionOf(now - 1.days, now, zone))
        assertEquals(ActivitySection.THIS_WEEK, sectionOf(now - 6.days, now, zone))
        assertEquals(ActivitySection.EARLIER, sectionOf(now - 8.days, now, zone))
    }

    @Test
    fun `follows in a section fold into one row, newest first`() {
        val rows = buildActivity(
            listOf(
                n(NotificationType.FOLLOW, "kai", now - 3.hours),
                n(NotificationType.FOLLOW, "diego", now - 1.hours),
                n(NotificationType.FOLLOW, "sofia", now - 2.hours)
            ),
            unreadIds = emptySet(), now = now, zone = zone
        )
        val (section, items) = rows.single()
        assertEquals(ActivitySection.TODAY, section)
        val item = items.single()
        assertEquals("Diego and 2 others", item.actorsLabel)
        assertEquals("started following you", item.action)
        assertEquals(ActivityTarget.Profile("diego"), item.target)
    }

    @Test
    fun `likes fold per trip and name it`() {
        val items = buildActivity(
            listOf(
                n(NotificationType.LIKE, "sofia", now - 1.hours, tokyo),
                n(NotificationType.LIKE, "kai", now - 2.hours, tokyo),
                n(NotificationType.LIKE, "jo", now - 3.hours, tokyo),
                n(NotificationType.LIKE, "amara", now - 4.hours, tokyo)
            ),
            unreadIds = emptySet(), now = now, zone = zone
        ).single().second
        assertEquals("Sofia and 3 others liked Tokyo Escape", "${items.single().actorsLabel} ${items.single().action}")
        assertEquals("https://example.invalid/tokyo.jpg", items.single().thumbnailUrl)
    }

    @Test
    fun `two people read as a pair, and other types never fold`() {
        val items = buildActivity(
            listOf(
                n(NotificationType.FOLLOW, "kai", now - 1.hours),
                n(NotificationType.FOLLOW, "jo", now - 2.hours),
                n(NotificationType.STOP_COMMENT, "kai", now - 3.hours, tokyo),
                n(NotificationType.STOP_COMMENT, "kai", now - 4.hours, tokyo)
            ),
            unreadIds = emptySet(), now = now, zone = zone
        ).single().second
        assertEquals(3, items.size)
        assertEquals("Kai and Jo", items.first().actorsLabel)
    }

    @Test
    fun `a row is unread if any of what it folds was unread when the screen opened`() {
        val old = n(NotificationType.FOLLOW, "kai", now - 2.hours)
        val fresh = n(NotificationType.FOLLOW, "jo", now - 1.hours)
        val read = buildActivity(listOf(old, fresh), unreadIds = emptySet(), now = now, zone = zone)
        val unread = buildActivity(listOf(old, fresh), unreadIds = setOf(old.id), now = now, zone = zone)
        assertFalse(read.single().second.single().isUnread)
        assertTrue(unread.single().second.single().isUnread)
    }

    @Test
    fun `the same kind in different sections stays apart`() {
        val rows = buildActivity(
            listOf(n(NotificationType.FOLLOW, "kai", now - 1.hours), n(NotificationType.FOLLOW, "jo", now - 10.days)),
            unreadIds = emptySet(), now = now, zone = zone
        )
        assertEquals(listOf(ActivitySection.TODAY, ActivitySection.EARLIER), rows.map { it.first })
    }

    @Test
    fun `a trip that can't be opened is unavailable, and named neutrally`() {
        val item = buildActivity(
            listOf(n(NotificationType.TRIP_SHARE, "kai", now - 1.hours, trip = null, tripId = "gone")),
            unreadIds = emptySet(), now = now, zone = zone
        ).single().second.single()
        assertEquals(ActivityTarget.Unavailable, item.target)
        assertEquals("sent you a trip", item.action)
    }

    @Test
    fun `a new day opens its trip at that day`() {
        val day = n(NotificationType.NEW_DAY, "sofia", now - 1.hours, tokyo).copy(dayId = "d3")
        val item = buildActivity(listOf(day), emptySet(), now, zone).single().second.single()
        assertEquals(ActivityTarget.Trip("tokyo", "d3"), item.target)
        assertEquals("posted a new day of Tokyo Escape", item.action)
    }

    @Test
    fun `follow back only for a single follower you don't follow yet`() {
        val one = buildActivity(listOf(n(NotificationType.FOLLOW, "kai", now)), emptySet(), now, zone).single().second.single()
        assertTrue(one.canFollowBack(following = emptySet()))
        assertFalse(one.canFollowBack(following = setOf("kai")))
        val two = buildActivity(
            listOf(n(NotificationType.FOLLOW, "kai", now), n(NotificationType.FOLLOW, "jo", now - 1.hours)),
            emptySet(), now, zone
        ).single().second.single()
        assertFalse(two.canFollowBack(following = emptySet()))
    }

    @Test
    fun `names fall back to the handle as written`() {
        assertEquals("Diego Martins", nameOf(user("roadtrip_diego", "Diego Martins")))
        assertEquals("@roadtrip_diego", nameOf(user("roadtrip_diego", "  ")))
        assertEquals("@MayaChen", nameOf(user("MayaChen", "")))
        assertEquals("Someone", nameOf(null))
    }
}
