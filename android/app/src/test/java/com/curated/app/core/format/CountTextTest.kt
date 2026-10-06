package com.curated.app.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class CountTextTest {

    @Test
    fun `one is singular`() {
        assertEquals("1 comment", countText(1, Noun.COMMENT))
        assertEquals("1 stop", countText(1, Noun.STOP))
        assertEquals("1 day", countText(1, Noun.DAY))
        assertEquals("1 photo", countText(1, Noun.PHOTO))
        assertEquals("1 like", countText(1, Noun.LIKE))
        assertEquals("1 person", countText(1, Noun.PERSON))
    }

    @Test
    fun `zero and more than one are plural`() {
        assertEquals("0 comments", countText(0, Noun.COMMENT))
        assertEquals("2 comments", countText(2, Noun.COMMENT))
        assertEquals("17 stops", countText(17, Noun.STOP))
        assertEquals("3 people", countText(3, Noun.PERSON))
    }
}
