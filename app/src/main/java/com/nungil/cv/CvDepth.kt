package com.nungil.cv

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * ARCore's depth image (millimetres, 0 = could not measure) through OpenCV's 3 x 3 median before Walk reads
 * floors and walls from it: the logs show 10-20 cm of wobble on a plain floor, and a median takes the spikes
 * while leaving a real step edge where it is. A pixel ARCore could not measure stays unmeasured: the filter
 * never invents depth. Call after [Cv.ensure].
 */
object CvDepth {
    private const val KERNEL = 3

    /** A new array, [depthMm] filtered; [depthMm] itself is left alone. */
    fun denoise(depthMm: ShortArray, width: Int, height: Int): ShortArray {
        require(depthMm.size == width * height) { "depth ${depthMm.size} is not $width x $height" }
        val src = Mat(height, width, CvType.CV_16UC1)
        val dst = Mat()
        try {
            src.put(0, 0, depthMm)
            Imgproc.medianBlur(src, dst, KERNEL)
            val out = ShortArray(depthMm.size)
            dst.get(0, 0, out)
            for (i in out.indices) if (depthMm[i].toInt() == 0) out[i] = 0
            return out
        } finally {
            src.release()
            dst.release()
        }
    }
}
