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

/** Sky Hop — bounce ever upward across drifting platforms. Slide to steer, hit springs for big
 * air, and climb as high as you dare before you fall. */
class SkyHopGame : ArcadeGame(meta()) {

    private enum class PType { NORMAL, MOVING, SPRING }
    private class Plat(var x: Float, var y: Float, var w: Float, var type: PType, var dir: Float, var phase: Float)

    private val plats = ArrayList<Plat>()
    private var px = 0f
    private var py = 0f
    private var prevPy = 0f
    private var pvy = 0f
    private var targetX = 0f
    private var camY = 0f
    private var startPy = 0f
    private var minPy = 0f
    private var highestY = 0f
    private var trailT = 0f

    private val grav get() = sc.px(2.7f)
    private val jumpV get() = -sc.px(1.18f)
    private val springV get() = -sc.px(1.95f)
    private val pr get() = sc.px(0.05f)
    private val gapY get() = sc.px(0.17f)

    override val controlHint = "SLIDE TO STEER"

    override fun onReset() {
        plats.clear()
        camY = 0f
        val baseY = sc.h * 0.82f
        plats.add(Plat(sc.cx, baseY, sc.px(0.34f), PType.NORMAL, 1f, 0f))
        highestY = baseY
        repeat(9) { addPlatformAbove() }
        px = sc.cx; targetX = sc.cx
        py = baseY - pr; prevPy = py; pvy = jumpV
        startPy = py; minPy = py
        trailT = 0f; scoreShadow = 0
    }

    private fun addPlatformAbove() {
        highestY -= Mathx.rand(gapY * 0.8f, gapY * 1.25f)
        val w = sc.px(Mathx.rand(0.22f, 0.34f))
        val hw = if (sc.isRound) sc.r * 0.82f else sc.w / 2 - sc.px(0.04f)
        val x = sc.cx + Mathx.rand(-hw + w / 2, hw - w / 2)
        val climb = (startPy - highestY) / sc.h
        val type = when {
            climb > 1.5f && Mathx.chance(0.18f) -> PType.MOVING
            Mathx.chance(0.12f) -> PType.SPRING
            else -> PType.NORMAL
        }
        plats.add(Plat(x, highestY, w, type, Mathx.randSign(), Mathx.rand(0f, Mathx.TAU)))
    }

    override fun onPress(x: Float, y: Float) { targetX = x }
    override fun onDrag(x: Float, y: Float) { targetX = x }

    override fun onStep(dt: Float) {
        // horizontal steering
        val hw = if (sc.isRound) sc.r * 0.92f else sc.w / 2 - sc.px(0.02f)
        targetX = targetX.coerceIn(sc.cx - hw + pr, sc.cx + hw - pr)
        px = Mathx.approach(px, targetX, 22f, dt)

        // vertical physics
        prevPy = py
        pvy += grav * dt
        py += pvy * dt

        // moving platforms
        for (p in plats) if (p.type == PType.MOVING) {
            p.phase += dt
            p.x += p.dir * sc.px(0.25f) * dt
            val lim = if (sc.isRound) sc.r * 0.8f else sc.w / 2 - sc.px(0.04f)
            if (p.x < sc.cx - lim + p.w / 2) { p.x = sc.cx - lim + p.w / 2; p.dir = 1f }
            if (p.x > sc.cx + lim - p.w / 2) { p.x = sc.cx + lim - p.w / 2; p.dir = -1f }
        }

        // landing (only while falling)
        if (pvy > 0) {
            for (p in plats) {
                if (prevPy + pr <= p.y && py + pr >= p.y &&
                    px > p.x - p.w / 2 - pr * 0.4f && px < p.x + p.w / 2 + pr * 0.4f
                ) {
                    bounce(p); break
                }
            }
        }

        // camera follows upward
        val followLine = camY + sc.h * 0.42f
        if (py < followLine) camY = py - sc.h * 0.42f
        if (py < minPy) {
            minPy = py
            val h = ((startPy - minPy) / gapY).toInt()
            if (h > score) setScoreTo(h)
        }

        // recycle + spawn
        val it = plats.iterator()
        while (it.hasNext()) { if (it.next().y > camY + sc.h + pr * 2) it.remove() }
        while (highestY > camY - sc.h * 0.3f) addPlatformAbove()

        // trail (particles render in screen space, so emit at the player's SCREEN y)
        trailT += dt
        if (trailT > 0.03f) { trailT = 0f; particles.trail(px, (py - camY) + pr, accent, pr * 0.45f, 0.3f) }

        // fall death
        if (py - camY > sc.h + pr * 2) {
            shake(sc.px(0.025f)); engine.sfx.fail(); flash(Palette.red, 0.4f); gameOver()
        }
    }

    private var scoreShadow = 0
    private fun setScoreTo(v: Int) { addScore(v - scoreShadow); scoreShadow = v }

    private fun bounce(p: Plat) {
        pvy = if (p.type == PType.SPRING) springV else jumpV
        if (p.type == PType.SPRING) { engine.sfx.powerup(); engine.haptics.medium(); particles.burst(px, p.y - camY, 14, Palette.lime, sc.px(0.6f), sc.px(0.016f), 0.5f) }
        else { engine.sfx.jump(); engine.haptics.light() }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.vGradientRect(c, 0f, 0f, s.w, s.h, 0xFF0A0F22.toInt(), 0xFF05060A.toInt())
        Draw.vignette(c, s, Palette.alpha(Palette.bg, 0f), Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            for (p in plats) {
                val sy = p.y - camY
                if (sy < -gapY || sy > s.h + gapY) continue
                drawPlat(c, s, p, sy)
            }
            // player
            val sy = py - camY
            Draw.glow(c, px, sy, pr * 2.1f, accent, 0.5f)
            Draw.fillCircle(c, px, sy, pr, accent)
            Draw.fillCircle(c, px, sy, pr * 0.6f, Palette.shade(accent, 0.4f))
            Draw.fillCircle(c, px - pr * 0.3f, sy - pr * 0.25f, pr * 0.16f, Palette.bg)
            Draw.fillCircle(c, px + pr * 0.3f, sy - pr * 0.25f, pr * 0.16f, Palette.bg)
        }
    }

    private fun drawPlat(c: Canvas, s: ScreenInfo, p: Plat, sy: Float) {
        val h = s.px(0.035f)
        val col = when (p.type) { PType.SPRING -> Palette.lime; PType.MOVING -> Palette.cyan; else -> Palette.violet }
        Draw.roundRect(c, p.x - p.w / 2, sy, p.w, h, h * 0.5f, col)
        Draw.roundRect(c, p.x - p.w / 2, sy, p.w, h * 0.45f, h * 0.5f, Palette.alpha(Palette.text, 0.18f))
        if (p.type == PType.SPRING) {
            Draw.line(c, p.x, sy - h * 0.8f, p.x, sy, Palette.lime, s.dp(2.5f))
            Draw.fillCircle(c, p.x, sy - h * 0.9f, h * 0.3f, Palette.lime)
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.12f, s.px(0.075f), Palette.text, com.cayadev.orbo.engine.Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "sky_hop",
            title = "Sky Hop",
            tagline = "Bounce to the sky",
            category = Category.ARCADE,
            tier = Tier.MINI,
            accent = Palette.violet,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.fillCircle(c, cx, cy - size * 0.14f, size * 0.16f, color)
                Draw.roundRectCentered(c, cx, cy + size * 0.16f, size * 0.5f, size * 0.1f, size * 0.05f, Palette.lime)
            },
            create = { SkyHopGame() }
        )
    }
}
