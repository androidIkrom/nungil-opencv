package com.nungil.cv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nungil.contract.Box
import com.nungil.core.scan.ColorVote
import com.nungil.debug.ReplayHostActivity
import com.nungil.scan.ColorSampler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Scan's colour through OpenCV (every pixel of the region) against the 24 x 24 grid sampler it replaces, over the
 * desk photos and five fixed boxes each. They measure the same thing two ways, so they agree on most boxes, not
 * all; both timings go to logcat (tag NungilCv) for the report.
 */
@RunWith(AndroidJUnit4::class)
class CvColorParityTest {
    @get:Rule val host = ActivityScenarioRule(ReplayHostActivity::class.java)

    private val boxes = listOf(
        Box(0.25f, 0.25f, 0.75f, 0.75f),
        Box(0.0f, 0.0f, 0.5f, 0.5f), Box(0.5f, 0.0f, 1.0f, 0.5f),
        Box(0.0f, 0.5f, 0.5f, 1.0f), Box(0.5f, 0.5f, 1.0f, 1.0f),
    )

    @After fun on() { Cv.forceOff = false }

    private fun photos(): List<Pair<String, Bitmap>> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return assets.list("desks").orEmpty().sorted().mapNotNull { name ->
            assets.open("desks/$name").use { BitmapFactory.decodeStream(it) }?.let { name to it }
        }
    }

    @Test fun opencvColourAgreesWithGridSampling() {
        assertTrue(Cv.ensure())
        var same = 0
        var named = 0
        var cvMs = 0.0
        var gridMs = 0.0
        for ((name, photo) in photos()) {
            Cv.forceOff = true
            var t = SystemClock.elapsedRealtimeNanos()
            val gridLight = ColorSampler.frameLight(photo)
            val grid = boxes.map { ColorSampler.colorOf(photo, it, gridLight) }
            gridMs += (SystemClock.elapsedRealtimeNanos() - t) / 1e6
            Cv.forceOff = false
            t = SystemClock.elapsedRealtimeNanos()
            val cvLight = ColorSampler.frameLight(photo)
            val cv = boxes.map { ColorSampler.colorOf(photo, it, cvLight) }
            cvMs += (SystemClock.elapsedRealtimeNanos() - t) / 1e6
            assertTrue("$name path", ColorSampler.explain(photo, boxes[0], cvLight).endsWith("; cv"))
            for (i in 0..2) assertTrue("$name gains $i", abs(gridLight.gains[i] - cvLight.gains[i]) <= 0.05f)
            assertEquals("$name dark", gridLight.isDark, cvLight.isDark)
            for (i in boxes.indices) {
                if (grid[i] == null && cv[i] == null) continue
                named++
                if (grid[i] == cv[i]) same++
            }
            Log.i("NungilCv", "$name grid=${grid.map { it?.en }} cv=${cv.map { it?.en }}")
        }
        val n = photos().size
        val agreement = if (named == 0) 1f else same.toFloat() / named
        Log.i("NungilCv", "colour parity: $same/$named agree (${"%.0f".format(agreement * 100)}%); per photo grid ${"%.1f".format(gridMs / n)} ms, cv ${"%.1f".format(cvMs / n)} ms")
        assertTrue("agreement $agreement", agreement >= 0.8f)
    }

    /** A bitmap OpenCV cannot read (ALPHA_8): the sampler must answer like the grid, not throw. */
    @Test fun fallsBackToGridWhenOpenCvThrows() {
        assertTrue(Cv.ensure())
        val odd = Bitmap.createBitmap(64, 48, Bitmap.Config.ALPHA_8)
        val light = ColorSampler.frameLight(odd)
        Cv.forceOff = true
        val grid = ColorSampler.frameLight(odd)
        assertEquals(grid.isDark, light.isDark)
        assertEquals(grid.gains.toList(), light.gains.toList())
        Cv.forceOff = false
        assertEquals(ColorSampler.colorOf(odd, boxes[0], light), run { Cv.forceOff = true; ColorSampler.colorOf(odd, boxes[0], light) })
        assertTrue(ColorSampler.explain(odd, boxes[0], light).endsWith("; grid"))
    }

    @Test fun fallsBackToGridWhenOpenCvIsOff() {
        Cv.forceOff = true
        val (_, photo) = photos().first()
        val light = ColorSampler.frameLight(photo)
        val points = ColorVote.gridPoints(ColorVote.sampleRegion(boxes[0]), photo.width, photo.height, ColorVote.SAMPLE_GRID)
        val pixels = IntArray(points.size / 2) { i -> photo.getPixel(points[2 * i], points[2 * i + 1]) }
        assertEquals(ColorVote.vote(pixels, light.gains, light.isDark), ColorSampler.colorOf(photo, boxes[0], light))
        assertTrue(ColorSampler.explain(photo, boxes[0], light).endsWith("; grid"))
    }
}
