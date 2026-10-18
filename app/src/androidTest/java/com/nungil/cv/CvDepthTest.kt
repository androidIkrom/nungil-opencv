package com.nungil.cv

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.random.Random

/**
 * Walk's depth image through OpenCV's median filter: the spikes depth invents on a plain floor go, a real edge
 * stays sharp, and no measurement is invented where ARCore had none.
 */
@RunWith(AndroidJUnit4::class)
class CvDepthTest {
    private val w = 160
    private val h = 90

    @Test fun removesSpikesAndKeepsZeros() {
        assertTrue(Cv.ensure())
        val random = Random(1)
        val depth = ShortArray(w * h) { 1500 }
        for (i in depth.indices) if (random.nextFloat() < 0.05f) depth[i] = random.nextInt(300, 6000).toShort()
        val zeros = (0 until w * h).filter { random.nextFloat() < 0.03f }
        for (i in zeros) depth[i] = 0
        val out = CvDepth.denoise(depth, w, h)
        assertEquals(depth.size, out.size)
        for (i in zeros) assertEquals("zero stays at $i", 0, out[i].toInt())
        val measured = out.filter { it.toInt() != 0 }
        val onPlane = measured.count { abs(it - 1500) <= 20 }
        assertTrue("$onPlane of ${measured.size} within 20 mm", onPlane >= measured.size * 0.99)
        // The input is left alone.
        assertTrue(depth.count { it.toInt() == 1500 } < depth.size)
    }

    @Test fun keepsAStepEdgeSharp() {
        assertTrue(Cv.ensure())
        val depth = ShortArray(w * h) { i -> if (i % w < w / 2) 1500 else 2500 }
        val out = CvDepth.denoise(depth, w, h)
        assertTrue(out.none { it in 1600..2400 })
        assertEquals(1500, out[10 * w + 5].toInt())
        assertEquals(2500, out[10 * w + w - 5].toInt())
    }

    @Test fun allZeroStaysAllZero() {
        assertTrue(Cv.ensure())
        val out = CvDepth.denoise(ShortArray(w * h), w, h)
        assertTrue(out.all { it.toInt() == 0 })
    }
}
