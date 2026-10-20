package com.nungil.cloud

import android.graphics.Bitmap
import android.graphics.Matrix
import com.nungil.contract.Box
import com.nungil.core.cloud.CloudCrop
import com.nungil.core.cloud.Crop
import java.io.ByteArrayOutputStream

/**
 * The picture "look closer" uploads: the frame upright, cut to the Find target box with its margin when there is
 * one (CloudCrop, so the upload carries the thing and not the room), long side at most [SIDE], JPEG at [QUALITY].
 * One picture per voice command, on FrameAnswers' worker thread.
 */
object CloudFrame {
    const val SIDE = 1280
    const val QUALITY = 80

    /** [rotation] is the turn that makes [bitmap] upright (VisionFrame.rotation); [box] is in the upright picture. */
    fun jpeg(bitmap: Bitmap, rotation: Int, box: Box?): ByteArray {
        val upright = if (rotation == 0) bitmap else {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        }
        val crop = box?.let { CloudCrop.rect(it, upright.width, upright.height) } ?: Crop(0, 0, upright.width, upright.height)
        val scale = minOf(1f, SIDE.toFloat() / maxOf(crop.width, crop.height))
        val cut = if (box == null && scale == 1f) upright else {
            val matrix = Matrix().apply { if (scale < 1f) postScale(scale, scale) }
            Bitmap.createBitmap(upright, crop.x, crop.y, crop.width, crop.height, matrix, true)
        }
        val out = ByteArrayOutputStream()
        cut.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        if (cut !== upright) cut.recycle()
        if (upright !== bitmap) upright.recycle()
        return out.toByteArray()
    }
}
