package com.curated.app.core.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/** Real JPEG decode/encode through Robolectric's native graphics. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoSanitizerTest {

    @get:Rule val tmp = TemporaryFolder()

    /**
     * A 1600x1200 landscape JPEG with noise (so it compresses like a photo), a
     * red block in its top-left corner, and the tags we must not leak: GPS,
     * capture time, camera, XMP - and orientation "rotate 90", as a phone
     * held upright writes it.
     */
    private fun fixture(): File {
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val random = Random(7)
        for (y in 0 until 1200 step 4) for (x in 0 until 1600 step 4) {
            val c = Color.rgb(random.nextInt(60, 200), random.nextInt(60, 200), random.nextInt(60, 200))
            for (dy in 0 until 4) for (dx in 0 until 4) bitmap.setPixel(x + dx, y + dy, c)
        }
        for (y in 0 until 300) for (x in 0 until 400) bitmap.setPixel(x, y, Color.RED)

        val file = tmp.newFile("fixture.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        ExifInterface(file).apply {
            setLatLong(38.7223, -9.1393)
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:09:20 10:15:00")
            setAttribute(ExifInterface.TAG_MAKE, "TestCam")
            setAttribute(ExifInterface.TAG_MODEL, "Fixture 1")
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_XMP, """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"/></x:xmpmeta>""")
            saveAttributes()
        }
        return file
    }

    @Test
    fun `the fixture really carries the tags`() {
        val exif = ExifInterface(fixture())
        assertNotNull(exif.latLong)
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
        assertNotNull(exif.getAttribute(ExifInterface.TAG_XMP))
    }

    @Test
    fun `the output has no GPS, time, camera, XMP or orientation tags`() {
        val input = fixture()
        val out = PhotoSanitizer.sanitize({ input.inputStream() }, maxEdge = 800)
        val outFile = tmp.newFile("out.jpg").apply { writeBytes(out) }

        val exif = ExifInterface(outFile)
        assertNull(exif.latLong)
        listOf(
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_XMP
        ).forEach { assertNull(it, exif.getAttribute(it)) }
        assertEquals(
            ExifInterface.ORIENTATION_UNDEFINED,
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        )

        // Belt and braces: no EXIF (APP1 "Exif"), XMP or IPTC (APP13 "Photoshop") segment at all.
        val raw = String(out, Charsets.ISO_8859_1)
        assertFalse(raw.contains("Exif\u0000\u0000"))
        assertFalse(raw.contains("http://ns.adobe.com/xap/1.0/"))
        assertFalse(raw.contains("Photoshop 3.0"))
    }

    @Test
    fun `orientation is applied to the pixels, then the long edge is capped`() {
        val input = fixture()
        val out = PhotoSanitizer.sanitize({ input.inputStream() }, maxEdge = 800)
        val bitmap = BitmapFactory.decodeByteArray(out, 0, out.size)

        // 1600x1200 rotated 90 is 1200x1600 (portrait), fitted to 800 -> 600x800.
        assertEquals(600, bitmap.width)
        assertEquals(800, bitmap.height)

        // Rotating 90 clockwise moves the source's top-left corner to the top-right.
        assertTrue("top-right should be red", isRed(bitmap.getPixel(560, 30)))
        assertFalse("top-left should not be red", isRed(bitmap.getPixel(30, 30)))
    }

    @Test
    fun `the output is smaller than the original`() {
        val input = fixture()
        val out = PhotoSanitizer.sanitize({ input.inputStream() }, maxEdge = PhotoSanitizer.PHOTO_MAX_EDGE)
        assertTrue("${out.size} should be < ${input.length()}", out.size < input.length())
    }

    @Test
    fun `small images are not enlarged`() {
        val small = tmp.newFile("small.jpg")
        small.outputStream().use { Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val out = PhotoSanitizer.sanitize({ small.inputStream() }, maxEdge = PhotoSanitizer.AVATAR_MAX_EDGE)
        val bitmap = BitmapFactory.decodeByteArray(out, 0, out.size)
        assertEquals(300 to 200, bitmap.width to bitmap.height)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `something that isn't an image is refused, not uploaded as-is`() {
        PhotoSanitizer.sanitize({ "not a jpeg".byteInputStream() }, maxEdge = 800)
    }

    @Test
    fun `sizing helpers`() {
        assertEquals(1, PhotoSanitizer.sampleSizeFor(2048, 2048))
        assertEquals(2, PhotoSanitizer.sampleSizeFor(4096, 2048))
        assertEquals(2, PhotoSanitizer.sampleSizeFor(8000, 2048))
        assertEquals(4, PhotoSanitizer.sampleSizeFor(8192, 2048))
        assertEquals(2048 to 1536, PhotoSanitizer.fittedSize(4000, 3000, 2048))
        assertEquals(1536 to 2048, PhotoSanitizer.fittedSize(3000, 4000, 2048))
        assertEquals(640 to 480, PhotoSanitizer.fittedSize(640, 480, 2048))
    }

    private fun isRed(c: Int) = Color.red(c) > 200 && Color.green(c) < 60 && Color.blue(c) < 60
}
