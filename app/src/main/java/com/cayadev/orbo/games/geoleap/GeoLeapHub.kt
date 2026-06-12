package com.cayadev.orbo.games.geoleap

import android.graphics.Canvas
import android.graphics.Paint
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.IconDrawer
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Backdrop
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Loc
import com.cayadev.orbo.engine.Mathx
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.Scene
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.engine.Swipe
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.engine.WatchList

/** Level picker for Geo Leap — shows each level with its best %, gated by completing the previous. */
class GeoLeapHub : Scene() {
    private val bg = Backdrop(30)
    private val list = WatchList()
    private var t = 0f
    private var pressed = -1

    override fun resized(s: ScreenInfo) {
        list.configure(LevelDefs.levels.size, s.px(0.215f), s.px(0.035f), s.h * 0.30f, s.h * 0.22f)
    }

    private fun unlocked(i: Int): Boolean = i == 0 || engine.scores.getBool("geo_done_${i - 1}", false)

    override fun update(dt: Float) { t += dt; bg.update(dt); list.update(dt, sc) }

    override fun render(c: Canvas, s: ScreenInfo) {
        bg.render(c, s, Palette.pink)
        Draw.withFaceClip(c, s) {
            for (i in LevelDefs.levels.indices) {
                val cy = list.itemCenterY(i)
                if (cy < -s.px(0.2f) || cy > s.h + s.px(0.2f)) continue
                drawRow(c, s, i, cy, list.focus(i, s), pressed == i)
            }
        }
        Draw.vGradientRect(c, 0f, 0f, s.w, s.h * 0.26f, Palette.alpha(Palette.bg, 0.95f), Palette.alpha(Palette.bg, 0f))
        Draw.textCentered(c, "GEO LEAP", s.cx, s.h * 0.14f, s.px(0.085f), Palette.pink, Fonts.display, letterSpacing = 0.06f)
        Draw.textCentered(c, Loc.t("SELECT LEVEL"), s.cx, s.h * 0.205f, s.px(0.03f), Palette.textDim, Fonts.mono, letterSpacing = 0.28f)
        Ui.appBackButton(c, s, t, Palette.pink)
    }

    private fun drawRow(c: Canvas, s: ScreenInfo, i: Int, cy: Float, focus: Float, down: Boolean) {
        val def = LevelDefs.levels[i]
        val open = unlocked(i)
        val margin = s.safeInset + s.px(0.015f)
        val w = s.w - margin * 2
        val press = if (down) 0.96f else 1f
        val h = list.itemH * (0.86f + 0.14f * focus) * press
        val x = (s.w - w) / 2f; val y = cy - h / 2f
        val accent = if (open) def.accent else Palette.textFaint
        Ui.rowPanel(c, x, y, w, h, focus, accent)

        val iconR = h * 0.30f; val iconX = x + h * 0.5f
        Ui.iconDisc(c, iconX, cy, iconR, accent, focus)
        if (open) Draw.textCentered(c, "${i + 1}", iconX, cy, iconR * 0.9f, accent, Fonts.display)
        else { // lock
            Draw.roundRectCentered(c, iconX, cy + iconR * 0.18f, iconR * 0.7f, iconR * 0.55f, iconR * 0.12f, accent)
            Draw.arc(c, iconX, cy - iconR * 0.15f, iconR * 0.28f, 180f, 180f, accent, s.dp(2.5f))
        }
        val textX = iconX + iconR + h * 0.26f
        Draw.text(c, def.name, textX, cy - h * 0.03f, h * 0.23f, if (open) Palette.text else Palette.textDim, Paint.Align.LEFT, Fonts.display)
        val best = engine.scores.getInt("geo_best_$i", 0)
        val sub = if (open) "${Loc.t("BEST")} $best%" else Loc.t("LOCKED")
        Draw.text(c, sub, textX, cy + h * 0.25f, h * 0.15f, if (open) accent else Palette.textFaint, Paint.Align.LEFT, Fonts.mono)
        if (engine.scores.getBool("geo_done_$i", false)) Ui.chip(c, x + w - h * 0.4f, cy, "★", Palette.gold, 0.03f, s)
    }

    override fun onDown(x: Float, y: Float) {
        if (Ui.hitAppBack(sc, x, y)) { pressed = -1; return }
        list.onDown(x, y); pressed = list.hit(x, y)
    }
    override fun onMove(x: Float, y: Float) { list.onMove(x, y, sc); if (pressed != list.hit(x, y)) pressed = -1 }
    override fun onUp(x: Float, y: Float) { list.onUp(); pressed = -1 }
    override fun onSwipe(dir: Swipe, vx: Float, vy: Float) { if (dir == Swipe.UP || dir == Swipe.DOWN) list.fling(vy); pressed = -1 }
    override fun onRotary(deltaPx: Float) { list.rotary(deltaPx, sc) }
    override fun onTap(x: Float, y: Float) {
        if (Ui.hitAppBack(sc, x, y)) { engine.back(); return }
        val i = list.hit(x, y)
        if (i in LevelDefs.levels.indices) {
            if (unlocked(i)) { engine.sfx.click(); engine.haptics.light(); engine.push(GeoLeapRunner(i)) }
            else { engine.sfx.hit(); engine.haptics.error() }
        }
    }

    companion object {
        fun meta() = GameMeta(
            id = "geo_leap",
            title = "Geo Leap",
            tagline = "Rhythm runner · 5 levels",
            category = Category.ARCADE,
            tier = Tier.FLAGSHIP,
            accent = Palette.pink,
            featured = true,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx - size * 0.12f, cy - size * 0.05f, size * 0.3f, size * 0.3f, size * 0.06f, color)
                val p = Draw.paint; p.reset(); p.isAntiAlias = true; p.color = Palette.amber
                val path = android.graphics.Path()
                path.moveTo(cx + size * 0.18f, cy + size * 0.28f); path.lineTo(cx + size * 0.34f, cy - size * 0.05f); path.lineTo(cx + size * 0.5f, cy + size * 0.28f); path.close()
                c.drawPath(path, p)
                Draw.line(c, cx - size * 0.45f, cy + size * 0.28f, cx + size * 0.5f, cy + size * 0.28f, color, size * 0.04f)
            },
            create = { GeoLeapHub() }
        )
    }
}
