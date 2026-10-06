package com.curated.app.features.explore

import org.junit.Assert.assertEquals
import org.junit.Test

class TripLoadErrorTest {

    @Test
    fun `a trip that no longer comes back reads as unavailable, not as an empty list`() {
        assertEquals("This trip isn't available.", tripLoadError(NoSuchElementException("List is empty.")))
    }

    @Test
    fun `any other failure reads as a connection problem`() {
        assertEquals(
            "Couldn't load this trip. Check your connection and try again.",
            tripLoadError(IllegalStateException("timeout"))
        )
    }
}
