package com.curated.app.core.data

import android.content.Context
import android.net.Uri
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage

private const val BUCKET = "stop-photos"

class PhotoStorageRepository(
    private val client: SupabaseClient,
    private val context: Context
) {
    private val bucket get() = client.storage.from(BUCKET)

    suspend fun upload(tripId: String, stopId: String, index: Int, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read photo at $uri")
        val path = "$tripId/$stopId/$index.jpg"
        bucket.upload(path, bytes)
        return path
    }

    fun publicUrl(storagePath: String): String = resolvePhotoUrl(client, storagePath)
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
