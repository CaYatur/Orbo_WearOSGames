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

/** Compass — a magnetic compass using the watch's sensors. The rose rotates so north always points
 * north; the heading in degrees and the nearest cardinal point are shown in the centre. */
class CompassTool : Scene() {

    private var sm: SensorManager? = null
    private var accSensor: Sensor? = null
    private var magSensor: Sensor? = null
    private val gravity = FloatArray(3)
    private val geomag = FloatArray(3)
    private var hasAcc = false
    private var hasMag = false
    @Volatile private var azimuth = 0f       // radians
    private var displayAng = 0f
    private var t = 0f
    private var available = true
    private val accent = Palette.cyan

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            when (e.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> { System.arraycopy(e.values, 0, gravity, 0, 3); hasAcc = true }
                Sensor.TYPE_MAGNETIC_FIELD -> { System.arraycopy(e.values, 0, geomag, 0, 3); hasMag = true }
            }
            if (hasAcc && hasMag) {
                val r = FloatArray(9); val i = FloatArray(9)
                if (SensorManager.getRotationMatrix(r, i, gravity, geomag)) {
                    val o = FloatArray(3); SensorManager.getOrientation(r, o); azimuth = o[0]
                }
            }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    override fun enter() {
        val mgr = engine.context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sm = mgr
        accSensor = mgr?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magSensor = mgr?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        available = accSensor != null && magSensor != null
        accSensor?.let { mgr?.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        magSensor?.let { mgr?.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun exit() { sm?.unregisterListener(listener) }

    override fun update(dt: Float) {
        t += dt
        // smooth toward azimuth, handling wrap
        var target = azimuth
        var diff = target - displayAng
        while (diff > Math.PI) diff -= Mathx.TAU
        while (diff < -Math.PI) diff += Mathx.TAU
        displayAng += diff * (1f - Math.exp((-8f * dt).toDouble()).toFloat())
    }

    override fun onTap(x: Float, y: Float) { if (Ui.hitAppBack(sc, x, y)) engine.back() }

    private fun cardinal(deg: Float): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return dirs[(((deg + 22.5f) / 45f).toInt()) % 8]
    }

    override fun render(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        val cyc = s.cy - s.px(0.02f); val R = if (s.isRound) s.r * 0.74f else s.minDim * 0.42f
        if (!available) {
            Draw.textCentered(c, Loc.t("No compass"), s.cx, s.cy, s.px(0.06f), Palette.textDim, Fonts.display)
            Ui.appBackButton(c, s, t, accent); return
        }
        val rot = -displayAng    // rotate rose so north points to magnetic north
        Draw.strokeCircle(c, s.cx, cyc, R, Palette.alpha(Palette.line, 0.7f), s.dp(2f))
        // ticks + cardinals
        for (i in 0 until 12) {
            val a = rot + i * Mathx.TAU / 12f - Math.PI.toFloat() / 2f
            val r1 = R * 0.86f; val r2 = R
            Draw.line(c, s.cx + Mathx.cosf(a) * r1, cyc + Mathx.sinf(a) * r1, s.cx + Mathx.cosf(a) * r2, cyc + Mathx.sinf(a) * r2, Palette.alpha(Palette.textDim, 0.7f), s.dp(1.5f))
        }
        val labels = arrayOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
        for ((lab, deg) in labels) {
            val a = rot + Math.toRadians(deg.toDouble()).toFloat() - Math.PI.toFloat() / 2f
            val rr = R * 0.7f
            Draw.textCentered(c, lab, s.cx + Mathx.cosf(a) * rr, cyc + Mathx.sinf(a) * rr, s.px(0.06f), if (lab == "N") Palette.red else Palette.text, Fonts.display)
        }
        // needle (points north)
        val na = rot - Math.PI.toFloat() / 2f
        Draw.line(c, s.cx, cyc, s.cx + Mathx.cosf(na) * R * 0.55f, cyc + Mathx.sinf(na) * R * 0.55f, Palette.red, s.dp(4f))
        Draw.line(c, s.cx, cyc, s.cx - Mathx.cosf(na) * R * 0.55f, cyc - Mathx.sinf(na) * R * 0.55f, Palette.alpha(Palette.text, 0.5f), s.dp(4f))
        Draw.fillCircle(c, s.cx, cyc, s.px(0.025f), accent)
        // heading text
        var deg = Math.toDegrees(displayAng.toDouble()).toFloat(); if (deg < 0) deg += 360f
        Draw.textCentered(c, "${deg.toInt()}°", s.cx, s.h * 0.9f, s.px(0.06f), Palette.text, Fonts.display)
        Draw.textCentered(c, cardinal(deg), s.cx + s.px(0.16f), s.h * 0.9f, s.px(0.05f), accent, Fonts.display)
        Ui.appBackButton(c, s, t, accent)
    }

    companion object {
        fun meta() = GameMeta(
            id = "compass",
            title = "Compass",
            tagline = "Find your bearing",
            category = Category.TOOLS,
            tier = Tier.TOOL,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.strokeCircle(c, cx, cy, size * 0.4f, color, size * 0.05f)
                val p = android.graphics.Path()
                p.moveTo(cx, cy - size * 0.3f); p.lineTo(cx - size * 0.12f, cy); p.lineTo(cx + size * 0.12f, cy); p.close()
                Draw.paint.reset(); Draw.paint.isAntiAlias = true; Draw.paint.color = Palette.red; c.drawPath(p, Draw.paint)
                val p2 = android.graphics.Path()
                p2.moveTo(cx, cy + size * 0.3f); p2.lineTo(cx - size * 0.12f, cy); p2.lineTo(cx + size * 0.12f, cy); p2.close()
                Draw.paint.color = Palette.alpha(color, 0.5f); c.drawPath(p2, Draw.paint)
            },
            create = { CompassTool() }
        )
    }
}
