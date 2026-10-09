package com.curated.app.core.data

import com.curated.app.core.model.NotificationType
import com.curated.app.core.model.Trip
import com.curated.app.features.activity.ActivityTarget
import com.curated.app.features.activity.buildActivity
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

class NotificationRecordTest {

    /** What Realtime sends for an insert: every column, plus ones this build may not know. */
    private fun record(type: String, extra: String = "") = Json.parseToJsonElement(
        """
        {
          "id": "n1", "recipient_id": "maya", "actor_id": "sofia", "type": "$type",
          "trip_id": "bangkok", "plan_id": null, "stop_id": null, "day_id": "day-3",
          "created_at": "2026-10-08T15:10:42.123456+00:00", "read_at": null$extra
        }
        """.trimIndent()
    ).jsonObject

    @Test
    fun `a realtime new_day insert keeps its day`() {
        val n = decodeNotificationRecord(SupabaseProvider.serializer, record("new_day", """, "added_later": 1"""))!!
        assertEquals(NotificationType.NEW_DAY, n.type)
        assertEquals("bangkok", n.tripId)
        assertEquals("day-3", n.dayId)
    }

    @Test
    fun `and tapping it opens the trip at that day`() {
        // The record carries ids only; the trip is attached the way the fetched path attaches it.
        val trip = Trip(
            id = "bangkok", authorId = "sofia", title = "Bangkok Getaway", destination = "Bangkok",
            startDate = LocalDate(2026, 10, 6), endDate = LocalDate(2026, 10, 9),
            createdAt = Instant.parse("2026-10-06T00:00:00Z"), updatedAt = Instant.parse("2026-10-06T00:00:00Z")
        )
        val n = decodeNotificationRecord(SupabaseProvider.serializer, record("new_day"))!!.copy(trip = trip)
        val item = buildActivity(listOf(n), emptySet(), Instant.parse("2026-10-08T16:00:00Z"), TimeZone.UTC)
            .single().second.single()
        assertEquals(ActivityTarget.Trip("bangkok", "day-3"), item.target)
    }

    @Test
    fun `a type this build doesn't know is dropped, not a crash`() {
        assertNull(decodeNotificationRecord(SupabaseProvider.serializer, record("trip_reminder")))
    }

    @Test
    fun `a record missing its columns decodes to nothing`() {
        assertNull(decodeNotificationRecord(SupabaseProvider.serializer, JsonObject(emptyMap())))
    }
}
