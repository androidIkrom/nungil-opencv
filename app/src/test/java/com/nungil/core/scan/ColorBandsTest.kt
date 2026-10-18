package com.nungil.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The HSV bands are ColorMapper's rules written as boxes in OpenCV's 8-bit HSV units (hue 0..179, saturation and
 * value 0..255), so OpenCV can count them with inRange. They must agree with ColorMapper on every pixel.
 */
class ColorBandsTest {
    @Test fun bandsAgreeWithColorMapperEverywhere() {
        var checked = 0
        for (h in 0 until 180) for (s in 0..255 step 3) for (v in 0..255 step 3) {
            val want = ColorMapper.nameFromHsv(h * 2f, s / 255f, v / 255f, frameIsDark = false)
            assertEquals("h=$h s=$s v=$v", want, ColorBands.nameOf(h, s, v))
            checked++
        }
        assertTrue(checked > 1_000_000)
    }

    @Test fun bandsAreDisjointAndCoverTheCube() {
        for (h in 0 until 180 step 2) for (s in 0..255 step 5) for (v in 0..255 step 5) {
            val hits = ColorBands.bands.count { it.contains(h, s, v) }
            assertEquals("h=$h s=$s v=$v", 1, hits)
        }
    }

    @Test fun bandsStayInsideOpenCvUnits() {
        for (b in ColorBands.bands) {
            assertTrue(b.toString(), b.h0 in 0..b.h1 && b.h1 <= 179)
            assertTrue(b.toString(), b.s0 in 0..b.s1 && b.s1 <= 255)
            assertTrue(b.toString(), b.v0 in 0..b.v1 && b.v1 <= 255)
        }
    }
}
