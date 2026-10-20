package com.nungil.cloud

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nungil.contract.Box
import com.nungil.core.cloud.CloudCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The JPEG "look closer" uploads: the whole frame, or in Find the target box with its margin, upright, long side at
 * most CloudFrame.SIDE. Sizes are checked within a pixel (Bitmap.createBitmap rounds the scaled size itself).
 */
@RunWith(AndroidJUnit4::class)
class CloudFrameTest {
    private val box = Box(0.3f, 0.2f, 0.6f, 0.7f)

    private fun desk(): Bitmap {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val name = assets.list("desks").orEmpty().sorted().first()
        return assets.open("desks/$name").use { BitmapFactory.decodeStream(it) }!!
    }

    private fun size(jpeg: ByteArray): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, o)
        return o.outWidth to o.outHeight
    }

    private fun assertSize(w: Int, h: Int, jpeg: ByteArray) {
        val scale = minOf(1f, CloudFrame.SIDE.toFloat() / maxOf(w, h))
        val (jw, jh) = size(jpeg)
        assertEquals("width", Math.round(w * scale).toFloat(), jw.toFloat(), 1f)
        assertEquals("height", Math.round(h * scale).toFloat(), jh.toFloat(), 1f)
        assertTrue("long side $jw x $jh", maxOf(jw, jh) <= CloudFrame.SIDE)
    }

    @Test fun theWholeFrameKeepsItsShapeWithinTheSideLimit() {
        val photo = desk()
        assertSize(photo.width, photo.height, CloudFrame.jpeg(photo, 0, null))
    }

    @Test fun theBoxIsCutWithItsMargin() {
        val photo = desk()
        val crop = CloudCrop.rect(box, photo.width, photo.height)
        assertSize(crop.width, crop.height, CloudFrame.jpeg(photo, 0, box))
    }

    @Test fun aSidewaysFrameIsCutInTheUprightPicture() {
        // Upright 400 x 300: blue, with a red rectangle exactly where the box is; then laid on its side.
        val upright = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        Canvas(upright).apply {
            drawColor(Color.BLUE)
            drawRect(box.left * 400, box.top * 300, box.right * 400, box.bottom * 300, Paint().apply { color = Color.RED })
        }
        val sideways = Bitmap.createBitmap(upright, 0, 0, 400, 300, Matrix().apply { postRotate(270f) }, true)
        assertEquals(300, sideways.width)
        val jpeg = CloudFrame.jpeg(sideways, 90, box)
        val crop = CloudCrop.rect(box, 400, 300)
        assertSize(crop.width, crop.height, jpeg)
        val cut = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        val centre = cut.getPixel(cut.width / 2, cut.height / 2)
        val corner = cut.getPixel(2, 2)
        assertTrue("centre is red: ${Integer.toHexString(centre)}", Color.red(centre) > 150 && Color.blue(centre) < 100)
        assertTrue("margin is blue: ${Integer.toHexString(corner)}", Color.blue(corner) > 150 && Color.red(corner) < 100)
    }

    @Test fun theBoxUploadIsSmallerThanTheWholeFrame() {
        val photo = desk()
        val whole = CloudFrame.jpeg(photo, 0, null)
        val cut = CloudFrame.jpeg(photo, 0, box)
        Log.i("NungilCv", "look closer upload: whole frame ${photo.width}x${photo.height} ${whole.size} bytes, box ${cut.size} bytes")
        assertTrue("${cut.size} < ${whole.size}", cut.size < whole.size / 2)
    }
}
