package com.nungil.cv

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.CvType
import org.opencv.core.Mat

/** OpenCV 5's native library loads on this device and a Mat can be made: the floor every other cv test stands on. */
@RunWith(AndroidJUnit4::class)
class CvSmokeTest {
    @Test fun loadsOpenCv5() {
        assertTrue(Cv.ensure())
        assertTrue(Cv.version!!.startsWith("5."))
        val m = Mat.zeros(4, 4, CvType.CV_8UC1)
        assertEquals(16, m.total().toInt())
    }
}
