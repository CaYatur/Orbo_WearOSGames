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
 * Digit Span — memory for numbers. Watch the digits flash one by one, then key them back in order on
 * the pad. Every clean recall adds a digit to the next sequence; one slip ends the run. Your score is
 * the longest span you reproduce.
 */
class DigitSpanGame : ArcadeGame(meta()) {

    private enum class Phase { SHOW, INPUT }
    private var phase = Phase.SHOW
    private val seq = ArrayList<Int>()
    private var showIdx = 0
    private var showOn = false
    private var timer = 0f
    private var inputPos = 0
    private var flashKey = -1
    private var flashT = 0f

    override val controlHint = "WATCH THEN KEY IT BACK"

    override fun onReset() {
        seq.clear(); repeat(3) { seq.add(Mathx.rand(10)) }
        startShow()
    }

    private fun startShow() { phase = Phase.SHOW; showIdx = 0; showOn = false; timer = 0.4f; inputPos = 0 }

    override fun onStep(dt: Float) {
        if (flashT > 0f) flashT -= dt
        if (phase == Phase.SHOW) {
            timer -= dt
            if (timer <= 0f) {
                if (!showOn) {
                    if (showIdx >= seq.size) { phase = Phase.INPUT }
                    else { showOn = true; timer = 0.55f; engine.sfx.beep(440f + seq[showIdx] * 40f, 0.2f, 0.4f) }
                } else { showOn = false; showIdx++; timer = 0.22f }
            }
        }
    }

    private fun keyRect(s: ScreenInfo, idx: Int): FloatArray {
        // idx 0..9 -> 2 rows of 5
        val row = idx / 5; val col = idx % 5
        val bw = (if (s.isRound) s.minDim * 0.68f else s.minDim * 0.86f) / 5f
        val startX = s.cx - bw * 2.5f + bw / 2
        val y = s.h * 0.66f + row * s.px(0.15f)
        return floatArrayOf(startX + col * bw, y, bw * 0.42f)
    }
    private fun keyDigit(idx: Int) = (idx + 1) % 10   // idx0->1 ... idx8->9, idx9->0

    override fun onTapWorld(x: Float, y: Float) {
        if (phase != Phase.INPUT) return
        for (i in 0 until 10) {
            val r = keyRect(sc, i)
            if (Mathx.dist(x, y, r[0], r[1]) < r[2] * 1.2f) {
                val d = keyDigit(i)
                flashKey = i; flashT = 0.2f
                if (d == seq[inputPos]) {
                    inputPos++; engine.sfx.click(); engine.haptics.light()
                    if (inputPos >= seq.size) {
                        score = seq.size
                        engine.sfx.success(); engine.haptics.success(); flash(accent, 0.2f)
                        seq.add(Mathx.rand(10)); startShow()
                    }
                } else {
                    engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.3f); shake(sc.px(0.012f))
                    gameOver()
                }
                return
            }
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        if (phase == Phase.SHOW) {
            Draw.textCentered(c, Loc.t("WATCH"), s.cx, s.h * 0.2f, s.px(0.045f), Palette.textDim, Fonts.mono, letterSpacing = 0.18f)
            if (showOn && showIdx < seq.size) {
                Draw.glow(c, s.cx, s.h * 0.42f, s.px(0.18f), accent, 0.25f)
                Draw.textCentered(c, "${seq[showIdx]}", s.cx, s.h * 0.42f, s.px(0.28f), Palette.text, Fonts.display)
            }
            // progress dots
            for (i in seq.indices) Draw.fillCircle(c, s.cx + (i - (seq.size - 1) / 2f) * s.px(0.045f), s.h * 0.62f, s.px(0.012f),
                if (i <= showIdx) accent else Palette.alpha(Palette.line, 0.6f))
        } else {
            Draw.textCentered(c, "$inputPos / ${seq.size}", s.cx, s.h * 0.18f, s.px(0.06f), Palette.text, Fonts.display)
            for (i in 0 until 10) {
                val r = keyRect(s, i)
                val on = flashKey == i && flashT > 0f
                Draw.fillCircle(c, r[0], r[1], r[2], if (on) accent else Palette.panelHi)
                Draw.strokeCircle(c, r[0], r[1], r[2], Palette.alpha(accent, 0.4f), s.dp(1.4f))
                Draw.textCentered(c, "${keyDigit(i)}", r[0], r[1], r[2] * 0.9f, if (on) Palette.bg else Palette.text, Fonts.display)
            }
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) { /* span shown in-scene */ }

    companion object {
        fun meta() = GameMeta(
            id = "digit_span",
            title = "Digit Span",
            tagline = "Recall the digits",
            category = Category.BRAIN,
            tier = Tier.MINI,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.textCentered(c, "5 2", cx, cy - size * 0.18f, size * 0.4f, color, Fonts.display)
                Draw.textCentered(c, "8 1", cx, cy + size * 0.22f, size * 0.4f, Palette.alpha(color, 0.5f), Fonts.display)
            },
            create = { DigitSpanGame() }
        )
    }
}
