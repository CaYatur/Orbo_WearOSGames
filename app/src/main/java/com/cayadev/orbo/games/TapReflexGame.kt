package com.cayadev.orbo.games

import android.graphics.Canvas
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.IconDrawer
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Ease
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Mathx
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame

/**
 * Tap Reflex — glowing targets pop around the dial; tap the good ones before the timer ring
 * empties, dodge the bombs, and chain combos. Speeds up the longer you survive.
 */
class TapReflexGame : ArcadeGame(meta()) {

    private enum class Kind { NORMAL, GOLD, BOMB }

    private var tx = 0f
    private var ty = 0f
    private var tr = 0f
    private var kind = Kind.NORMAL
    private var timeLimit = 1.7f
    private var timeLeft = 1.7f
    private var appear = 0f          // 0..1 pop-in animation
    private var combo = 0
    private var lives = 3
    private var pulse = 0f

    override val controlHint = "TAP TARGETS · AVOID BOMBS"
    override val scoreLabel = "SCORE"

    override fun onReset() {
        combo = 0; lives = 3; timeLimit = 1.7f
        spawn(first = true)
    }

    private fun spawn(first: Boolean = false) {
        // difficulty ramps with score
        val diff = (score / 220f).coerceIn(0f, 1f)
        timeLimit = Mathx.lerp(1.7f, 0.62f, diff)
        timeLeft = timeLimit
        appear = 0f
        tr = sc.px(Mathx.lerp(0.13f, 0.085f, diff))
        val maxR = sc.r - tr - sc.safeInset * 0.4f - sc.px(0.04f)
        val minR = sc.px(0.04f)
        var ok = false; var ax = sc.cx; var ay = sc.cy; var guard = 0
        while (!ok && guard++ < 30) {
            val a = Mathx.rand(0f, Mathx.TAU)
            val rad = Mathx.rand(minR, maxR.coerceAtLeast(minR + 1f))
            ax = sc.cx + Mathx.cosf(a) * rad
            ay = sc.cy + Mathx.sinf(a) * rad
            ok = sc.insideFace(ax, ay, tr + sc.px(0.01f)) && ay > sc.h * 0.20f
        }
        tx = ax; ty = ay
        kind = when {
            first -> Kind.NORMAL
            Mathx.chance((0.10f + diff * 0.16f)) -> Kind.BOMB
            Mathx.chance(0.12f) -> Kind.GOLD
            else -> Kind.NORMAL
        }
    }

    override fun onStep(dt: Float) {
        pulse += dt
        if (appear < 1f) appear = (appear + dt * 6f).coerceAtMost(1f)
        timeLeft -= dt
        if (timeLeft <= 0f) {
            // timer expired
            if (kind == Kind.BOMB) {
                engine.sfx.blip()        // safely avoided
                combo = 0
                spawn()
            } else {
                loseLife("MISS")
                if (lives > 0) spawn()
            }
        }
    }

    private fun loseLife(reason: String) {
        lives--
        combo = 0
        engine.haptics.medium()
        engine.sfx.hit()
        shake(sc.px(0.02f))
        flash(Palette.red, 0.35f)
        particles.burst(tx, ty, 14, Palette.red, sc.px(0.5f), sc.px(0.02f), 0.5f)
        if (lives <= 0) gameOver()
    }

    override fun onTapWorld(x: Float, y: Float) {
        val hit = Mathx.dist(x, y, tx, ty) <= tr * 1.18f
        if (!hit) { combo = 0; engine.sfx.click(); return }
        when (kind) {
            Kind.BOMB -> {
                loseLife("BOMB")
                if (lives > 0) spawn()
            }
            else -> {
                combo++
                val base = if (kind == Kind.GOLD) 50 else 10
                val gained = base + combo * 2
                addScore(gained)
                engine.haptics.light()
                if (kind == Kind.GOLD) engine.sfx.coin() else engine.sfx.blip()
                val col = if (kind == Kind.GOLD) Palette.gold else accent
                particles.burst(tx, ty, if (kind == Kind.GOLD) 22 else 14, col, sc.px(0.7f), sc.px(0.022f), 0.55f)
                spawn()
            }
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)

        if (state == GState.READY) return

        val popR = tr * Ease.outBack(appear)
        val col = when (kind) {
            Kind.GOLD -> Palette.gold
            Kind.BOMB -> Palette.red
            else -> accent
        }
        // glow + body
        Draw.glow(c, tx, ty, popR * 2.1f, col, 0.5f)
        val breathe = 1f + 0.04f * Mathx.sinf(pulse * 9f)
        Draw.fillCircle(c, tx, ty, popR * breathe, Palette.alpha(col, 0.22f))
        Draw.strokeCircle(c, tx, ty, popR * breathe, col, popR * 0.13f)
        Draw.fillCircle(c, tx, ty, popR * 0.30f, col)

        // type glyph
        when (kind) {
            Kind.GOLD -> Draw.textCentered(c, "★", tx, ty, popR * 0.9f, Palette.bg, Fonts.display)
            Kind.BOMB -> {
                val rr = popR * 0.34f
                Draw.line(c, tx - rr, ty - rr, tx + rr, ty + rr, Palette.bg, popR * 0.12f)
                Draw.line(c, tx + rr, ty - rr, tx - rr, ty + rr, Palette.bg, popR * 0.12f)
            }
            else -> {}
        }

        // timer ring
        val frac = (timeLeft / timeLimit).coerceIn(0f, 1f)
        val ringCol = if (kind == Kind.BOMB) Palette.alpha(Palette.red, 0.5f) else Palette.lerp(Palette.red, col, frac)
        Draw.arc(c, tx, ty, popR * 1.42f, -90f, 360f * frac, ringCol, popR * 0.10f)
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        // lives pips
        val n = 3
        val gap = s.px(0.05f)
        val y = s.h * 0.225f
        val startX = s.cx - gap * (n - 1) / 2f
        for (i in 0 until n) {
            val on = i < lives
            val px = startX + i * gap
            if (on) Draw.fillCircle(c, px, y, s.px(0.018f), Palette.red)
            else Draw.strokeCircle(c, px, y, s.px(0.016f), Palette.alpha(Palette.red, 0.35f), s.dp(1.5f))
        }
        if (combo > 1) {
            Draw.textCentered(c, "x$combo", s.cx, s.h * 0.30f, s.px(0.05f), accent, Fonts.bodyBold)
        }
    }

    companion object {
        fun meta() = GameMeta(
            id = "tap_reflex",
            title = "Tap Reflex",
            tagline = "Pop the targets",
            category = Category.REFLEX,
            tier = Tier.MINI,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                val r = size * 0.42f
                Draw.strokeCircle(c, cx, cy, r, color, size * 0.07f)
                Draw.strokeCircle(c, cx, cy, r * 0.6f, Palette.alpha(color, 0.7f), size * 0.06f)
                Draw.fillCircle(c, cx, cy, r * 0.22f, color)
            },
            create = { TapReflexGame() }
        )
    }
}
