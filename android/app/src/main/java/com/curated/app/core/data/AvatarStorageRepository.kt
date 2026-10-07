package com.curated.app.core.data

import android.content.Context
import android.net.Uri
import com.curated.app.core.photo.PhotoSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BUCKET = "avatars"

class AvatarStorageRepository(
    private val client: SupabaseClient,
    private val context: Context
) {
    private val bucket get() = client.storage.from(BUCKET)

    suspend fun upload(userId: String, uri: Uri): String {
        // Upright, resized, and stripped of all metadata (GPS included) - see PhotoSanitizer.
        val bytes = withContext(Dispatchers.Default) {
            PhotoSanitizer.sanitize(
                open = { context.contentResolver.openInputStream(uri) ?: error("Unable to read avatar at $uri") },
                maxEdge = PhotoSanitizer.AVATAR_MAX_EDGE
            )
        }
        val path = "$userId-${System.currentTimeMillis()}.jpg"
        bucket.upload(path, bytes)
        return bucket.publicUrl(path)
    }
}
