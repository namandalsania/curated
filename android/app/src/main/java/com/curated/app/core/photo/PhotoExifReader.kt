package com.curated.app.core.photo

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.datetime.Instant
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

data class PhotoExifData(
    val uri: Uri,
    val latitude: Double?,
    val longitude: Double?,
    val takenAt: Instant?
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
}

/** Reads GPS coordinates + capture timestamp out of a photo's EXIF tags. */
object PhotoExifReader {

    private val exifDateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)

    fun read(context: Context, uri: Uri): PhotoExifData {
        val latLong = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).latLong
            }
        }.getOrNull()

        val takenAt = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                raw?.let { parseExifDate(it) }
            }
        }.getOrNull()

        return PhotoExifData(
            uri = uri,
            latitude = latLong?.get(0),
            longitude = latLong?.get(1),
            takenAt = takenAt
        )
    }

    private fun parseExifDate(raw: String): Instant? {
        val parsed = exifDateFormat.parse(raw, ParsePosition(0)) ?: return null
        return Instant.fromEpochMilliseconds(parsed.time)
    }
}
