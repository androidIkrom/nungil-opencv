package com.nungil.cv

import android.graphics.Bitmap
import com.nungil.contract.Box
import com.nungil.core.scan.ColorBands
import com.nungil.core.scan.ColorName
import com.nungil.core.scan.ColorVote
import com.nungil.core.scan.WhiteBalance
import com.nungil.scan.ColorSampler
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * Scan's colour work in OpenCV, over every pixel of a region instead of a 24 x 24 grid of them: the frame's
 * gray-world gains and darkness from channel means, and a region's colour counts from one inRange per
 * [ColorBands] band. The names follow ColorVote.counts: a pixel that is white, gray or black as shot keeps that
 * name; a colourful pixel is named after white balance. Call on a worker thread, after [Cv.ensure].
 */
object CvColor {
    private val achromatic = setOf(ColorName.BLACK, ColorName.WHITE, ColorName.GRAY)

    /**
     * Whether OpenCV may read [bitmap]: Utils.bitmapToMat takes ARGB_8888 and RGB_565 only, and on anything else its
     * native side asserts after locking the pixels, which crashed the process (Scudo double free) in the emulator.
     * Callers check this first and take the grid path otherwise.
     */
    fun canRead(bitmap: Bitmap): Boolean =
        !bitmap.isRecycled && (bitmap.config == Bitmap.Config.ARGB_8888 || bitmap.config == Bitmap.Config.RGB_565)

    fun frameLight(bitmap: Bitmap): ColorSampler.FrameLight {
        val rgba = Mat()
        val rgb = Mat()
        val hsv = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            val mean = Core.mean(rgb).`val`
            val gains = WhiteBalance.gainsOf(mean[0].toFloat(), mean[1].toFloat(), mean[2].toFloat())
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            val meanV = Core.mean(hsv).`val`[2] / 255.0
            return ColorSampler.FrameLight(gains, meanV < ColorVote.DARK_FRAME_V)
        } finally {
            rgba.release()
            rgb.release()
            hsv.release()
        }
    }

    /** How many pixels of [region] (0..1 of [bitmap]) each colour name gets, by ColorName.ordinal, after [gains]. */
    fun counts(bitmap: Bitmap, region: Box, gains: FloatArray): IntArray {
        val counts = IntArray(ColorName.entries.size)
        val rgba = Mat()
        val mats = ArrayList<Mat>()
        fun mat() = Mat().also { mats += it }
        try {
            Utils.bitmapToMat(bitmap, rgba)
            val roi = pixelRect(region, rgba.cols(), rgba.rows())
            val rgb = mat()
            Imgproc.cvtColor(rgba.submat(roi).also { mats += it }, rgb, Imgproc.COLOR_RGBA2RGB)
            val hsvRaw = mat()
            Imgproc.cvtColor(rgb, hsvRaw, Imgproc.COLOR_RGB2HSV)
            // White, gray and black as shot keep their names; the rest of the region is "colourful".
            val colourful = Mat(rgb.rows(), rgb.cols(), CvType.CV_8UC1, Scalar(255.0)).also { mats += it }
            val band = mat()
            for (b in ColorBands.bands) {
                if (b.name !in achromatic) continue
                Core.inRange(hsvRaw, lower(b), upper(b), band)
                counts[b.name.ordinal] += Core.countNonZero(band)
                Core.subtract(colourful, band, colourful)
            }
            val balanced = mat()
            Core.multiply(rgb, Scalar(gains[0].toDouble(), gains[1].toDouble(), gains[2].toDouble()), balanced)
            val hsvBal = mat()
            Imgproc.cvtColor(balanced, hsvBal, Imgproc.COLOR_RGB2HSV)
            val masked = mat()
            for (b in ColorBands.bands) {
                Core.inRange(hsvBal, lower(b), upper(b), band)
                Core.bitwise_and(band, colourful, masked)
                counts[b.name.ordinal] += Core.countNonZero(masked)
            }
            return counts
        } finally {
            rgba.release()
            mats.forEach { it.release() }
        }
    }

    private fun lower(b: ColorBands.Band) = Scalar(b.h0.toDouble(), b.s0.toDouble(), b.v0.toDouble())
    private fun upper(b: ColorBands.Band) = Scalar(b.h1.toDouble(), b.s1.toDouble(), b.v1.toDouble())

    /** [region] in pixels, clamped to the image and at least one pixel wide and high. */
    fun pixelRect(region: Box, width: Int, height: Int): Rect {
        val x0 = (region.left * width).toInt().coerceIn(0, width - 1)
        val y0 = (region.top * height).toInt().coerceIn(0, height - 1)
        val x1 = (region.right * width).toInt().coerceIn(x0 + 1, width)
        val y1 = (region.bottom * height).toInt().coerceIn(y0 + 1, height)
        return Rect(x0, y0, x1 - x0, y1 - y0)
    }
}
