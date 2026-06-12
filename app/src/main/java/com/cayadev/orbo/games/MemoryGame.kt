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

/** Memory — a glowing four-pad "repeat the sequence" game. Each round adds a step. */
class MemoryGame : ArcadeGame(meta()) {

    private val colors = intArrayOf(Palette.mint, Palette.pink, Palette.amber, Palette.cyan)
    private val tones = floatArrayOf(330f, 415f, 494f, 622f)
    private val seq = ArrayList<Int>()

    private enum class Phase { SHOW, INPUT, GOOD, BAD }
    private var phase = Phase.SHOW
    private var stepTimer = 0f
    private var showIndex = 0
    private var showOn = false
    private var inputPos = 0
    private var flashPad = -1
    private var flashT = 0f

    override val controlHint = "REPEAT THE PATTERN"

    override fun onReset() {
        seq.clear(); seq.add(Mathx.rand(4))
        startShow()
    }

    private fun startShow() {
        phase = Phase.SHOW; showIndex = 0; showOn = false; stepTimer = 0f; flashPad = -1
    }

    private fun lightPad(i: Int) {
        flashPad = i; flashT = 0.42f
        engine.sfx.beep(tones[i], 0.3f, 0.5f); engine.haptics.light()
    }

    override fun onStep(dt: Float) {
        if (flashT > 0f) flashT -= dt
        when (phase) {
            Phase.SHOW -> {
                stepTimer -= dt
                if (stepTimer <= 0f) {
                    if (!showOn) {
                        if (showIndex >= seq.size) { phase = Phase.INPUT; inputPos = 0; flashPad = -1 }
                        else { lightPad(seq[showIndex]); showOn = true; stepTimer = 0.42f }
                    } else {
                        showOn = false; flashPad = -1; stepTimer = 0.16f; showIndex++
                    }
                }
            }
            Phase.GOOD -> { stepTimer -= dt; if (stepTimer <= 0f) { seq.add(Mathx.rand(4)); startShow() } }
            Phase.BAD -> { stepTimer -= dt; if (stepTimer <= 0f) gameOver() }
            else -> {}
        }
    }

    override fun onTapWorld(x: Float, y: Float) {
        if (phase != Phase.INPUT) return
        val pad = padAt(x, y)
        if (pad < 0) return
        lightPad(pad)
        if (pad == seq[inputPos]) {
            inputPos++
            particles.burst(x, y, 8, colors[pad], sc.px(0.4f), sc.px(0.014f), 0.35f)
            if (inputPos >= seq.size) {
                addScore(1); engine.sfx.coin()
                phase = Phase.GOOD; stepTimer = 0.55f
            }
        } else {
            flash(Palette.red, 0.4f); shake(sc.px(0.02f)); engine.sfx.fail()
            phase = Phase.BAD; stepTimer = 0.5f
        }
    }

    private fun inner(s: ScreenInfo) = s.r * 0.34f
    private fun outer(s: ScreenInfo) = s.r * 0.84f

    private fun padAt(x: Float, y: Float): Int {
        val dx = x - sc.cx; val dy = y - sc.cy
        val rad = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (rad < inner(sc) || rad > outer(sc)) return -1
        var deg = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (deg < 0) deg += 360f
        return (deg / 90f).toInt().coerceIn(0, 3)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return

        val mid = (inner(s) + outer(s)) / 2f
        val w = outer(s) - inner(s)
        for (i in 0 until 4) {
            val on = flashPad == i
            val col = colors[i]
            val a = if (on) 1f else 0.42f
            if (on) Draw.arc(c, s.cx, s.cy, mid, i * 90f + 4f, 82f, Palette.alpha(col, 0.25f), w * 1.25f)
            Draw.arc(c, s.cx, s.cy, mid, i * 90f + 4f, 82f, Palette.alpha(col, a), w)
        }
        // center hub
        Draw.fillCircle(c, s.cx, s.cy, inner(s) * 0.8f, Palette.panel)
        Draw.strokeCircle(c, s.cx, s.cy, inner(s) * 0.8f, Palette.line, s.dp(1.5f))
        val label = when (phase) {
            Phase.SHOW -> Loc.t("WATCH"); Phase.INPUT -> Loc.t("GO"); Phase.GOOD -> "✓"; Phase.BAD -> "✗"
        }
        val lc = when (phase) { Phase.GOOD -> Palette.mint; Phase.BAD -> Palette.red; Phase.INPUT -> accent; else -> Palette.textDim }
        Draw.textCentered(c, label, s.cx, s.cy, inner(s) * 0.5f, lc, Fonts.bodyBold)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "${Loc.t("LV")} $score", s.cx, s.h * 0.115f, s.px(0.05f), Palette.alpha(Palette.text, 0.8f), Fonts.mono)
    }

    companion object {
        fun meta() = GameMeta(
            id = "memory",
            title = "Memory",
            tagline = "Repeat the pattern",
            category = Category.BRAIN,
            tier = Tier.MINI,
            accent = Palette.mint,
            icon = IconDrawer { c, cx, cy, size, _ ->
                val cols = intArrayOf(Palette.mint, Palette.pink, Palette.amber, Palette.cyan)
                val mid = size * 0.34f; val w = size * 0.22f
                for (i in 0 until 4) Draw.arc(c, cx, cy, mid, i * 90f + 5f, 80f, cols[i], w)
            },
            create = { MemoryGame() }
        )
    }
}
