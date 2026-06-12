package com.cayadev.orbo.games

import android.graphics.Canvas
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.IconDrawer
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Loc
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame

/**
 * Towers — the Tower of Hanoi, against the clock. Tap a peg to lift its top disc, tap another to set
 * it down — never on a smaller disc. Clear the tower to the right peg to score, then a disc is added.
 * Solve as many towers as you can before time runs out.
 */
class HanoiGame : ArcadeGame(meta()) {

    private val pegs = Array(3) { ArrayList<Int>() }
    private var n = 3
    private var selected = -1
    private var moves = 0
    private var timeLeft = 120f
    private var solvedCount = 0

    override val controlHint = "TAP PEG TO LIFT · TAP TO DROP"
    override val gameOverTitle = "TIME UP"

    override fun onReset() { n = 3; timeLeft = 120f; solvedCount = 0; selected = -1; setup() }

    private fun setup() {
        for (p in pegs) p.clear()
        for (d in n downTo 1) pegs[0].add(d)
        moves = 0; selected = -1
    }

    private fun pegX(s: ScreenInfo, i: Int) = s.cx + (i - 1) * (if (s.isRound) s.minDim * 0.26f else s.minDim * 0.3f)
    private fun baseY(s: ScreenInfo) = s.h * 0.78f
    private fun diskH(s: ScreenInfo) = s.px(0.055f)
    private fun maxDiskW(s: ScreenInfo) = if (s.isRound) s.minDim * 0.22f else s.minDim * 0.26f

    override fun onStep(dt: Float) {
        timeLeft -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver() }
    }

    override fun onTapWorld(x: Float, y: Float) {
        val s = sc
        var peg = -1; var bestD = Float.MAX_VALUE
        for (i in 0 until 3) { val d = kotlin.math.abs(x - pegX(s, i)); if (d < bestD) { bestD = d; peg = i } }
        if (bestD > s.minDim * 0.18f) return
        if (selected < 0) {
            if (pegs[peg].isNotEmpty()) { selected = peg; engine.sfx.blip(); engine.haptics.light() }
        } else {
            if (peg == selected) { selected = -1; engine.sfx.click(); return }
            val disk = pegs[selected].last()
            if (pegs[peg].isEmpty() || pegs[peg].last() > disk) {
                pegs[selected].removeAt(pegs[selected].size - 1)
                pegs[peg].add(disk); moves++
                engine.sfx.click(); engine.haptics.light()
                selected = -1
                if (pegs[2].size == n) onSolved()
            } else { engine.sfx.fail(); engine.haptics.medium(); flash(Palette.red, 0.2f); selected = -1 }
        }
    }

    private fun onSolved() {
        solvedCount++
        val optimal = (1 shl n) - 1
        val bonus = (200 - (moves - optimal) * 5).coerceAtLeast(50) + n * 30
        addScore(bonus); timeLeft += 12f
        engine.sfx.success(); engine.haptics.success(); flash(accent, 0.25f)
        n = (n + 1).coerceAtMost(6)
        setup()
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val by = baseY(s); val dh = diskH(s)
        // pegs
        for (i in 0 until 3) {
            val px = pegX(s, i)
            Draw.roundRect(c, px - s.dp(3f), by - dh * 6.2f, s.dp(6f), dh * 6.2f, s.dp(3f), Palette.alpha(accent, 0.25f))
            Draw.roundRectCentered(c, px, by + dh * 0.4f, maxDiskW(s) * 2.3f, dh * 0.4f, dh * 0.2f, Palette.alpha(accent, 0.3f))
            if (i == 2) Draw.strokeCircle(c, px, by - dh * 6.6f, s.px(0.02f), Palette.alpha(accent, 0.5f), s.dp(1.5f))
        }
        // disks
        for (i in 0 until 3) {
            val px = pegX(s, i)
            for (j in pegs[i].indices) {
                val d = pegs[i][j]
                val w = maxDiskW(s) * (0.4f + 0.6f * d / n)
                val cy = by - dh * (j + 0.5f)
                val col = Palette.accents[(d - 1) % Palette.accents.size]
                Draw.roundRectCentered(c, px, cy, w * 2, dh * 0.85f, dh * 0.3f, col)
            }
            // lifted disk
            if (selected == i && pegs[i].isNotEmpty()) {
                val d = pegs[i].last()
                val w = maxDiskW(s) * (0.4f + 0.6f * d / n)
                val col = Palette.accents[(d - 1) % Palette.accents.size]
                Draw.glow(c, px, by - dh * 7.6f, w, col, 0.3f)
                Draw.roundRectCentered(c, px, by - dh * 7.6f, w * 2, dh * 0.85f, dh * 0.3f, col)
            }
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        Ui.edgeProgress(c, s, timeLeft / 120f, Palette.lerp(Palette.red, accent, timeLeft / 120f))
        Draw.textCentered(c, "${Loc.t("MOVES")} $moves", s.cx, s.h * 0.88f, s.px(0.04f), Palette.textDim, Fonts.mono, letterSpacing = 0.08f)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.13f, s.px(0.06f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "towers",
            title = "Towers",
            tagline = "Move the tower",
            category = Category.PUZZLE,
            tier = Tier.MINI,
            accent = Palette.violet,
            icon = IconDrawer { c, cx, cy, size, _ ->
                val cols = Palette.accents
                for (j in 0 until 3) {
                    val w = size * (0.2f + 0.16f * (2 - j))
                    Draw.roundRectCentered(c, cx, cy + size * 0.32f - j * size * 0.2f, w * 2, size * 0.15f, size * 0.05f, cols[j % cols.size])
                }
            },
            create = { HanoiGame() }
        )
    }
}
