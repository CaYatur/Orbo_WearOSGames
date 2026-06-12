package com.cayadev.orbo.home

import android.graphics.Canvas
import android.graphics.Paint
import com.cayadev.orbo.data.Catalog
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.engine.Backdrop
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Ease
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Loc
import com.cayadev.orbo.engine.Mathx
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.Scene
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.engine.Swipe
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.engine.WatchList

/** Main menu: a curved, scrollable list of categories, each opening its game list. */
class HomeScene : Scene() {
    private val bg = Backdrop()
    private val list = WatchList()
    private var cats: List<Category> = emptyList()
    private var t = 0f
    private var pressed = -1

    override fun attached() {
        Catalog.ensureLoaded()
        cats = Catalog.activeCategories()
    }

    override fun enter() {
        cats = Catalog.activeCategories()
    }

    override fun resized(s: ScreenInfo) {
        list.configure(
            count = cats.size,
            itemH = s.px(0.225f),
            gap = s.px(0.035f),
            topPad = s.h * 0.30f,
            bottomPad = s.h * 0.26f
        )
    }

    override fun update(dt: Float) {
        t += dt
        bg.update(dt)
        list.count = cats.size
        list.update(dt, sc)
    }

    override fun render(c: Canvas, s: ScreenInfo) {
        bg.render(c, s, Palette.violet)

        Draw.withFaceClip(c, s) {
            for (i in cats.indices) {
                val cat = cats[i]
                val cy = list.itemCenterY(i)
                if (cy < -s.px(0.2f) || cy > s.h + s.px(0.2f)) continue
                val focus = list.focus(i, s)
                drawCategoryRow(c, s, cat, cy, focus, pressed == i)
            }
        }

        // Header (drawn over the list, with a fade scrim)
        Draw.vGradientRect(c, 0f, 0f, s.w, s.h * 0.26f, Palette.alpha(Palette.bg, 0.95f), Palette.alpha(Palette.bg, 0f))
        Draw.textCentered(c, "ORBO", s.cx, s.h * 0.135f, s.px(0.105f), Palette.mint, Fonts.brand, letterSpacing = 0.14f)
        val tagline = if (engine.isWatch) "WRIST ARCADE" else "POCKET ARCADE"
        Draw.textCentered(c, Loc.t(tagline), s.cx, s.h * 0.205f, s.px(0.032f), Palette.textDim, Fonts.mono, letterSpacing = 0.34f)

        // Footer orbs
        Draw.vGradientRect(c, 0f, s.h * 0.80f, s.w, s.h * 0.20f, Palette.alpha(Palette.bg, 0f), Palette.alpha(Palette.bg, 0.9f))
        Ui.starCount(c, s, engine.scores.orbs())

        // settings gear
        val g = gearRect(s)
        Draw.fillCircle(c, g[0], g[1], g[2], Palette.alpha(Palette.panelHi, 0.9f))
        Draw.strokeCircle(c, g[0], g[1], g[2] * 0.5f, Palette.textDim, s.dp(2f))
        for (k in 0 until 8) {
            val a = k * Mathx.TAU / 8f
            Draw.fillCircle(c, g[0] + Mathx.cosf(a) * g[2] * 0.62f, g[1] + Mathx.sinf(a) * g[2] * 0.62f, g[2] * 0.12f, Palette.textDim)
        }
        Draw.fillCircle(c, g[0], g[1], g[2] * 0.22f, Palette.bg)
    }

    private fun gearRect(s: ScreenInfo) = floatArrayOf(s.cx + s.px(0.26f), s.h * 0.855f, s.px(0.05f))

    private fun drawCategoryRow(c: Canvas, s: ScreenInfo, cat: Category, cy: Float, focus: Float, down: Boolean) {
        val margin = s.safeInset + s.px(0.02f)
        val w = s.w - margin * 2
        val h = list.itemH * (0.86f + 0.14f * focus)
        val x = (s.w - w) / 2f
        val y = cy - h / 2f
        val press = if (down) 0.96f else 1f

        val ph = h * press
        val py = cy - ph / 2f
        Ui.rowPanel(c, x, py, w, ph, focus, cat.accent)

        val iconR = ph * 0.30f
        val iconX = x + ph * 0.5f
        Ui.iconDisc(c, iconX, cy, iconR, cat.accent, focus)
        Draw.textCentered(c, cat.glyph, iconX, cy, iconR * 1.05f, cat.accent, Fonts.display)

        val textX = iconX + iconR + ph * 0.28f
        Draw.text(c, Loc.t(cat.title), textX, cy - ph * 0.04f, ph * 0.27f, Palette.text, Paint.Align.LEFT, Fonts.display)
        val n = Catalog.count(cat)
        Draw.text(c, "$n ${Loc.t(if (n == 1) "game" else "games")}", textX, cy + ph * 0.26f,
            ph * 0.16f, Palette.textDim, Paint.Align.LEFT, Fonts.mono)

        // chevron
        val chX = x + w - ph * 0.42f
        val chR = ph * 0.12f
        val a = 0.5f + 0.4f * focus
        Draw.line(c, chX - chR * 0.5f, cy - chR, chX + chR * 0.5f, cy, Palette.alpha(cat.accent, a), s.dp(2.4f))
        Draw.line(c, chX + chR * 0.5f, cy, chX - chR * 0.5f, cy + chR, Palette.alpha(cat.accent, a), s.dp(2.4f))
    }

    // --- input ---
    override fun onDown(x: Float, y: Float) { list.onDown(x, y); pressed = list.hit(x, y) }
    override fun onMove(x: Float, y: Float) { list.onMove(x, y, sc); if (pressed != list.hit(x, y)) pressed = -1 }
    override fun onUp(x: Float, y: Float) { list.onUp(); pressed = -1 }
    override fun onSwipe(dir: Swipe, vx: Float, vy: Float) {
        if (dir == Swipe.UP || dir == Swipe.DOWN) list.fling(vy)
        pressed = -1
    }
    override fun onRotary(deltaPx: Float) { list.rotary(deltaPx, sc) }
    override fun onTap(x: Float, y: Float) {
        val g = gearRect(sc)
        if (Mathx.dist(x, y, g[0], g[1]) < g[2] * 1.3f) {
            engine.haptics.light(); engine.sfx.click(); engine.push(SettingsScene()); return
        }
        val i = list.hit(x, y)
        if (i in cats.indices) {
            engine.haptics.light(); engine.sfx.click()
            engine.push(CategoryScene(cats[i]))
        }
    }
}
