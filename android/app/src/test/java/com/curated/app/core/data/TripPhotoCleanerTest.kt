package com.curated.app.core.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TripPhotoCleanerTest {

    /**
     * Storage in memory: files by full path, listed one folder level at a
     * time with limit/offset, like the real list endpoint.
     */
    private class FakeStore(vararg paths: String) : PhotoFolderStore {
        val files = sortedSetOf(*paths)
        val listCalls = mutableListOf<Triple<String, Int, Int>>()
        val deleteCalls = mutableListOf<List<String>>()
        var failList = false
        var failDelete = false
        /** Like RLS on someone else's file: the delete "succeeds" but the file stays. */
        var undeletable = emptySet<String>()

        override suspend fun list(prefix: String, limit: Int, offset: Int): List<StoredEntry> {
            listCalls += Triple(prefix, limit, offset)
            if (failList) throw IllegalStateException("list failed")
            val children = files.filter { it.startsWith("$prefix/") }
                .map { it.removePrefix("$prefix/") }
                .map { rest -> if ('/' in rest) StoredEntry(rest.substringBefore('/'), isFolder = true) else StoredEntry(rest, isFolder = false) }
                .distinct()
                .sortedBy { it.name }
            return children.drop(offset).take(limit)
        }

        override suspend fun delete(paths: List<String>) {
            deleteCalls += paths
            if (failDelete) throw IllegalStateException("delete failed")
            files.removeAll((paths - undeletable).toSet())
        }
    }

    private val trip = "trip-1"

    /** Runs the repository's ordering with this store and a recorded row delete. */
    private fun deleteTrip(store: FakeStore, cleaner: TripPhotoCleaner = TripPhotoCleaner(store)): MutableList<String> {
        val events = mutableListOf<String>()
        runBlocking {
            deletePhotosThenRow(
                deletePhotos = { cleaner.deleteTripFolder(trip); events += "photos" },
                deleteRow = { events += "row:$trip" }
            )
        }
        return events
    }

    @Test
    fun `deletes every photo in the trip folder, then the row`() {
        val store = FakeStore(
            "$trip/stop-a/0.jpg", "$trip/stop-a/1.jpg", "$trip/stop-b/0.jpg",
            "trip-2/stop-c/0.jpg", "trip-10/stop-d/0.jpg"
        )
        val events = deleteTrip(store)
        assertEquals(listOf("photos", "row:$trip"), events)
        // Other trips, including one whose id starts the same way, are untouched.
        assertEquals(sortedSetOf("trip-10/stop-d/0.jpg", "trip-2/stop-c/0.jpg"), store.files)
        assertEquals(listOf(listOf("$trip/stop-a/0.jpg", "$trip/stop-a/1.jpg", "$trip/stop-b/0.jpg")), store.deleteCalls)
    }

    @Test
    fun `a Storage delete failure keeps the row and reaches the caller`() {
        val store = FakeStore("$trip/stop-a/0.jpg").apply { failDelete = true }
        val events = mutableListOf<String>()
        try {
            runBlocking {
                deletePhotosThenRow({ TripPhotoCleaner(store).deleteTripFolder(trip) }, { events += "row" })
            }
            fail("expected the Storage failure to be thrown")
        } catch (e: IllegalStateException) {
            assertEquals("delete failed", e.message)
        }
        assertTrue("row must not be deleted", events.isEmpty())
        assertEquals(sortedSetOf("$trip/stop-a/0.jpg"), store.files)
    }

    @Test
    fun `a Storage list failure deletes nothing and keeps the row`() {
        val store = FakeStore("$trip/stop-a/0.jpg").apply { failList = true }
        val events = mutableListOf<String>()
        try {
            runBlocking {
                deletePhotosThenRow({ TripPhotoCleaner(store).deleteTripFolder(trip) }, { events += "row" })
            }
            fail("expected the Storage failure to be thrown")
        } catch (e: IllegalStateException) {
            assertEquals("list failed", e.message)
        }
        assertTrue(events.isEmpty())
        assertTrue(store.deleteCalls.isEmpty())
    }

    @Test
    fun `photos still there after the delete count as a failure`() {
        val store = FakeStore("$trip/stop-a/0.jpg", "$trip/stop-a/1.jpg").apply { undeletable = setOf("$trip/stop-a/1.jpg") }
        val events = mutableListOf<String>()
        try {
            runBlocking {
                deletePhotosThenRow({ TripPhotoCleaner(store).deleteTripFolder(trip) }, { events += "row" })
            }
            fail("expected the leftover photo to fail the delete")
        } catch (e: IllegalStateException) {
            assertEquals("1 photo(s) are still in Storage under $trip/", e.message)
        }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `pages through big folders and deletes in batches`() {
        // 120 stops (more than a page of folders), one of them with 250 photos.
        val paths = (0 until 120).map { "$trip/stop-${"%03d".format(it)}/0.jpg" } +
            (1 until 250).map { "$trip/stop-000/$it.jpg" }
        val store = FakeStore(*paths.toTypedArray())
        val cleaner = TripPhotoCleaner(store, pageSize = 100, deleteBatch = 100)

        val deleted = runBlocking { cleaner.deleteTripFolder(trip) }

        assertEquals(369, deleted)
        assertTrue(store.files.isEmpty())
        // The trip folder was read in two pages, the big stop folder in three.
        assertEquals(listOf(0, 100), store.listCalls.filter { it.first == trip }.map { it.third }.take(2))
        assertEquals(listOf(0, 100, 200), store.listCalls.filter { it.first == "$trip/stop-000" }.map { it.third })
        assertEquals(listOf(100, 100, 100, 69), store.deleteCalls.map { it.size })
    }

    @Test
    fun `a trip with no photos deletes nothing in Storage and still deletes the row`() {
        val store = FakeStore("trip-2/stop-c/0.jpg")
        val events = deleteTrip(store)
        assertEquals(listOf("photos", "row:$trip"), events)
        assertTrue(store.deleteCalls.isEmpty())
        assertEquals(sortedSetOf("trip-2/stop-c/0.jpg"), store.files)
    }

    @Test
    fun `refuses an id that would list more than one trip folder`() {
        val store = FakeStore("trip-2/stop-c/0.jpg")
        for (bad in listOf("", "  ", "trip-2/stop-c")) {
            try {
                runBlocking { TripPhotoCleaner(store).deleteTripFolder(bad) }
                fail("expected '$bad' to be refused")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertTrue(store.listCalls.isEmpty())
        assertTrue(store.deleteCalls.isEmpty())
    }

    // --- One stop: deleteStop uses the same order and the same cleaner. ---

    /** Runs deleteStop's ordering for stop-a of [trip], recording into [events]. */
    private fun deleteStop(store: FakeStore, events: MutableList<String> = mutableListOf()): MutableList<String> {
        runBlocking {
            deletePhotosThenRow(
                deletePhotos = { TripPhotoCleaner(store).deleteStopFolder(trip, "stop-a"); events += "photos" },
                deleteRow = { events += "row:stop-a" }
            )
        }
        return events
    }

    @Test
    fun `deleting a stop deletes only that stop's photos, then its row`() {
        val store = FakeStore(
            "$trip/stop-a/0.jpg", "$trip/stop-a/1.jpg",
            "$trip/stop-ab/0.jpg", "$trip/stop-b/0.jpg", "trip-2/stop-a/0.jpg"
        )
        val events = deleteStop(store)
        assertEquals(listOf("photos", "row:stop-a"), events)
        // Its sibling stops, one whose id starts the same way, and another trip's stop-a stay.
        assertEquals(sortedSetOf("$trip/stop-ab/0.jpg", "$trip/stop-b/0.jpg", "trip-2/stop-a/0.jpg"), store.files)
        assertEquals(listOf(listOf("$trip/stop-a/0.jpg", "$trip/stop-a/1.jpg")), store.deleteCalls)
    }

    @Test
    fun `a Storage failure deleting a stop's photos keeps the stop`() {
        val store = FakeStore("$trip/stop-a/0.jpg", "$trip/stop-b/0.jpg").apply { failDelete = true }
        val events = mutableListOf<String>()
        try {
            deleteStop(store, events)
            fail("expected the Storage failure to be thrown")
        } catch (e: IllegalStateException) {
            assertEquals("delete failed", e.message)
        }
        assertTrue("the stop's row must not be deleted", events.isEmpty())
        assertEquals(sortedSetOf("$trip/stop-a/0.jpg", "$trip/stop-b/0.jpg"), store.files)

        // And a photo Storage keeps despite the delete stops it the same way.
        val stubborn = FakeStore("$trip/stop-a/0.jpg").apply { undeletable = setOf("$trip/stop-a/0.jpg") }
        val stubbornEvents = mutableListOf<String>()
        try {
            deleteStop(stubborn, stubbornEvents)
            fail("expected the leftover photo to fail the delete")
        } catch (e: IllegalStateException) {
            assertEquals("1 photo(s) are still in Storage under $trip/stop-a/", e.message)
        }
        assertTrue("the stop's row must not be deleted", stubbornEvents.isEmpty())
    }

    @Test
    fun `a stop with no photos deletes nothing in Storage and still deletes its row`() {
        val store = FakeStore("$trip/stop-b/0.jpg")
        assertEquals(listOf("photos", "row:stop-a"), deleteStop(store))
        assertTrue(store.deleteCalls.isEmpty())
        assertEquals(sortedSetOf("$trip/stop-b/0.jpg"), store.files)
    }

    @Test
    fun `refuses a stop id that would list more than one stop folder`() {
        val store = FakeStore("$trip/stop-a/0.jpg")
        for ((tripId, stopId) in listOf(trip to "", trip to " ", trip to "stop-a/0.jpg", "" to "stop-a")) {
            try {
                runBlocking { TripPhotoCleaner(store).deleteStopFolder(tripId, stopId) }
                fail("expected '$tripId' / '$stopId' to be refused")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertTrue(store.listCalls.isEmpty())
        assertEquals(sortedSetOf("$trip/stop-a/0.jpg"), store.files)
    }
}
