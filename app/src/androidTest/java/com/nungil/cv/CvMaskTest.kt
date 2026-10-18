package com.nungil.cv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nungil.core.items.ItemMask
import com.nungil.debug.ReplayHostActivity
import com.nungil.items.ItemMasks
import com.nungil.items.ItemSegmenter
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The segmenter's mask cleaned by OpenCV (open, close, keep the touched component) against the raw threshold, on
 * desk photos pointed at three places each: one component left, and still the same thing (intersection over
 * union 0.85 or more on average). Logs what was removed and the time (tag NungilCv).
 */
@RunWith(AndroidJUnit4::class)
class CvMaskTest {
    @get:Rule val host = ActivityScenarioRule(ReplayHostActivity::class.java)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val points = listOf(0.5f to 0.5f, 0.3f to 0.6f, 0.7f to 0.4f)

    @After fun on() { Cv.forceOff = false }

    private fun photos(): List<Pair<String, Bitmap>> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return assets.list("desks").orEmpty().sorted().take(10).mapNotNull { name ->
            assets.open("desks/$name").use { BitmapFactory.decodeStream(it) }?.let { name to it }
        }
    }

    private fun iou(a: BooleanArray, b: BooleanArray): Double {
        var inter = 0
        var union = 0
        for (i in a.indices) {
            if (a[i] && b[i]) inter++
            if (a[i] || b[i]) union++
        }
        return if (union == 0) 1.0 else inter.toDouble() / union
    }

    @Test fun cleanedMaskIsOneComponentAndCloseToRaw() {
        assertTrue(Cv.ensure())
        val segmenter = ItemSegmenter(context)
        val ious = ArrayList<Double>()
        var rawComponents = 0
        var pairs = 0
        var ms = 0.0
        try {
            for ((name, photo) in photos()) for ((x, y) in points) {
                val answer = segmenter.at(photo, x, y) ?: continue
                val raw = ItemMask.of(answer.values, answer.width, answer.height, x, y) ?: continue
                val t = SystemClock.elapsedRealtimeNanos()
                val cleaned = CvMask.clean(answer.values, answer.width, answer.height, x, y)
                ms += (SystemClock.elapsedRealtimeNanos() - t) / 1e6
                assertNotNull("$name $x,$y cleaned", cleaned)
                assertEquals("$name $x,$y components", 1, CvMask.components(cleaned!!.inside, cleaned.width, cleaned.height))
                rawComponents += CvMask.components(raw.inside, raw.width, raw.height)
                ious += iou(raw.inside, cleaned.inside)
                pairs++
                Log.i("NungilCv", "$name $x,$y raw cover ${"%.3f".format(raw.cover)} -> ${"%.3f".format(cleaned.cover)}, iou ${"%.3f".format(ious.last())}")
            }
        } finally {
            segmenter.close()
        }
        assertTrue("pairs $pairs", pairs >= 10)
        val meanIou = ious.average()
        Log.i("NungilCv", "mask clean-up: $pairs masks, raw components ${"%.2f".format(rawComponents.toDouble() / pairs)} each -> 1, mean iou ${"%.3f".format(meanIou)}, ${"%.1f".format(ms / pairs)} ms each")
        assertTrue("mean iou $meanIou", meanIou >= 0.85)
    }

    @Test fun fallsBackToRawThresholdWhenOpenCvIsOff() {
        val segmenter = ItemSegmenter(context)
        try {
            val (_, photo) = photos().first()
            val answer = segmenter.at(photo, 0.5f, 0.5f)!!
            Cv.forceOff = true
            val got = ItemMasks.cut(answer, 0.5f, 0.5f)
            val want = ItemMask.of(answer.values, answer.width, answer.height, 0.5f, 0.5f)
            assertEquals(want == null, got == null)
            if (want != null) assertArrayEquals(want.inside, got!!.inside)
        } finally {
            segmenter.close()
        }
    }
}
