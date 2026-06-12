package com.cayadev.orbo.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
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
import com.cayadev.orbo.engine.Ui
import java.io.File
import kotlin.math.hypot
import kotlin.math.sqrt

/** Sketch — a little drawing studio: 8 inks, an eraser, four brush sizes, multi-step undo, clear,
 *  a gallery of saved work, and save-to-storage. Open an existing drawing via the gallery to edit it.
 *  Pass a [loadFile] to start by editing a saved drawing. */
class SketchTool(private val loadFile: File? = null) : Scene() {

    private var bmp: Bitmap? = null
    private var bcanvas: Canvas? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val undoStack = ArrayList<Bitmap>()

    private val paper = 0xFF12151E.toInt()
    private val inks = intArrayOf(Palette.text, Palette.red, Palette.orange, Palette.amber, Palette.mint, Palette.cyan, Palette.violet, Palette.pink)
    private val sizes = floatArrayOf(0.012f, 0.022f, 0.04f, 0.07f)
    private var colorIdx = 5
    private var sizeIdx = 1
    private var eraser = false

    private var lastX = 0f; private var lastY = 0f
    private var drawing = false
    private var t = 0f
    private var savedFx = 0f

    /** File this drawing maps to (when editing a saved one or after first save). */
    private var currentFile: File? = loadFile
    private var pendingLoad: File? = loadFile

    override fun resized(s: ScreenInfo) {
        val w = s.w.toInt().coerceAtLeast(1); val h = s.h.toInt().coerceAtLeast(1)
        val old = bmp
        val nb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val nc = Canvas(nb)
        nc.drawColor(paper)
        val pl = pendingLoad
        if (pl != null) {
            SketchStore.load(pl)?.let { loaded ->
                nc.drawBitmap(loaded, Rect(0, 0, loaded.width, loaded.height), Rect(0, 0, w, h), null)
                loaded.recycle()
            }
            pendingLoad = null
        } else if (old != null) nc.drawBitmap(old, 0f, 0f, null)
        bmp = nb; bcanvas = nc
    }

    override fun update(dt: Float) { t += dt; if (savedFx > 0f) savedFx -= dt }

    // ---- tool geometry (5 icons, shifted right to clear the top-left back button) ----
    private fun topIcon(s: ScreenInfo, i: Int): FloatArray { // 0 gallery,1 undo,2 save,3 size,4 clear
        val y = s.h * 0.13f
        val xs = floatArrayOf(0.39f, 0.49f, 0.59f, 0.69f, 0.79f)
        return floatArrayOf(s.w * xs[i], y, s.px(0.043f))
    }
    private fun swatch(s: ScreenInfo, i: Int): FloatArray { // 0..7 colors, 8 eraser
        val n = 9
        val y = s.h * 0.88f
        val halfW = if (s.isRound) sqrt((s.r * s.r - (y - s.cy) * (y - s.cy)).coerceAtLeast(0f)) - s.px(0.03f) else s.w / 2 - s.px(0.03f)
        val span = (halfW * 2f).coerceAtMost(s.px(0.9f))
        val step = span / (n - 1)
        val x0 = s.cx - span / 2
        return floatArrayOf(x0 + i * step, y, s.px(0.026f))
    }

    override fun onDown(x: Float, y: Float) {
        if (Ui.hitAppBack(sc, x, y)) { engine.back(); return }
        // toolbar hits first
        for (i in 0 until 5) { val r = topIcon(sc, i); if (hypot(x - r[0], y - r[1]) < r[2] * 1.25f) { topAction(i); return } }
        for (i in 0 until 9) { val r = swatch(sc, i); if (hypot(x - r[0], y - r[1]) < r[2] * 1.7f) { selectSwatch(i); return } }
        if (y < sc.h * 0.20f || y > sc.h * 0.81f) return  // dead-zone near toolbars
        // start stroke
        pushUndo()
        drawing = true; lastX = x; lastY = y
        stroke(x, y, x, y)
    }

    override fun onMove(x: Float, y: Float) {
        if (!drawing) return
        stroke(lastX, lastY, x, y); lastX = x; lastY = y
    }
    override fun onUp(x: Float, y: Float) { drawing = false }
    override fun onTap(x: Float, y: Float) {}

