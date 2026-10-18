package com.nungil.items

import com.nungil.core.items.ItemMask
import com.nungil.cv.Cv
import com.nungil.cv.CvMask

/** The mask of the thing at ([x], [y]) from a segmenter [answer]: cleaned by OpenCV when it is there, else the raw threshold. */
object ItemMasks {
    fun cut(answer: ItemSegmenter.Answer, x: Float, y: Float): ItemMask? =
        if (Cv.ensure()) CvMask.clean(answer.values, answer.width, answer.height, x, y)
        else ItemMask.of(answer.values, answer.width, answer.height, x, y)
}
