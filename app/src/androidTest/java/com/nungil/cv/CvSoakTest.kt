package com.nungil.cv

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nungil.contract.Box
import com.nungil.debug.ReplayHostActivity
import com.nungil.items.ItemMasks
import com.nungil.items.ItemSegmenter
import com.nungil.scan.ColorSampler
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The OpenCV paths over a video, frame after frame, as Live scan and item learning meet them: every frame goes
 * through the colour path (light + three boxes), every tenth through the segmenter and the mask clean-up. The
 * native heap must not keep growing (Mats are released, not leaked) and the frame time must stay flat. The video
 * is made by tools/desk-photos/make_video.sh (1 050 frames, 640 x 480).
 */
@RunWith(AndroidJUnit4::class)
class CvSoakTest {
    @get:Rule val host = ActivityScenarioRule(ReplayHostActivity::class.java)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val boxes = listOf(Box(0.25f, 0.25f, 0.75f, 0.75f), Box(0f, 0f, 0.5f, 0.5f), Box(0.5f, 0.5f, 1f, 1f))

    @Test fun colourAndMaskStayFlatOverAVideo() {
        assertTrue(Cv.ensure())
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val names = assets.list("videos").orEmpty()
        assumeTrue("needs app/src/androidTest/assets/videos/desks.mp4 (tools/desk-photos/make_video.sh)", "desks.mp4" in names)
        val copy = File(context.cacheDir, "desks.mp4")
        assets.open("videos/desks.mp4").use { input -> copy.outputStream().use { input.copyTo(it) } }
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(copy.path)
        val total = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toInt() ?: 0
        assertTrue("frames $total", total >= 500)
        val segmenter = ItemSegmenter(context)
        val colourMs = DoubleArray(total)
        var masks = 0
        var named = 0
        var nativeAt100 = 0L
        var javaAt100 = 0L
        try {
            for (i in 0 until total) {
                val frame = retriever.getFrameAtIndex(i)?.copy(Bitmap.Config.ARGB_8888, false) ?: continue
                val t = SystemClock.elapsedRealtimeNanos()
                val light = ColorSampler.frameLight(frame)
                for (b in boxes) if (ColorSampler.colorOf(frame, b, light) != null) named++
                colourMs[i] = (SystemClock.elapsedRealtimeNanos() - t) / 1e6
                if (i % 10 == 0) {
                    val answer = segmenter.at(frame, 0.5f, 0.5f)
                    if (answer != null && ItemMasks.cut(answer, 0.5f, 0.5f) != null) masks++
                }
                frame.recycle()
                if (i == 100) {
                    System.gc()
                    nativeAt100 = Debug.getNativeHeapAllocatedSize()
                    javaAt100 = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
                }
            }
        } finally {
            segmenter.close()
            retriever.release()
            copy.delete()
        }
        System.gc()
        val nativeEnd = Debug.getNativeHeapAllocatedSize()
        val javaEnd = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        val first = colourMs.copyOfRange(10, 110).average()
        val last = colourMs.copyOfRange(total - 100, total).average()
        Log.i(
            "NungilCv",
            "soak: $total frames; colour ${"%.1f".format(first)} ms early, ${"%.1f".format(last)} ms late; " +
                "$named named boxes, $masks masks; native heap ${nativeAt100 / 1048576} -> ${nativeEnd / 1048576} MB, " +
                "java heap ${javaAt100 / 1048576} -> ${javaEnd / 1048576} MB",
        )
        assertTrue("native heap grew ${(nativeEnd - nativeAt100) / 1048576} MB", nativeEnd - nativeAt100 < 20L * 1048576)
        assertTrue("colour time drifted: $first -> $last ms", last < first * 2 + 2)
        assertTrue("masks $masks", masks >= total / 10 / 2)
    }
}
