package com.cayadev.orbo.tools

import android.content.Context
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import kotlin.math.sqrt

/** Level — a spirit level using the watch's accelerometer. The bubble drifts to the high side; centre
 * it to find true level. Shows the tilt angle and buzzes when you're perfectly flat. */
class LevelTool : Scene() {

    private var sensorManager: SensorManager? = null
    private var sensor: Sensor? = null
    @Volatile private var ax = 0f
    @Volatile private var ay = 0f
    @Volatile private var az = 9.8f
    private var bx = 0f
    private var by = 0f
    private var t = 0f
    private var wasLevel = false
    private var available = true
    private val accent = Palette.lime

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) { ax = e.values[0]; ay = e.values[1]; az = e.values[2] }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    override fun enter() {
        val sm = engine.context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager = sm
        sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        available = sensor != null
        sensor?.let { sm?.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun exit() { sensorManager?.unregisterListener(listener) }

    override fun update(dt: Float) {
        t += dt
        // bubble moves to the raised side -> opposite the in-plane gravity vector
        bx = Mathx.approach(bx, (-ax / 9.8f).coerceIn(-1f, 1f), 8f, dt)
        by = Mathx.approach(by, (ay / 9.8f).coerceIn(-1f, 1f), 8f, dt)
        val level = sqrt(ax * ax + ay * ay) < 0.6f
        if (level && !wasLevel) engine.haptics.medium()
        wasLevel = level
    }

    override fun onTap(x: Float, y: Float) { if (Ui.hitAppBack(sc, x, y)) engine.back() }

    override fun render(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        val cyc = s.cy - s.px(0.02f); val R = if (s.isRound) s.r * 0.72f else s.minDim * 0.4f
        if (!available) {
            Draw.textCentered(c, Loc.t("No sensor"), s.cx, s.cy, s.px(0.06f), Palette.textDim, Fonts.display)
            Ui.appBackButton(c, s, t, accent); return
        }
        val level = wasLevel
        val ringCol = if (level) accent else Palette.textDim
        Draw.strokeCircle(c, s.cx, cyc, R, Palette.alpha(ringCol, 0.4f), s.dp(2f))
        Draw.strokeCircle(c, s.cx, cyc, R * 0.3f, Palette.alpha(accent, 0.5f), s.dp(2f))
        Draw.line(c, s.cx - R, cyc, s.cx + R, cyc, Palette.alpha(Palette.line, 0.6f), s.dp(1f))
        Draw.line(c, s.cx, cyc - R, s.cx, cyc + R, Palette.alpha(Palette.line, 0.6f), s.dp(1f))
        // bubble
        val bxp = s.cx + bx * R * 0.85f; val byp = cyc + by * R * 0.85f
        Draw.glow(c, bxp, byp, s.px(0.09f), if (level) accent else Palette.amber, 0.4f)
        Draw.fillCircle(c, bxp, byp, s.px(0.06f), if (level) accent else Palette.amber)
        // tilt angle
        val tilt = Math.toDegrees(Math.atan2(sqrt(ax * ax + ay * ay).toDouble(), az.toDouble())).toFloat()
        Draw.textCentered(c, String.format("%.0f°", tilt), s.cx, s.h * 0.9f, s.px(0.06f), if (level) accent else Palette.text, Fonts.display)
        Ui.appBackButton(c, s, t, accent)
    }

    companion object {
        fun meta() = GameMeta(
            id = "level",
            title = "Level",
            tagline = "Find true level",
            category = Category.TOOLS,
            tier = Tier.TOOL,
            accent = Palette.lime,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.strokeCircle(c, cx, cy, size * 0.4f, color, size * 0.05f)
                Draw.line(c, cx - size * 0.4f, cy, cx + size * 0.4f, cy, Palette.alpha(color, 0.5f), size * 0.03f)
                Draw.fillCircle(c, cx + size * 0.12f, cy, size * 0.12f, color)
            },
            create = { LevelTool() }
        )
    }
}
