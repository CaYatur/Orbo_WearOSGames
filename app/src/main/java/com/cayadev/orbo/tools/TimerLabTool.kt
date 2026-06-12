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

/** Timer Lab — a stopwatch and a countdown timer in one. Tap the dial to start/stop; set the timer
 * with the +buttons or the rotating bezel; a finished timer rings and buzzes until you dismiss it. */
class TimerLabTool : Scene() {

    private var mode = 0           // 0 stopwatch, 1 timer
    private var running = false

    private var swMs = 0.0
    private var timerTotal = 0.0
    private var timerLeft = 0.0
    private var alarm = false
    private var alarmT = 0f
    private var t = 0f

    private val accent get() = if (mode == 0) Palette.cyan else Palette.amber

    override fun update(dt: Float) {
        t += dt
        if (running) {
            if (mode == 0) swMs += dt * 1000.0
            else {
                timerLeft -= dt * 1000.0
                if (timerLeft <= 0.0) { timerLeft = 0.0; running = false; triggerAlarm() }
            }
        }
        if (alarm) {
            alarmT += dt
            if ((alarmT * 2f).toInt() != ((alarmT - dt) * 2f).toInt()) { engine.sfx.beep(880f, 0.18f, 0.6f); engine.haptics.heavy() }
            if (alarmT > 6f) alarm = false
        }
    }

    private fun triggerAlarm() { alarm = true; alarmT = 0f; engine.sfx.success(); engine.haptics.error() }

    // ---- layout ----
    private fun tabRect(s: ScreenInfo, i: Int): FloatArray {
        val w = s.px(0.62f); val h = s.px(0.1f); val x = s.cx - w / 2
        return floatArrayOf(x + i * w / 2, s.h * 0.12f - h / 2, w / 2, h)
    }
    private fun centerR(s: ScreenInfo) = s.px(0.3f)
    private fun btn(s: ScreenInfo, pos: Int): FloatArray { // 0 left,1 center,2 right (bottom row)
        val y = s.h * 0.85f; val w = s.px(0.2f); val h = s.px(0.11f)
        val cx = when (pos) { 0 -> s.cx - s.px(0.26f); 2 -> s.cx + s.px(0.26f); else -> s.cx }
        return floatArrayOf(cx - w / 2, y - h / 2, w, h)
    }

    override fun onTap(x: Float, y: Float) {
        if (Ui.hitAppBack(sc, x, y)) { engine.back(); return }
        if (alarm) { alarm = false; engine.sfx.click(); return }
        // tabs
        for (i in 0 until 2) { val r = tabRect(sc, i); if (inR(x, y, r)) { switchMode(i); return } }
        // bottom buttons
        if (mode == 0) {
            if (inR(x, y, btn(sc, 1))) { reset(); return }
        } else {
            if (inR(x, y, btn(sc, 0))) { addTime(60); return }
            if (inR(x, y, btn(sc, 1))) { reset(); return }
            if (inR(x, y, btn(sc, 2))) { addTime(10); return }
        }
        // center toggle
        if (Mathx.dist(x, y, sc.cx, sc.cy) < centerR(sc)) toggleRun()
    }

    override fun onRotary(deltaPx: Float) {
        if (mode == 1 && !running) {
            val delta = if (deltaPx > 0) 5 else -5
            timerTotal = (timerTotal + delta * 1000).coerceIn(0.0, 99 * 60 * 1000.0)
            timerLeft = timerTotal
            engine.haptics.tick()
        }
    }

    private fun inR(x: Float, y: Float, r: FloatArray) = x in r[0]..(r[0] + r[2]) && y in r[1]..(r[1] + r[3])

    private fun switchMode(m: Int) { if (mode == m) return; mode = m; running = false; alarm = false; engine.sfx.click(); engine.haptics.light() }
    private fun toggleRun() {
        if (mode == 1 && timerLeft <= 0.0) return
        running = !running; engine.sfx.blip(); engine.haptics.medium()
    }
    private fun reset() {
        running = false; alarm = false
        if (mode == 0) swMs = 0.0 else { timerLeft = timerTotal }
        engine.sfx.click(); engine.haptics.light()
    }
    private fun addTime(sec: Int) {
        timerTotal = (timerTotal + sec * 1000).coerceIn(0.0, 99 * 60 * 1000.0)
        timerLeft = timerTotal; engine.sfx.click(); engine.haptics.light()
    }

