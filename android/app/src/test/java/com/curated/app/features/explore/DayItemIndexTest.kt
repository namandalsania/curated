package com.curated.app.features.explore

import com.curated.app.core.data.StopWithPhotos
import com.curated.app.core.data.TripDaySection
import com.curated.app.core.model.Stop
import com.curated.app.core.model.StopCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayItemIndexTest {

    private fun day(index: Int, stops: Int) = TripDaySection(
        dayIndex = index, date = null, stops = List(stops) { stop("d$index-s$it") }
    )

    private fun stop(id: String) = StopWithPhotos(
        Stop(id = id, tripId = "t", name = id, category = StopCategory.SIGHT, latitude = 0.0, longitude = 0.0, orderInDay = 0,
            createdAt = kotlin.time.Instant.parse("2026-01-01T00:00:00Z")),
        photoUrls = emptyList()
    )

    @Test
    fun `a day's header comes after the trip header and every earlier day's header and stops`() {
        val days = listOf(day(1, 3), day(2, 2), day(3, 4))
        assertEquals(1, dayItemIndex(days, 1))
        assertEquals(5, dayItemIndex(days, 2)) // 1 + (1 + 3)
        assertEquals(8, dayItemIndex(days, 3)) // 5 + (1 + 2)
    }

    @Test
    fun `a day that isn't there gives nothing to scroll to`() {
        assertNull(dayItemIndex(listOf(day(1, 2)), 4))
        assertNull(dayItemIndex(emptyList(), 1))
    }
}
