package com.curated.app.core.data

import android.content.Context
import android.net.Uri
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage

private const val BUCKET = "avatars"

class AvatarStorageRepository(
    private val client: SupabaseClient,
    private val context: Context
) {
    private val bucket get() = client.storage.from(BUCKET)

    suspend fun upload(userId: String, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read avatar at $uri")
        val path = "$userId-${System.currentTimeMillis()}.jpg"
        bucket.upload(path, bytes)
        return bucket.publicUrl(path)
    }
}
