package com.curated.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayCoverPathTest {

    private fun stop(order: Int, vararg photos: Pair<String, Int>) =
        DayStopRow(dayId = "d", name = "s$order", orderInDay = order, photos = photos.map { DayStopPhotoRow(it.first, it.second) })

    @Test
    fun `the first place's first photo, by photo order`() {
        val stops = listOf(stop(0, "b.jpg" to 1, "a.jpg" to 0), stop(1, "c.jpg" to 0))
        assertEquals("a.jpg", dayCoverPath(stops))
    }

    @Test
    fun `skips places without photos`() {
        assertEquals("c.jpg", dayCoverPath(listOf(stop(0), stop(1, "c.jpg" to 0))))
    }

    @Test
    fun `nothing when the day has no photos`() {
        assertNull(dayCoverPath(listOf(stop(0))))
        assertNull(dayCoverPath(emptyList()))
    }
}
