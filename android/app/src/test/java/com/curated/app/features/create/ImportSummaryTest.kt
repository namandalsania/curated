package com.curated.app.features.create

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportSummaryTest {

    @Test
    fun `every part is its own sentence, with one period each`() {
        assertEquals(
            "Added 2 stops. 1 photo had no location, so add that place yourself. " +
                "1 photo was taken on another day, so it wasn't added to Day 1.",
            importSummaryText(stopsAdded = 2, withoutLocation = 1, otherDays = 1, dayIndex = 1)
        )
    }

    @Test
    fun `stops alone is one sentence`() {
        assertEquals("Added 1 stop.", importSummaryText(stopsAdded = 1, withoutLocation = 0, otherDays = 0))
    }

    @Test
    fun `plurals for several photos`() {
        assertEquals(
            "Added 3 stops. 2 photos had no location, so add those places yourself. " +
                "4 photos were taken on other days, so they weren't added to Day 2.",
            importSummaryText(stopsAdded = 3, withoutLocation = 2, otherDays = 4, dayIndex = 2)
        )
    }

    @Test
    fun `other days only count for a single day's import`() {
        assertEquals("Added 0 stops.", importSummaryText(stopsAdded = 0, withoutLocation = 0, otherDays = 3))
    }
}
