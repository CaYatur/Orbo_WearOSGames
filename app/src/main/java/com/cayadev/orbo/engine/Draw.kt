package com.cayadev.orbo.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Stateless drawing helpers. All rendering happens on the UI thread, so a single shared
 * Paint/Path/RectF is reused to avoid per-frame allocations (important for smooth watch fps).
 */
object Draw {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()
    private val path = Path()

    fun reset() {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.alpha = 255
        paint.strokeCap = Paint.Cap.ROUND
    }

    fun background(c: Canvas, color: Int) = c.drawColor(color)

    /** Radial vignette that frames the round face nicely and adds depth. */
    fun vignette(c: Canvas, s: ScreenInfo, inner: Int, outer: Int) {
        reset()
        paint.shader = RadialGradient(
            s.cx, s.cy, s.r * 1.18f,
            intArrayOf(inner, inner, outer),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, s.w, s.h, paint)
        paint.shader = null
    }

    fun fillCircle(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        reset(); paint.color = color
        c.drawCircle(x, y, radius, paint)
    }

    fun strokeCircle(c: Canvas, x: Float, y: Float, radius: Float, color: Int, width: Float) {
        reset(); paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.color = color
        c.drawCircle(x, y, radius, paint)
    }

    /** Soft glow disc (additive-looking) — great for neon dots, sparks, highlights. */
    fun glow(c: Canvas, x: Float, y: Float, radius: Float, color: Int, intensity: Float = 0.9f) {
        reset()
        paint.shader = RadialGradient(
            x, y, radius,
            intArrayOf(Palette.alpha(color, intensity), Palette.alpha(color, intensity * 0.5f), Palette.alpha(color, 0f)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, radius, paint)
        paint.shader = null
    }

    fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        reset(); paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.color = color
        c.drawLine(x1, y1, x2, y2, paint)
    }

    fun roundRect(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) {
        reset(); paint.color = color
        rf.set(x, y, x + w, y + h)
        c.drawRoundRect(rf, r, r, paint)
    }

    fun roundRectStroke(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Int, width: Float) {
        reset(); paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.color = color
        rf.set(x, y, x + w, y + h)
        c.drawRoundRect(rf, r, r, paint)
    }

    fun roundRectCentered(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, r: Float, color: Int) =
        roundRect(c, cx - w / 2f, cy - h / 2f, w, h, r, color)

    fun arc(c: Canvas, cx: Float, cy: Float, radius: Float, startDeg: Float, sweepDeg: Float, color: Int, width: Float, cap: Paint.Cap = Paint.Cap.ROUND) {
        reset(); paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.color = color; paint.strokeCap = cap
        rf.set(cx - radius, cy - radius, cx + radius, cy + radius)
        c.drawArc(rf, startDeg, sweepDeg, false, paint)
    }

    fun text(
        c: Canvas, str: String, x: Float, y: Float, size: Float, color: Int,
        align: Paint.Align = Paint.Align.CENTER, font: Typeface = Fonts.bodyBold,
        alpha: Float = 1f, letterSpacing: Float = 0f
    ) {
        reset()
        paint.color = color; paint.textSize = size; paint.textAlign = align
        paint.typeface = font; paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        paint.letterSpacing = letterSpacing
        c.drawText(str, x, y, paint)
    }

    /** Draw text vertically centered on `cy` (baseline-corrected). */
    fun textCentered(
        c: Canvas, str: String, cx: Float, cy: Float, size: Float, color: Int,
        font: Typeface = Fonts.bodyBold, alpha: Float = 1f, letterSpacing: Float = 0f
    ) {
        reset()
        paint.color = color; paint.textSize = size; paint.textAlign = Paint.Align.CENTER
        paint.typeface = font; paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        paint.letterSpacing = letterSpacing
        val fm = paint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        c.drawText(str, cx, baseline, paint)
    }

    fun measure(str: String, size: Float, font: Typeface = Fonts.bodyBold, letterSpacing: Float = 0f): Float {
        reset(); paint.textSize = size; paint.typeface = font; paint.letterSpacing = letterSpacing
        return paint.measureText(str)
    }

    /** Largest size ≤ [size] whose rendered width fits [maxWidth] (text width scales ~linearly). */
    fun fitSize(str: String, size: Float, maxWidth: Float, font: Typeface = Fonts.bodyBold, letterSpacing: Float = 0f): Float {
        if (str.isEmpty() || maxWidth <= 0f) return size
        val w = measure(str, size, font, letterSpacing)
        return if (w <= maxWidth) size else size * (maxWidth / w)
    }

    /** Centered text that auto-shrinks to stay within [maxWidth] (e.g. labels inside buttons/pills). */
    fun textCenteredFit(
        c: Canvas, str: String, cx: Float, cy: Float, size: Float, maxWidth: Float, color: Int,
        font: Typeface = Fonts.bodyBold, alpha: Float = 1f, letterSpacing: Float = 0f
    ) = textCentered(c, str, cx, cy, fitSize(str, size, maxWidth, font, letterSpacing), color, font, alpha, letterSpacing)

    /** Vertical linear gradient fill of a rect via simple two-stop interpolation. */
    fun vGradientRect(c: Canvas, x: Float, y: Float, w: Float, h: Float, top: Int, bottom: Int) {
        reset()
        paint.shader = android.graphics.LinearGradient(0f, y, 0f, y + h, top, bottom, Shader.TileMode.CLAMP)
        rf.set(x, y, x + w, y + h)
        c.drawRect(rf, paint)
        paint.shader = null
    }

    /** Begin a clip to the round face so nothing draws into the clipped corners. */
    fun withFaceClip(c: Canvas, s: ScreenInfo, block: () -> Unit) {
        if (!s.isRound) { block(); return }
        val save = c.save()
        path.rewind()
        path.addCircle(s.cx, s.cy, s.r, Path.Direction.CW)
        c.clipPath(path)
        block()
        c.restoreToCount(save)
    }
}
