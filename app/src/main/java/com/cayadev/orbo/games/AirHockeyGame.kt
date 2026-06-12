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
import kotlin.math.hypot

/**
 * Air Hockey — slide your mallet to smack the puck into the AI's goal. The puck ricochets off the
 * rails; catch it on a moving mallet to add power. Every goal you net scores a point; concede three
 * and the match is over. The table is sized to fit snugly inside a round face.
 */
class AirHockeyGame : ArcadeGame(meta()) {

    private var puckX = 0f; private var puckY = 0f; private var pvx = 0f; private var pvy = 0f
    private var padX = 0f; private var padY = 0f; private var prevPadX = 0f; private var prevPadY = 0f
    private var aiX = 0f; private var aiY = 0f
    private var lives = 3
    private var resetT = 0f

    override val controlHint = "DRAG YOUR MALLET"

    private fun tw(s: ScreenInfo) = if (s.isRound) s.minDim * 0.6f else s.w * 0.86f
    private fun th(s: ScreenInfo) = if (s.isRound) s.minDim * 0.66f else s.h * 0.86f
    private fun left(s: ScreenInfo) = s.cx - tw(s) / 2f
    private fun right(s: ScreenInfo) = s.cx + tw(s) / 2f
    private fun topY(s: ScreenInfo) = s.cy - th(s) / 2f
    private fun botY(s: ScreenInfo) = s.cy + th(s) / 2f
    private fun goalHalf(s: ScreenInfo) = tw(s) * 0.22f
    private fun puckR(s: ScreenInfo) = s.px(0.04f)
    private fun padR(s: ScreenInfo) = s.px(0.07f)

    override fun onReset() {
        lives = 3; resetT = 0f
        centerPuck(1f)
        val s = sc
        padX = s.cx; padY = botY(s) - th(s) * 0.18f; prevPadX = padX; prevPadY = padY
        aiX = s.cx; aiY = topY(s) + th(s) * 0.18f
    }

    private fun centerPuck(dir: Float) {
        val s = sc
        puckX = s.cx; puckY = s.cy
        val a = Mathx.rand(-0.5f, 0.5f)
        pvx = Mathx.sinf(a) * s.px(0.3f)
        pvy = dir * s.px(0.5f)
        resetT = 0.6f
    }

    override fun onPress(x: Float, y: Float) { setPad(x, y) }
    override fun onDrag(x: Float, y: Float) { setPad(x, y) }
    private fun setPad(x: Float, y: Float) {
        val s = sc; val pr = padR(s)
        padX = x.coerceIn(left(s) + pr, right(s) - pr)
        padY = y.coerceIn(s.cy + pr * 0.1f, botY(s) - pr)   // bottom half only
    }

    override fun onStep(dt: Float) {
        val s = sc
        if (resetT > 0f) resetT -= dt
        val padVx = (padX - prevPadX) / dt; val padVy = (padY - prevPadY) / dt
        prevPadX = padX; prevPadY = padY

        // AI
        val aiSpeed = s.px(0.7f) + score * s.px(0.01f)
        val aiTargetX = if (puckY < s.cy) puckX else s.cx
        aiX = Mathx.approach(aiX, aiTargetX.coerceIn(left(s) + padR(s), right(s) - padR(s)), 6f, dt)
        val attack = puckY < topY(s) + th(s) * 0.4f && abs(puckX - aiX) < padR(s) * 2.5f
        val aiTargetY = if (attack) puckY else topY(s) + th(s) * 0.18f
        aiY = Mathx.approach(aiY, aiTargetY.coerceIn(topY(s) + padR(s), s.cy - padR(s) * 0.1f), 5f, dt)
        // limit ai velocity (already smoothed)

        // puck move
        puckX += pvx * dt; puckY += pvy * dt
        val pr = puckR(s)
        // side walls
        if (puckX - pr < left(s)) { puckX = left(s) + pr; pvx = abs(pvx) }
        if (puckX + pr > right(s)) { puckX = right(s) - pr; pvx = -abs(pvx) }
        // top wall / AI goal
        if (puckY - pr < topY(s)) {
            if (abs(puckX - s.cx) < goalHalf(s) && resetT <= 0f) { playerGoal(); return }
            else { puckY = topY(s) + pr; pvy = abs(pvy) }
        }
        // bottom wall / player goal
        if (puckY + pr > botY(s)) {
            if (abs(puckX - s.cx) < goalHalf(s) && resetT <= 0f) { aiGoal(); return }
            else { puckY = botY(s) - pr; pvy = -abs(pvy) }
        }
        // mallet collisions
        collide(padX, padY, padVx, padVy)
        collide(aiX, aiY, 0f, 0f)

        // friction + cap
        pvx *= (1f - 0.18f * dt); pvy *= (1f - 0.18f * dt)
        val sp = hypot(pvx.toDouble(), pvy.toDouble()).toFloat()
        val maxSp = s.px(1.9f)
        if (sp > maxSp) { pvx = pvx / sp * maxSp; pvy = pvy / sp * maxSp }
    }

