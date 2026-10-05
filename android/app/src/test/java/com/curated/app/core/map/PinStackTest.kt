package com.curated.app.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinStackTest {

    @Test
    fun `identical points are one spot`() {
        assertTrue(allAtSameSpot(listOf(35.6586 to 139.7454, 35.6586 to 139.7454)))
    }

    @Test
    fun `points a few metres apart are still one spot`() {
        assertTrue(allAtSameSpot(listOf(35.65860 to 139.74540, 35.65865 to 139.74545)))
    }

    @Test
    fun `points a street apart are not`() {
        assertFalse(allAtSameSpot(listOf(35.6586 to 139.7454, 35.6596 to 139.7454)))
    }

    @Test
    fun `no points is not a spot`() {
        assertFalse(allAtSameSpot(emptyList()))
    }

    @Test
    fun `the stacked trip that ranks highest is chosen`() {
        assertEquals("b", firstRanked(setOf("c", "b"), listOf("a", "b", "c")))
    }

    @Test
    fun `nothing is chosen when none of the stack is in the list`() {
        assertNull(firstRanked(setOf("x"), listOf("a", "b")))
    }
}
