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
 * Dice Poker — roll five dice, hold the keepers, reroll twice, then score the best poker hand. Bigger
 * hands pay more. Play ten rounds and stack up the highest total you can. Tap dice to hold; reroll or
 * bank when you're happy.
 */
class DicePokerGame : ArcadeGame(meta()) {

    private val dice = IntArray(5) { 1 }
    private val held = BooleanArray(5)
    private var rerollsLeft = 2
    private var roundsLeft = 10
    private var lastHand = ""
    private var handFx = 0f
    private var rollFx = 0f

    override val controlHint = "HOLD DICE · REROLL · BANK"
    override val gameOverTitle = "RESULTS"

    override fun onReset() { roundsLeft = 10; lastHand = ""; newRound() }

    private fun newRound() {
        for (i in 0 until 5) { dice[i] = Mathx.randRange(1, 6); held[i] = false }
        rerollsLeft = 2; rollFx = 0.25f
    }

    private fun reroll() {
        if (rerollsLeft <= 0) return
        for (i in 0 until 5) if (!held[i]) dice[i] = Mathx.randRange(1, 6)
        rerollsLeft--; rollFx = 0.25f
        engine.sfx.blip(); engine.haptics.light()
    }

    private fun evaluate(): Pair<String, Int> {
        val counts = IntArray(7)
        for (d in dice) counts[d]++
        val maxc = counts.max()
        val pairs = (1..6).count { counts[it] == 2 }
        val distinct = (1..6).count { counts[it] > 0 }
        val straight = distinct == 5 && (counts[1] == 0 || counts[6] == 0)
        return when {
            maxc == 5 -> "FIVE OF A KIND" to 100
            maxc == 4 -> "FOUR OF A KIND" to 60
            counts.any { it == 3 } && pairs == 1 -> "FULL HOUSE" to 45
            straight -> "STRAIGHT" to 40
            maxc == 3 -> "THREE OF A KIND" to 30
            pairs == 2 -> "TWO PAIR" to 20
            pairs == 1 -> "PAIR" to 10
            else -> "HIGH CARD" to 5
        }
    }

    private fun bank() {
        val (name, pts) = evaluate()
        addScore(pts); lastHand = name; handFx = 1.2f
        engine.sfx.success(); engine.haptics.success(); flash(accent, 0.2f)
        roundsLeft--
        if (roundsLeft <= 0) gameOver() else newRound()
    }

    override fun onStep(dt: Float) { if (rollFx > 0f) rollFx -= dt; if (handFx > 0f) handFx -= dt }

    private fun diePos(s: ScreenInfo, i: Int): FloatArray {
        val sz = s.px(0.13f)
        val spacing = if (s.isRound) s.minDim * 0.155f else s.minDim * 0.17f
        return floatArrayOf(s.cx + (i - 2) * spacing, s.h * 0.45f, sz)
    }
    private fun btn(s: ScreenInfo, left: Boolean): FloatArray {
        val w = s.px(0.32f); val h = s.px(0.12f)
        return floatArrayOf(if (left) s.cx - s.px(0.19f) else s.cx + s.px(0.19f), s.h * 0.82f, w, h)
    }

    override fun onTapWorld(x: Float, y: Float) {
        val s = sc
        for (i in 0 until 5) { val p = diePos(s, i); if (kotlin.math.abs(x - p[0]) < p[2] * 0.7f && kotlin.math.abs(y - p[1]) < p[2] * 0.7f) {
            if (rerollsLeft > 0) { held[i] = !held[i]; engine.sfx.click(); engine.haptics.tick() }; return } }
        val l = btn(s, true); val r = btn(s, false)
        if (kotlin.math.abs(x - l[0]) < l[2] / 2 && kotlin.math.abs(y - l[1]) < l[3] / 2 && rerollsLeft > 0) reroll()
        else if (kotlin.math.abs(x - r[0]) < r[2] / 2 && kotlin.math.abs(y - r[1]) < r[3] / 2) bank()
    }

    private fun drawDie(c: Canvas, s: ScreenInfo, cx: Float, cy: Float, v: Int, hold: Boolean) {
        val sz = s.px(0.13f) * (if (hold) 1.05f else 1f)
        val lift = if (hold) -s.px(0.02f) else 0f
        if (hold) Draw.glow(c, cx, cy + lift, sz * 0.9f, accent, 0.2f)
        Draw.roundRectCentered(c, cx, cy + lift, sz, sz, sz * 0.2f, if (hold) accent else Palette.text)
        val pip = sz * 0.1f; val o = sz * 0.25f; val col = if (hold) Palette.bg else Palette.bg
        val dots = when (v) {
            1 -> listOf(0f to 0f); 2 -> listOf(-o to -o, o to o); 3 -> listOf(-o to -o, 0f to 0f, o to o)
            4 -> listOf(-o to -o, o to -o, -o to o, o to o); 5 -> listOf(-o to -o, o to -o, 0f to 0f, -o to o, o to o)
            else -> listOf(-o to -o, o to -o, -o to 0f, o to 0f, -o to o, o to o)
        }
        for (d in dots) Draw.fillCircle(c, cx + d.first, cy + lift + d.second, pip, col)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        Draw.textCentered(c, "${Loc.t("ROUND")} ${11 - roundsLeft}/10", s.cx, s.h * 0.15f, s.px(0.04f), Palette.textDim, Fonts.mono, letterSpacing = 0.08f)
        // current hand preview
        val (name, pts) = evaluate()
        Draw.textCenteredFit(c, "${Loc.t(name)}  +$pts", s.cx, s.h * 0.27f, s.px(0.05f), s.px(0.86f), accent, Fonts.display)
        for (i in 0 until 5) { val p = diePos(s, i); drawDie(c, s, p[0], p[1], dice[i], held[i]) }
        // reroll dots
        for (i in 0 until 2) Draw.fillCircle(c, s.cx - s.px(0.03f) + i * s.px(0.06f), s.h * 0.6f, s.px(0.014f),
            if (i < rerollsLeft) accent else Palette.alpha(Palette.line, 0.6f))
        val l = btn(s, true); val r = btn(s, false)
        Ui.pill(c, l[0], l[1], l[2], l[3], Loc.t("REROLL"), if (rerollsLeft > 0) accent else Palette.textFaint, rerollsLeft > 0)
        Ui.pill(c, r[0], r[1], r[2], r[3], Loc.t("BANK"), Palette.amber, true)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.08f, s.px(0.05f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "dice_poker",
            title = "Dice Poker",
            tagline = "Roll the best hand",
            category = Category.CLASSIC,
            tier = Tier.MINI,
            accent = Palette.violet,
            icon = IconDrawer { c, cx, cy, size, color ->
                for (i in 0 until 2) for (j in 0 until 2) {
                    val x = cx + (i - 0.5f) * size * 0.44f; val y = cy + (j - 0.5f) * size * 0.44f
                    Draw.roundRectCentered(c, x, y, size * 0.36f, size * 0.36f, size * 0.07f, if ((i + j) % 2 == 0) Palette.text else color)
                    Draw.fillCircle(c, x, y, size * 0.05f, Palette.bg)
                }
            },
            create = { DicePokerGame() }
        )
    }
}
