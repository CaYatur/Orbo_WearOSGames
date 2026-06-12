package com.cayadev.orbo.engine

import android.graphics.Canvas

/** Animated, lightweight menu background: a twinkling starfield + slow orbital rings. */
class Backdrop(starCount: Int = 46) {
    private val sx = FloatArray(starCount)
    private val sy = FloatArray(starCount)
    private val ss = FloatArray(starCount)
    private val sp = FloatArray(starCount)
    private val sc = IntArray(starCount)
    private var t = 0f

    init {
        for (i in sx.indices) {
            sx[i] = Mathx.rand(0f, 1f)
            sy[i] = Mathx.rand(0f, 1f)
            ss[i] = Mathx.rand(0.6f, 2.2f)
            sp[i] = Mathx.rand(0f, Mathx.TAU)
            sc[i] = when (Mathx.rand(5)) {
                0 -> Palette.mint; 1 -> Palette.violet; 2 -> Palette.cyan; 3 -> Palette.pink; else -> Palette.text
            }
        }
    }

    fun update(dt: Float) { t += dt }

    fun render(c: Canvas, s: ScreenInfo, accent: Int = Palette.violet) {
        Draw.background(c, Palette.bg)
        // orbital rings
        Draw.withFaceClip(c, s) {
            val ringA = 0.10f
            Draw.strokeCircle(c, s.cx, s.cy, s.r * 0.95f, Palette.alpha(accent, ringA), s.dp(1.2f))
            // rotating dashed arc
            val sweep = 42f
            var ang = (t * 26f) % 360f
            repeat(6) {
                Draw.arc(c, s.cx, s.cy, s.r * 0.78f, ang, sweep * 0.5f, Palette.alpha(accent, 0.12f), s.dp(1.4f))
                ang += 60f
            }
            // stars
            for (i in sx.indices) {
                val tw = 0.4f + 0.6f * (0.5f + 0.5f * Mathx.sinf(t * 1.6f + sp[i]))
                val px = sx[i] * s.w
                val py = sy[i] * s.h
                Draw.fillCircle(c, px, py, s.dp(ss[i]) * tw, Palette.alpha(sc[i], 0.22f + 0.4f * tw))
            }
        }
        Draw.vignette(c, s, Palette.alpha(Palette.bg, 0f), Palette.bg)
    }
}
