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

/** Catch — steer the basket to scoop coins and gold, but never the bombs. Three lives. */
class CatchGame : ArcadeGame(meta()) {

    private enum class Kind { COIN, GOLD, BOMB }
    private class Drop(var x: Float, var y: Float, var vy: Float, var kind: Kind, var rot: Float)

    private val drops = ArrayList<Drop>()
    private var bx = 0f
    private var targetX = 0f
    private val by get() = sc.h * 0.80f
    private val bw get() = sc.px(0.18f)
    private var spawnT = 0f
    private var lives = 3

    override val controlHint = "DRAG THE BASKET"

    override fun onReset() {
        drops.clear(); bx = sc.cx; targetX = sc.cx; spawnT = 0f; lives = 3
    }

    private fun halfW(y: Float): Float {
        if (!sc.isRound) return sc.w / 2f - sc.px(0.02f)
        val dy = y - sc.cy; val v = sc.r * sc.r - dy * dy
        return if (v <= 0) 0f else sqrt(v) - sc.px(0.02f)
    }

    override fun onPress(x: Float, y: Float) { targetX = x }
    override fun onDrag(x: Float, y: Float) { targetX = x }

    override fun onStep(dt: Float) {
        val diff = (score / 60f).coerceIn(0f, 1f)
        val hw = halfW(by)
        targetX = targetX.coerceIn(sc.cx - hw + bw / 2, sc.cx + hw - bw / 2)
        bx = Mathx.approach(bx, targetX, 18f, dt)

        spawnT -= dt
        if (spawnT <= 0f) {
            spawnT = Mathx.lerp(0.95f, 0.42f, diff)
            val topY = sc.h * 0.08f
            val hwTop = halfW(topY + sc.px(0.05f))
            val k = when { Mathx.chance(0.22f) -> Kind.BOMB; Mathx.chance(0.14f) -> Kind.GOLD; else -> Kind.COIN }
            drops.add(Drop(sc.cx + Mathx.rand(-hwTop, hwTop), topY,
                sc.px(Mathx.lerp(0.55f, 1.0f, diff)) * Mathx.rand(0.9f, 1.15f), k, 0f))
        }

        val it = drops.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.y += d.vy * dt; d.rot += dt * 3f
            // caught?
            if (d.y in (by - sc.px(0.06f))..(by + sc.px(0.05f)) && kotlin.math.abs(d.x - bx) < bw / 2 + sc.px(0.02f)) {
                when (d.kind) {
                    Kind.BOMB -> { loseLife(d.x, d.y) }
                    Kind.GOLD -> { addScore(25); engine.sfx.coin(); engine.haptics.light(); particles.burst(d.x, by, 18, Palette.gold, sc.px(0.6f), sc.px(0.018f), 0.5f) }
                    Kind.COIN -> { addScore(10); engine.sfx.blip(); engine.haptics.light(); particles.burst(d.x, by, 12, Palette.amber, sc.px(0.5f), sc.px(0.015f), 0.45f) }
                }
                it.remove(); continue
            }
            // missed off the bottom
            if (d.y > sc.h + sc.px(0.06f)) {
                if (d.kind != Kind.BOMB) loseLife(d.x, sc.h * 0.92f)
                it.remove()
            }
        }
    }

    private fun loseLife(x: Float, y: Float) {
        lives--; engine.haptics.heavy(); engine.sfx.hit(); flash(Palette.red, 0.35f); shake(sc.px(0.02f))
        particles.burst(x, y, 16, Palette.red, sc.px(0.6f), sc.px(0.018f), 0.5f)
        if (lives <= 0) gameOver()
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.vGradientRect(c, 0f, 0f, s.w, s.h, 0xFF0C0A16.toInt(), Palette.bg)
        Draw.vignette(c, s, Palette.alpha(Palette.bg, 0f), Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            for (d in drops) drawDrop(c, d)
            // basket
            Draw.glow(c, bx, by, bw * 0.7f, accent, 0.3f)
            val w = bw; val h = sc.px(0.07f)
            Draw.roundRect(c, bx - w / 2, by - h * 0.2f, w, h, h * 0.35f, Palette.lerp(accent, Palette.text, 0.05f))
            Draw.roundRectStroke(c, bx - w / 2, by - h * 0.5f, w, h * 0.5f, h * 0.2f, accent, s.dp(3f))
        }
    }

    private fun drawDrop(c: Canvas, d: Drop) {
        val r = sc.px(0.05f)
        when (d.kind) {
            Kind.BOMB -> {
                Draw.glow(c, d.x, d.y, r * 1.6f, Palette.red, 0.25f)
                Draw.fillCircle(c, d.x, d.y, r, Palette.red)
                Draw.fillCircle(c, d.x, d.y, r * 0.6f, Palette.shade(Palette.red, -0.2f))
                Draw.line(c, d.x, d.y - r, d.x + r * 0.4f, d.y - r * 1.5f, Palette.amber, sc.dp(2.5f))
            }
            Kind.GOLD -> { Draw.glow(c, d.x, d.y, r * 1.8f, Palette.gold, 0.4f); Draw.fillCircle(c, d.x, d.y, r, Palette.gold); Draw.textCentered(c, "★", d.x, d.y, r * 1.1f, Palette.bg, Fonts.display) }
            Kind.COIN -> { Draw.glow(c, d.x, d.y, r * 1.4f, Palette.amber, 0.25f); Draw.fillCircle(c, d.x, d.y, r, Palette.amber); Draw.strokeCircle(c, d.x, d.y, r * 0.6f, Palette.shade(Palette.amber, -0.2f), sc.dp(2f)) }
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.05f); val y = s.h * 0.21f; val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, y, s.px(0.017f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, y, s.px(0.015f), Palette.alpha(Palette.red, 0.35f), s.dp(1.5f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.125f, s.px(0.08f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "catch",
            title = "Catch",
            tagline = "Scoop the coins",
            category = Category.ARCADE,
            tier = Tier.MINI,
            accent = Palette.mint,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.fillCircle(c, cx, cy - size * 0.2f, size * 0.13f, Palette.amber)
                val w = size * 0.5f; val h = size * 0.2f
                Draw.roundRect(c, cx - w / 2, cy + size * 0.05f, w, h, h * 0.3f, color)
            },
            create = { CatchGame() }
        )
    }
}
