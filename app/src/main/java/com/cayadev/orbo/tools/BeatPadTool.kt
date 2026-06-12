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
import com.cayadev.orbo.engine.Sfx
import com.cayadev.orbo.engine.Ui

/** Beat Pad — a pocket synth. Nine pentatonic pads (always in tune), four waveforms, an octave
 * shift, and a one-touch loop recorder so you can layer a groove on your wrist. */
class BeatPadTool : Scene() {

    private val semis = intArrayOf(0, 2, 4, 7, 9, 12, 14, 16, 19)
    private val padFx = FloatArray(9)
    private val waves = arrayOf(Sfx.Wave.SINE, Sfx.Wave.SQUARE, Sfx.Wave.TRI, Sfx.Wave.SAW)
    private val waveNames = arrayOf("SINE", "SQR", "TRI", "SAW")
    private var waveIdx = 0
    private var octave = 0
    private var t = 0f

    // loop recorder
    private class Ev(val time: Float, val pad: Int)
    private val events = ArrayList<Ev>()
    private var recording = false
    private var recStart = 0f
    private var playing = false
    private var playHead = 0f
    private var playIdx = 0
    private var loopLen = 0f

    private fun freq(pad: Int): Float = (261.63 * Math.pow(2.0, (semis[pad] + 12 * octave) / 12.0)).toFloat()

    override fun update(dt: Float) {
        t += dt
        for (i in 0 until 9) if (padFx[i] > 0f) padFx[i] -= dt * 3f
        if (playing && loopLen > 0f) {
            playHead += dt
            while (playIdx < events.size && events[playIdx].time <= playHead) {
                trigger(events[playIdx].pad, record = false); playIdx++
            }
            if (playHead >= loopLen) { playHead -= loopLen; playIdx = 0 }
        }
    }

    private fun trigger(pad: Int, record: Boolean) {
        padFx[pad] = 1f
        engine.sfx.tone(waves[waveIdx], freq(pad), freq(pad), 0.5f, 0.5f, 0.005f, 0.22f, 0.3f)
        engine.haptics.light()
        if (record && recording) events.add(Ev(t - recStart, pad))
    }

    // ---- layout ----
    // Controls live on the BOTTOM row so they never collide with the top-left back button.
    private fun ctrl(s: ScreenInfo, i: Int): FloatArray { // 0 wave,1 oct-,2 oct+,3 rec,4 play
        val y = s.h * 0.90f; val r = s.px(0.05f)
        val xs = floatArrayOf(0.27f, 0.385f, 0.50f, 0.615f, 0.73f)
        return floatArrayOf(s.w * xs[i], y, r)
    }
    private fun padGeom(s: ScreenInfo): FloatArray {
        val side = if (s.isRound) s.minDim * 0.54f else s.minDim * 0.72f
        val cellc = side / 3
        return floatArrayOf(s.cx - side / 2, s.h * 0.54f - side / 2, cellc)
    }

    override fun onDown(x: Float, y: Float) {
        if (Ui.hitAppBack(sc, x, y)) { engine.back(); return }
        for (i in 0 until 5) { val r = ctrl(sc, i); if (com.cayadev.orbo.engine.Mathx.dist(x, y, r[0], r[1]) < r[2] * 1.2f) { control(i); return } }
        val g = padGeom(sc)
        val col = ((x - g[0]) / g[2]).toInt(); val row = ((y - g[1]) / g[2]).toInt()
        if (col in 0..2 && row in 0..2) {
            val pad = row * 3 + col
            if (recording && events.isEmpty()) recStart = t
            trigger(pad, record = true)
        }
    }
    override fun onTap(x: Float, y: Float) {}

