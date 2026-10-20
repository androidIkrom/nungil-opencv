package com.nungil.core.cloud

import com.nungil.contract.Box
import kotlin.math.roundToInt

/** Pixels of a frame: [x], [y] is the top-left corner, [width] x [height] the size, all inside the frame. */
data class Crop(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * Which pixels of a frame "look closer" sends in Find: the target box grown by [MARGIN] of its own size on every
 * side (some surroundings for the model), clamped to the frame, at least one pixel. Cut on the phone, so the
 * upload carries the thing itself and not the room around it. The same rule as crop() in cloud/app/prep.py,
 * which serves clients that send a whole frame and a box.
 */
object CloudCrop {
    const val MARGIN = 0.15f

    fun rect(box: Box, width: Int, height: Int): Crop {
        require(width > 0 && height > 0) { "empty frame ${width}x$height" }
        val dx = box.width * MARGIN
        val dy = box.height * MARGIN
        val x0 = ((box.left - dx) * width).roundToInt().coerceIn(0, width - 1)
        val y0 = ((box.top - dy) * height).roundToInt().coerceIn(0, height - 1)
        val x1 = ((box.right + dx) * width).roundToInt().coerceIn(x0 + 1, width)
        val y1 = ((box.bottom + dy) * height).roundToInt().coerceIn(y0 + 1, height)
        return Crop(x0, y0, x1 - x0, y1 - y0)
    }
}
