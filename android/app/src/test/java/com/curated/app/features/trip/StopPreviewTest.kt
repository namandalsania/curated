package com.curated.app.features.trip

import com.curated.app.core.format.Noun
import com.curated.app.core.format.countText
import org.junit.Assert.assertEquals
import org.junit.Test

class StopPreviewTest {

    @Test
    fun `two stops in a row at the same place show once and both are counted`() {
        // Bangkok Getaway, Day 3: Thip Samai at 12:31 and again at 3:53.
        val names = listOf("Chatuchak Weekend Market", "Thip Samai Pad Thai", "Thip Samai Pad Thai", "Wat Pho")
        val preview = stopPreview(names, names.size)
        assertEquals("Chatuchak Weekend Market → Thip Samai Pad Thai → Wat Pho", preview.names)
        assertEquals(0, preview.remaining)
        assertEquals("4 stops", countText(names.size, Noun.STOP))
    }

    @Test
    fun `the collapsed pair still counts as two when there's a remainder`() {
        val preview = stopPreview(listOf("A", "B", "B", "C", "D"), 5)
        assertEquals("A → B → C", preview.names)
        assertEquals(1, preview.remaining)
    }

    @Test
    fun `the same place apart is shown both times`() {
        assertEquals("A → B → A", stopPreview(listOf("A", "B", "A"), 3).names)
    }
}
