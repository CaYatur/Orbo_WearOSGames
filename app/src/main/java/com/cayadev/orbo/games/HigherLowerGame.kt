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
 * Higher or Lower — will the next card beat this one? Call it. A correct call builds your streak and
 * score; a wrong call costs a life. Ties are safe. Three lives. Simple nerve and a little luck.
 */
class HigherLowerGame : ArcadeGame(meta()) {

    private enum class Phase { CHOOSE, REVEAL }
    private var phase = Phase.CHOOSE
    private var current = 7
    private var next = 7
    private var guessHigher = true
    private var lives = 3
    private var streak = 0
    private var revealT = 0f
    private var correct = false
    private var tie = false

    override val controlHint = "HIGHER OR LOWER?"

    override fun onReset() { lives = 3; streak = 0; current = Mathx.randRange(2, 13); phase = Phase.CHOOSE }

    private fun guess(higher: Boolean) {
        if (phase != Phase.CHOOSE) return
        guessHigher = higher
        next = Mathx.randRange(1, 13)
        tie = next == current
        correct = if (tie) true else if (higher) next > current else next < current
        phase = Phase.REVEAL; revealT = 1.2f
        if (tie) { engine.sfx.blip() }
        else if (correct) { streak++; addScore(10 + streak * 2); engine.sfx.success(); engine.haptics.success() }
        else { lives--; engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.2f) }
    }

    override fun onStep(dt: Float) {
        if (phase == Phase.REVEAL) {
            revealT -= dt
            if (revealT <= 0f) {
                if (!correct && lives <= 0) { gameOver(); return }
                current = next; phase = Phase.CHOOSE
            }
        }
    }

    private fun btn(s: ScreenInfo, left: Boolean): FloatArray {
        val w = s.px(0.32f); val h = s.px(0.13f)
        return floatArrayOf(if (left) s.cx - s.px(0.19f) else s.cx + s.px(0.19f), s.h * 0.82f, w, h)
    }

    override fun onTapWorld(x: Float, y: Float) {
        if (phase != Phase.CHOOSE) return
        val l = btn(sc, true); val r = btn(sc, false)
        if (kotlin.math.abs(x - l[0]) < l[2] / 2 && kotlin.math.abs(y - l[1]) < l[3] / 2) guess(true)
        else if (kotlin.math.abs(x - r[0]) < r[2] / 2 && kotlin.math.abs(y - r[1]) < r[3] / 2) guess(false)
    }

    private fun rankStr(r: Int) = when (r) { 1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> "$r" }

    private fun drawCard(c: Canvas, s: ScreenInfo, cx: Float, cy: Float, rank: Int, faded: Boolean) {
        val w = s.px(0.22f); val h = s.px(0.32f)
        Draw.roundRectCentered(c, cx, cy, w, h, w * 0.14f, if (faded) Palette.panelHi else Palette.text)
        Draw.roundRectStroke(c, cx - w / 2, cy - h / 2, w, h, w * 0.14f, Palette.alpha(Palette.line, 0.8f), s.dp(1.5f))
        val red = rank % 2 == 0
        val col = if (faded) Palette.textDim else if (red) Palette.red else Palette.bg
        Draw.textCentered(c, rankStr(rank), cx, cy - h * 0.1f, w * 0.5f, col, Fonts.display)
        Draw.textCentered(c, if (red) "♥" else "♠", cx, cy + h * 0.22f, w * 0.35f, col, Fonts.bodyBold)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        if (phase == Phase.REVEAL) {
            drawCard(c, s, s.cx - s.px(0.16f), s.h * 0.42f, current, true)
            drawCard(c, s, s.cx + s.px(0.16f), s.h * 0.42f, next, false)
            val msg = if (tie) "PUSH" else if (correct) "YES" else "NO"
            Draw.textCentered(c, Loc.t(msg), s.cx, s.h * 0.74f, s.px(0.08f), if (tie) Palette.textDim else if (correct) accent else Palette.red, Fonts.display)
        } else {
            drawCard(c, s, s.cx, s.h * 0.42f, current, false)
            val l = btn(s, true); val r = btn(s, false)
            Ui.pill(c, l[0], l[1], l[2], l[3], "▲ " + Loc.t("HIGH"), accent, true)
            Ui.pill(c, r[0], r[1], r[2], r[3], "▼ " + Loc.t("LOW"), Palette.blue, true)
        }
        if (streak > 1) Draw.textCentered(c, "${Loc.t("STREAK")} $streak", s.cx, s.h * 0.2f, s.px(0.04f), accent, Fonts.mono, letterSpacing = 0.1f)
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
            id = "higher_lower",
            title = "Higher or Lower",
            tagline = "Call the card",
            category = Category.CLASSIC,
            tier = Tier.MINI,
            accent = Palette.amber,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx, cy, size * 0.5f, size * 0.72f, size * 0.1f, Palette.text)
                Draw.textCentered(c, "7", cx, cy, size * 0.4f, Palette.bg, Fonts.display)
                Draw.line(c, cx + size * 0.42f, cy - size * 0.1f, cx + size * 0.52f, cy - size * 0.25f, color, size * 0.05f)
                Draw.line(c, cx + size * 0.52f, cy - size * 0.25f, cx + size * 0.62f, cy - size * 0.1f, color, size * 0.05f)
            },
            create = { HigherLowerGame() }
        )
    }
}