    private fun fmt(ms: Double, centi: Boolean): String {
        val totalCenti = (ms / 10).toLong()
        val cs = (totalCenti % 100).toInt()
        val totalSec = totalCenti / 100
        val ss = (totalSec % 60).toInt()
        val mm = (totalSec / 60).toInt()
        return if (centi) String.format("%02d:%02d.%02d", mm, ss, cs) else String.format("%02d:%02d", mm, ss)
    }

    override fun render(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)

        // tabs
        for (i in 0 until 2) {
            val r = tabRect(s, i); val on = mode == i
            val label = Loc.t(if (i == 0) "STOPWATCH" else "TIMER")
            Draw.roundRect(c, r[0], r[1], r[2], r[3], r[3] / 2, if (on) Palette.alpha(accent, 0.22f) else Palette.alpha(Palette.panel, 0.6f))
            Draw.textCenteredFit(c, label, r[0] + r[2] / 2, r[1] + r[3] / 2, r[3] * 0.34f, r[2] * 0.86f, if (on) accent else Palette.textDim, Fonts.bodyBold)
        }

        // progress ring
        val rr = s.px(0.36f)
        Draw.strokeCircle(c, s.cx, s.cy, rr, Palette.track, s.dp(7f))
        val frac = if (mode == 0) ((swMs / 60000.0) % 1.0).toFloat() else if (timerTotal > 0) (1f - (timerLeft / timerTotal).toFloat()) else 0f
        if (frac > 0f || running) Draw.arc(c, s.cx, s.cy, rr, -90f, 360f * frac.coerceIn(0f, 1f), accent, s.dp(7f))

        // time text
        val time = if (mode == 0) fmt(swMs, true) else fmt(timerLeft, false)
        val flashOn = alarm && (alarmT * 2f).toInt() % 2 == 0
        Draw.textCentered(c, time, s.cx, s.cy - s.px(0.01f), s.px(if (mode == 0) 0.115f else 0.15f), if (flashOn) Palette.red else Palette.text, Fonts.display)
        val sub = Loc.t(if (alarm) "TIME!" else if (running) "RUNNING" else if (mode == 1 && timerLeft <= 0.0) "SET TIME ↻" else "PAUSED")
        Draw.textCentered(c, sub, s.cx, s.cy + s.px(0.11f), s.px(0.035f), if (alarm) Palette.red else Palette.textDim, Fonts.mono, letterSpacing = 0.14f)

        // bottom buttons
        if (mode == 0) {
            drawBtn(c, s, btn(s, 1), Loc.t(if (running) "STOP" else "RESET"), Palette.textDim)
        } else {
            drawBtn(c, s, btn(s, 0), "+1m", accent)
            drawBtn(c, s, btn(s, 1), Loc.t("RESET"), Palette.textDim)
            drawBtn(c, s, btn(s, 2), "+10s", accent)
        }
        Ui.appBackButton(c, s, t, accent)
    }

    private fun drawBtn(c: Canvas, s: ScreenInfo, r: FloatArray, label: String, col: Int) {
        Draw.roundRect(c, r[0], r[1], r[2], r[3], r[3] / 2, Palette.alpha(Palette.panelHi, 0.92f))
        Draw.roundRectStroke(c, r[0], r[1], r[2], r[3], r[3] / 2, Palette.alpha(col, 0.5f), s.dp(1.4f))
        Draw.textCenteredFit(c, label, r[0] + r[2] / 2, r[1] + r[3] / 2, r[3] * 0.38f, r[2] * 0.86f, col, Fonts.bodyBold)
    }

    companion object {
        fun meta() = GameMeta(
            id = "timer_lab",
            title = "Timer Lab",
            tagline = "Stopwatch & timer",
            category = Category.TOOLS,
            tier = Tier.TOOL,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.strokeCircle(c, cx, cy + size * 0.05f, size * 0.32f, color, size * 0.05f)
                Draw.line(c, cx, cy + size * 0.05f, cx, cy - size * 0.18f, color, size * 0.05f)
                Draw.line(c, cx, cy + size * 0.05f, cx + size * 0.16f, cy + size * 0.05f, color, size * 0.045f)
                Draw.roundRectCentered(c, cx, cy - size * 0.32f, size * 0.2f, size * 0.1f, size * 0.04f, color)
            },
            create = { TimerLabTool() }
        )
    }
}
