package com.cayadev.orbo.engine

import android.graphics.Canvas
import android.graphics.Paint

/** Shared, reusable canvas widgets for the menus and game chrome. */
object Ui {

    fun screenTitle(c: Canvas, s: ScreenInfo, title: String, accent: Int, sub: String? = null) {
        val topY = if (s.isRound) s.h * 0.115f else s.h * 0.07f
        Draw.textCentered(c, title, s.cx, topY + s.px(0.02f), s.px(0.072f), Palette.text,
            Fonts.display, letterSpacing = 0.04f)
        if (sub != null) {
            Draw.textCentered(c, sub, s.cx, topY + s.px(0.085f), s.px(0.04f), accent,
                Fonts.mono, letterSpacing = 0.12f)
        }
        // small accent tick under title
        Draw.roundRectCentered(c, s.cx, topY + s.px(0.052f), s.px(0.12f), s.dp(3f), s.dp(2f), Palette.alpha(accent, 0.9f))
    }

    /** A glowing disc with an optional drawn glyph — used as a game/category icon. */
    fun iconDisc(c: Canvas, cx: Float, cy: Float, r: Float, accent: Int, focus: Float = 1f) {
        Draw.glow(c, cx, cy, r * 1.8f, accent, 0.22f * focus)
        Draw.fillCircle(c, cx, cy, r, Palette.alpha(accent, 0.16f))
        Draw.strokeCircle(c, cx, cy, r, Palette.alpha(accent, 0.85f), r * 0.10f)
    }

    fun rowPanel(c: Canvas, x: Float, y: Float, w: Float, h: Float, focus: Float, accent: Int) {
        val r = h * 0.30f
        Draw.roundRect(c, x, y, w, h, r, Palette.lerp(Palette.panel, Palette.panelHi, focus))
        Draw.roundRectStroke(c, x, y, w, h, r, Palette.alpha(accent, 0.18f + 0.32f * focus), h * 0.035f)
    }

    fun pill(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, label: String, accent: Int,
             filled: Boolean = true, alpha: Float = 1f) {
        val x = cx - w / 2f; val y = cy - h / 2f; val r = h / 2f
        // Keep the label inside the pill (rounded ends eat width, so reserve ~1 radius each side).
        val maxW = w - h
        if (filled) {
            Draw.roundRect(c, x, y, w, h, r, Palette.alpha(accent, alpha))
            Draw.textCenteredFit(c, label, cx, cy, h * 0.42f, maxW, Palette.bg, Fonts.bodyBold, alpha, 0.04f)
        } else {
            Draw.roundRectStroke(c, x, y, w, h, r, Palette.alpha(accent, alpha), h * 0.07f)
            Draw.textCenteredFit(c, label, cx, cy, h * 0.42f, maxW, Palette.alpha(accent, alpha), Fonts.bodyBold, 1f, 0.04f)
        }
    }

    /**
     * [cx, cy, r] of the universal corner back button. The position is computed so the FULL
     * touch target always stays inside the visible face — on round screens the anchor is pulled
     * back toward the centre if it would otherwise spill past the clipped edge (which is what made
     * the button un-tappable / overlap other controls on some devices).
     */
    fun appBackBounds(s: ScreenInfo): FloatArray {
        val r = s.px(0.055f)
        if (!s.isRound) {
            // Square / phone: tuck into the true top-left corner, inside the safe inset.
            val x = s.safeInset + r * 1.2f
            val y = s.safeInset + r * 1.2f
            return floatArrayOf(x, y, r)
        }
        var x = s.cx - s.px(0.245f)
        var y = s.h * 0.185f
        // Keep the whole touch target (1.45r) plus a hair of margin within the round face.
        val maxDist = s.r - (r * 1.5f + s.dp(2f))
        val d = Mathx.dist(x, y, s.cx, s.cy)
        if (d > maxDist && d > 0f) {
            val k = maxDist / d
            x = s.cx + (x - s.cx) * k
            y = s.cy + (y - s.cy) * k
        }
        return floatArrayOf(x, y, r)
    }

    fun appBackButton(c: Canvas, s: ScreenInfo, t: Float, accent: Int = Palette.mint) {
        val b = appBackBounds(s)
        val pulse = 0.72f + 0.18f * Ease.pulse(t)
        Draw.fillCircle(c, b[0], b[1], b[2], Palette.alpha(Palette.panelHi, 0.92f))
        Draw.strokeCircle(c, b[0], b[1], b[2], Palette.alpha(accent, pulse), s.dp(1.4f))
        val r = b[2] * 0.42f
        Draw.line(c, b[0] + r * 0.45f, b[1] - r, b[0] - r * 0.45f, b[1], Palette.text, s.dp(2.6f))
        Draw.line(c, b[0] - r * 0.45f, b[1], b[0] + r * 0.45f, b[1] + r, Palette.text, s.dp(2.6f))
    }

    fun hitAppBack(s: ScreenInfo, x: Float, y: Float): Boolean {
        val b = appBackBounds(s)
        return Mathx.dist(x, y, b[0], b[1]) <= b[2] * 1.4f
    }

    fun chip(c: Canvas, cx: Float, cy: Float, label: String, accent: Int, sizeFrac: Float, s: ScreenInfo) {
        val ts = s.px(sizeFrac)
        val pad = ts * 0.7f
        val w = Draw.measure(label, ts, Fonts.bodyBold) + pad * 2
        val h = ts * 1.7f
        Draw.roundRectCentered(c, cx, cy, w, h, h / 2f, Palette.alpha(accent, 0.16f))
        Draw.textCentered(c, label, cx, cy, ts, accent, Fonts.bodyBold)
    }

    /**
     * Timer / progress indicator. On a ROUND screen it's the signature ring hugging the watch face;
     * on a phone/square screen a full-circle edge ring looks wrong (it floats mid-screen), so we
     * draw a clean horizontal bar near the top instead. Pass the already-resolved [color].
     */
    fun edgeProgress(c: Canvas, s: ScreenInfo, frac: Float, color: Int, thickness: Float = 0f) {
        val f = frac.coerceIn(0f, 1f)
        if (s.isRound) {
            Draw.arc(c, s.cx, s.cy, s.r * 0.95f, -90f, 360f * f, color, if (thickness > 0f) thickness else s.dp(5f))
        } else {
            val barW = s.w * 0.64f
            val x = s.cx - barW / 2f
            val y = s.safeInset + s.dp(4f)
            val th = if (thickness > 0f) thickness else s.dp(6f)
            Draw.roundRect(c, x, y, barW, th, th / 2f, Palette.alpha(Palette.track, 0.9f))
            Draw.roundRect(c, x, y, barW * f, th, th / 2f, color)
        }
    }

    fun starCount(c: Canvas, s: ScreenInfo, orbs: Int) {
        val y = s.h * 0.86f
        Draw.glow(c, s.cx - s.px(0.07f), y, s.px(0.04f), Palette.gold, 0.5f)
        Draw.fillCircle(c, s.cx - s.px(0.07f), y, s.px(0.018f), Palette.gold)
        Draw.text(c, "$orbs", s.cx - s.px(0.04f), y + s.px(0.018f), s.px(0.045f), Palette.text,
            Paint.Align.LEFT, Fonts.mono)
    }
}
