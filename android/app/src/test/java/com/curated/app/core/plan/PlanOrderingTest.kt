package com.curated.app.core.plan

import com.curated.app.core.model.PlanItem
import com.curated.app.core.model.StopCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanOrderingTest {

    private fun item(id: String, day: Int, position: Int, stopId: String? = "stop-$id") = PlanItem(
        id = id, planId = "plan", savedPlaceId = "saved-$id", dayNumber = day, position = position,
        stopId = stopId, name = "Place $id", category = StopCategory.SIGHT, latitude = 0.0, longitude = 0.0
    )

    /** Each day's ids in order, e.g. [[a, b], [c]]. */
    private fun List<PlanItem>.layout(dayCount: Int) = PlanOrdering.byDay(this, dayCount).map { day -> day.map { it.id } }

    /** Positions must be 0..n-1 within every day, whatever happened. */
    private fun assertContiguous(items: List<PlanItem>) {
        items.groupBy { it.dayNumber }.forEach { (day, inDay) ->
            assertEquals("day $day positions", inDay.indices.toList(), inDay.map { it.position }.sorted())
        }
    }

    private val sample = listOf(item("a", 1, 0), item("b", 1, 1), item("c", 1, 2), item("d", 2, 0))

    @Test
    fun `moving down swaps with the next item in the same day`() {
        val moved = PlanOrdering.moveWithinDay(sample, "a", +1)
        assertEquals(listOf(listOf("b", "a", "c"), listOf("d")), moved.layout(2))
        assertContiguous(moved)
    }

    @Test
    fun `moving past either end of a day does nothing`() {
        assertEquals(sample, PlanOrdering.moveWithinDay(sample, "a", -1))
        assertEquals(sample, PlanOrdering.moveWithinDay(sample, "c", +1))
    }

    @Test
    fun `moving to another day appends it there and closes the gap it left`() {
        val moved = PlanOrdering.moveToDay(sample, "b", 2)
        assertEquals(listOf(listOf("a", "c"), listOf("d", "b")), moved.layout(2))
        assertContiguous(moved)
    }

    @Test
    fun `removing an item renumbers the rest of its day`() {
        val removed = PlanOrdering.remove(sample, "a")
        assertEquals(listOf(listOf("b", "c"), listOf("d")), removed.layout(2))
        assertContiguous(removed)
    }

    @Test
    fun `appending skips places already in the plan`() {
        val newItems = listOf(
            item("x", 2, 0, stopId = "stop-a"), // same stop as "a", already on day 1
            item("y", 2, 0, stopId = "stop-new")
        )
        val appended = PlanOrdering.append(sample, newItems, day = 2)
        assertEquals(listOf(listOf("a", "b", "c"), listOf("d", "y")), appended.layout(2))
        assertContiguous(appended)
    }

    @Test
    fun `a collaborator's copy of the same stop counts as already in the plan`() {
        // Maya saved stop-a (saved-a); Leo saved the same stop separately.
        val leosCopy = item("leo", 1, 0, stopId = "stop-a").copy(savedPlaceId = "leos-saved-a")
        assertEquals(sample, PlanOrdering.append(sample, listOf(leosCopy), day = 2))
    }

    @Test
    fun `removing a day drops its items and shifts later days up`() {
        val threeDays = sample + item("e", 3, 0)
        val result = PlanOrdering.removeDay(threeDays, 2)
        assertEquals(listOf(listOf("a", "b", "c"), listOf("e")), result.layout(2))
    }

    @Test
    fun `changes separate new items, moved items and deletions`() {
        val moved = PlanOrdering.moveToDay(sample, "c", 2)
        val changes = PlanOrdering.changedSince(sample, moved)
        // c moved day; a and b kept their positions; d kept its position.
        assertEquals(listOf("c"), changes.moves.map { it.id })
        assertTrue(changes.inserts.isEmpty())
        assertTrue(changes.deletedIds.isEmpty())

        val removed = PlanOrdering.remove(sample, "a")
        val removal = PlanOrdering.changedSince(sample, removed)
        assertEquals(listOf("a"), removal.deletedIds)
        assertEquals(setOf("b", "c"), removal.moves.map { it.id }.toSet()) // shifted up

        val added = PlanOrdering.append(sample, listOf(item("n", 1, 0, stopId = "stop-n")), day = 1)
        val addition = PlanOrdering.changedSince(sample, added)
        assertEquals(listOf("n"), addition.inserts.map { it.id })
        assertTrue(addition.moves.isEmpty()) // appended at the end, nothing else shifts
    }
}
