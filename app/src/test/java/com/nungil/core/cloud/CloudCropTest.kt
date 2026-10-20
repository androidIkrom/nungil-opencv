package com.nungil.core.cloud

import com.nungil.contract.Box
import org.junit.Assert.assertEquals
import org.junit.Test

/** The pixels "look closer" sends in Find: the target box with a margin, inside the frame; same rule as prep.py crop(). */
class CloudCropTest {
    @Test fun growsTheBoxByTheMarginOnEverySide() {
        // A 0.5 x 0.5 box grows by 0.075 on every side (15 % of its own size).
        assertEquals(Crop(175, 140, 650, 520), CloudCrop.rect(Box(0.25f, 0.25f, 0.75f, 0.75f), 1000, 800))
    }

    @Test fun staysInsideTheFrameAtTheTopLeft() {
        assertEquals(Crop(0, 0, 46, 46), CloudCrop.rect(Box(0f, 0f, 0.4f, 0.4f), 100, 100))
    }

    @Test fun staysInsideTheFrameAtTheBottomRight() {
        assertEquals(Crop(84, 84, 16, 16), CloudCrop.rect(Box(0.9f, 0.9f, 1.3f, 1.3f), 100, 100))
    }

    @Test fun aPointIsOnePixel() {
        assertEquals(Crop(50, 50, 1, 1), CloudCrop.rect(Box(0.5f, 0.5f, 0.5f, 0.5f), 100, 100))
    }

    @Test fun aPointAtTheFarCornerIsTheLastPixel() {
        assertEquals(Crop(99, 99, 1, 1), CloudCrop.rect(Box(1f, 1f, 1f, 1f), 100, 100))
    }
}
