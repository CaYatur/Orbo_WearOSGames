package com.cayadev.orbo.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
import java.io.File
import kotlin.math.min

/** Gallery of saved Sketch drawings — scroll the list, tap a row to open & edit, or the trash icon
 *  to delete. Reached from the gallery button in [SketchTool]. */
class SketchGalleryScene : Scene() {
    private val bg = Backdrop(26)
    private val list = WatchList()
    private var files: List<File> = emptyList()
    private val thumbs = HashMap<String, Bitmap?>()
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var t = 0f
    private var pressed = -1
    private var confirmDelete: File? = null

    override fun enter() { refresh() }

    private fun refresh() {
        files = SketchStore.list(engine.context)
        thumbs.clear()
        configure(sc)
    }

    private fun configure(s: ScreenInfo) {
        list.configure(files.size, s.px(0.36f), s.px(0.04f), s.h * 0.26f, s.h * 0.18f)
    }

    override fun resized(s: ScreenInfo) { configure(s) }
    override fun update(dt: Float) { t += dt; bg.update(dt); list.update(dt, sc) }

    private fun thumb(f: File): Bitmap? {
        val key = f.absolutePath
        if (thumbs.containsKey(key)) return thumbs[key]
        val b = SketchStore.loadThumb(f, 4)
        thumbs[key] = b
        return b
    }

    override fun render(c: Canvas, s: ScreenInfo) {
        bg.render(c, s, Palette.pink)
        Draw.withFaceClip(c, s) {
            for (i in files.indices) {
                val cy = list.itemCenterY(i)
                if (cy < -s.px(0.25f) || cy > s.h + s.px(0.25f)) continue
                drawRow(c, s, i, cy, list.focus(i, s), pressed == i)
            }
        }
        if (files.isEmpty()) {
            Draw.textCentered(c, Loc.t("No saved drawings"), s.cx, s.cy, s.px(0.05f), Palette.textDim, Fonts.bodyBold)
            Draw.textCentered(c, Loc.t("Draw, then Save"), s.cx, s.cy + s.px(0.09f), s.px(0.035f), Palette.textFaint, Fonts.mono)
        }
        Draw.vGradientRect(c, 0f, 0f, s.w, s.h * 0.23f, Palette.alpha(Palette.bg, 0.95f), Palette.alpha(Palette.bg, 0f))
        Ui.screenTitle(c, s, Loc.t("Gallery"), Palette.pink, "${files.size}")
        Ui.appBackButton(c, s, t, Palette.pink)

        if (confirmDelete != null) drawConfirm(c, s)
    }

    /** Returns [x, y, w, h] of row i's panel and stores the trash centre via [trashCx]/[trashCy]. */
    private fun rowRect(s: ScreenInfo, i: Int, focus: Float): FloatArray {
        val margin = s.safeInset + s.px(0.015f)
        val w = s.w - margin * 2
        val h = list.itemH * (0.88f + 0.12f * focus)
        val cy = list.itemCenterY(i)
        val x = (s.w - w) / 2f
        return floatArrayOf(x, cy - h / 2f, w, h)
    }

    private fun drawRow(c: Canvas, s: ScreenInfo, i: Int, cy: Float, focus: Float, down: Boolean) {
        val r = rowRect(s, i, focus)
        val x = r[0]; val y = r[1]; val w = r[2]; val h = r[3]
        Ui.rowPanel(c, x, y, w, h, focus * (if (down) 0.7f else 1f), Palette.pink)

        // thumbnail
        val box = h * 0.72f
        val bcx = x + h * 0.55f; val bcy = cy
        Draw.roundRect(c, bcx - box / 2, bcy - box / 2, box, box, box * 0.14f, Palette.bg)
        val tb = thumb(files[i])
        if (tb != null) {
            val sc2 = min(box / tb.width, box / tb.height)
            val dw = tb.width * sc2; val dh = tb.height * sc2
            val dst = RectF(bcx - dw / 2, bcy - dh / 2, bcx + dw / 2, bcy + dh / 2)
            c.drawBitmap(tb, null, dst, bmpPaint)
        }
        Draw.roundRectStroke(c, bcx - box / 2, bcy - box / 2, box, box, box * 0.14f, Palette.alpha(Palette.pink, 0.5f), s.dp(1.4f))

        // short index label (kept tiny so it never overflows toward the trash icon)
        val textX = bcx + box / 2 + h * 0.20f
        Draw.text(c, "#${files.size - i}", textX, cy + h * 0.09f, h * 0.28f, Palette.text, Paint.Align.LEFT, Fonts.display)

        // trash icon
        val tc = trashCenter(r)
        drawTrash(c, s, tc[0], tc[1], h * 0.16f)
    }

