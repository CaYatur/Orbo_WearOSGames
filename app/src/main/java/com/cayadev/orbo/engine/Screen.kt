package com.cayadev.orbo.engine

import kotlin.math.min

/**
 * Describes the current watch screen and provides the auto-scaling primitives every
 * scene/game uses so content looks correct on ANY Wear OS device — small or large,
 * round or square.
 *
 * Authoring model: design relative to the screen center using [px] (fraction of the
 * smaller dimension) and keep critical UI inside [safeInset] on round screens.
 */
class ScreenInfo(
    val w: Float,
    val h: Float,
    val density: Float,
    val isRound: Boolean
) {
    val cx = w / 2f
    val cy = h / 2f
    val minDim = min(w, h)
    /** Radius of the largest circle that fits the screen (the watch face on round devices). */
    val r = minDim / 2f

    /**
     * Inset (in px) to keep important UI away from clipped corners on round screens.
     * On round devices we reserve the gap between the circle and its inscribed square.
     */
    val safeInset: Float = if (isRound) r * (1f - 0.70710677f) + r * 0.04f else minDim * 0.05f

    /** Convert a fraction (0..1) of the smaller screen dimension to pixels. */
    fun px(frac: Float) = frac * minDim
    /** Convert dp to px. */
    fun dp(v: Float) = v * density
    /** Scaled text size: fraction of min dimension, clamped to sane bounds. */
    fun textPx(frac: Float) = (frac * minDim)

    /** True if (x,y) is inside the visible round area (always true on square screens). */
    fun insideFace(x: Float, y: Float, margin: Float = 0f): Boolean {
        if (!isRound) return x >= margin && y >= margin && x <= w - margin && y <= h - margin
        val dx = x - cx; val dy = y - cy
        val rr = r - margin
        return dx * dx + dy * dy <= rr * rr
    }

    fun sameAs(other: ScreenInfo?) =
        other != null && other.w == w && other.h == h && other.isRound == isRound
}
