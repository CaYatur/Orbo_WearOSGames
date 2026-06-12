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
 * Odd One — a perception drill. Every tile is the same colour except one; tap the odd tile. Each
 * find grows the grid and shrinks the difference. Beat the 60-second clock; wrong taps cost time.
 */
class OddOneGame : ArcadeGame(meta()) {

    private var grid = 2
    private var base = Palette.mint
    private var oddIndex = 0
    private var oddColor = Palette.mint
    private var delta = 0.5f
    private var timeLeft = 60f
    private var found = 0
    private var pickFx = 0f
    private var pickX = 0f
    private var pickY = 0f

    override val controlHint = "TAP THE ODD TILE"
    override val gameOverTitle = "TIME UP"

    override fun onReset() { found = 0; timeLeft = 60f; nextRound() }

    private fun nextRound() {
        val d = (found / 25f).coerceIn(0f, 1f)
        grid = (2 + (found / 3)).coerceAtMost(6)
        base = Palette.accents[Mathx.rand(Palette.accents.size)]
        delta = Mathx.lerp(0.42f, 0.12f, d)
        if (Mathx.chance(0.5f)) delta = -delta
        oddColor = Palette.shade(base, delta)
        oddIndex = Mathx.rand(grid * grid)
    }

    private fun boardSide(s: ScreenInfo) = if (s.isRound) s.minDim * 0.66f else s.minDim * 0.82f
    private fun boardTop(s: ScreenInfo) = s.h * 0.55f - boardSide(s) / 2f
    private fun cell(s: ScreenInfo) = boardSide(s) / grid

    override fun onStep(dt: Float) {
        timeLeft -= dt
        if (pickFx > 0f) pickFx -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver() }
    }

    override fun onTapWorld(x: Float, y: Float) {
        val s = sc; val cs = cell(s); val x0 = s.cx - boardSide(s) / 2f; val y0 = boardTop(s)
        val col = ((x - x0) / cs).toInt(); val row = ((y - y0) / cs).toInt()
        if (col !in 0 until grid || row !in 0 until grid) return
        val idx = row * grid + col
        if (idx == oddIndex) {
            found++; addScore(10 + grid * 4)
            engine.sfx.coin(); engine.haptics.light()
            pickX = x0 + col * cs + cs / 2; pickY = y0 + row * cs + cs / 2; pickFx = 0.3f
            particles.burst(pickX, pickY, 14, oddColor, sc.px(0.5f), sc.px(0.018f), 0.45f)
            nextRound()
        } else {
            timeLeft -= 3f                                 // wrong guess penalty
            engine.sfx.hit(); engine.haptics.medium(); flash(Palette.red, 0.22f); shake(sc.px(0.012f))
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val cs = cell(s); val x0 = s.cx - boardSide(s) / 2f; val y0 = boardTop(s)
        val pad = cs * 0.09f
        for (i in 0 until grid * grid) {
            val col = i % grid; val row = i / grid
            val cx = x0 + col * cs + cs / 2; val cy = y0 + row * cs + cs / 2
            val side = cs - pad * 2
            val color = if (i == oddIndex) oddColor else base
            Draw.roundRectCentered(c, cx, cy, side, side, side * 0.18f, color)
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val barW = s.px(0.4f); val frac = (timeLeft / 60f).coerceIn(0f, 1f)
        Draw.roundRect(c, s.cx - barW / 2, s.h * 0.20f, barW, s.dp(5f), s.dp(3f), Palette.track)
        Draw.roundRect(c, s.cx - barW / 2, s.h * 0.20f, barW * frac, s.dp(5f), s.dp(3f), Palette.lerp(Palette.red, accent, frac))
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.125f, s.px(0.065f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "odd_one",
            title = "Odd One",
            tagline = "Spot the difference",
            category = Category.BRAIN,
            tier = Tier.MINI,
            accent = Palette.violet,
            icon = IconDrawer { c, cx, cy, size, color ->
                val u = size * 0.28f
                for (r in -1..1) for (cc in -1..1) {
                    val odd = r == 0 && cc == 1
                    Draw.roundRectCentered(c, cx + cc * u, cy + r * u, u * 0.82f, u * 0.82f, u * 0.18f,
                        if (odd) Palette.amber else Palette.alpha(color, 0.85f))
                }
            },
            create = { OddOneGame() }
        )
    }
}
