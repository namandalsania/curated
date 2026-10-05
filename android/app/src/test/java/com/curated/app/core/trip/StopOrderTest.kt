package com.curated.app.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StopOrderTest {

    private fun stop(id: String, day: Int?, position: Int, trip: String = "t1") =
        StopPlacement(stopId = id, tripId = trip, dayIndex = day, orderInDay = position, latitude = 0.0, longitude = 0.0)

    private val published = { _: String -> false }

    @Test
    fun `stops out of order across days - the earliest day wins over the lowest position`() {
        // Listed day 3 first, and day 2's stop has position 0 while day 1's has 2.
        val stops = listOf(stop("c", day = 3, position = 0), stop("b", day = 2, position = 0), stop("a", day = 1, position = 2))
        assertEquals("a", firstStopByTrip(stops, published).getValue("t1").stopId)
    }

    @Test
    fun `within the first day, the lowest position wins`() {
        val stops = listOf(stop("late", day = 1, position = 4), stop("early", day = 1, position = 1))
        assertEquals("early", firstStopByTrip(stops, published).getValue("t1").stopId)
    }

    @Test
    fun `a stop not on a day is ignored on a published trip`() {
        val stops = listOf(stop("dayless", day = null, position = 0), stop("onday", day = 2, position = 3))
        assertEquals("onday", firstStopByTrip(stops, published).getValue("t1").stopId)
    }

    @Test
    fun `a published trip whose only stops aren't on a day has no first stop`() {
        assertFalse(firstStopByTrip(listOf(stop("dayless", day = null, position = 0)), published).containsKey("t1"))
    }

    @Test
    fun `where allowed, a stop not on a day still comes after every day`() {
        val stops = listOf(stop("dayless", day = null, position = 0), stop("onday", day = 9, position = 9))
        assertEquals("onday", firstStopByTrip(stops) { true }.getValue("t1").stopId)
        assertEquals("dayless", firstStopByTrip(listOf(stop("dayless", day = null, position = 0))) { true }.getValue("t1").stopId)
    }

    @Test
    fun `two trips each get their own first stop`() {
        val stops = listOf(
            stop("t2-day2", day = 2, position = 0, trip = "t2"),
            stop("t1-day1", day = 1, position = 5, trip = "t1"),
            stop("t2-day1", day = 1, position = 1, trip = "t2"),
            stop("t1-day3", day = 3, position = 0, trip = "t1")
        )
        val first = firstStopByTrip(stops, published)
        assertEquals("t1-day1", first.getValue("t1").stopId)
        assertEquals("t2-day1", first.getValue("t2").stopId)
    }

    @Test
    fun `position ties across days go to the earliest day, whatever the listing order`() {
        // Every day starts at position 0 - the case that made pins land on a random day.
        val stops = listOf(stop("d3", day = 3, position = 0), stop("d1", day = 1, position = 0), stop("d2", day = 2, position = 0))
        assertEquals("d1", firstStopByTrip(stops, published).getValue("t1").stopId)
        assertEquals("d1", firstStopByTrip(stops.reversed(), published).getValue("t1").stopId)
    }

    @Test
    fun `a tie on day and position goes to the lower stop id, every time`() {
        val stops = listOf(stop("b", day = 1, position = 0), stop("a", day = 1, position = 0))
        assertEquals("a", firstStopByTrip(stops, published).getValue("t1").stopId)
        assertEquals("a", firstStopByTrip(stops.reversed(), published).getValue("t1").stopId)
    }
}