    private fun stroke(x0: Float, y0: Float, x1: Float, y1: Float) {
        val cv = bcanvas ?: return
        paint.strokeWidth = sc.px(sizes[sizeIdx])
        if (eraser) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            paint.color = 0
        } else {
            paint.xfermode = null
            paint.color = inks[colorIdx]
        }
        if (x0 == x1 && y0 == y1) cv.drawPoint(x0, y0, paint)
        else cv.drawLine(x0, y0, x1, y1, paint)
        engine.haptics.tick()
    }

    private fun selectSwatch(i: Int) {
        engine.sfx.click(); engine.haptics.light()
        if (i == 8) eraser = true else { eraser = false; colorIdx = i }
    }

    private fun topAction(i: Int) {
        engine.sfx.click(); engine.haptics.light()
        when (i) {
            0 -> engine.push(SketchGalleryScene())
            1 -> undo()
            2 -> save()
            3 -> sizeIdx = (sizeIdx + 1) % sizes.size
            4 -> newDrawing()
        }
    }

    private fun pushUndo() {
        val b = bmp ?: return
        // MUST be a mutable copy — undo() opens a Canvas on it, and a Canvas on an immutable
        // bitmap throws (this was crashing the app, which looked like it "exited to home").
        undoStack.add(b.copy(Bitmap.Config.ARGB_8888, true))
        if (undoStack.size > 10) undoStack.removeAt(0)
    }
    private fun undo() {
        if (undoStack.isEmpty()) return
        val b = undoStack.removeAt(undoStack.size - 1)
        bmp = b; bcanvas = Canvas(b)
    }
    /** Start a fresh, blank drawing that will Save to a NEW file (clears the current file link). */
    private fun newDrawing() {
        bcanvas?.drawColor(paper, PorterDuff.Mode.SRC)
        undoStack.clear()
        currentFile = null; pendingLoad = null
        engine.haptics.medium(); engine.sfx.swoosh()
    }

    private fun save() {
        val b = bmp ?: return
        try {
            // composite over an opaque copy so transparency (eraser) shows the paper colour
            val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
            val cc = Canvas(out); cc.drawColor(paper); cc.drawBitmap(b, 0f, 0f, null)
            val file = currentFile ?: SketchStore.newFile(engine.context)
            if (SketchStore.save(file, out)) {
                currentFile = file
                savedFx = 1.6f; engine.sfx.success(); engine.haptics.success()
            } else { savedFx = -1.6f; engine.sfx.fail() }
            out.recycle()
        } catch (_: Throwable) { savedFx = -1.6f; engine.sfx.fail() }
    }

    override fun render(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.withFaceClip(c, s) {
            Draw.background(c, paper)
            bmp?.let { c.drawBitmap(it, 0f, 0f, null) }
        }
        // top toolbar
        drawTopIcon(c, s, 0, "gallery")
        drawTopIcon(c, s, 1, "undo")
        drawTopIcon(c, s, 2, "save")
        drawTopIcon(c, s, 3, "size")
        drawTopIcon(c, s, 4, "new")
        // swatches
        for (i in 0 until 9) {
            val r = swatch(s, i)
            if (i == 8) {
                val on = eraser
                Draw.strokeCircle(c, r[0], r[1], r[2], if (on) Palette.text else Palette.textDim, s.dp(if (on) 3f else 2f))
                Draw.line(c, r[0] - r[2] * 0.5f, r[1] + r[2] * 0.5f, r[0] + r[2] * 0.5f, r[1] - r[2] * 0.5f, Palette.textDim, s.dp(2f))
            } else {
                val sel = !eraser && colorIdx == i
                if (sel) Draw.strokeCircle(c, r[0], r[1], r[2] * 1.5f, Palette.text, s.dp(2f))
                Draw.fillCircle(c, r[0], r[1], r[2], inks[i])
            }
        }
        Ui.appBackButton(c, s, t, Palette.pink)
        if (savedFx > 0f) Draw.textCentered(c, Loc.t("SAVED ✓"), s.cx, s.h * 0.5f, s.px(0.07f), Palette.mint, Fonts.display, (savedFx / 1.6f).coerceIn(0f, 1f))
        if (savedFx < 0f) Draw.textCentered(c, Loc.t("SAVE FAILED"), s.cx, s.h * 0.5f, s.px(0.06f), Palette.red, Fonts.display, (-savedFx / 1.6f).coerceIn(0f, 1f))
    }

    private fun drawTopIcon(c: Canvas, s: ScreenInfo, i: Int, kind: String) {
        val r = topIcon(s, i)
        Draw.fillCircle(c, r[0], r[1], r[2], Palette.alpha(Palette.panelHi, 0.92f))
        Draw.strokeCircle(c, r[0], r[1], r[2], Palette.alpha(Palette.line, 0.8f), s.dp(1.2f))
        val rr = r[2] * 0.5f
        when (kind) {
            "gallery" -> {
                Draw.roundRectStroke(c, r[0] - rr, r[1] - rr, rr * 2, rr * 2, rr * 0.3f, Palette.text, s.dp(1.8f))
                Draw.fillCircle(c, r[0] - rr * 0.4f, r[1] - rr * 0.4f, rr * 0.26f, Palette.amber)
                Draw.line(c, r[0] - rr * 0.8f, r[1] + rr * 0.7f, r[0] - rr * 0.1f, r[1] - rr * 0.05f, Palette.mint, s.dp(2f))
                Draw.line(c, r[0] - rr * 0.1f, r[1] - rr * 0.05f, r[0] + rr * 0.8f, r[1] + rr * 0.7f, Palette.mint, s.dp(2f))
            }
            "undo" -> {
                Draw.arc(c, r[0], r[1], rr, 40f, 260f, Palette.text, s.dp(2.5f))
                Draw.line(c, r[0] - rr, r[1] - rr * 0.2f, r[0] - rr * 0.2f, r[1] - rr * 0.9f, Palette.text, s.dp(2.5f))
            }
            "save" -> { Draw.line(c, r[0], r[1] - rr, r[0], r[1] + rr * 0.4f, Palette.mint, s.dp(2.5f)); Draw.line(c, r[0] - rr * 0.5f, r[1] - rr * 0.1f, r[0], r[1] + rr * 0.4f, Palette.mint, s.dp(2.5f)); Draw.line(c, r[0] + rr * 0.5f, r[1] - rr * 0.1f, r[0], r[1] + rr * 0.4f, Palette.mint, s.dp(2.5f)) }
            "size" -> Draw.fillCircle(c, r[0], r[1], r[2] * (0.2f + sizeIdx * 0.18f), Palette.cyan)
            "new" -> {
                Draw.roundRectStroke(c, r[0] - rr * 0.75f, r[1] - rr, rr * 1.5f, rr * 2, rr * 0.22f, Palette.text, s.dp(1.8f))
                Draw.line(c, r[0], r[1] - rr * 0.45f, r[0], r[1] + rr * 0.45f, Palette.mint, s.dp(2.3f))
                Draw.line(c, r[0] - rr * 0.45f, r[1], r[0] + rr * 0.45f, r[1], Palette.mint, s.dp(2.3f))
            }
        }
    }

    companion object {
        fun meta() = GameMeta(
            id = "sketch",
            title = "Sketch",
            tagline = "Draw on your wrist",
            category = Category.TOOLS,
            tier = Tier.TOOL,
            accent = Palette.pink,
            icon = IconDrawer { c, cx, cy, size, color ->
                val p = Draw.paint; p.reset(); p.isAntiAlias = true; p.color = color; p.style = Paint.Style.STROKE; p.strokeWidth = size * 0.07f; p.strokeCap = Paint.Cap.ROUND
                val path = android.graphics.Path()
                path.moveTo(cx - size * 0.3f, cy + size * 0.25f)
                path.cubicTo(cx - size * 0.1f, cy - size * 0.35f, cx + size * 0.15f, cy + size * 0.3f, cx + size * 0.32f, cy - size * 0.25f)
                c.drawPath(path, p)
                p.style = Paint.Style.FILL; p.color = Palette.amber
                c.drawCircle(cx + size * 0.32f, cy - size * 0.25f, size * 0.07f, p)
            },
            create = { SketchTool() }
        )
    }
}
