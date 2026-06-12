package com.cayadev.orbo.games

import android.graphics.Canvas
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.IconDrawer
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Mathx
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame
import kotlin.math.sqrt

/**
 * Slice — fruit-ninja for the wrist. Objects arc up from the bottom; swipe through the fruit to
 * slice them (chain slices in one swipe for combos), but never cut a bomb. Let three fruit fall
 * uncut and it's over.
 */
class FruitSlashGame : ArcadeGame(meta()) {

    private class Obj(var x: Float, var y: Float, var vx: Float, var vy: Float,
                      val hue: Int, val bomb: Boolean) { var sliced = false; var rot = 0f }

    private val objs = ArrayList<Obj>()
    private val fruitHues = intArrayOf(Palette.red, Palette.orange, Palette.amber, Palette.lime, Palette.mint, Palette.pink)
    private val trail = ArrayList<FloatArray>()       // [x, y, life]
    private var lastX = 0f
    private var lastY = 0f
    private var tracking = false
    private var spawnT = 0f
    private var lives = 3
    private var combo = 0
    private var comboT = 0f

    override val controlHint = "SWIPE TO SLICE"

    private val grav get() = sc.px(2.3f)
    private val objR get() = sc.px(0.062f)

    override fun onReset() {
        objs.clear(); trail.clear(); spawnT = 0.4f; lives = 3; combo = 0; comboT = 0f; tracking = false
    }

    override fun onPress(x: Float, y: Float) { tracking = true; lastX = x; lastY = y; addTrail(x, y) }
    override fun onDrag(x: Float, y: Float) {
        if (!tracking) { lastX = x; lastY = y; tracking = true }
        sliceSegment(lastX, lastY, x, y)
        addTrail(x, y); lastX = x; lastY = y
    }
    override fun onRelease(x: Float, y: Float) { tracking = false }

    private fun addTrail(x: Float, y: Float) {
        trail.add(floatArrayOf(x, y, 0.22f))
        if (trail.size > 14) trail.removeAt(0)
    }

    private fun sliceSegment(ax: Float, ay: Float, bx: Float, by: Float) {
        val seg = Mathx.dist(ax, ay, bx, by)
        if (seg < sc.px(0.01f)) return                 // ignore taps; slicing needs motion
        for (o in objs) {
            if (o.sliced) continue
            if (segDist(o.x, o.y, ax, ay, bx, by) <= objR) slice(o)
        }
    }

    private fun slice(o: Obj) {
        o.sliced = true
        if (o.bomb) {
            lives--; engine.sfx.explosion(); engine.haptics.heavy(); flash(Palette.red, 0.45f); shake(sc.px(0.03f))
            particles.burst(o.x, o.y, 26, Palette.red, sc.px(0.9f), sc.px(0.022f), 0.7f)
            if (lives <= 0) gameOver()
        } else {
            combo++; comboT = 0.6f
            addScore(8 + combo * 2)
            engine.sfx.blip(); engine.haptics.light()
            particles.burst(o.x, o.y, 18, o.hue, sc.px(0.8f), sc.px(0.02f), 0.6f)
            o.vy -= sc.px(0.3f); o.vx *= 1.4f          // halves "pop" apart
        }
    }

    override fun onStep(dt: Float) {
        if (comboT > 0f) { comboT -= dt; if (comboT <= 0f) combo = 0 }
        // trail aging
        run {
            val it = trail.iterator()
            while (it.hasNext()) { val p = it.next(); p[2] -= dt; if (p[2] <= 0f) it.remove() }
        }
        // spawning
        spawnT -= dt
        if (spawnT <= 0f) {
            val diff = (score / 500f).coerceIn(0f, 1f)
            spawnT = Mathx.lerp(1.15f, 0.5f, diff)
            val count = if (Mathx.chance(0.35f + diff * 0.3f)) 2 else 1
            for (k in 0 until count) launch(diff)
        }
        // physics
        val it = objs.iterator()
        while (it.hasNext()) {
            val o = it.next()
            o.vy += grav * dt
            o.x += o.vx * dt; o.y += o.vy * dt; o.rot += dt * 3f
            if (o.y > sc.h + sc.px(0.12f)) {
                if (!o.sliced && !o.bomb) {            // a fruit fell uncut
                    lives--; engine.sfx.hit(); engine.haptics.medium(); flash(Palette.red, 0.25f)
                    if (lives <= 0) { it.remove(); gameOver(); continue }
                }
                it.remove()
            }
        }
    }

