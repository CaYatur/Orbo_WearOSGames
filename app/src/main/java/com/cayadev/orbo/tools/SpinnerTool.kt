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

/** Spinner — a decision wheel. Tap to send it spinning; it slows to a stop and the pointer picks a
 * number. Great for settling arguments, choosing turns, or picking at random. */
class SpinnerTool : Scene() {

    private val segs = 8
    private var rot = 0f
    private var vel = 0f
    private var t = 0f
    private var result = -1
    private var resultFx = 0f
    private val accent = Palette.violet

    override fun update(dt: Float) {
        t += dt
        if (resultFx > 0f) resultFx -= dt
        if (kotlin.math.abs(vel) > 0.001f) {
            rot += vel * dt
            vel *= (1f - 1.1f * dt)
            if (kotlin.math.abs(vel) < 0.15f) { vel = 0f; settle() }
        }
        rot %= Mathx.TAU; if (rot < 0) rot += Mathx.TAU
    }

    private fun spin() {
        if (kotlin.math.abs(vel) > 0.001f) return
        vel = Mathx.rand(9f, 15f)
        result = -1
        engine.sfx.swoosh(); engine.haptics.light()
    }

    private fun settle() {
        val segAng = Mathx.TAU / segs
        // pointer at top = screen angle -PI/2
        var a = (-Math.PI.toFloat() / 2f - rot) % Mathx.TAU
        if (a < 0) a += Mathx.TAU
        result = (a / segAng).toInt().coerceIn(0, segs - 1)
        resultFx = 1.2f
        engine.sfx.success(); engine.haptics.success()
    }

    override fun onTap(x: Float, y: Float) {
        if (Ui.hitAppBack(sc, x, y)) { engine.back(); return }
        spin()
    }

    override fun render(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        val cyc = s.cy; val r = if (s.isRound) s.r * 0.72f else s.minDim * 0.4f
        val segAng = Mathx.TAU / segs
        for (i in 0 until segs) {
            val start = rot + i * segAng
            val col = Palette.accents[i % Palette.accents.size]
            val on = result == i && resultFx > 0f
            Draw.arc(c, s.cx, cyc, r * 0.6f, Math.toDegrees(start.toDouble()).toFloat(), Math.toDegrees(segAng.toDouble()).toFloat() + 1f,
                if (on) col else Palette.alpha(col, 0.8f), r * 0.6f, android.graphics.Paint.Cap.BUTT)
            // number label at mid-angle
            val mid = start + segAng / 2
            val lx = s.cx + Mathx.cosf(mid) * r * 0.62f; val ly = cyc + Mathx.sinf(mid) * r * 0.62f
            Draw.textCentered(c, "${i + 1}", lx, ly, r * 0.16f, Palette.bg, Fonts.display)
        }
        Draw.fillCircle(c, s.cx, cyc, r * 0.16f, Palette.panelHi)
        Draw.strokeCircle(c, s.cx, cyc, r * 0.16f, Palette.alpha(accent, 0.6f), s.dp(2f))
        // pointer at top
        val p = android.graphics.Path()
        p.moveTo(s.cx, cyc - r * 0.62f + s.px(0.02f)); p.lineTo(s.cx - s.px(0.04f), cyc - r * 0.62f - s.px(0.04f)); p.lineTo(s.cx + s.px(0.04f), cyc - r * 0.62f - s.px(0.04f)); p.close()
        Draw.paint.reset(); Draw.paint.isAntiAlias = true; Draw.paint.color = Palette.text
        c.drawPath(p, Draw.paint)
        if (result >= 0 && vel == 0f) Draw.textCentered(c, "${result + 1}", s.cx, cyc, r * 0.22f, accent, Fonts.display)
        Ui.appBackButton(c, s, t, accent)
    }

    companion object {
        fun meta() = GameMeta(
            id = "spinner",
            title = "Spinner",
            tagline = "Spin to decide",
            category = Category.TOOLS,
            tier = Tier.TOOL,
            accent = Palette.violet,
            icon = IconDrawer { c, cx, cy, size, _ ->
                val cols = Palette.accents
                for (i in 0 until 6) Draw.arc(c, cx, cy, size * 0.22f, i * 60f, 60f, cols[i % cols.size], size * 0.44f, android.graphics.Paint.Cap.BUTT)
                Draw.fillCircle(c, cx, cy, size * 0.1f, Palette.panelHi)
            },
            create = { SpinnerTool() }
        )
    }
}
