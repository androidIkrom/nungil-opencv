package com.nungil.core.scan

/**
 * [ColorMapper]'s rules as boxes in OpenCV's 8-bit HSV units (hue 0..179, saturation and value 0..255, bounds
 * inclusive), so OpenCV can count a region's colours with one inRange per band. The boxes are disjoint and cover
 * the whole cube; [nameOf] agrees with [ColorMapper.nameFromHsv] on every 8-bit pixel (ColorBandsTest).
 */
object ColorBands {
    data class Band(val name: ColorName, val h0: Int, val h1: Int, val s0: Int, val s1: Int, val v0: Int, val v1: Int) {
        fun contains(h: Int, s: Int, v: Int): Boolean = h in h0..h1 && s in s0..s1 && v in v0..v1
    }

    // v < 0.2 is v <= 50; s < 0.15 is s <= 38; v > 0.8 is v >= 205; hue degrees are 2 x the 8-bit hue.
    private const val BLACK_V = 50
    private const val GRAY_S = 38
    private const val WHITE_V = 205
    private const val LIGHT_S = 127   // s < 0.5
    private const val LIGHT_V = 179   // v > 0.7
    private const val BROWN_V = 152   // v < 0.6

    val bands: List<Band> = buildList {
        add(Band(ColorName.BLACK, 0, 179, 0, 255, 0, BLACK_V))
        add(Band(ColorName.WHITE, 0, 179, 0, GRAY_S, WHITE_V, 255))
        add(Band(ColorName.GRAY, 0, 179, 0, GRAY_S, BLACK_V + 1, WHITE_V - 1))
        // Red hue: below 15 degrees and from 345: a light red is pink.
        for ((h0, h1) in listOf(0 to 7, 173 to 179)) {
            add(Band(ColorName.PINK, h0, h1, GRAY_S + 1, LIGHT_S, LIGHT_V, 255))
            add(Band(ColorName.RED, h0, h1, GRAY_S + 1, LIGHT_S, BLACK_V + 1, LIGHT_V - 1))
            add(Band(ColorName.RED, h0, h1, LIGHT_S + 1, 255, BLACK_V + 1, 255))
        }
        add(Band(ColorName.ORANGE, 8, 22, GRAY_S + 1, 255, BROWN_V + 1, 255))
        add(Band(ColorName.BROWN, 8, 22, GRAY_S + 1, 255, BLACK_V + 1, BROWN_V))
        add(Band(ColorName.YELLOW, 23, 34, GRAY_S + 1, 255, BLACK_V + 1, 255))
        add(Band(ColorName.GREEN, 35, 84, GRAY_S + 1, 255, BLACK_V + 1, 255))
        add(Band(ColorName.BLUE, 85, 129, GRAY_S + 1, 255, BLACK_V + 1, 255))
        add(Band(ColorName.PURPLE, 130, 144, GRAY_S + 1, 255, BLACK_V + 1, 255))
        add(Band(ColorName.PINK, 145, 172, GRAY_S + 1, 255, BLACK_V + 1, 255))
    }

    /** The name of an 8-bit HSV pixel, by the bands. */
    fun nameOf(h: Int, s: Int, v: Int): ColorName? = bands.firstOrNull { it.contains(h, s, v) }?.name
}
