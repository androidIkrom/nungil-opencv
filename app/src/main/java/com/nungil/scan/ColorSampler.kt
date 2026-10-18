package com.nungil.scan

import android.graphics.Bitmap
import com.nungil.contract.Box
import com.nungil.core.scan.ColorName
import com.nungil.core.scan.ColorVote
import com.nungil.core.scan.WhiteBalance
import com.nungil.cv.Cv
import com.nungil.cv.CvColor
import java.util.Locale

/**
 * The colour work on an upright frame bitmap: through OpenCV over every pixel of a region (CvColor) when its
 * library is there, else ColorVote over a grid of sampled pixels. Call on a worker thread.
 */
object ColorSampler {
    /** Light of the whole frame: white-balance gains and whether it is too dark for colours. */
    class FrameLight(val gains: FloatArray, val isDark: Boolean)

    fun frameLight(bitmap: Bitmap): FrameLight {
        viaCv(bitmap) { CvColor.frameLight(bitmap) }?.let { return it }
        val pixels = read(bitmap, Box(0f, 0f, 1f, 1f), ColorVote.FRAME_GRID)
        return FrameLight(WhiteBalance.gains(pixels), ColorVote.isDark(pixels))
    }

    /** Colour of the object in [box], or null when the frame is dark or nothing is named. */
    fun colorOf(bitmap: Bitmap, box: Box, light: FrameLight): ColorName? {
        if (light.isDark) return null
        val region = ColorVote.sampleRegion(box)
        viaCv(bitmap) { CvColor.counts(bitmap, region, light.gains) }?.let { return ColorVote.decide(it, light.isDark) }
        return ColorVote.vote(read(bitmap, region, ColorVote.SAMPLE_GRID), light.gains, light.isDark)
    }

    /**
     * For the log: the votes in [box] before and after white balance, and the frame's gains (Task 2 of the
     * live-quality plan: why a white bottle was "blue"), and which path counted ("cv" or "grid").
     */
    fun explain(bitmap: Bitmap, box: Box, light: FrameLight): String {
        val region = ColorVote.sampleRegion(box)
        val g = light.gains
        val raw = viaCv(bitmap) { CvColor.counts(bitmap, region, NEUTRAL) }
        val cv = raw != null
        val counts: (FloatArray) -> IntArray =
            if (raw != null) { gains -> if (gains === NEUTRAL) raw else CvColor.counts(bitmap, region, gains) }
            else { gains -> ColorVote.counts(read(bitmap, region, ColorVote.SAMPLE_GRID), gains) }
        return "raw " + ColorVote.shares(counts(NEUTRAL)) +
            "; balanced " + ColorVote.shares(counts(g)) +
            String.format(Locale.US, "; gains %.2f %.2f %.2f", g[0], g[1], g[2]) +
            if (cv) "; cv" else "; grid"
    }

    private val NEUTRAL = floatArrayOf(1f, 1f, 1f)

    /**
     * [block] through OpenCV, or null when the library is off, [bitmap] is a format OpenCV cannot read, or the call
     * threw: the grid answers then.
     */
    private inline fun <T : Any> viaCv(bitmap: Bitmap, block: () -> T): T? {
        if (!Cv.ensure() || !CvColor.canRead(bitmap)) return null
        return try {
            block()
        } catch (t: Throwable) {
            Cv.failed(t)
            null
        }
    }

    private fun read(bitmap: Bitmap, region: Box, grid: Int): IntArray {
        val points = ColorVote.gridPoints(region, bitmap.width, bitmap.height, grid)
        return IntArray(points.size / 2) { i -> bitmap.getPixel(points[2 * i], points[2 * i + 1]) }
    }
}