    private fun launch(diff: Float) {
        val x0 = Mathx.rand(sc.px(0.22f), sc.w - sc.px(0.22f))
        val vy0 = -sc.px(Mathx.rand(1.55f, 1.85f))
        val vx0 = (sc.cx - x0) * Mathx.rand(0.4f, 0.9f) / 1.4f + sc.px(Mathx.rand(-0.1f, 0.1f))
        val bomb = Mathx.chance(0.15f + diff * 0.1f)
        objs.add(Obj(x0, sc.h * 1.02f, vx0, vy0, fruitHues[Mathx.rand(fruitHues.size)], bomb))
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.vGradientRect(c, 0f, 0f, s.w, s.h, 0xFF160A06.toInt(), Palette.bg)
        Draw.vignette(c, s, Palette.alpha(Palette.bg, 0f), Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            for (o in objs) drawObj(c, o)
            // slice trail
            for (i in 1 until trail.size) {
                val a = trail[i - 1]; val b = trail[i]
                val al = (b[2] / 0.22f).coerceIn(0f, 1f)
                Draw.line(c, a[0], a[1], b[0], b[1], Palette.alpha(Palette.text, 0.7f * al), s.px(0.02f) * al + s.dp(1f))
            }
        }
    }

    private fun drawObj(c: Canvas, o: Obj) {
        val r = objR
        if (o.bomb) {
            Draw.glow(c, o.x, o.y, r * 1.6f, Palette.red, 0.25f)
            Draw.fillCircle(c, o.x, o.y, r, Palette.shade(Palette.red, -0.35f))
            Draw.strokeCircle(c, o.x, o.y, r, Palette.red, sc.dp(2.5f))
            Draw.line(c, o.x, o.y - r, o.x + r * 0.4f, o.y - r * 1.6f, Palette.amber, sc.dp(2.5f))
            Draw.fillCircle(c, o.x + r * 0.4f, o.y - r * 1.6f, sc.px(0.012f), Palette.gold)
        } else {
            val alpha = if (o.sliced) 0.35f else 1f
            Draw.glow(c, o.x, o.y, r * 1.5f, o.hue, 0.3f * alpha)
            Draw.fillCircle(c, o.x, o.y, r, Palette.alpha(o.hue, alpha))
            Draw.fillCircle(c, o.x - r * 0.3f, o.y - r * 0.3f, r * 0.3f, Palette.alpha(Palette.text, 0.3f * alpha))
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.05f); val y = s.h * 0.21f; val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, y, s.px(0.017f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, y, s.px(0.015f), Palette.alpha(Palette.red, 0.35f), s.dp(1.5f))
        }
        if (combo > 2) Draw.textCentered(c, "x$combo", s.cx, s.h * 0.83f, s.px(0.055f), Palette.amber, Fonts.bodyBold)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.125f, s.px(0.075f), Palette.text, Fonts.display)
    }

    private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 <= 1e-4f) return Mathx.dist(px, py, ax, ay)
        var t = ((px - ax) * dx + (py - ay) * dy) / len2
        t = t.coerceIn(0f, 1f)
        val cx = ax + t * dx; val cy = ay + t * dy
        return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
    }

    companion object {
        fun meta() = GameMeta(
            id = "fruit_slash",
            title = "Slice",
            tagline = "Swipe to slice",
            category = Category.ACTION,
            tier = Tier.BIG,
            accent = Palette.lime,
            featured = true,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.fillCircle(c, cx - size * 0.15f, cy + size * 0.12f, size * 0.22f, Palette.red)
                Draw.fillCircle(c, cx + size * 0.2f, cy - size * 0.05f, size * 0.16f, Palette.amber)
                Draw.line(c, cx - size * 0.4f, cy - size * 0.35f, cx + size * 0.4f, cy + size * 0.35f, color, size * 0.07f)
            },
            create = { FruitSlashGame() }
        )
    }
}
