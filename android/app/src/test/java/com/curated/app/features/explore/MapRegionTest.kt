package com.curated.app.features.explore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapRegionTest {

    // Roughly Lisbon: 1 degree tall, 2 wide.
    private val lisbon = MapRegion(south = 38.0, west = -10.0, north = 39.0, east = -8.0)

    @Test
    fun `the same view hasn't moved`() {
        assertFalse(lisbon.differsFrom(lisbon))
    }

    @Test
    fun `a small nudge doesn't count`() {
        assertFalse(lisbon.copy(west = -9.9, east = -7.9).differsFrom(lisbon))
    }

    @Test
    fun `panning a fifth of the view or more does`() {
        assertTrue(lisbon.copy(west = -9.5, east = -7.5).differsFrom(lisbon))
        assertTrue(lisbon.copy(south = 38.3, north = 39.3).differsFrom(lisbon))
    }

    @Test
    fun `zooming in or out by about a third of a level does`() {
        assertTrue(MapRegion(38.25, -9.5, 38.75, -8.5).differsFrom(lisbon))
        assertTrue(MapRegion(37.5, -11.0, 39.5, -7.0).differsFrom(lisbon))
    }

    @Test
    fun `contains is inclusive of the edges`() {
        assertTrue(lisbon.contains(38.0, -10.0))
        assertTrue(lisbon.contains(38.7, -9.1))
        assertFalse(lisbon.contains(40.0, -9.1))
    }
}
