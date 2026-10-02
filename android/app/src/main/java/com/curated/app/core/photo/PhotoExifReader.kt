package com.curated.app.core.photo

import android.content.Context
import android.net.Uri
import android.util.Log
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

    /**
     * Where the location comes from depends on how the photo was picked, as
     * tested on API 37:
     * - The system file picker (ACTION_OPEN_DOCUMENT) gives the real GPS when
     *   ACCESS_MEDIA_LOCATION is granted, and zeroes it when it isn't.
     * - The Photo Picker always zeroes it, permission or not.
     * - MediaStore.setRequireOriginal() fails on both kinds of URI, so it isn't
     *   used. ExifInterface reports zeroed GPS as no location.
     */
    fun read(context: Context, uri: Uri): PhotoExifData {
        val exif = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        }.onFailure { Log.w(TAG, "Couldn't read EXIF from $uri", it) }.getOrNull()

        val latLong = exif?.latLong
        val takenAt = exif?.let {
            val raw = it.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: it.getAttribute(ExifInterface.TAG_DATETIME)
            raw?.let(::parseExifDate)
        }

        return PhotoExifData(
            uri = uri,
            latitude = latLong?.get(0),
            longitude = latLong?.get(1),
            takenAt = takenAt
        )
    }

    private const val TAG = "PhotoExifReader"

    private fun parseExifDate(raw: String): Instant? {
        val parsed = exifDateFormat.parse(raw, ParsePosition(0)) ?: return null
        return Instant.fromEpochMilliseconds(parsed.time)
    }
}
