package com.nungil.cv

import com.nungil.core.items.ItemMask
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * The segmenter's answer cleaned with OpenCV before it becomes an [ItemMask]: an opening and a closing with a
 * 3 x 3 ellipse take the specks and seal the pinholes, then only the connected component under the user's finger
 * is kept, so a stray blob on the table never becomes part of a saved object. Call after [Cv.ensure].
 */
object CvMask {
    private const val KERNEL_PX = 3.0

    /** The item at ([x], [y]) (0..1) of a [width] x [height] [confidence] answer, cleaned; null as [ItemMask.of]. */
    fun clean(confidence: FloatArray, width: Int, height: Int, x: Float, y: Float): ItemMask? {
        if (width <= 0 || height <= 0 || confidence.size != width * height) return null
        val px = (x * width).toInt().coerceIn(0, width - 1)
        val py = (y * height).toInt().coerceIn(0, height - 1)
        val high = ItemMask.pointIsHigh(confidence, width, height, px, py)
        val mask = Mat(height, width, CvType.CV_8UC1)
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(KERNEL_PX, KERNEL_PX))
        try {
            mask.put(0, 0, ByteArray(confidence.size) { i -> if ((confidence[i] >= ItemMask.INSIDE) == high) ON else 0 })
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
            val n = Imgproc.connectedComponentsWithStats(mask, labels, stats, centroids, 8, CvType.CV_32S)
            if (n <= 1) return null
            var keep = labels.get(py, px)[0].toInt()
            // The finger landed on a speck the opening took: the biggest thing left is the item.
            if (keep == 0) keep = (1 until n).maxBy { stats.get(it, Imgproc.CC_STAT_AREA)[0] }
            val all = IntArray(confidence.size)
            labels.get(0, 0, all)
            return ItemMask.of(BooleanArray(confidence.size) { all[it] == keep }, width, height)
        } finally {
            mask.release()
            labels.release()
            stats.release()
            centroids.release()
            kernel.release()
        }
    }

    /** How many separate pieces [inside] has (8-connected), for tests and the report. */
    fun components(inside: BooleanArray, width: Int, height: Int): Int {
        val mask = Mat(height, width, CvType.CV_8UC1)
        val labels = Mat()
        try {
            mask.put(0, 0, ByteArray(inside.size) { if (inside[it]) ON else 0 })
            return Imgproc.connectedComponents(mask, labels, 8, CvType.CV_32S) - 1
        } finally {
            mask.release()
            labels.release()
        }
    }

    private const val ON: Byte = -1 // 0xFF
}