    private fun collide(mx: Float, my: Float, mvx: Float, mvy: Float) {
        val s = sc; val pr = puckR(s); val mr = padR(s)
        val d = Mathx.dist(puckX, puckY, mx, my)
        val minD = pr + mr
        if (d < minD && d > 0.0001f) {
            val nx = (puckX - mx) / d; val ny = (puckY - my) / d
            puckX = mx + nx * minD; puckY = my + ny * minD
            val base = s.px(0.85f)
            val impulse = (abs(mvx) + abs(mvy)) * 0.35f
            val speed = (base + impulse).coerceAtMost(s.px(2.0f))
            pvx = nx * speed + mvx * 0.25f
            pvy = ny * speed + mvy * 0.25f
            engine.sfx.click(); engine.haptics.tick()
        }
    }

    private fun playerGoal() {
        addScore(1); engine.sfx.success(); engine.haptics.success(); flash(accent, 0.25f)
        particles.burst(puckX, topY(sc), 20, accent, sc.px(0.9f), sc.px(0.018f), 0.5f)
        centerPuck(-1f)
    }

    private fun aiGoal() {
        lives--; engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.3f); shake(sc.px(0.018f))
        particles.burst(puckX, botY(sc), 20, Palette.red, sc.px(0.9f), sc.px(0.018f), 0.5f)
        if (lives <= 0) gameOver() else centerPuck(1f)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val l = left(s); val r = right(s); val t = topY(s); val b = botY(s)
        // table
        Draw.roundRect(c, l, t, tw(s), th(s), s.px(0.04f), Palette.panel)
        Draw.roundRectStroke(c, l, t, tw(s), th(s), s.px(0.04f), Palette.alpha(accent, 0.3f), s.dp(2f))
        // centre line + circle
        Draw.line(c, l, s.cy, r, s.cy, Palette.alpha(accent, 0.2f), s.dp(1.5f))
        Draw.strokeCircle(c, s.cx, s.cy, tw(s) * 0.16f, Palette.alpha(accent, 0.2f), s.dp(1.5f))
        // goals
        Draw.line(c, s.cx - goalHalf(s), t, s.cx + goalHalf(s), t, Palette.alpha(Palette.red, 0.7f), s.dp(4f))
        Draw.line(c, s.cx - goalHalf(s), b, s.cx + goalHalf(s), b, Palette.alpha(accent, 0.7f), s.dp(4f))
        // mallets
        drawMallet(c, s, aiX, aiY, Palette.red)
        drawMallet(c, s, padX, padY, accent)
        // puck
        Draw.glow(c, puckX, puckY, puckR(s) * 1.5f, Palette.text, 0.3f)
        Draw.fillCircle(c, puckX, puckY, puckR(s), Palette.text)
    }

    private fun drawMallet(c: Canvas, s: ScreenInfo, x: Float, y: Float, col: Int) {
        Draw.glow(c, x, y, padR(s) * 1.4f, col, 0.25f)
        Draw.fillCircle(c, x, y, padR(s), col)
        Draw.fillCircle(c, x, y, padR(s) * 0.5f, Palette.shade(col, -0.3f))
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.045f); val ly = topY(s) - s.px(0.06f); val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, ly, s.px(0.015f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, ly, s.px(0.013f), Palette.alpha(Palette.red, 0.35f), s.dp(1.4f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.cy, s.px(0.16f), Palette.alpha(Palette.text, 0.12f), Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "air_hockey",
            title = "Air Hockey",
            tagline = "Smack the puck",
            category = Category.ACTION,
            tier = Tier.MINI,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx, cy, size * 0.5f, size * 0.7f, size * 0.08f, Palette.alpha(color, 0.25f))
                Draw.line(c, cx - size * 0.25f, cy, cx + size * 0.25f, cy, Palette.alpha(color, 0.5f), size * 0.03f)
                Draw.fillCircle(c, cx, cy - size * 0.22f, size * 0.12f, Palette.red)
                Draw.fillCircle(c, cx, cy + size * 0.22f, size * 0.12f, color)
                Draw.fillCircle(c, cx, cy, size * 0.07f, Palette.text)
            },
            create = { AirHockeyGame() }
        )
    }
}
