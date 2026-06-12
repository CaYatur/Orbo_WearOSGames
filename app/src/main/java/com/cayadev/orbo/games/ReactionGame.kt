package com.cayadev.orbo.games

import android.graphics.Canvas
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.IconDrawer
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Ease
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Loc
import com.cayadev.orbo.engine.Mathx
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame

/**
 * Reaction Time — five rounds of "wait for green, then tap". The faster you react, the more
 * points; jumping the gun costs the round. Reports your best run.
 */
class ReactionGame : ArcadeGame(meta()) {

    private enum class Phase { GETREADY, WAIT, GO, RESULT }
    private var phase = Phase.GETREADY
    private var timer = 0f
    private var waitFor = 0f
    private var round = 0
    private val rounds = 5
    private var lastMs = 0
    private var bestMs = 9999
    private var flashGo = 0f

    override val controlHint = "TAP WHEN GREEN"
    override val gameOverTitle = "RESULTS"

    override fun onReset() {
        round = 0; bestMs = 9999
        beginRound()
    }

    private fun beginRound() {
        round++
        phase = Phase.GETREADY
        timer = 0f
    }

    override fun onStep(dt: Float) {
        timer += dt
        when (phase) {
            Phase.GETREADY -> if (timer > 0.9f) { phase = Phase.WAIT; timer = 0f; waitFor = Mathx.rand(1.1f, 2.9f) }
            Phase.WAIT -> if (timer >= waitFor) { phase = Phase.GO; timer = 0f; flashGo = 1f; engine.sfx.blip(); engine.haptics.light() }
            Phase.GO -> { if (flashGo > 0f) flashGo = (flashGo - dt * 3f).coerceAtLeast(0f) }
            Phase.RESULT -> if (timer > 1.0f) { if (round >= rounds) gameOver() else beginRound() }
        }
    }

    override fun onTapWorld(x: Float, y: Float) {
        when (phase) {
            Phase.WAIT, Phase.GETREADY -> {
                // too soon
                lastMs = -1
                engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.4f)
                phase = Phase.RESULT; timer = 0f
            }
            Phase.GO -> {
                lastMs = (timer * 1000f).toInt()
                if (lastMs < bestMs) bestMs = lastMs
                val pts = (520 - lastMs).coerceAtLeast(5)
                addScore(pts)
                engine.sfx.coin(); engine.haptics.light()
                particles.burst(sc.cx, sc.cy, 18, Palette.lime, sc.px(0.7f), sc.px(0.02f), 0.6f)
                phase = Phase.RESULT; timer = 0f
            }
            else -> {}
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        val bgCol = when (phase) {
            Phase.WAIT, Phase.GETREADY -> Palette.lerp(Palette.bg, 0xFF3A0E12.toInt(), 0.9f)
            Phase.GO -> Palette.lerp(0xFF0E3A1E.toInt(), Palette.lime, 0.15f + 0.25f * flashGo)
            Phase.RESULT -> Palette.bg
        }
        Draw.background(c, bgCol)
        Draw.vignette(c, s, Palette.alpha(Palette.bg, 0f), Palette.alpha(Palette.bg, 0.5f))
        if (state == GState.READY) return

        when (phase) {
            Phase.GETREADY -> {
                Draw.textCentered(c, "${Loc.t("ROUND")} $round/$rounds", s.cx, s.cy - s.px(0.05f), s.px(0.06f), Palette.textDim, Fonts.mono)
                Draw.textCentered(c, Loc.t("GET READY"), s.cx, s.cy + s.px(0.05f), s.px(0.08f), Palette.text, Fonts.display)
            }
            Phase.WAIT -> {
                Draw.textCentered(c, Loc.t("WAIT"), s.cx, s.cy, s.px(0.13f), Palette.red, Fonts.display)
                Draw.textCentered(c, "…", s.cx, s.cy + s.px(0.11f), s.px(0.1f), Palette.alpha(Palette.red, 0.6f), Fonts.display)
            }
            Phase.GO -> {
                val sc2 = Ease.outBack((timer * 4f).coerceIn(0f, 1f))
                Draw.glow(c, s.cx, s.cy, s.px(0.4f), Palette.lime, 0.3f)
                Draw.textCentered(c, Loc.t("TAP!"), s.cx, s.cy, s.px(0.18f) * sc2, Palette.text, Fonts.display)
            }
            Phase.RESULT -> {
                if (lastMs < 0) {
                    Draw.textCentered(c, Loc.t("TOO SOON"), s.cx, s.cy - s.px(0.02f), s.px(0.085f), Palette.red, Fonts.display)
                    Draw.textCentered(c, "+0", s.cx, s.cy + s.px(0.09f), s.px(0.05f), Palette.textDim, Fonts.mono)
                } else {
                    Draw.textCentered(c, "$lastMs ms", s.cx, s.cy - s.px(0.02f), s.px(0.1f), Palette.lime, Fonts.display)
                    Draw.textCentered(c, "+${(520 - lastMs).coerceAtLeast(5)}", s.cx, s.cy + s.px(0.09f), s.px(0.05f), accent, Fonts.mono)
                }
            }
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.13f, s.px(0.06f), Palette.alpha(Palette.text, 0.85f), Fonts.mono)
    }

    companion object {
        fun meta() = GameMeta(
            id = "reaction",
            title = "Reaction",
            tagline = "Wait for green",
            category = Category.REFLEX,
            tier = Tier.MINI,
            accent = Palette.lime,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.fillCircle(c, cx, cy - size * 0.18f, size * 0.16f, Palette.red)
                Draw.fillCircle(c, cx, cy + size * 0.18f, size * 0.16f, Palette.lime)
                Draw.strokeCircle(c, cx, cy, size * 0.42f, Palette.alpha(color, 0.5f), size * 0.05f)
            },
            create = { ReactionGame() }
        )
    }
}
