package com.cayadev.orbo.tools

import android.graphics.Canvas
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.IconDrawer
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Loc
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.Scene
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.engine.Ui

/** Tally Counter — tap the big circle to count up; use the minus and reset buttons or the rotating
 * bezel to adjust. Persists your count so you never lose your place. Great for laps, reps and crowds. */
class TallyTool : Scene() {

    private var count = 0
    private var bump = 0f
    private var t = 0f
    private val accent = Palette.mint

    override fun enter() { count = engine.scores.getInt("tally_count", 0) }
    private fun save() { engine.scores.setInt("tally_count", count) }

    override fun update(dt: Float) { t += dt; if (bump > 0f) bump -= dt }

    private fun change(d: Int) {
        count = (count + d).coerceIn(0, 99999); bump = 0.2f
        engine.sfx.click(); engine.haptics.light(); save()
    }
    private fun reset() { count = 0; engine.sfx.swoosh(); engine.haptics.medium(); save() }

    override fun onTap(x: Float, y: Float) {
        if (Ui.hitAppBack(sc, x, y)) { engine.back(); return }
        val s = sc
        if (y > s.h * 0.82f) {
            if (x < s.cx - s.px(0.1f)) { change(-1); return }
            if (x > s.cx + s.px(0.1f)) { reset(); return }
        }
        change(1)
    }

    override fun onRotary(deltaPx: Float) { change(if (deltaPx > 0) 1 else -1) }

    override fun render(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        val cyc = s.cy - s.px(0.05f); val r = s.px(0.3f) * (1f + 0.04f * bump.coerceIn(0f, 1f) * 5f)
        Draw.glow(c, s.cx, cyc, r * 1.2f, accent, 0.16f)
        Draw.fillCircle(c, s.cx, cyc, r, Palette.alpha(accent, 0.12f))
        Draw.strokeCircle(c, s.cx, cyc, r, Palette.alpha(accent, 0.7f), s.dp(3f))
        Draw.textCentered(c, "$count", s.cx, cyc, s.px(0.2f), Palette.text, Fonts.display)
        // buttons
        Draw.fillCircle(c, s.cx - s.px(0.2f), s.h * 0.88f, s.px(0.06f), Palette.panelHi)
        Draw.textCentered(c, "−", s.cx - s.px(0.2f), s.h * 0.88f, s.px(0.07f), accent, Fonts.display)
        Draw.fillCircle(c, s.cx + s.px(0.2f), s.h * 0.88f, s.px(0.06f), Palette.panelHi)
        Draw.textCentered(c, "↺", s.cx + s.px(0.2f), s.h * 0.88f, s.px(0.05f), Palette.textDim, Fonts.bodyBold)
        Ui.appBackButton(c, s, t, accent)
    }

    companion object {
        fun meta() = GameMeta(
            id = "tally",
            title = "Tally Counter",
            tagline = "Count anything",
            category = Category.TOOLS,
            tier = Tier.TOOL,
            accent = Palette.mint,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.strokeCircle(c, cx, cy, size * 0.4f, color, size * 0.06f)
                Draw.textCentered(c, "7", cx, cy, size * 0.5f, color, Fonts.display)
            },
            create = { TallyTool() }
        )
    }
}
