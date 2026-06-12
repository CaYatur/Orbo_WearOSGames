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
 * Bullseye — a precision tap test. A ring shrinks toward a bullseye; tap as late (and as small) as
 * you dare for more points, but let it vanish and you lose a life. Three lives, rising tempo.
 */
class BullseyeGame : ArcadeGame(meta()) {

    private var tx = 0f
    private var ty = 0f
    private var maxR = 0f
    private var life = 0f          // current target's full lifetime
    private var age = 0f           // how long it has been alive
    private var alive = false
    private var lives = 3
    private var combo = 0
    private var hitFx = 0f

    override val controlHint = "TAP THE TARGETS"

    override fun onReset() { lives = 3; combo = 0; hitFx = 0f; spawn() }

    private fun spawn() {
        maxR = sc.px(0.20f)
        val margin = maxR + sc.px(0.05f)
        if (sc.isRound) {
            val rr = Mathx.rand(0f, sc.r - margin)
            val a = Mathx.rand(0f, Mathx.TAU)
            tx = sc.cx + Mathx.cosf(a) * rr
            ty = sc.cy + Mathx.sinf(a) * rr
        } else {
            tx = Mathx.rand(margin, sc.w - margin)
            ty = Mathx.rand(margin, sc.h - margin)
        }
        val diff = (score / 800f).coerceIn(0f, 1f)
        life = Mathx.lerp(1.9f, 0.85f, diff)
        age = 0f; alive = true
    }

    override fun onStep(dt: Float) {
        if (hitFx > 0f) hitFx -= dt
        if (!alive) return
        age += dt
        if (age >= life) {                 // vanished uncaught
            alive = false; combo = 0
            engine.sfx.hit(); engine.haptics.heavy(); flash(Palette.red, 0.3f); shake(sc.px(0.015f))
            lives--
            if (lives <= 0) gameOver() else spawn()
        }
    }

    private fun curR(): Float = maxR * (1f - (age / life).coerceIn(0f, 1f))

    override fun onTapWorld(x: Float, y: Float) {
        if (!alive) return
        val r = curR()
        if (Mathx.dist(x, y, tx, ty) <= r + sc.px(0.03f)) {
            val precision = (age / life).coerceIn(0f, 1f)        // later tap = smaller ring = more
            combo++
            val pts = (6 + precision * 44f).toInt() * (1 + combo / 6)
            addScore(pts)
            engine.sfx.coin(); engine.haptics.light(); hitFx = 0.4f
            particles.burst(tx, ty, 16, accent, sc.px(0.6f), sc.px(0.02f), 0.5f)
            alive = false; spawn()
        } else {
            combo = 0; engine.sfx.click()                        // wasted tap breaks the combo
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        if (alive) {
            val r = curR()
            // faint guide of the full ring
            Draw.strokeCircle(c, tx, ty, maxR, Palette.alpha(accent, 0.12f), s.dp(1.5f))
            Draw.glow(c, tx, ty, r * 1.6f, accent, 0.3f)
            Draw.strokeCircle(c, tx, ty, r, accent, s.dp(3.5f))
            Draw.fillCircle(c, tx, ty, s.px(0.02f), Palette.alpha(accent, 0.9f))
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.05f); val y = s.h * 0.21f; val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, y, s.px(0.017f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, y, s.px(0.015f), Palette.alpha(Palette.red, 0.35f), s.dp(1.5f))
        }
        if (combo > 1) Draw.textCentered(c, "x$combo", s.cx, s.h * 0.83f, s.px(0.05f), accent, Fonts.bodyBold)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.125f, s.px(0.075f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "bullseye",
            title = "Bullseye",
            tagline = "Tap on the dot",
            category = Category.REFLEX,
            tier = Tier.MINI,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.strokeCircle(c, cx, cy, size * 0.4f, color, size * 0.06f)
                Draw.strokeCircle(c, cx, cy, size * 0.24f, Palette.alpha(color, 0.7f), size * 0.05f)
                Draw.fillCircle(c, cx, cy, size * 0.09f, Palette.red)
            },
            create = { BullseyeGame() }
        )
    }
}
