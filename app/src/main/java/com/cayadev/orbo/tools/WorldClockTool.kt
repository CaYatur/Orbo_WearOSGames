package com.cayadev.orbo.tools

import android.graphics.Canvas
import android.graphics.Paint
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** World Clock — your local time plus a handful of cities around the globe at a glance. Handy for
 * calls and travel without leaving your wrist. */
class WorldClockTool : Scene() {

    private val cities = listOf(
        "LON" to "Europe/London",
        "NYC" to "America/New_York",
        "IST" to "Europe/Istanbul",
        "TOK" to "Asia/Tokyo",
        "SYD" to "Australia/Sydney"
    )
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
    private val dateFmt = SimpleDateFormat("EEE d MMM", Locale.getDefault())
    private val date = Date()
    private var t = 0f
    private val accent = Palette.blue

    override fun update(dt: Float) { t += dt }
    override fun onTap(x: Float, y: Float) { if (Ui.hitAppBack(sc, x, y)) engine.back() }

    private fun timeIn(zone: String): String {
        date.time = System.currentTimeMillis()
        timeFmt.timeZone = TimeZone.getTimeZone(zone)
        return timeFmt.format(date)
    }

    override fun render(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        date.time = System.currentTimeMillis()
        // local
        timeFmt.timeZone = TimeZone.getDefault()
        val now = timeFmt.format(date)
        Draw.textCentered(c, now, s.cx, s.h * 0.2f, s.px(0.16f), Palette.text, Fonts.display)
        dateFmt.timeZone = TimeZone.getDefault()
        Draw.textCentered(c, dateFmt.format(date), s.cx, s.h * 0.31f, s.px(0.042f), accent, Fonts.mono, letterSpacing = 0.08f)
        Draw.line(c, s.cx - s.px(0.3f), s.h * 0.37f, s.cx + s.px(0.3f), s.h * 0.37f, Palette.alpha(Palette.line, 0.8f), s.dp(1.5f))
        // cities
        val n = cities.size
        val startY = s.h * 0.46f; val rowH = s.h * 0.1f
        for (i in cities.indices) {
            val (label, zone) = cities[i]
            val y = startY + i * rowH
            Draw.text(c, label, s.cx - s.px(0.26f), y + s.px(0.018f), s.px(0.05f), Palette.textDim, Paint.Align.LEFT, Fonts.bodyBold)
            Draw.text(c, timeIn(zone), s.cx + s.px(0.26f), y + s.px(0.018f), s.px(0.055f), Palette.text, Paint.Align.RIGHT, Fonts.display)
        }
        Ui.appBackButton(c, s, t, accent)
    }

    companion object {
        fun meta() = GameMeta(
            id = "world_clock",
            title = "World Clock",
            tagline = "Time around the globe",
            category = Category.TOOLS,
            tier = Tier.TOOL,
            accent = Palette.blue,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.strokeCircle(c, cx, cy, size * 0.4f, color, size * 0.05f)
                Draw.line(c, cx, cy, cx, cy - size * 0.24f, color, size * 0.05f)
                Draw.line(c, cx, cy, cx + size * 0.16f, cy + size * 0.06f, color, size * 0.045f)
                for (i in 0 until 4) { val a = i * Mathx.TAU / 4f; Draw.fillCircle(c, cx + Mathx.cosf(a) * size * 0.32f, cy + Mathx.sinf(a) * size * 0.32f, size * 0.03f, Palette.alpha(color, 0.6f)) }
            },
            create = { WorldClockTool() }
        )
    }
}
