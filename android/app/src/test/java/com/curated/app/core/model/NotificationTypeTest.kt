package com.curated.app.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTypeTest {

    /** notifications_type_check in 20261008_notifications_from_triggers.sql. */
    private val databaseTypes = setOf("follow", "new_trip", "new_day", "like", "stop_comment", "trip_share", "plan_invite")

    @Test
    fun `the app knows exactly the types the database creates`() {
        assertEquals(databaseTypes, NotificationType.entries.map { it.dbValue }.toSet())
    }

    @Test
    fun `each type decodes from its database value`() {
        NotificationType.entries.forEach {
            assertEquals(it, Json.decodeFromString(NotificationType.serializer(), "\"${it.dbValue}\""))
        }
    }
}
