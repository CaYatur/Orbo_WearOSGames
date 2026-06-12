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
import kotlin.math.abs

/**
 * Meteor Shield — a radial defender made for the round face. Drag anywhere to swing your shield arc
 * around the planet; meteors streak in from every direction. Block them with the shield, but any that
 * reach the core cost a life. Three lives, rising storm.
 */
class MeteorShieldGame : ArcadeGame(meta()) {

    private class Meteor(var ang: Float, var dist: Float, var speed: Float, var r: Float, var blocked: Boolean)

    private val meteors = ArrayList<Meteor>()
    private var shieldAng = -Math.PI.toFloat() / 2f
    private val shieldHalf = 0.55f
    private var spawnTimer = 0f
    private var lives = 3

    override val controlHint = "DRAG TO AIM THE SHIELD"

    private fun planetR(s: ScreenInfo) = s.px(0.12f)
    private fun shieldR(s: ScreenInfo) = if (s.isRound) s.r * 0.5f else s.minDim * 0.3f
    private fun edgeR(s: ScreenInfo) = if (s.isRound) s.r * 1.05f else s.minDim * 0.62f

    override fun onReset() { meteors.clear(); shieldAng = -Math.PI.toFloat() / 2f; spawnTimer = 0.5f; lives = 3 }

    private fun spawn() {
        val s = sc
        val diff = (timeAlive / 60f).coerceIn(0f, 1.5f)
        meteors.add(Meteor(Mathx.rand(0f, Mathx.TAU), edgeR(s), Mathx.rand(s.px(0.18f), s.px(0.3f)) * (1f + diff * 0.5f),
            Mathx.rand(s.px(0.025f), s.px(0.04f)), false))
    }

    override fun onPress(x: Float, y: Float) { aim(x, y) }
    override fun onDrag(x: Float, y: Float) { aim(x, y) }
    private fun aim(x: Float, y: Float) {
        val dx = x - sc.cx; val dy = y - sc.cy
        if (dx * dx + dy * dy > sc.px(0.02f) * sc.px(0.02f)) shieldAng = Mathx.angle(dx, dy)
    }

    private fun angDiff(a: Float, b: Float): Float {
        var d = (a - b) % Mathx.TAU
        if (d < -Math.PI) d += Mathx.TAU
        if (d > Math.PI) d -= Mathx.TAU
        return abs(d)
    }

    override fun onStep(dt: Float) {
        val s = sc
        spawnTimer -= dt
        val interval = Mathx.lerp(1.3f, 0.45f, (timeAlive / 70f).coerceIn(0f, 1f))
        if (spawnTimer <= 0f) { spawn(); spawnTimer = interval }
        val sr = shieldR(s); val pr = planetR(s)
        val it = meteors.iterator()
        while (it.hasNext()) {
            val m = it.next()
            if (m.blocked) { m.dist += m.speed * 2f * dt; if (m.dist > edgeR(s) * 1.2f) it.remove(); continue }
            m.dist -= m.speed * dt
            // hit shield band
            if (m.dist <= sr + m.r && m.dist >= sr - m.r - s.px(0.02f)) {
                if (angDiff(m.ang, shieldAng) <= shieldHalf) {
                    m.blocked = true; m.speed = m.speed.coerceAtLeast(s.px(0.4f))
                    addScore(10); engine.sfx.hit(); engine.haptics.tick()
                    val mx = s.cx + Mathx.cosf(m.ang) * m.dist; val my = s.cy + Mathx.sinf(m.ang) * m.dist
                    particles.burst(mx, my, 10, accent, s.px(0.5f), s.px(0.012f), 0.35f)
                    continue
                }
            }
            if (m.dist <= pr) {
                it.remove(); lives--
                engine.sfx.explosion(); engine.haptics.heavy(); flash(Palette.red, 0.4f); shake(s.px(0.022f))
                particles.burst(s.cx, s.cy, 22, Palette.red, s.px(0.9f), s.px(0.02f), 0.5f)
                if (lives <= 0) { gameOver(); return }
            }
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val sr = shieldR(s); val pr = planetR(s)
        // shield orbit guide
        Draw.strokeCircle(c, s.cx, s.cy, sr, Palette.alpha(Palette.line, 0.5f), s.dp(1f))
        // shield arc
        val startDeg = Math.toDegrees((shieldAng - shieldHalf).toDouble()).toFloat()
        val sweep = Math.toDegrees((shieldHalf * 2f).toDouble()).toFloat()
        Draw.arc(c, s.cx, s.cy, sr, startDeg, sweep, Palette.alpha(accent, 0.3f), s.px(0.06f))
        Draw.arc(c, s.cx, s.cy, sr, startDeg, sweep, accent, s.dp(6f))
        // planet
        Draw.glow(c, s.cx, s.cy, pr * 1.6f, accent, 0.25f)
        Draw.fillCircle(c, s.cx, s.cy, pr, Palette.panelHi)
        Draw.strokeCircle(c, s.cx, s.cy, pr, Palette.alpha(accent, 0.6f), s.dp(2f))
        // lives
        val gap = s.px(0.045f); val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, s.cy, s.px(0.014f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, s.cy, s.px(0.012f), Palette.alpha(Palette.red, 0.3f), s.dp(1.2f))
        }
        // meteors
        for (m in meteors) {
            val mx = s.cx + Mathx.cosf(m.ang) * m.dist; val my = s.cy + Mathx.sinf(m.ang) * m.dist
            val col = if (m.blocked) accent else Palette.orange
            Draw.glow(c, mx, my, m.r * 1.6f, col, 0.4f)
            Draw.fillCircle(c, mx, my, m.r, col)
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.12f, s.px(0.06f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "meteor_shield",
            title = "Meteor Shield",
            tagline = "Block the storm",
            category = Category.ACTION,
            tier = Tier.MINI,
            accent = Palette.mint,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.fillCircle(c, cx, cy, size * 0.16f, color)
                Draw.arc(c, cx, cy, size * 0.38f, -150f, 120f, color, size * 0.08f)
                Draw.fillCircle(c, cx + size * 0.45f, cy - size * 0.35f, size * 0.08f, Palette.orange)
            },
            create = { MeteorShieldGame() }
        )
    }
}
