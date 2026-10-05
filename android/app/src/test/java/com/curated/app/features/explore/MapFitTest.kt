package com.curated.app.features.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapFitTest {

    @Test
    fun `no pins keeps the default view`() {
        assertNull(MapFit.of(emptyList()))
    }

    @Test
    fun `one pin is a point`() {
        assertEquals(MapFit.Point(38.7, -9.1), MapFit.of(listOf(38.7 to -9.1)))
    }

    @Test
    fun `pins in the same spot are a point, not a zero-size box`() {
        assertEquals(MapFit.Point(38.7, -9.1), MapFit.of(listOf(38.7 to -9.1, 38.7 to -9.1, 38.70001 to -9.10001)))
    }

    @Test
    fun `spread pins are the box around them`() {
        // Lisbon, Paris, Istanbul: about 38 degrees across, so all of them fit.
        assertEquals(
            MapFit.Box(south = 38.7, west = -9.1, north = 48.9, east = 28.9),
            MapFit.of(listOf(38.7 to -9.1, 48.9 to 2.3, 41.0 to 28.9))
        )
    }

    @Test
    fun `pins too far apart to fit keep the largest group that does`() {
        // Mexico City and Tokyo are each too far from the rest; Lisbon, Paris and Rome fit together.
        val mexico = 19.4 to -99.1
        val lisbon = 38.7 to -9.1
        val paris = 48.9 to 2.3
        val rome = 41.9 to 12.5
        val tokyo = 35.7 to 139.7
        assertEquals(
            MapFit.Box(south = 38.7, west = -9.1, north = 48.9, east = 12.5),
            MapFit.of(listOf(tokyo, lisbon, mexico, paris, rome))
        )
    }
}
