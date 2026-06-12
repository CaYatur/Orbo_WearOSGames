package com.cayadev.orbo.tools

import android.app.Activity
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

/** Flashlight — turn the whole screen into a torch at full brightness. Tap to switch it on and off.
 * Restores your screen brightness when you leave. */
class FlashlightTool : Scene() {

    private var on = false
    private var t = 0f
    private val accent = Palette.gold

    private fun setBrightness(v: Float) {
        val act = engine.context as? Activity ?: return
        try { val lp = act.window.attributes; lp.screenBrightness = v; act.window.attributes = lp } catch (_: Throwable) {}
    }

    override fun exit() { setBrightness(-1f) }

    override fun update(dt: Float) { t += dt }

    override fun onTap(x: Float, y: Float) {
        if (!on && Ui.hitAppBack(sc, x, y)) { engine.back(); return }
        on = !on
        setBrightness(if (on) 1f else -1f)
        engine.sfx.click(); engine.haptics.light()
    }

    override fun render(c: Canvas, s: ScreenInfo) {
        if (on) {
            Draw.background(c, 0xFFFFFFFF.toInt())
            // subtle power hint
            Draw.strokeCircle(c, s.cx, s.cy, s.px(0.12f), 0x22000000, s.dp(2f))
            Draw.textCentered(c, Loc.t("TAP TO TURN OFF"), s.cx, s.h * 0.9f, s.px(0.038f), 0x44000000, Fonts.mono, letterSpacing = 0.1f)
            return
        }
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        val r = s.px(0.22f)
        Draw.fillCircle(c, s.cx, s.cy - s.px(0.02f), r, Palette.panelHi)
        Draw.strokeCircle(c, s.cx, s.cy - s.px(0.02f), r, Palette.alpha(accent, 0.6f), s.dp(3f))
        // power glyph
        Draw.arc(c, s.cx, s.cy - s.px(0.02f), r * 0.5f, -60f, 300f, accent, s.dp(4f))
        Draw.line(c, s.cx, s.cy - s.px(0.02f) - r * 0.55f, s.cx, s.cy - s.px(0.02f) - r * 0.1f, accent, s.dp(4f))
        Draw.textCentered(c, Loc.t("TAP TO TURN ON"), s.cx, s.h * 0.82f, s.px(0.045f), Palette.textDim, Fonts.mono, letterSpacing = 0.1f)
        Ui.appBackButton(c, s, t, accent)
    }

    companion object {
        fun meta() = GameMeta(
            id = "flashlight",
            title = "Flashlight",
            tagline = "Light it up",
            category = Category.TOOLS,
            tier = Tier.TOOL,
            accent = Palette.gold,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.fillCircle(c, cx, cy, size * 0.28f, color)
                for (i in 0 until 8) {
                    val a = i * Mathx.TAU / 8f
                    Draw.line(c, cx + Mathx.cosf(a) * size * 0.34f, cy + Mathx.sinf(a) * size * 0.34f, cx + Mathx.cosf(a) * size * 0.46f, cy + Mathx.sinf(a) * size * 0.46f, color, size * 0.05f)
                }
            },
            create = { FlashlightTool() }
        )
    }
}
