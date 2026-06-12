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
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame

/**
 * Pig — the push-your-luck dice race to 100. Roll to build your turn total, but roll a 1 and you lose
 * it all. Hold to bank it. First to 100 wins. Beat the AI to score and play on; lose and the run ends.
 */
class PigGame : ArcadeGame(meta()) {

    private val target = 100
    private var pTotal = 0
    private var aiTotal = 0
    private var turnTotal = 0
    private var turn = 1
    private var die = 1
    private var rollFx = 0f
    private var aiTimer = 0f
    private var ended = false
    private var endTimer = 0f
    private var resultWin = 0
    private var lastRollOne = false

    override val controlHint = "ROLL TO BUILD · HOLD TO BANK"

    override fun onReset() { pTotal = 0; aiTotal = 0; turnTotal = 0; turn = 1; die = 1; ended = false; endTimer = 0f; resultWin = 0; lastRollOne = false }

    private fun roll(who: Int) {
        die = Mathx.randRange(1, 6); rollFx = 0.25f
        engine.sfx.blip(); engine.haptics.light()
        if (die == 1) { turnTotal = 0; lastRollOne = true; switchTurn(who) }
        else { turnTotal += die; lastRollOne = false }
    }

    private fun hold(who: Int) {
        if (who == 1) pTotal += turnTotal else aiTotal += turnTotal
        turnTotal = 0
        engine.sfx.coin(); engine.haptics.medium()
        if ((who == 1 && pTotal >= target) || (who == 2 && aiTotal >= target)) endGame(who)
        else switchTurn(who)
    }

    private fun switchTurn(who: Int) { turnTotal = 0; turn = 3 - who; aiTimer = 0f }

    private fun endGame(winner: Int) {
        ended = true; resultWin = winner
        if (winner == 1) { addScore(100); engine.sfx.success(); engine.haptics.success(); flash(accent, 0.25f) }
        else { engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.25f) }
        endTimer = 2f
    }

    override fun onStep(dt: Float) {
        if (rollFx > 0f) rollFx -= dt
        if (ended) { endTimer -= dt; if (endTimer <= 0f) { if (resultWin == 1) onReset() else gameOver() }; return }
        if (turn == 2) {
            aiTimer += dt
            if (aiTimer > 0.7f) {
                aiTimer = 0f
                // AI: hold at 20+, or if it can win, or if behind push a bit more
                if (aiTotal + turnTotal >= target) hold(2)
                else if (turnTotal >= 20) hold(2)
                else roll(2)
            }
        }
    }

    private fun btn(s: ScreenInfo, left: Boolean): FloatArray {
        val w = s.px(0.32f); val h = s.px(0.13f)
        return floatArrayOf(if (left) s.cx - s.px(0.19f) else s.cx + s.px(0.19f), s.h * 0.83f, w, h)
    }

    override fun onTapWorld(x: Float, y: Float) {
        if (ended || turn != 1) return
        val l = btn(sc, true); val r = btn(sc, false)
        if (kotlin.math.abs(x - l[0]) < l[2] / 2 && kotlin.math.abs(y - l[1]) < l[3] / 2) roll(1)
        else if (kotlin.math.abs(x - r[0]) < r[2] / 2 && kotlin.math.abs(y - r[1]) < r[3] / 2) hold(1)
    }

    private fun drawDie(c: Canvas, s: ScreenInfo, cx: Float, cy: Float, v: Int) {
        val sz = s.px(0.16f) * (1f + 0.06f * rollFx.coerceIn(0f, 1f))
        Draw.roundRectCentered(c, cx, cy, sz, sz, sz * 0.18f, Palette.text)
        val pip = sz * 0.1f; val o = sz * 0.26f
        val dots = when (v) {
            1 -> listOf(0f to 0f)
            2 -> listOf(-o to -o, o to o)
            3 -> listOf(-o to -o, 0f to 0f, o to o)
            4 -> listOf(-o to -o, o to -o, -o to o, o to o)
            5 -> listOf(-o to -o, o to -o, 0f to 0f, -o to o, o to o)
            else -> listOf(-o to -o, o to -o, -o to 0f, o to 0f, -o to o, o to o)
        }
        for (d in dots) Draw.fillCircle(c, cx + d.first, cy + d.second, pip, Palette.bg)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        // scores
        Draw.textCentered(c, Loc.t("YOU"), s.cx - s.px(0.2f), s.h * 0.16f, s.px(0.035f), accent, Fonts.mono)
        Draw.textCentered(c, "$pTotal", s.cx - s.px(0.2f), s.h * 0.23f, s.px(0.07f), Palette.text, Fonts.display)
        Draw.textCentered(c, "AI", s.cx + s.px(0.2f), s.h * 0.16f, s.px(0.035f), Palette.red, Fonts.mono)
        Draw.textCentered(c, "$aiTotal", s.cx + s.px(0.2f), s.h * 0.23f, s.px(0.07f), Palette.text, Fonts.display)
        // die + turn total
        drawDie(c, s, s.cx, s.h * 0.46f, die)
        Draw.textCentered(c, "+$turnTotal", s.cx, s.h * 0.62f, s.px(0.06f),
            if (turnTotal > 0) accent else Palette.textDim, Fonts.display)
        if (ended) Draw.textCentered(c, Loc.t(if (resultWin == 1) "YOU WIN" else "AI WINS"), s.cx, s.h * 0.83f, s.px(0.06f), if (resultWin == 1) accent else Palette.red, Fonts.display)
        else if (turn == 1) {
            val l = btn(s, true); val r = btn(s, false)
            Ui.pill(c, l[0], l[1], l[2], l[3], Loc.t("ROLL"), accent, true)
            Ui.pill(c, r[0], r[1], r[2], r[3], Loc.t("HOLD"), Palette.amber, true)
        } else Draw.textCentered(c, Loc.t("AI…"), s.cx, s.h * 0.83f, s.px(0.045f), Palette.textDim, Fonts.mono)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.08f, s.px(0.045f), accent, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "pig_dice",
            title = "Pig",
            tagline = "Press your luck",
            category = Category.CLASSIC,
            tier = Tier.MINI,
            accent = Palette.pink,
            icon = IconDrawer { c, cx, cy, size, _ ->
                Draw.roundRectCentered(c, cx, cy, size * 0.7f, size * 0.7f, size * 0.14f, Palette.text)
                val o = size * 0.2f
                for (d in listOf(-o to -o, o to -o, 0f to 0f, -o to o, o to o)) Draw.fillCircle(c, cx + d.first, cy + d.second, size * 0.07f, Palette.bg)
            },
            create = { PigGame() }
        )
    }
}
