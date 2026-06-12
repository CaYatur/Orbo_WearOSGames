package com.cayadev.orbo.games

import android.graphics.Canvas
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.IconDrawer
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Loc
import com.cayadev.orbo.engine.Mathx
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame

/**
 * Code Breaker — crack the hidden colour code. Tap a slot to cycle its colour, then submit. Black
 * pegs mean right colour and place, white pegs mean right colour wrong place. Crack it within six
 * tries to score; fail and lose a life. Three lives.
 */
class MastermindGame : ArcadeGame(meta()) {

    private val L = 4
    private val C = 6
    private val palette = intArrayOf(Palette.mint, Palette.violet, Palette.pink, Palette.cyan, Palette.amber, Palette.lime)
    private val maxTries = 6
    private val secret = IntArray(L)
    private val guess = IntArray(L)
    private class Row(val g: IntArray, val black: Int, val white: Int)
    private val history = ArrayList<Row>()
    private var lives = 3

    override val controlHint = "TAP SLOTS · CRACK THE CODE"

    override fun onReset() { lives = 3; newCode() }

    private fun newCode() {
        for (i in 0 until L) secret[i] = Mathx.rand(C)
        for (i in 0 until L) guess[i] = 0
        history.clear()
    }

    override fun onStep(dt: Float) {}

    private fun rowY(s: ScreenInfo, i: Int) = s.h * 0.2f + i * s.px(0.085f)
    private fun pegX(s: ScreenInfo, i: Int) = s.cx - s.px(0.13f) + i * s.px(0.087f)
    private fun curY(s: ScreenInfo) = s.h * 0.76f
    private fun goY(s: ScreenInfo) = s.h * 0.9f
    private fun pegR(s: ScreenInfo) = s.px(0.032f)

    override fun onTapWorld(x: Float, y: Float) {
        val s = sc
        // current slots
        if (kotlin.math.abs(y - curY(s)) < pegR(s) * 1.8f) {
            for (i in 0 until L) if (kotlin.math.abs(x - pegX(s, i)) < pegR(s) * 1.3f) {
                guess[i] = (guess[i] + 1) % C; engine.sfx.click(); engine.haptics.tick(); return
            }
        }
        // GO button
        if (kotlin.math.abs(y - goY(s)) < s.px(0.055f) && kotlin.math.abs(x - s.cx) < s.px(0.16f)) submit()
    }

    private fun submit() {
        val s = sc
        var black = 0; var white = 0
        val sc2 = secret.copyOf(); val gc = guess.copyOf()
        for (i in 0 until L) if (gc[i] == sc2[i]) { black++; sc2[i] = -1; gc[i] = -2 }
        for (i in 0 until L) if (gc[i] >= 0) { val idx = sc2.indexOf(gc[i]); if (idx >= 0) { white++; sc2[idx] = -1 } }
        history.add(Row(guess.copyOf(), black, white))
        if (black == L) {
            addScore(100 + (maxTries - history.size + 1) * 40)
            engine.sfx.success(); engine.haptics.success(); flash(accent, 0.25f)
            newCode()
        } else if (history.size >= maxTries) {
            lives--; engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.25f)
            if (lives <= 0) gameOver() else newCode()
        } else { engine.sfx.blip(); engine.haptics.light() }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val pr = pegR(s)
        // history
        for (i in 0 until maxTries) {
            val y = rowY(s, i)
            if (i < history.size) {
                val row = history[i]
                for (j in 0 until L) { Draw.fillCircle(c, pegX(s, j), y, pr * 0.85f, palette[row.g[j]]) }
                // feedback pegs at right
                val fx = pegX(s, L - 1) + s.px(0.07f)
                for (k in 0 until L) {
                    val col = when { k < row.black -> Palette.text; k < row.black + row.white -> Palette.textDim; else -> Palette.alpha(Palette.line, 0.6f) }
                    val ox = fx + (k % 2) * s.px(0.028f); val oy = y - s.px(0.018f) + (k / 2) * s.px(0.034f)
                    Draw.fillCircle(c, ox, oy, s.px(0.012f), col)
                }
            } else {
                for (j in 0 until L) Draw.strokeCircle(c, pegX(s, j), y, pr * 0.7f, Palette.alpha(Palette.line, 0.6f), s.dp(1.2f))
            }
        }
        // current guess
        for (j in 0 until L) {
            Draw.glow(c, pegX(s, j), curY(s), pr * 1.3f, palette[guess[j]], 0.25f)
            Draw.fillCircle(c, pegX(s, j), curY(s), pr, palette[guess[j]])
            Draw.strokeCircle(c, pegX(s, j), curY(s), pr, Palette.alpha(Palette.text, 0.3f), s.dp(1.5f))
        }
        // GO button
        com.cayadev.orbo.engine.Ui.pill(c, s.cx, goY(s), s.px(0.3f), s.px(0.08f), Loc.t("GUESS"), accent, true)
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.045f); val startX = s.cx + s.px(0.16f)
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, s.h * 0.13f, s.px(0.013f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, s.h * 0.13f, s.px(0.011f), Palette.alpha(Palette.red, 0.3f), s.dp(1.2f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.13f, s.px(0.05f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "code_breaker",
            title = "Code Breaker",
            tagline = "Crack the code",
            category = Category.BRAIN,
            tier = Tier.MINI,
            accent = Palette.violet,
            icon = IconDrawer { c, cx, cy, size, _ ->
                val cols = intArrayOf(Palette.mint, Palette.pink, Palette.amber, Palette.cyan)
                for (i in 0 until 4) Draw.fillCircle(c, cx + (i - 1.5f) * size * 0.26f, cy, size * 0.11f, cols[i])
                Draw.fillCircle(c, cx - size * 0.2f, cy + size * 0.3f, size * 0.05f, Palette.text)
                Draw.fillCircle(c, cx + size * 0.05f, cy + size * 0.3f, size * 0.05f, Palette.textDim)
            },
            create = { MastermindGame() }
        )
    }
}
