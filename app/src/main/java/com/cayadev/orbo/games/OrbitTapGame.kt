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
 * Orbit Tap — a one-tap timing game built for the round face. A dot races around a ring; tap the
 * instant it crosses the glowing target arc. Each hit shifts the target, narrows it and speeds the
 * dot. Three lives; tapping off-target costs one. The closer to centre you hit, the more points.
 */
class OrbitTapGame : ArcadeGame(meta()) {

    private var ang = 0f          // dot angle
    private var dir = 1f
    private var speed = 0f        // rad / s
    private var targ = 0f         // target arc centre angle
    private var aw = 0f           // target arc half-width (rad)
    private var lives = 3
    private var combo = 0
    private var hitFx = 0f

    override val controlHint = "TAP WHEN THE DOT HITS THE ARC"

    override fun onReset() {
        ang = 0f; dir = 1f; speed = 2.2f; lives = 3; combo = 0; hitFx = 0f
        aw = 0.5f; targ = Mathx.rand(0f, Mathx.TAU)
    }

    private fun ring(s: ScreenInfo) = if (s.isRound) s.r * 0.62f else s.minDim * 0.36f

    private fun diff(a: Float, b: Float): Float {
        var d = (a - b) % Mathx.TAU
        if (d < -Math.PI) d += Mathx.TAU
        if (d > Math.PI) d -= Mathx.TAU
        return abs(d)
    }

    override fun onStep(dt: Float) {
        if (hitFx > 0f) hitFx -= dt
        ang = (ang + dir * speed * dt) % Mathx.TAU
        if (ang < 0) ang += Mathx.TAU
    }

    override fun onTapWorld(x: Float, y: Float) {
        val d = diff(ang, targ)
        if (d <= aw) {
            combo++
            val precision = 1f - (d / aw)                 // 1 = dead centre
            addScore(8 + (precision * 22f).toInt() + combo)
            engine.sfx.coin(); engine.haptics.light(); hitFx = 0.35f
            val rr = ring(sc)
            particles.burst(sc.cx + Mathx.cosf(ang) * rr, sc.cy + Mathx.sinf(ang) * rr, 14, accent, sc.px(0.6f), sc.px(0.018f), 0.45f)
            // advance difficulty
            speed = (speed + 0.18f).coerceAtMost(7.5f)
            aw = (aw - 0.018f).coerceAtLeast(0.2f)
            if (Mathx.chance(0.35f)) dir = -dir            // occasional reversal keeps you honest
            var nt = Mathx.rand(0f, Mathx.TAU)
            while (diff(nt, ang) < 1.1f) nt = Mathx.rand(0f, Mathx.TAU)
            targ = nt
        } else {
            lives--; combo = 0
            engine.sfx.fail(); engine.haptics.heavy(); flash(Palette.red, 0.3f); shake(sc.px(0.012f))
            if (lives <= 0) gameOver()
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val rr = ring(s)
        // ring track
        Draw.strokeCircle(c, s.cx, s.cy, rr, Palette.alpha(Palette.track, 0.9f), s.dp(7f))
        // target arc
        val startDeg = Math.toDegrees((targ - aw).toDouble()).toFloat()
        val sweep = Math.toDegrees((aw * 2f).toDouble()).toFloat()
        Draw.arc(c, s.cx, s.cy, rr, startDeg, sweep, Palette.alpha(accent, 0.3f), s.dp(13f))
        Draw.arc(c, s.cx, s.cy, rr, startDeg, sweep, accent, s.dp(7f))
        // dot
        val dx = s.cx + Mathx.cosf(ang) * rr
        val dy = s.cy + Mathx.sinf(ang) * rr
        Draw.glow(c, dx, dy, s.px(0.06f), Palette.text, 0.5f + 0.4f * hitFx.coerceIn(0f, 1f))
        Draw.fillCircle(c, dx, dy, s.px(0.028f), Palette.text)
        // centre lives
        val gap = s.px(0.045f); val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, s.cy, s.px(0.016f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, s.cy, s.px(0.014f), Palette.alpha(Palette.red, 0.35f), s.dp(1.4f))
        }
        if (combo > 1) Draw.textCentered(c, "x$combo", s.cx, s.cy + s.px(0.09f), s.px(0.05f), accent, Fonts.bodyBold)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.cy - s.px(0.09f), s.px(0.07f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "orbit_tap",
            title = "Orbit Tap",
            tagline = "Tap on the arc",
            category = Category.REFLEX,
            tier = Tier.MINI,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.strokeCircle(c, cx, cy, size * 0.36f, Palette.alpha(color, 0.4f), size * 0.06f)
                Draw.arc(c, cx, cy, size * 0.36f, -40f, 80f, color, size * 0.08f)
                val a = -0.6f
                Draw.fillCircle(c, cx + Mathx.cosf(a) * size * 0.36f, cy + Mathx.sinf(a) * size * 0.36f, size * 0.1f, Palette.text)
            },
            create = { OrbitTapGame() }
        )
    }
}
