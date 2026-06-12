package com.cayadev.orbo.games

import android.graphics.Canvas
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.IconDrawer
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Mathx
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame
import kotlin.math.sqrt

/** Dodge — slide your ship to weave through a rain of falling shards. Survive, score, repeat. */
class DodgeGame : ArcadeGame(meta()) {

    private class Rock(var x: Float, var y: Float, var r: Float, var vy: Float, var spin: Float, var rot: Float, var passed: Boolean = false)

    private var px = 0f
    private var targetX = 0f
    private val py get() = sc.h * 0.80f
    private val pr get() = sc.px(0.05f)
    private val rocks = ArrayList<Rock>()
    private var spawnT = 0f
    private var spawnEvery = 0.85f
    private var fall = 0f

    override val controlHint = "DRAG TO MOVE"

    override fun onReset() {
        px = sc.cx; targetX = sc.cx; rocks.clear(); spawnT = 0f
        spawnEvery = 0.85f; fall = sc.px(0.7f)
    }

    private fun halfWidthAt(y: Float): Float {
        if (!sc.isRound) return sc.w / 2f - sc.px(0.02f)
        val dy = y - sc.cy
        val v = sc.r * sc.r - dy * dy
        return if (v <= 0) 0f else sqrt(v) - sc.px(0.02f)
    }

    override fun onPress(x: Float, y: Float) { targetX = x }
    override fun onDrag(x: Float, y: Float) { targetX = x }

    override fun onStep(dt: Float) {
        val diff = (score / 40f).coerceIn(0f, 1f)
        spawnEvery = Mathx.lerp(0.85f, 0.34f, diff)
        fall = Mathx.lerp(sc.px(0.7f), sc.px(1.35f), diff)

        val hw = halfWidthAt(py)
        targetX = targetX.coerceIn(sc.cx - hw, sc.cx + hw)
        px = Mathx.approach(px, targetX, 16f, dt)

        spawnT += dt
        if (spawnT >= spawnEvery) {
            spawnT = 0f
            val topY = sc.h * 0.06f
            val hwTop = halfWidthAt(topY + sc.px(0.05f))
            val rx = sc.cx + Mathx.rand(-hwTop, hwTop)
            val rr = sc.px(Mathx.rand(0.04f, 0.075f))
            rocks.add(Rock(rx, topY, rr, fall * Mathx.rand(0.85f, 1.2f), Mathx.rand(-3f, 3f), 0f))
        }

        val it = rocks.iterator()
        while (it.hasNext()) {
            val r = it.next()
            r.y += r.vy * dt; r.rot += r.spin * dt
            if (!r.passed && r.y > py + pr) { r.passed = true; addScore(1); engine.sfx.blip() }
            if (r.y - r.r > sc.h + sc.px(0.1f)) { it.remove(); continue }
            val d = Mathx.dist(px, py, r.x, r.y)
            if (d < pr * 0.78f + r.r * 0.8f) { crash(r); return }
        }
    }

    private fun crash(r: Rock) {
        shake(sc.px(0.03f)); flash(Palette.red, 0.45f)
        particles.burst(px, py, 26, accent, sc.px(0.9f), sc.px(0.022f), 0.7f, gravity = sc.px(1.2f))
        engine.sfx.explosion(); engine.haptics.heavy()
        gameOver()
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, 0xFF0B0F1C.toInt(), Palette.bg)
        Draw.withFaceClip(c, s) {
            for (r in rocks) drawRock(c, r)
            if (state != GState.READY) {
                Draw.glow(c, px, py, pr * 2.2f, accent, 0.5f)
                val save = c.save()
                c.translate(px, py)
                // ship triangle
                val p = Draw.paint
                p.reset(); p.isAntiAlias = true; p.color = accent
                val path = android.graphics.Path()
                path.moveTo(0f, -pr); path.lineTo(pr * 0.85f, pr * 0.8f); path.lineTo(0f, pr * 0.45f); path.lineTo(-pr * 0.85f, pr * 0.8f); path.close()
                c.drawPath(path, p)
                Draw.fillCircle(c, 0f, -pr * 0.1f, pr * 0.3f, Palette.shade(accent, 0.6f))
                c.restoreToCount(save)
            }
        }
    }

    private fun drawRock(c: Canvas, r: Rock) {
        Draw.glow(c, r.x, r.y, r.r * 1.7f, Palette.red, 0.25f)
        val save = c.save()
        c.rotate(Math.toDegrees(r.rot.toDouble()).toFloat(), r.x, r.y)
        Draw.roundRectCentered(c, r.x, r.y, r.r * 1.5f, r.r * 1.5f, r.r * 0.3f, Palette.lerp(Palette.red, Palette.orange, 0.3f))
        Draw.roundRectCentered(c, r.x, r.y, r.r * 0.8f, r.r * 0.8f, r.r * 0.2f, Palette.shade(Palette.red, -0.25f))
        c.restoreToCount(save)
    }

    companion object {
        fun meta() = GameMeta(
            id = "dodge",
            title = "Dodge",
            tagline = "Weave the shards",
            category = Category.ARCADE,
            tier = Tier.MINI,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                val p = Draw.paint; p.reset(); p.isAntiAlias = true; p.color = color
                val path = android.graphics.Path()
                path.moveTo(cx, cy - size * 0.3f); path.lineTo(cx + size * 0.26f, cy + size * 0.24f)
                path.lineTo(cx, cy + size * 0.1f); path.lineTo(cx - size * 0.26f, cy + size * 0.24f); path.close()
                c.drawPath(path, p)
            },
            create = { DodgeGame() }
        )
    }
}