    private fun control(i: Int) {
        engine.sfx.click(); engine.haptics.light()
        when (i) {
            0 -> waveIdx = (waveIdx + 1) % waves.size
            1 -> octave = (octave - 1).coerceAtLeast(-1)
            2 -> octave = (octave + 1).coerceAtMost(2)
            3 -> toggleRecord()
            4 -> togglePlay()
        }
    }
    private fun toggleRecord() {
        recording = !recording
        if (recording) { events.clear(); recStart = t; playing = false }
        else { loopLen = if (events.isEmpty()) 0f else events.maxOf { it.time } + 0.6f }
    }
    private fun togglePlay() {
        if (events.isEmpty()) return
        if (recording) { recording = false; loopLen = events.maxOf { it.time } + 0.6f }
        playing = !playing
        if (playing) { playHead = 0f; playIdx = 0 }
    }

    override fun render(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)

        // controls
        drawCtrl(c, s, 0, waveNames[waveIdx], Palette.cyan, false)
        drawCtrl(c, s, 1, "8↓", Palette.violet, false)
        drawCtrl(c, s, 2, "8↑", Palette.violet, false)
        drawCtrl(c, s, 3, "●", if (recording) Palette.red else Palette.textDim, recording)
        drawCtrl(c, s, 4, "▶", if (playing) Palette.mint else Palette.textDim, playing)

        // pads
        val g = padGeom(s); val cellc = g[2]
        for (i in 0 until 9) {
            val col = i % 3; val row = i / 3
            val cx = g[0] + col * cellc + cellc / 2; val cy = g[1] + row * cellc + cellc / 2
            val sz = cellc * 0.9f
            val color = Palette.accents[i % Palette.accents.size]
            val fx = padFx[i].coerceIn(0f, 1f)
            if (fx > 0f) Draw.glow(c, cx, cy, sz * 0.8f * (1f + fx * 0.4f), color, 0.4f * fx)
            Draw.roundRectCentered(c, cx, cy, sz, sz, sz * 0.2f, Palette.lerp(Palette.panelHi, color, 0.15f + 0.6f * fx))
            Draw.roundRectStroke(c, cx - sz / 2, cy - sz / 2, sz, sz, sz * 0.2f, Palette.alpha(color, 0.4f + 0.5f * fx), s.dp(1.6f))
        }

        Draw.textCentered(c, "BEAT PAD", s.cx, s.h * 0.05f, s.px(0.04f), Palette.textDim, Fonts.mono, letterSpacing = 0.24f)
        if (recording) Draw.textCentered(c, "● ${Loc.t("REC")}", s.cx, s.h * 0.115f, s.px(0.04f), Palette.red, Fonts.mono)
        else if (playing) Draw.textCentered(c, "▶ ${Loc.t("LOOP")}", s.cx, s.h * 0.115f, s.px(0.04f), Palette.mint, Fonts.mono)
        Ui.appBackButton(c, s, t, Palette.lime)
    }

    private fun drawCtrl(c: Canvas, s: ScreenInfo, i: Int, label: String, col: Int, on: Boolean) {
        val r = ctrl(s, i)
        Draw.fillCircle(c, r[0], r[1], r[2], if (on) Palette.alpha(col, 0.3f) else Palette.alpha(Palette.panelHi, 0.92f))
        Draw.strokeCircle(c, r[0], r[1], r[2], Palette.alpha(col, 0.7f), s.dp(1.4f))
        Draw.textCentered(c, label, r[0], r[1], r[2] * 0.7f, col, Fonts.bodyBold)
    }

    companion object {
        fun meta() = GameMeta(
            id = "beat_pad",
            title = "Beat Pad",
            tagline = "Tap out a tune",
            category = Category.TOOLS,
            tier = Tier.TOOL,
            accent = Palette.lime,
            icon = IconDrawer { c, cx, cy, size, _ ->
                for (i in 0 until 9) {
                    val col = i % 3; val row = i / 3; val u = size * 0.22f
                    Draw.roundRectCentered(c, cx + (col - 1) * u, cy + (row - 1) * u, u * 0.82f, u * 0.82f, u * 0.2f, Palette.accents[i % Palette.accents.size])
                }
            },
            create = { BeatPadTool() }
        )
    }
}
