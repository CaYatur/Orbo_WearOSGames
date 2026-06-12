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
 * Rock Paper Scissors — best your streak against the AI. Pick your throw; ties replay, wins score,
 * losses cost a life. Three lives. The little classic, refined with juicy reveals.
 */
class RockPaperScissorsGame : ArcadeGame(meta()) {

    private enum class Phase { CHOOSE, REVEAL }
    private var phase = Phase.CHOOSE
    private var playerPick = 0
    private var aiPick = 0
    private var result = 0      // 0 tie, 1 win, 2 lose
    private var lives = 3
    private var streak = 0
    private var revealT = 0f

    override val controlHint = "ROCK · PAPER · SCISSORS"

    override fun onReset() { lives = 3; streak = 0; phase = Phase.CHOOSE }

    private fun pick(p: Int) {
        if (phase != Phase.CHOOSE) return
        playerPick = p; aiPick = Mathx.rand(3)
        result = (playerPick - aiPick + 3) % 3   // 0 tie, 1 player, 2 ai
        phase = Phase.REVEAL; revealT = 1.3f
        when (result) {
            1 -> { streak++; addScore(15 + streak * 3); engine.sfx.success(); engine.haptics.success(); flash(accent, 0.2f) }
            2 -> { lives--; streak = 0; engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.2f) }
            else -> { engine.sfx.blip() }
        }
    }

    override fun onStep(dt: Float) {
        if (phase == Phase.REVEAL) { revealT -= dt; if (revealT <= 0f) { if (result == 2 && lives <= 0) gameOver() else phase = Phase.CHOOSE } }
    }

    private fun choicePos(s: ScreenInfo, i: Int): FloatArray {
        val r = s.px(0.1f)
        return floatArrayOf(s.cx + (i - 1) * s.px(0.26f), s.h * 0.74f, r)
    }

    override fun onTapWorld(x: Float, y: Float) {
        if (phase != Phase.CHOOSE) return
        for (i in 0 until 3) { val p = choicePos(sc, i); if (Mathx.dist(x, y, p[0], p[1]) < p[2] * 1.3f) { pick(i); return } }
    }

    private fun drawShape(c: Canvas, s: ScreenInfo, cx: Float, cy: Float, r: Float, kind: Int, col: Int) {
        when (kind) {
            0 -> { Draw.fillCircle(c, cx, cy, r * 0.8f, col); Draw.fillCircle(c, cx - r * 0.3f, cy - r * 0.2f, r * 0.2f, Palette.alpha(Palette.text, 0.2f)) }   // rock
            1 -> Draw.roundRectCentered(c, cx, cy, r * 1.4f, r * 1.7f, r * 0.2f, col)  // paper
            else -> { // scissors
                Draw.line(c, cx - r * 0.7f, cy + r * 0.7f, cx + r * 0.5f, cy - r * 0.6f, col, s.dp(5f))
                Draw.line(c, cx + r * 0.7f, cy + r * 0.7f, cx - r * 0.5f, cy - r * 0.6f, col, s.dp(5f))
                Draw.fillCircle(c, cx - r * 0.7f, cy + r * 0.7f, r * 0.22f, col)
                Draw.fillCircle(c, cx + r * 0.7f, cy + r * 0.7f, r * 0.22f, col)
            }
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        if (phase == Phase.REVEAL) {
            Draw.textCentered(c, Loc.t("AI"), s.cx, s.h * 0.16f, s.px(0.035f), Palette.red, Fonts.mono)
            drawShape(c, s, s.cx, s.h * 0.3f, s.px(0.1f), aiPick, Palette.red)
            drawShape(c, s, s.cx, s.h * 0.62f, s.px(0.11f), playerPick, accent)
            val msg = if (result == 1) "YOU WIN" else if (result == 2) "AI WINS" else "DRAW"
            Draw.textCentered(c, Loc.t(msg), s.cx, s.h * 0.85f, s.px(0.06f), if (result == 1) accent else if (result == 2) Palette.red else Palette.textDim, Fonts.display)
        } else {
            Draw.textCentered(c, Loc.t("CHOOSE"), s.cx, s.h * 0.32f, s.px(0.05f), Palette.textDim, Fonts.mono, letterSpacing = 0.14f)
            for (i in 0 until 3) {
                val p = choicePos(s, i)
                Draw.fillCircle(c, p[0], p[1], p[2] * 1.25f, Palette.panelHi)
                Draw.strokeCircle(c, p[0], p[1], p[2] * 1.25f, Palette.alpha(accent, 0.4f), s.dp(1.5f))
                drawShape(c, s, p[0], p[1], p[2] * 0.65f, i, accent)
            }
            if (streak > 1) Draw.textCentered(c, "${Loc.t("STREAK")} $streak", s.cx, s.h * 0.2f, s.px(0.04f), accent, Fonts.mono, letterSpacing = 0.1f)
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.045f); val startX = s.cx - s.px(0.2f)
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, s.h * 0.12f, s.px(0.013f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, s.h * 0.12f, s.px(0.011f), Palette.alpha(Palette.red, 0.3f), s.dp(1.2f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx + s.px(0.2f), s.h * 0.12f, s.px(0.045f), accent, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "rps",
            title = "Rock Paper Scissors",
            tagline = "Read the AI",
            category = Category.CLASSIC,
            tier = Tier.MINI,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.fillCircle(c, cx - size * 0.3f, cy - size * 0.2f, size * 0.18f, color)
                Draw.roundRectCentered(c, cx + size * 0.28f, cy - size * 0.2f, size * 0.3f, size * 0.36f, size * 0.06f, Palette.alpha(color, 0.7f))
                Draw.line(c, cx - size * 0.1f, cy + size * 0.4f, cx + size * 0.15f, cy + size * 0.12f, color, size * 0.06f)
                Draw.line(c, cx + size * 0.1f, cy + size * 0.4f, cx - size * 0.15f, cy + size * 0.12f, color, size * 0.06f)
            },
            create = { RockPaperScissorsGame() }
        )
    }
}
