package com.curated.app.core.data

import android.content.Context
import android.net.Uri
import com.curated.app.core.photo.PhotoSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.BucketApi
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BUCKET = "stop-photos"

class PhotoStorageRepository(
    private val client: SupabaseClient,
    private val context: Context
) {
    private val bucket get() = client.storage.from(BUCKET)

    suspend fun upload(tripId: String, stopId: String, index: Int, uri: Uri): String {
        // Upright, resized, and stripped of all metadata (GPS included) - see PhotoSanitizer.
        val bytes = withContext(Dispatchers.Default) {
            PhotoSanitizer.sanitize(
                open = { context.contentResolver.openInputStream(uri) ?: error("Unable to read stop photo at $uri") },
                maxEdge = PhotoSanitizer.PHOTO_MAX_EDGE
            )
        }
        val path = "$tripId/$stopId/$index.jpg"
        bucket.upload(path, bytes)
        return path
    }

    /**
     * Deletes every photo under the trip's folder (`<tripId>/`). Throws if
     * listing or deleting fails, or if anything is still there afterwards, so
     * the caller can keep the trip row and let the user retry.
     */
    suspend fun deleteTripPhotos(tripId: String): Int = TripPhotoCleaner(SupabasePhotoFolderStore(bucket)).deleteTripFolder(tripId)

    fun publicUrl(storagePath: String): String = resolvePhotoUrl(client, storagePath)
}

/** One entry in a Storage folder listing: a file, or a folder to look inside. */
data class StoredEntry(val name: String, val isFolder: Boolean)

/** The two Storage calls deleting a trip's photos needs, so the logic can be tested without a server. */
interface PhotoFolderStore {
    /** One page of [prefix]'s direct children. */
    suspend fun list(prefix: String, limit: Int, offset: Int): List<StoredEntry>

    suspend fun delete(paths: List<String>)
}

private class SupabasePhotoFolderStore(private val bucket: BucketApi) : PhotoFolderStore {
    override suspend fun list(prefix: String, limit: Int, offset: Int): List<StoredEntry> =
        bucket.list(prefix) {
            this.limit = limit
            this.offset = offset
        }.map { StoredEntry(it.name, isFolder = it.id == null) } // Storage lists folders with no id

    override suspend fun delete(paths: List<String>) = bucket.delete(paths)
}

/**
 * Empties a trip's photo folder. Photos live at `<tripId>/<stopId>/<n>.jpg`,
 * so it walks every subfolder, page by page, collects every path first (deleting
 * while paging would shift the offsets), deletes in batches, then lists again.
 *
 * Storage only lists and deletes the caller's own files; a file it can't see
 * can't be deleted from the app. The account-deletion function, which runs with
 * the service role, still finds a trip's files by its folder.
 */
class TripPhotoCleaner(
    private val store: PhotoFolderStore,
    private val pageSize: Int = 100,
    private val deleteBatch: Int = 100
) {
    suspend fun deleteTripFolder(tripId: String): Int {
        // A blank or nested id would list far more than this trip's folder.
        require(tripId.isNotBlank() && '/' !in tripId) { "Not a trip id: '$tripId'" }
        val paths = filesUnder(tripId)
        paths.chunked(deleteBatch).forEach { store.delete(it) }
        val left = filesUnder(tripId)
        check(left.isEmpty()) { "${left.size} photo(s) of trip $tripId are still in Storage" }
        return paths.size
    }

    private suspend fun filesUnder(prefix: String): List<String> {
        val files = mutableListOf<String>()
        var offset = 0
        while (true) {
            val page = store.list(prefix, pageSize, offset)
            for (entry in page) {
                val path = "$prefix/${entry.name}"
                if (entry.isFolder) files += filesUnder(path) else files += path
            }
            if (page.size < pageSize) return files
            offset += page.size
        }
    }
}

/**
 * `stop_photos.storage_path` holds a bucket-relative path for photos the app
 * uploaded, but seeded/demo rows store an absolute https URL. Both resolve to
 * something Coil can load.
 */
fun resolvePhotoUrl(client: SupabaseClient, storagePath: String): String =
    if (storagePath.startsWith("http://") || storagePath.startsWith("https://")) {
        storagePath
    } else {
        client.storage.from(BUCKET).publicUrl(storagePath)
    }
