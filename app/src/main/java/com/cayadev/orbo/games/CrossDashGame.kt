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
import com.cayadev.orbo.engine.Swipe
import com.cayadev.orbo.game.ArcadeGame
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Cross Dash — hop up the lanes of evenly-spaced traffic and reach the safe zone at the top. Tap (or
 * swipe up) to hop; time the gaps. Each crossing speeds the traffic up. Three lives.
 */
class CrossDashGame : ArcadeGame(meta()) {

    private val lanes = 7                       // 0 = bottom safe, 1..5 traffic, 6 = top safe
    private val dir = IntArray(lanes)
    private val speed = FloatArray(lanes)
    private val spacing = FloatArray(lanes)
    private val phase = FloatArray(lanes)
    private val hue = IntArray(lanes)

    private var playerLane = 0
    private var playerY = 0f
    private var level = 1
    private var lives = 3
    private var safeT = 0f                       // brief grace after a hop/respawn

    override val controlHint = "TAP TO HOP UP"

    private val playerR get() = sc.px(0.05f)
    private val carHalf get() = sc.px(0.11f)
    private fun laneY(i: Int) = sc.h * (0.86f - 0.70f * i / (lanes - 1))
    private fun laneBand() = sc.h * 0.70f / (lanes - 1)

    override fun onReset() { level = 1; lives = 3; playerLane = 0; playerY = laneY(0); safeT = 0f; genLanes() }

    private fun genLanes() {
        val accents = intArrayOf(Palette.cyan, Palette.violet, Palette.pink, Palette.amber, Palette.mint)
        for (i in 1 until lanes - 1) {
            dir[i] = if (Mathx.chance(0.5f)) 1 else -1
            speed[i] = sc.px(Mathx.lerp(0.30f, 0.85f, (level / 10f).coerceIn(0f, 1f)) * Mathx.rand(0.85f, 1.2f))
            spacing[i] = sc.px(Mathx.rand(0.56f, 0.82f))
            phase[i] = Mathx.rand(0f, spacing[i])
            hue[i] = accents[(i - 1) % accents.size]
        }
    }

    override fun onTapWorld(x: Float, y: Float) { hop(1) }
    override fun onFlick(dir: Swipe, vx: Float, vy: Float) {
        when (dir) { Swipe.UP -> hop(1); Swipe.DOWN -> hop(-1); else -> {} }
    }

    private fun hop(d: Int) {
        val nl = (playerLane + d).coerceIn(0, lanes - 1)
        if (nl == playerLane) return
        playerLane = nl; safeT = 0.0f
        engine.sfx.jump(); engine.haptics.light()
        if (playerLane == lanes - 1) crossingDone()
        else if (occupied(playerLane, sc.cx)) die()
    }

    private fun crossingDone() {
        addScore(30 + level * 10); level++
        engine.sfx.success(); engine.haptics.success(); flash(Palette.mint, 0.25f)
        particles.burst(sc.cx, laneY(lanes - 1), 22, Palette.mint, sc.px(0.8f), sc.px(0.02f), 0.6f)
        playerLane = 0; playerY = laneY(0); genLanes()
    }

    private fun die() {
        lives--; engine.sfx.explosion(); engine.haptics.heavy(); flash(Palette.red, 0.4f); shake(sc.px(0.03f))
        particles.burst(sc.cx, playerY, 24, Palette.red, sc.px(0.9f), sc.px(0.022f), 0.7f)
        playerLane = 0; playerY = laneY(0)
        if (lives <= 0) gameOver()
    }

    private fun occupied(lane: Int, x: Float): Boolean {
        if (lane <= 0 || lane >= lanes - 1) return false
        val sp = spacing[lane]; val ph = phase[lane]
        val nearest = ph + ((x - ph) / sp).roundToInt() * sp
        return abs(nearest - x) < carHalf + playerR * 0.7f
    }

    override fun onStep(dt: Float) {
        if (safeT < 1f) safeT += dt
        for (i in 1 until lanes - 1) {
            var p = phase[i] + dir[i] * speed[i] * dt
            val sp = spacing[i]
            p = ((p % sp) + sp) % sp
            phase[i] = p
        }
        playerY = Mathx.approach(playerY, laneY(playerLane), 22f, dt)
        if (playerLane in 1 until lanes - 1 && occupied(playerLane, sc.cx)) die()
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.vGradientRect(c, 0f, 0f, s.w, s.h, 0xFF0A1014.toInt(), Palette.bg)
        Draw.vignette(c, s, Palette.alpha(Palette.bg, 0f), Palette.bg)
        if (state == GState.READY) return
        val band = laneBand()
        Draw.withFaceClip(c, s) {
            // lane bands
            for (i in 0 until lanes) {
                val y = laneY(i)
                val safe = i == 0 || i == lanes - 1
                val col = if (safe) Palette.alpha(Palette.mint, 0.10f) else Palette.alpha(Palette.panel, 0.6f)
                Draw.roundRect(c, 0f, y - band / 2, s.w, band, 0f, col)
                if (!safe) Draw.line(c, 0f, y - band / 2, s.w, y - band / 2, Palette.alpha(hue[i], 0.18f), s.dp(1f))
            }
            // cars
            for (i in 1 until lanes - 1) {
                val y = laneY(i); val sp = spacing[i]
                var cx = phase[i] - sp
                while (cx < s.w + sp) {
                    drawCar(c, cx, y, band, hue[i])
                    cx += sp
                }
            }
            // player
            val grace = safeT < 0.12f
            Draw.glow(c, s.cx, playerY, playerR * 1.8f, accent, 0.4f)
            Draw.fillCircle(c, s.cx, playerY, playerR, if (grace) Palette.text else accent)
            Draw.fillCircle(c, s.cx, playerY, playerR * 0.5f, Palette.shade(accent, -0.3f))
        }
    }

    private fun drawCar(c: Canvas, cx: Float, cy: Float, band: Float, col: Int) {
        val w = carHalf * 2f; val h = band * 0.66f
        Draw.roundRect(c, cx - w / 2, cy - h / 2, w, h, h * 0.3f, Palette.alpha(col, 0.9f))
        Draw.roundRectStroke(c, cx - w / 2, cy - h / 2, w, h, h * 0.3f, Palette.alpha(Palette.text, 0.2f), sc.dp(1.4f))
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.05f); val y = s.h * 0.10f; val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, y, s.px(0.017f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, y, s.px(0.015f), Palette.alpha(Palette.red, 0.35f), s.dp(1.5f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.05f, s.px(0.055f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "cross_dash",
            title = "Cross Dash",
            tagline = "Hop through traffic",
            category = Category.ACTION,
            tier = Tier.MINI,
            accent = Palette.mint,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx, cy - size * 0.28f, size * 0.5f, size * 0.16f, size * 0.05f, Palette.cyan)
                Draw.roundRectCentered(c, cx, cy + size * 0.05f, size * 0.5f, size * 0.16f, size * 0.05f, Palette.pink)
                Draw.fillCircle(c, cx, cy + size * 0.34f, size * 0.11f, color)
            },
            create = { CrossDashGame() }
        )
    }
}
