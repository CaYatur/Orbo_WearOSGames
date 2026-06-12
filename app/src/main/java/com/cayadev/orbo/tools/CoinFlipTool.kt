package com.cayadev.orbo.tools

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
import com.cayadev.orbo.engine.Scene
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.engine.Ui

/** Coin Flip — tap to toss a coin. A spinning animation lands on heads or tails, and a running tally
 * keeps score of how it's fallen. The fairest tie-breaker on your wrist. */
class CoinFlipTool : Scene() {

    private var flipT = 0f
    private var flipDur = 0f
    private var result = 0          // 0 heads, 1 tails
    private var spins = 0f
    private var heads = 0
    private var tails = 0
    private var t = 0f
    private val accent = Palette.gold

    override fun update(dt: Float) {
        t += dt
        if (flipT > 0f) {
            flipT -= dt
            if (flipT <= 0f) {
                flipT = 0f
                if (result == 0) heads++ else tails++
                engine.sfx.coin(); engine.haptics.medium()
            }
        }
    }

    private fun flip() {
        if (flipT > 0f) return
        result = Mathx.rand(2)
        spins = Mathx.randRange(8, 12) + result * 0.5f
        flipDur = 1.0f; flipT = flipDur
        engine.sfx.blip(); engine.haptics.light()
    }

    override fun onTap(x: Float, y: Float) {
        if (Ui.hitAppBack(sc, x, y)) { engine.back(); return }
        flip()
    }

    override fun render(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        val cyc = s.cy - s.px(0.04f); val r = s.px(0.24f)
        val prog = if (flipDur > 0f) 1f - flipT / flipDur else 1f
        // squash from spinning around horizontal axis
        val angle = if (flipT > 0f) prog * spins * Mathx.TAU else 0f
        val squash = if (flipT > 0f) kotlin.math.abs(Mathx.cosf(angle)) else 1f
        val faceUp = if (flipT > 0f) (Mathx.cosf(angle) >= 0f) else (result == 0)
        val showResult = if (flipT > 0f) faceUp else (result == 0)
        val rr = r * (0.25f + 0.75f * squash)
        Draw.glow(c, s.cx, cyc, r * 1.2f, accent, 0.2f)
        // draw the coin as an ellipse (rounded rect squashed by the spin)
        Draw.roundRectCentered(c, s.cx, cyc, r * 2f, rr * 2f, rr, accent)
        Draw.roundRectStroke(c, s.cx - r, cyc - rr, r * 2f, rr * 2f, rr, Palette.shade(accent, -0.3f), s.dp(3f))
        if (squash > 0.4f) {
            val label = if (showResult) Loc.t("HEADS") else Loc.t("TAILS")
            val sym = if (showResult) "H" else "T"
            Draw.textCentered(c, sym, s.cx, cyc, rr * 1.3f, Palette.shade(accent, -0.5f), Fonts.display)
            if (flipT <= 0f) Draw.textCentered(c, label, s.cx, s.h * 0.74f, s.px(0.06f), accent, Fonts.display)
        }
        // tally
        Draw.textCentered(c, "${Loc.t("HEADS")}  $heads", s.cx - s.px(0.16f), s.h * 0.88f, s.px(0.038f), Palette.textDim, Fonts.mono)
        Draw.textCentered(c, "${Loc.t("TAILS")}  $tails", s.cx + s.px(0.16f), s.h * 0.88f, s.px(0.038f), Palette.textDim, Fonts.mono)
        if (flipT <= 0f && heads + tails == 0) Draw.textCentered(c, Loc.t("TAP TO FLIP"), s.cx, s.h * 0.74f, s.px(0.045f), Palette.textDim, Fonts.mono, letterSpacing = 0.1f)
        Ui.appBackButton(c, s, t, accent)
    }

    companion object {
        fun meta() = GameMeta(
            id = "coin_flip",
            title = "Coin Flip",
            tagline = "Heads or tails",
            category = Category.TOOLS,
            tier = Tier.TOOL,
            accent = Palette.gold,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.fillCircle(c, cx, cy, size * 0.4f, color)
                Draw.strokeCircle(c, cx, cy, size * 0.4f, Palette.shade(color, -0.3f), size * 0.05f)
                Draw.textCentered(c, "H", cx, cy, size * 0.5f, Palette.shade(color, -0.5f), Fonts.display)
            },
            create = { CoinFlipTool() }
        )
    }
}