    private fun trashCenter(r: FloatArray): FloatArray = floatArrayOf(r[0] + r[2] - r[3] * 0.5f, r[1] + r[3] / 2f)

    private fun drawTrash(c: Canvas, s: ScreenInfo, cx: Float, cy: Float, rr: Float) {
        Draw.fillCircle(c, cx, cy, rr * 1.3f, Palette.alpha(Palette.panelHi, 0.9f))
        Draw.strokeCircle(c, cx, cy, rr * 1.3f, Palette.alpha(Palette.red, 0.6f), s.dp(1.3f))
        Draw.roundRect(c, cx - rr * 0.55f, cy - rr * 0.35f, rr * 1.1f, rr * 1.0f, rr * 0.18f, Palette.red)
        Draw.line(c, cx - rr * 0.7f, cy - rr * 0.45f, cx + rr * 0.7f, cy - rr * 0.45f, Palette.red, s.dp(2f))
        Draw.line(c, cx - rr * 0.2f, cy - rr * 0.6f, cx + rr * 0.2f, cy - rr * 0.6f, Palette.red, s.dp(2f))
    }

    private fun drawConfirm(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.alpha(Palette.bg, 0.82f))
        Draw.textCentered(c, Loc.t("Delete?"), s.cx, s.h * 0.32f, s.px(0.08f), Palette.text, Fonts.display)
        Ui.pill(c, s.cx, s.h * 0.56f, s.px(0.52f), s.px(0.135f), Loc.t("YES"), Palette.red, true)
        Ui.pill(c, s.cx, s.h * 0.73f, s.px(0.52f), s.px(0.135f), Loc.t("NO"), Palette.mint, false)
    }

    private fun inPill(s: ScreenInfo, x: Float, y: Float, cyFrac: Float): Boolean {
        val cy = s.h * cyFrac; val w = s.px(0.52f); val h = s.px(0.135f)
        return x in (s.cx - w / 2)..(s.cx + w / 2) && y in (cy - h / 2)..(cy + h / 2)
    }

    // --- input ---
    override fun onDown(x: Float, y: Float) {
        if (confirmDelete != null) return
        if (Ui.hitAppBack(sc, x, y)) { pressed = -1; return }
        list.onDown(x, y); pressed = list.hit(x, y)
    }
    override fun onMove(x: Float, y: Float) {
        if (confirmDelete != null) return
        list.onMove(x, y, sc); if (pressed != list.hit(x, y)) pressed = -1
    }
    override fun onUp(x: Float, y: Float) { list.onUp(); pressed = -1 }
    override fun onSwipe(dir: Swipe, vx: Float, vy: Float) {
        if (confirmDelete != null) return
        if (dir == Swipe.UP || dir == Swipe.DOWN) list.fling(vy); pressed = -1
    }
    override fun onRotary(deltaPx: Float) { if (confirmDelete == null) list.rotary(deltaPx, sc) }

    override fun onTap(x: Float, y: Float) {
        if (confirmDelete != null) {
            when {
                inPill(sc, x, y, 0.56f) -> {
                    SketchStore.delete(confirmDelete!!); engine.sfx.click(); engine.haptics.medium()
                    confirmDelete = null; refresh()
                }
                inPill(sc, x, y, 0.73f) -> { confirmDelete = null; engine.sfx.click() }
            }
            return
        }
        if (Ui.hitAppBack(sc, x, y)) { engine.back(); return }
        val i = list.hit(x, y)
        if (i in files.indices) {
            val r = rowRect(sc, i, list.focus(i, sc))
            val tc = trashCenter(r)
            if (com.cayadev.orbo.engine.Mathx.dist(x, y, tc[0], tc[1]) <= r[3] * 0.24f) {
                confirmDelete = files[i]; engine.sfx.click(); engine.haptics.light()
            } else {
                engine.sfx.click(); engine.haptics.light()
                engine.push(SketchTool(files[i]))
            }
        }
    }
}
