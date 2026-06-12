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

/**
 * Missile Guard — defend your base. Missiles rain down toward you; tap to detonate a flak burst that
 * vaporises anything it touches. Time your taps to catch clusters. Each missile that reaches the base
 * costs a life. Three lives, rising barrage. Built around the bottom of the round face.
 */
class MissileGuardGame : ArcadeGame(meta()) {

    private class Missile(var x: Float, var y: Float, var vx: Float, var vy: Float, val sx: Float, val sy: Float)
    private class Blast(var x: Float, var y: Float, var t: Float, var maxR: Float)

    private val missiles = ArrayList<Missile>()
    private val blasts = ArrayList<Blast>()
    private var spawnTimer = 0f
    private var lives = 3
    private val blastDur = 0.55f

    override val controlHint = "TAP TO INTERCEPT"

    private fun baseY(s: ScreenInfo) = s.h * 0.86f

    override fun onReset() { missiles.clear(); blasts.clear(); spawnTimer = 0.6f; lives = 3 }

    private fun spawn() {
        val s = sc
        val sx = Mathx.rand(s.cx - s.r * 0.7f, s.cx + s.r * 0.7f)
        val sy = -s.px(0.05f)
        val tx = s.cx + Mathx.rand(-s.px(0.12f), s.px(0.12f))
        val ty = baseY(s)
        val ang = Mathx.angle(tx - sx, ty - sy)
        val diff = timeAlive / 60f
        val speed = Mathx.rand(s.px(0.16f), s.px(0.26f)) * (1f + diff.coerceIn(0f, 1.4f))
        missiles.add(Missile(sx, sy, Mathx.cosf(ang) * speed, Mathx.sinf(ang) * speed, sx, sy))
    }

    override fun onStep(dt: Float) {
        spawnTimer -= dt
        val interval = Mathx.lerp(1.5f, 0.55f, (timeAlive / 70f).coerceIn(0f, 1f))
        if (spawnTimer <= 0f) { spawn(); if (Mathx.chance(0.25f)) spawn(); spawnTimer = interval }

        // blasts
        val bi = blasts.iterator()
        while (bi.hasNext()) { val b = bi.next(); b.t += dt; if (b.t >= blastDur) bi.remove() }

        val mi = missiles.iterator()
        while (mi.hasNext()) {
            val m = mi.next()
            m.x += m.vx * dt; m.y += m.vy * dt
            var destroyed = false
            for (b in blasts) {
                val r = blastR(b)
                if (Mathx.dist(m.x, m.y, b.x, b.y) <= r) { destroyed = true; break }
            }
            if (destroyed) {
                addScore(15); engine.sfx.hit(); engine.haptics.tick()
                particles.burst(m.x, m.y, 10, accent, sc.px(0.6f), sc.px(0.014f), 0.4f)
                mi.remove(); continue
            }
            if (m.y >= baseY(sc)) {
                mi.remove(); lives--
                engine.sfx.explosion(); engine.haptics.heavy(); flash(Palette.red, 0.4f); shake(sc.px(0.022f))
                particles.burst(m.x, baseY(sc), 22, Palette.red, sc.px(0.9f), sc.px(0.02f), 0.5f)
                if (lives <= 0) { gameOver(); return }
            }
        }
    }

    private fun blastR(b: Blast): Float {
        val p = (b.t / blastDur).coerceIn(0f, 1f)
        return b.maxR * Mathx.sinf(p * Math.PI.toFloat())
    }

    override fun onTapWorld(x: Float, y: Float) {
        blasts.add(Blast(x, y, 0f, sc.px(0.16f)))
        engine.sfx.click(); engine.haptics.light()
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            // base
            val by = baseY(s)
            Draw.roundRectCentered(c, s.cx, by + s.px(0.04f), s.px(0.3f), s.px(0.08f), s.px(0.02f), Palette.alpha(accent, 0.5f))
            for (i in -1..1) {
                val cx = s.cx + i * s.px(0.1f)
                Draw.fillCircle(c, cx, by, s.px(0.03f), if ((i + 2) <= lives) accent else Palette.alpha(accent, 0.2f))
            }
            // missiles + trails
            for (m in missiles) {
                Draw.line(c, m.sx, m.sy, m.x, m.y, Palette.alpha(Palette.red, 0.35f), s.dp(1.5f))
                Draw.glow(c, m.x, m.y, s.px(0.035f), Palette.red, 0.5f)
                Draw.fillCircle(c, m.x, m.y, s.px(0.016f), Palette.red)
            }
            // blasts
            for (b in blasts) {
                val r = blastR(b)
                Draw.glow(c, b.x, b.y, r * 1.3f, accent, 0.4f)
                Draw.strokeCircle(c, b.x, b.y, r, Palette.alpha(accent, 0.8f), s.dp(3f))
            }
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.05f); val ly = s.h * 0.12f; val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, ly, s.px(0.015f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, ly, s.px(0.013f), Palette.alpha(Palette.red, 0.35f), s.dp(1.4f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.06f, s.px(0.055f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "missile_guard",
            title = "Missile Guard",
            tagline = "Defend the base",
            category = Category.ACTION,
            tier = Tier.MINI,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx, cy + size * 0.32f, size * 0.5f, size * 0.14f, size * 0.04f, color)
                Draw.line(c, cx - size * 0.3f, cy - size * 0.4f, cx, cy + size * 0.1f, Palette.red, size * 0.05f)
                Draw.strokeCircle(c, cx + size * 0.18f, cy - size * 0.05f, size * 0.2f, Palette.alpha(color, 0.8f), size * 0.05f)
            },
            create = { MissileGuardGame() }
        )
    }
}
