package com.cayadev.orbo.engine

import kotlin.math.abs
import kotlin.math.max

/**
 * Reusable vertical list tuned for round watch screens: momentum flinging, rotary support,
 * soft overscroll, and a "center focus" value so items can grow/brighten toward the middle
 * (the signature Wear OS curved-list feel). The owning scene draws each row itself.
 */
class WatchList {
    var count = 0
    var itemH = 0f
    var gap = 0f
    var topPad = 0f
    var bottomPad = 0f
    var curve = true

    var scroll = 0f
        private set
    private var vel = 0f
    private var dragging = false
    private var lastY = 0f
    private var overshoot = 0f

    fun configure(count: Int, itemH: Float, gap: Float, topPad: Float, bottomPad: Float) {
        this.count = count; this.itemH = itemH; this.gap = gap
        this.topPad = topPad; this.bottomPad = bottomPad
    }

    private fun contentH() = topPad + count * itemH + (count - 1).coerceAtLeast(0) * gap + bottomPad
    private fun maxScroll(s: ScreenInfo) = max(0f, contentH() - s.h)

    fun jumpTo(v: Float) { scroll = v; vel = 0f }
    fun ensureVisible(i: Int, s: ScreenInfo) {
        val top = topPad + i * (itemH + gap)
        val bottom = top + itemH
        if (top - scroll < s.h * 0.18f) scroll = top - s.h * 0.18f
        if (bottom - scroll > s.h * 0.82f) scroll = bottom - s.h * 0.82f
        scroll = scroll.coerceIn(0f, maxScroll(s))
    }

    fun onDown(@Suppress("UNUSED_PARAMETER") x: Float, y: Float) { dragging = true; lastY = y; vel = 0f }
    fun onMove(@Suppress("UNUSED_PARAMETER") x: Float, y: Float, s: ScreenInfo) {
        if (!dragging) return
        val dy = y - lastY; lastY = y
        var ns = scroll - dy
        val lo = 0f; val hi = maxScroll(s)
        if (ns < lo) ns = lo + (ns - lo) * 0.35f
        if (ns > hi) ns = hi + (ns - hi) * 0.35f
        scroll = ns
    }
    fun onUp() { dragging = false }
    fun fling(vy: Float) { dragging = false; vel = (-vy).coerceIn(-6000f, 6000f) }
    fun rotary(deltaPx: Float, s: ScreenInfo) {
        scroll = (scroll + deltaPx).coerceIn(0f, maxScroll(s)); vel = 0f
    }

    fun update(dt: Float, s: ScreenInfo) {
        val lo = 0f; val hi = maxScroll(s)
        if (!dragging) {
            if (abs(vel) > 1f) {
                scroll += vel * dt
                vel *= Math.exp((-3.2f * dt).toDouble()).toFloat()
                if (abs(vel) < 8f) vel = 0f
            }
            // spring back from overscroll
            if (scroll < lo) { scroll = Mathx.approach(scroll, lo, 14f, dt); if (abs(scroll - lo) < 0.5f) scroll = lo; vel = 0f }
            else if (scroll > hi) { scroll = Mathx.approach(scroll, hi, 14f, dt); if (abs(scroll - hi) < 0.5f) scroll = hi; vel = 0f }
        }
    }

    /** Y of the row's top edge in screen space. */
    fun itemTop(i: Int) = topPad + i * (itemH + gap) - scroll
    fun itemCenterY(i: Int) = itemTop(i) + itemH / 2f

    /** 1 at vertical center of screen, fading toward edges (for curved scaling). */
    fun focus(i: Int, s: ScreenInfo): Float {
        if (!curve) return 1f
        val d = abs(itemCenterY(i) - s.cy) / (s.h * 0.5f)
        return (1f - d * 0.5f).coerceIn(0.55f, 1f)
    }

    fun firstVisible(): Int = ((scroll - topPad) / (itemH + gap)).toInt().coerceAtLeast(0)
    fun lastVisible(s: ScreenInfo): Int =
        (((scroll + s.h) - topPad) / (itemH + gap)).toInt().coerceIn(0, count - 1)

    /** Returns the row index hit at (x,y) or -1. */
    fun hit(@Suppress("UNUSED_PARAMETER") x: Float, y: Float): Int {
        for (i in 0 until count) {
            val top = itemTop(i)
            if (y >= top && y <= top + itemH) return i
        }
        return -1
    }
}
