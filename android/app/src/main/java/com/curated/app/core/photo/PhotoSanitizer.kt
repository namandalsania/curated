package com.curated.app.core.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a picked image into what we store: upright, no larger than needed, and
 * with **no metadata** - no EXIF (GPS, time, camera), XMP or IPTC.
 *
 * The image is decoded to pixels and encoded again as a fresh JPEG. Android's
 * JPEG encoder writes no metadata, so nothing from the original file survives.
 * Its EXIF orientation is applied to the pixels first, so portraits stay
 * upright without needing the tag.
 *
 * The original's location and time are still read on the device, before this
 * runs (see [PhotoExifReader]), to build stops.
 */
object PhotoSanitizer {

    /** Stop photos: the largest display is a full-width card (~1080 px); nothing zooms. */
    const val PHOTO_MAX_EDGE = 2048

    /** Avatars: shown at 96 dp at most (~290 px). */
    const val AVATAR_MAX_EDGE = 640

    const val JPEG_QUALITY = 85

    /**
     * [open] is called more than once (the bounds, the orientation tag, then
     * the pixels), so it must return a fresh stream each time.
     *
     * @throws IllegalArgumentException if the image can't be decoded. Callers
     * should fail the upload rather than fall back to the original bytes.
     */
    fun sanitize(open: () -> InputStream, maxEdge: Int, quality: Int = JPEG_QUALITY): ByteArray {
        require(maxEdge > 0) { "maxEdge must be positive" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not a decodable image" }

        val orientation = runCatching {
            open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        // Decode at a power-of-two reduction that still leaves at least maxEdge,
        // so a 48 MP photo never needs its full size in memory.
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(max(bounds.outWidth, bounds.outHeight), maxEdge)
        }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            ?: throw IllegalArgumentException("Not a decodable image")

        val upright = applyOrientation(decoded, orientation)
        val scaled = scaleToFit(upright, maxEdge)
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }.also {
            if (scaled !== upright) scaled.recycle()
            if (upright !== decoded) upright.recycle()
            decoded.recycle()
        }
    }

    /** The largest power of two that keeps the long edge at or above [maxEdge]. */
    internal fun sampleSizeFor(longEdge: Int, maxEdge: Int): Int {
        var sample = 1
        while (longEdge / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    /** Width and height after fitting [width]x[height] inside a [maxEdge] square. */
    internal fun fittedSize(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
        val long = max(width, height)
        if (long <= maxEdge) return width to height
        val scale = maxEdge.toFloat() / long
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    private fun scaleToFit(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val (w, h) = fittedSize(bitmap.width, bitmap.height, maxEdge)
        return if (w == bitmap.width && h == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
