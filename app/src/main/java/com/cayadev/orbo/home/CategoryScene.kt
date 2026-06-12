package com.cayadev.orbo.home

import android.graphics.Canvas
import android.graphics.Paint
import com.cayadev.orbo.data.Catalog
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Backdrop
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Loc
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.Scene
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.engine.Swipe
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.engine.WatchList

/** Lists the games inside a category. Tapping a row launches it. */
class CategoryScene(private val category: Category) : Scene() {
    private val bg = Backdrop(34)
    private val list = WatchList()
    private var items: List<GameMeta> = emptyList()
    private var t = 0f
    private var pressed = -1

    override fun attached() { items = Catalog.inCategory(category) }

    override fun resized(s: ScreenInfo) {
        list.configure(
            count = items.size,
            itemH = s.px(0.225f),
            gap = s.px(0.035f),
            topPad = s.h * 0.26f,
            bottomPad = s.h * 0.20f
        )
    }

    override fun update(dt: Float) {
        t += dt; bg.update(dt); list.update(dt, sc)
    }

    override fun render(c: Canvas, s: ScreenInfo) {
        bg.render(c, s, category.accent)
        Draw.withFaceClip(c, s) {
            for (i in items.indices) {
                val cy = list.itemCenterY(i)
                if (cy < -s.px(0.2f) || cy > s.h + s.px(0.2f)) continue
                drawGameRow(c, s, items[i], cy, list.focus(i, s), pressed == i)
            }
        }
        // header
        Draw.vGradientRect(c, 0f, 0f, s.w, s.h * 0.23f, Palette.alpha(Palette.bg, 0.95f), Palette.alpha(Palette.bg, 0f))
        Ui.screenTitle(c, s, Loc.t(category.title), category.accent, "${items.size} ${Loc.t("GAMES")}")
        Ui.appBackButton(c, s, t, category.accent)
    }

    private fun drawGameRow(c: Canvas, s: ScreenInfo, g: GameMeta, cy: Float, focus: Float, down: Boolean) {
        val margin = s.safeInset + s.px(0.015f)
        val w = s.w - margin * 2
        val press = if (down) 0.96f else 1f
        val h = list.itemH * (0.86f + 0.14f * focus) * press
        val x = (s.w - w) / 2f
        val y = cy - h / 2f

        Ui.rowPanel(c, x, y, w, h, focus, g.accent)

        val iconR = h * 0.31f
        val iconX = x + h * 0.5f
        Ui.iconDisc(c, iconX, cy, iconR, g.accent, focus)
        g.icon.draw(c, iconX, cy, iconR * 1.25f, g.accent)

        val textX = iconX + iconR + h * 0.26f
        val titleSize = h * 0.255f
        Draw.text(c, g.title, textX, cy - h * 0.045f, titleSize, Palette.text, Paint.Align.LEFT, Fonts.display)

        val best = engine.scores.best(g.id)
        val sub = when {
            g.tier == Tier.TOOL -> Loc.t(g.tagline)
            best > 0 -> "${Loc.t("BEST")} $best"
            else -> Loc.t(g.tagline)
        }
        Draw.text(c, sub, textX, cy + h * 0.25f, h * 0.15f,
            if (best > 0 && g.tier != Tier.TOOL) g.accent else Palette.textDim,
            Paint.Align.LEFT, Fonts.mono)

        // tier badge
        if (g.tier == Tier.FLAGSHIP || g.tier == Tier.BIG) {
            val label = Loc.t(if (g.tier == Tier.FLAGSHIP) "EPIC" else "BIG")
            val bx = x + w - h * 0.40f
            Ui.chip(c, bx, cy - h * 0.30f, label, if (g.tier == Tier.FLAGSHIP) Palette.gold else g.accent, 0.028f, s)
        }
    }

    override fun onDown(x: Float, y: Float) {
        if (Ui.hitAppBack(sc, x, y)) { pressed = -1; return }
        list.onDown(x, y); pressed = list.hit(x, y)
    }
    override fun onMove(x: Float, y: Float) { list.onMove(x, y, sc); if (pressed != list.hit(x, y)) pressed = -1 }
    override fun onUp(x: Float, y: Float) { list.onUp(); pressed = -1 }
    override fun onSwipe(dir: Swipe, vx: Float, vy: Float) {
        if (dir == Swipe.UP || dir == Swipe.DOWN) list.fling(vy); pressed = -1
    }
    override fun onRotary(deltaPx: Float) { list.rotary(deltaPx, sc) }
    override fun onTap(x: Float, y: Float) {
        if (Ui.hitAppBack(sc, x, y)) { engine.back(); return }
        val i = list.hit(x, y)
        if (i in items.indices) {
            engine.haptics.light(); engine.sfx.click()
            engine.scores.recordPlay(items[i].id); engine.scores.bumpTotalPlays()
            engine.push(items[i].create())
        }
    }
}
