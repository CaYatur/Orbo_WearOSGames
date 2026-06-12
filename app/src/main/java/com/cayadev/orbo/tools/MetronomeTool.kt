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

/** Metronome — keep time with a swinging pendulum, a click and a haptic pulse on every beat. Tap the
 * dial to start or stop; use the buttons or bezel to set the tempo from 40 to 240 BPM. */
class MetronomeTool : Scene() {

    private var bpm = 100
    private var playing = false
    private var phase = 0f        // continuous swing phase (radians)
    private var beatTimer = 0f
    private var flash = 0f
    private var t = 0f
    private val accent = Palette.cyan

    override fun enter() { bpm = engine.scores.getInt("metro_bpm", 100).coerceIn(40, 240) }

    private fun interval() = 60f / bpm

    override fun update(dt: Float) {
        t += dt
        if (flash > 0f) flash -= dt
        if (playing) {
            phase += (Math.PI.toFloat() / interval()) * dt
            beatTimer += dt
            if (beatTimer >= interval()) {
                beatTimer -= interval()
                flash = 0.12f
                engine.sfx.beep(1400f, 0.04f, 0.5f); engine.haptics.medium()
            }
        }
    }

    private fun toggle() { playing = !playing; beatTimer = 0f; phase = 0f; engine.sfx.blip(); engine.haptics.light() }
    private fun setBpm(v: Int) { bpm = v.coerceIn(40, 240); engine.scores.setInt("metro_bpm", bpm); engine.sfx.click(); engine.haptics.tick() }

    override fun onTap(x: Float, y: Float) {
        if (Ui.hitAppBack(sc, x, y)) { engine.back(); return }
        val s = sc
        if (y > s.h * 0.82f) {
            if (x < s.cx - s.px(0.1f)) { setBpm(bpm - 4); return }
            if (x > s.cx + s.px(0.1f)) { setBpm(bpm + 4); return }
        }
        if (Mathx.dist(x, y, s.cx, s.cy) < s.px(0.3f)) toggle()
    }

    override fun onRotary(deltaPx: Float) { setBpm(bpm + if (deltaPx > 0) 2 else -2) }

    override fun render(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        val pivotY = s.h * 0.78f; val len = s.px(0.5f)
        val maxAng = 0.5f
        val ang = if (playing) Mathx.sinf(phase) * maxAng else 0f
        // pendulum
        val bx = s.cx + Mathx.sinf(ang) * len; val by = pivotY - Mathx.cosf(ang) * len
        Draw.line(c, s.cx, pivotY, bx, by, Palette.alpha(accent, 0.6f), s.dp(3f))
        Draw.glow(c, bx, by, s.px(0.06f), accent, 0.3f)
        Draw.fillCircle(c, bx, by, s.px(0.04f), accent)
        Draw.fillCircle(c, s.cx, pivotY, s.px(0.02f), Palette.textDim)
        // beat flash ring
        if (flash > 0f) Draw.strokeCircle(c, s.cx, s.cy - s.px(0.04f), s.px(0.28f) + flash * s.px(0.2f), Palette.alpha(accent, flash * 5f), s.dp(3f))
        // center dial
        Draw.fillCircle(c, s.cx, s.cy - s.px(0.04f), s.px(0.22f), Palette.panelHi)
        Draw.strokeCircle(c, s.cx, s.cy - s.px(0.04f), s.px(0.22f), Palette.alpha(accent, 0.6f), s.dp(2f))
        Draw.textCentered(c, "$bpm", s.cx, s.cy - s.px(0.07f), s.px(0.11f), Palette.text, Fonts.display)
        Draw.textCentered(c, "BPM", s.cx, s.cy + s.px(0.04f), s.px(0.035f), Palette.textDim, Fonts.mono, letterSpacing = 0.2f)
        Draw.textCentered(c, Loc.t(if (playing) "STOP" else "GO"), s.cx, s.cy + s.px(0.11f), s.px(0.04f), accent, Fonts.bodyBold)
        // bpm buttons
        Draw.textCentered(c, "−", s.cx - s.px(0.2f), s.h * 0.88f, s.px(0.08f), Palette.textDim, Fonts.display)
        Draw.textCentered(c, "+", s.cx + s.px(0.2f), s.h * 0.88f, s.px(0.08f), Palette.textDim, Fonts.display)
        Ui.appBackButton(c, s, t, accent)
    }

    companion object {
        fun meta() = GameMeta(
            id = "metronome",
            title = "Metronome",
            tagline = "Keep the tempo",
            category = Category.TOOLS,
            tier = Tier.TOOL,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                val p = android.graphics.Path()
                p.moveTo(cx - size * 0.3f, cy + size * 0.4f); p.lineTo(cx + size * 0.3f, cy + size * 0.4f); p.lineTo(cx + size * 0.16f, cy - size * 0.4f); p.lineTo(cx - size * 0.16f, cy - size * 0.4f); p.close()
                Draw.paint.reset(); Draw.paint.isAntiAlias = true; Draw.paint.color = Palette.alpha(color, 0.3f); c.drawPath(p, Draw.paint)
                Draw.line(c, cx, cy + size * 0.35f, cx + size * 0.18f, cy - size * 0.3f, color, size * 0.05f)
                Draw.fillCircle(c, cx + size * 0.18f, cy - size * 0.3f, size * 0.08f, color)
            },
            create = { MetronomeTool() }
        )
    }
}
