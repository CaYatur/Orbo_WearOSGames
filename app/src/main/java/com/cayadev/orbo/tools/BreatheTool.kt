package com.cayadev.orbo.tools

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
import com.cayadev.orbo.engine.Scene
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.engine.Ui

/** Breathe — a calm guided breathing exercise. Follow the glowing orb as it expands to inhale, holds,
 * and contracts to exhale. A gentle haptic marks each phase change. Just breathe and unwind. */
class BreatheTool : Scene() {

    private val inhale = 4f
    private val hold1 = 2f
    private val exhale = 6f
    private val cycle get() = inhale + hold1 + exhale
    private var t = 0f
    private var lastPhase = -1
    private val accent = Palette.mint

    override fun update(dt: Float) {
        t += dt
        val ph = phaseOf(t % cycle)
        if (ph != lastPhase) { lastPhase = ph; engine.haptics.light() }
    }

    private fun phaseOf(p: Float): Int = when { p < inhale -> 0; p < inhale + hold1 -> 1; else -> 2 }

    override fun onTap(x: Float, y: Float) { if (Ui.hitAppBack(sc, x, y)) engine.back() }

    override fun render(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        val p = t % cycle
        val phase = phaseOf(p)
        val scale: Float
        val label: String
        val col: Int
        when (phase) {
            0 -> { scale = 0.35f + 0.65f * Ease.inOutCubic((p / inhale).coerceIn(0f, 1f)); label = "BREATHE IN"; col = Palette.mint }
            1 -> { scale = 1f; label = "HOLD"; col = Palette.cyan }
            else -> { val e = ((p - inhale - hold1) / exhale).coerceIn(0f, 1f); scale = 1f - 0.65f * Ease.inOutCubic(e); label = "BREATHE OUT"; col = Palette.violet }
        }
        val cyc = s.cy - s.px(0.03f)
        val r = s.px(0.34f) * scale
        // soft rings
        for (k in 3 downTo 1) Draw.strokeCircle(c, s.cx, cyc, r + k * s.px(0.03f), Palette.alpha(col, 0.08f), s.dp(2f))
        Draw.glow(c, s.cx, cyc, r * 1.5f, col, 0.3f)
        Draw.fillCircle(c, s.cx, cyc, r, Palette.alpha(col, 0.22f))
        Draw.strokeCircle(c, s.cx, cyc, r, Palette.alpha(col, 0.8f), s.dp(3f))
        // breathing guide dot orbiting subtly
        Draw.textCentered(c, Loc.t(label), s.cx, cyc, s.px(0.06f), Palette.text, Fonts.display)
        Draw.textCentered(c, Loc.t("Just breathe"), s.cx, s.h * 0.9f, s.px(0.038f), Palette.textDim, Fonts.mono, letterSpacing = 0.12f)
        Ui.appBackButton(c, s, t, accent)
    }

    companion object {
        fun meta() = GameMeta(
            id = "breathe",
            title = "Breathe",
            tagline = "Calm your mind",
            category = Category.TOOLS,
            tier = Tier.TOOL,
            accent = Palette.mint,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.strokeCircle(c, cx, cy, size * 0.42f, Palette.alpha(color, 0.3f), size * 0.04f)
                Draw.strokeCircle(c, cx, cy, size * 0.28f, Palette.alpha(color, 0.6f), size * 0.04f)
                Draw.fillCircle(c, cx, cy, size * 0.14f, color)
            },
            create = { BreatheTool() }
        )
    }
}
