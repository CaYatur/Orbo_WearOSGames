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

/**
 * Wing It — a polished tap-to-flap flyer. Glide through neon gates, ride the momentum, and
 * keep your nerve as the gaps tighten. Smooth physics, particle trail, screen juice.
 */
class WingItGame : ArcadeGame(meta()) {

    private class Gate(var x: Float, var gapY: Float, var passed: Boolean = false)

    private var birdY = 0f
    private var vel = 0f
    private var rot = 0f
    private val grav get() = sc.px(2.6f)
    private val jump get() = -sc.px(0.92f)
    private val birdX get() = sc.w * 0.32f
    private val birdR get() = sc.px(0.052f)
    private val gateW get() = sc.px(0.15f)
    private var gapH = 0f
    private var speed = 0f
    private val gates = ArrayList<Gate>()
    private var spawnX = 0f
    private var trailT = 0f
    private val topY get() = sc.h * 0.04f
    private val botY get() = sc.h * 0.96f

    override val controlHint = "TAP TO FLAP"

    override fun onReset() {
        birdY = sc.cy; vel = 0f; rot = 0f
        gapH = sc.px(0.36f); speed = sc.px(0.46f)
        gates.clear()
        spawnX = sc.w + gateW
        repeat(3) { spawnGate(sc.w + sc.w * 0.55f * it + sc.w * 0.6f) }
    }

    private fun spawnGate(x: Float) {
        val margin = topY + gapH / 2 + sc.px(0.04f)
        val gy = Mathx.rand(margin, botY - gapH / 2 - sc.px(0.04f))
        gates.add(Gate(x, gy))
    }

    override fun onTapWorld(x: Float, y: Float) {
        vel = jump
        engine.sfx.jump(); engine.haptics.light()
        particles.burst(birdX - birdR, birdY + birdR * 0.5f, 6, accent, sc.px(0.3f), sc.px(0.014f), 0.35f)
    }

    override fun onStep(dt: Float) {
        vel += grav * dt
        birdY += vel * dt
        rot = Mathx.clamp(vel / sc.px(1.4f), -0.5f, 1.1f)

        // difficulty ramp
        gapH = Mathx.lerp(sc.px(0.36f), sc.px(0.26f), (score / 30f).coerceIn(0f, 1f))
        speed = Mathx.lerp(sc.px(0.46f), sc.px(0.72f), (score / 40f).coerceIn(0f, 1f))

        // trail
        trailT += dt
        if (trailT > 0.02f) { trailT = 0f; particles.trail(birdX - birdR * 0.6f, birdY, accent, birdR * 0.5f, 0.3f) }

        // move gates
        var furthest = 0f
        for (g in gates) { g.x -= speed * dt; if (g.x > furthest) furthest = g.x }
        // recycle
        val it = gates.iterator()
        while (it.hasNext()) { if (it.next().x < -gateW) it.remove() }
        while (gates.size < 3) { spawnGate(furthest + sc.w * 0.55f); furthest += sc.w * 0.55f }

        // scoring + collision
        for (g in gates) {
            if (!g.passed && g.x + gateW / 2 < birdX) {
                g.passed = true; addScore(1); engine.sfx.coin(); engine.haptics.light()
                particles.burst(birdX, birdY, 10, Palette.gold, sc.px(0.4f), sc.px(0.014f), 0.4f)
            }
            // collision with pipe
            val withinX = birdX + birdR > g.x - gateW / 2 && birdX - birdR < g.x + gateW / 2
            if (withinX) {
                val gapTop = g.gapY - gapH / 2
                val gapBot = g.gapY + gapH / 2
                if (birdY - birdR < gapTop || birdY + birdR > gapBot) { crash(); return }
            }
        }
        // bounds
        if (birdY + birdR > botY || birdY - birdR < topY) { crash(); return }
    }

    private fun crash() {
        shake(sc.px(0.03f)); flash(Palette.red, 0.45f)
        particles.burst(birdX, birdY, 26, accent, sc.px(0.9f), sc.px(0.022f), 0.7f, gravity = sc.px(1.5f))
        engine.sfx.explosion()
        gameOver()
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        // sky gradient
        Draw.vGradientRect(c, 0f, 0f, s.w, s.h, 0xFF0A1326.toInt(), Palette.bg)
        Draw.vignette(c, s, Palette.alpha(Palette.bg, 0f), Palette.bg)

        Draw.withFaceClip(c, s) {
            // gates
            for (g in gates) {
                val gapTop = g.gapY - gapH / 2
                val gapBot = g.gapY + gapH / 2
                val x = g.x - gateW / 2
                drawPipe(c, s, x, topY, gateW, gapTop - topY, true)
                drawPipe(c, s, x, gapBot, gateW, botY - gapBot, false)
            }

            if (state != GState.READY) {
                // bird
                val bx = birdX; val by = birdY
                Draw.glow(c, bx, by, birdR * 2.2f, accent, 0.5f)
                val save = c.save()
                c.rotate(rot * 28f, bx, by)
                Draw.fillCircle(c, bx, by, birdR, accent)
                Draw.fillCircle(c, bx, by, birdR * 0.62f, Palette.shade(accent, 0.4f))
                // eye
                Draw.fillCircle(c, bx + birdR * 0.35f, by - birdR * 0.25f, birdR * 0.18f, Palette.bg)
                // wing
                Draw.roundRectCentered(c, bx - birdR * 0.4f, by + birdR * 0.1f, birdR * 0.8f, birdR * 0.45f, birdR * 0.2f, Palette.shade(accent, -0.2f))
                c.restoreToCount(save)
            }
        }
    }

    private fun drawPipe(c: Canvas, s: ScreenInfo, x: Float, y: Float, w: Float, h: Float, top: Boolean) {
        if (h <= 0) return
        val r = w * 0.28f
        Draw.roundRect(c, x, y, w, h, r, Palette.lerp(Palette.mint, 0xFF12B07F.toInt(), 0.4f))
        Draw.roundRectStroke(c, x, y, w, h, r, Palette.alpha(Palette.text, 0.12f), s.dp(1.5f))
        // lip
        val lipH = w * 0.5f
        val ly = if (top) y + h - lipH else y
        Draw.roundRect(c, x - w * 0.08f, ly, w * 1.16f, lipH, r, Palette.lerp(Palette.mint, Palette.text, 0.15f))
    }

    companion object {
        fun meta() = GameMeta(
            id = "wing_it",
            title = "Wing It",
            tagline = "Flap through gates",
            category = Category.ARCADE,
            tier = Tier.MINI,
            accent = Palette.amber,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.fillCircle(c, cx, cy, size * 0.26f, color)
                Draw.fillCircle(c, cx + size * 0.1f, cy - size * 0.08f, size * 0.05f, Palette.bg)
                Draw.roundRectCentered(c, cx - size * 0.18f, cy + size * 0.04f, size * 0.22f, size * 0.13f, size * 0.06f, Palette.shade(color, -0.2f))
            },
            create = { WingItGame() }
        )
    }
}
