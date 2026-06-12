package com.cayadev.orbo.engine

import android.graphics.Color
import android.graphics.Typeface

/**
 * Orbo visual identity — dark "terminal/neon" aesthetic inspired by CaYaDev branding.
 * All colors are ARGB ints so they work with the raw Canvas/Paint API (no theme attrs needed).
 */
object Palette {
    const val bg = 0xFF05060A.toInt()
    const val bgSoft = 0xFF0A0C14.toInt()
    const val panel = 0xFF0E1320.toInt()
    const val panelHi = 0xFF161D2E.toInt()
    const val track = 0xFF1B2336.toInt()
    const val line = 0xFF26304A.toInt()

    const val text = 0xFFEAF2EF.toInt()
    const val textDim = 0xFF8794A6.toInt()
    const val textFaint = 0xFF4D586B.toInt()

    // Neon accents
    const val mint = 0xFF22E3A6.toInt()
    const val violet = 0xFF7C6CF0.toInt()
    const val pink = 0xFFFF4D8D.toInt()
    const val cyan = 0xFF34D2FF.toInt()
    const val amber = 0xFFFFC24B.toInt()
    const val lime = 0xFFB6FF3C.toInt()
    const val orange = 0xFFFF8A3D.toInt()
    const val red = 0xFFFF5468.toInt()
    const val blue = 0xFF4C8DFF.toInt()
    const val gold = 0xFFFFD54A.toInt()

    val accents = intArrayOf(mint, violet, pink, cyan, amber, lime, orange, blue)

    fun alpha(color: Int, a: Float): Int {
        val aa = (a.coerceIn(0f, 1f) * 255f).toInt()
        return (aa shl 24) or (color and 0x00FFFFFF)
    }

    /** Linear blend between two ARGB colors. */
    fun lerp(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        val ia = (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * tt).toInt()
        val ir = (Color.red(a) + (Color.red(b) - Color.red(a)) * tt).toInt()
        val ig = (Color.green(a) + (Color.green(b) - Color.green(a)) * tt).toInt()
        val ib = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * tt).toInt()
        return Color.argb(ia, ir, ig, ib)
    }

    /** Shift a color brighter (t>0) or darker (t<0). */
    fun shade(color: Int, t: Float): Int =
        if (t >= 0) lerp(color, Color.WHITE, t) else lerp(color, Color.BLACK, -t)
}

object Fonts {
    val brand: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    val mono: Typeface = Typeface.MONOSPACE
    val display: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val body: Typeface = Typeface.SANS_SERIF
    val bodyBold: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
}
