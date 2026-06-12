package com.cayadev.orbo.games

import android.graphics.Canvas
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.IconDrawer
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Ease
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Mathx
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame

/** Block Blast — fit the three shapes onto the 8×8 board; complete rows or columns to clear them.
 * No timer, pure spatial strategy. It ends when none of your pieces can fit. Tap a piece, then a
 * highlighted square to drop it. */
class BlockBlastGame : ArcadeGame(meta()) {

    private val n = 8
    private val cell = IntArray(n * n)          // 0 empty else color
    private val clearFx = FloatArray(n * n)
    private val shapes = buildShapes()
    private val tray = arrayOfNulls<Shape>(3)
    private val trayColor = IntArray(3)
    private var selected = -1
    private var placedTotal = 0

    private class Shape(val cells: List<IntArray>) {
        val w = (cells.maxOf { it[0] }) + 1
        val h = (cells.maxOf { it[1] }) + 1
        val ax = w / 2
        val ay = h / 2
    }

    override val controlHint = "TAP PIECE THEN BOARD"
    override val gameOverTitle = "BOARD JAMMED"

    override fun onReset() {
        for (i in cell.indices) { cell[i] = 0; clearFx[i] = 0f }
        selected = -1; placedTotal = 0
        deal()
    }

    private fun deal() {
        for (i in 0 until 3) { tray[i] = shapes[Mathx.rand(shapes.size)]; trayColor[i] = Palette.accents[Mathx.rand(Palette.accents.size)] }
        selected = -1
        if (!anyMoves()) gameOver()
    }

    override fun onStep(dt: Float) {
        for (i in clearFx.indices) if (clearFx[i] > 0f) clearFx[i] -= dt * 2.5f
    }

    private fun gridGeom(s: ScreenInfo): FloatArray {
        // Sized & centred to sit fully inside the round face, below the top-left back button and
        // above the tray — so no cell is ever clipped or covered by the back button.
        val side = if (s.isRound) s.minDim * 0.46f else s.minDim * 0.74f
        val c = side / n
        return floatArrayOf(s.cx - side / 2, s.h * 0.51f - side / 2, c)
    }

    private fun fits(shape: Shape, col: Int, row: Int): Boolean {
        for (cc in shape.cells) {
            val gx = col - shape.ax + cc[0]; val gy = row - shape.ay + cc[1]
            if (gx < 0 || gy < 0 || gx >= n || gy >= n) return false
            if (cell[gy * n + gx] != 0) return false
        }
        return true
    }

    private fun anyMoves(): Boolean {
        for (i in 0 until 3) {
            val sh = tray[i] ?: continue
            for (row in 0 until n) for (col in 0 until n) if (fits(sh, col, row)) return true
        }
        return false
    }

    override fun onTapWorld(x: Float, y: Float) {
        // tray hit?
        val ts = trayGeom(sc)
        for (i in 0 until 3) {
            val tx = ts[0] + i * ts[2]
            if (x in tx..(tx + ts[2]) && y > ts[1] - ts[2] * 0.5f && tray[i] != null) {
                selected = if (selected == i) -1 else i
                engine.sfx.click(); return
            }
        }
        // board placement
        if (selected < 0) return
        val sh = tray[selected] ?: return
        val g = gridGeom(sc)
        val col = ((x - g[0]) / g[2]).toInt(); val row = ((y - g[1]) / g[2]).toInt()
        if (col in 0 until n && row in 0 until n && fits(sh, col, row)) {
            place(sh, col, row, trayColor[selected])
            tray[selected] = null; selected = -1
            if (tray.all { it == null }) deal() else if (!anyMoves()) gameOver()
        } else {
            engine.sfx.hit(); engine.haptics.medium(); shake(sc.px(0.012f))
        }
    }

    private fun place(shape: Shape, col: Int, row: Int, color: Int) {
        var placed = 0
        for (cc in shape.cells) {
            val gx = col - shape.ax + cc[0]; val gy = row - shape.ay + cc[1]
            cell[gy * n + gx] = color; placed++
        }
        placedTotal += placed
        addScore(placed)
        engine.sfx.blip(); engine.haptics.light()
        clearLines()
    }

    private fun clearLines() {
        val fullRows = ArrayList<Int>(); val fullCols = ArrayList<Int>()
        for (r in 0 until n) if ((0 until n).all { cell[r * n + it] != 0 }) fullRows.add(r)
        for (cc in 0 until n) if ((0 until n).all { cell[it * n + cc] != 0 }) fullCols.add(cc)
        val lines = fullRows.size + fullCols.size
        if (lines == 0) return
        for (r in fullRows) for (cc in 0 until n) { cell[r * n + cc] = 0; clearFx[r * n + cc] = 1f }
        for (cc in fullCols) for (r in 0 until n) { cell[r * n + cc] = 0; clearFx[r * n + cc] = 1f }
        addScore(lines * 10 * lines)   // combo bonus for multi-clears
        engine.sfx.coin(); engine.haptics.success(); flash(accent, 0.2f)
        val g = gridGeom(sc)
        for (r in fullRows) particles.burst(g[0] + n * g[2] / 2, g[1] + r * g[2] + g[2] / 2, 18, accent, sc.px(0.7f), sc.px(0.015f), 0.5f)
        for (cc in fullCols) particles.burst(g[0] + cc * g[2] + g[2] / 2, g[1] + n * g[2] / 2, 18, accent, sc.px(0.7f), sc.px(0.015f), 0.5f)
    }

    private fun trayGeom(s: ScreenInfo): FloatArray {
        val total = s.px(0.72f); val slot = total / 3
        return floatArrayOf(s.cx - total / 2, s.h * 0.85f, slot)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val g = gridGeom(s); val ox = g[0]; val oy = g[1]; val cs = g[2]
        Draw.roundRect(c, ox - cs * 0.15f, oy - cs * 0.15f, cs * n + cs * 0.3f, cs * n + cs * 0.3f, cs * 0.3f, Palette.alpha(Palette.panel, 0.5f))
        // valid highlights for selected
        if (selected in 0..2) {
            val sh = tray[selected]
            if (sh != null) for (row in 0 until n) for (col in 0 until n) if (fits(sh, col, row)) {
                // highlight the anchor area lightly
            }
        }
        for (i in 0 until n * n) {
            val gx = i % n; val gy = i / n
            val cx = ox + gx * cs + cs / 2; val cy = oy + gy * cs + cs / 2
            if (cell[i] != 0) {
                Draw.roundRectCentered(c, cx, cy, cs * 0.92f, cs * 0.92f, cs * 0.2f, cell[i])
                Draw.roundRectCentered(c, cx, cy - cs * 0.05f, cs * 0.84f, cs * 0.36f, cs * 0.18f, Palette.alpha(Palette.text, 0.12f))
            } else {
                Draw.roundRectCentered(c, cx, cy, cs * 0.9f, cs * 0.9f, cs * 0.18f, Palette.alpha(Palette.bgSoft, 0.8f))
                if (clearFx[i] > 0f) Draw.roundRectCentered(c, cx, cy, cs * 0.92f * clearFx[i], cs * 0.92f * clearFx[i], cs * 0.2f, Palette.alpha(accent, clearFx[i]))
            }
        }
        // ghost preview of selected piece
        if (selected in 0..2) {
            val sh = tray[selected]
            if (sh != null) drawGhostHint(c, s, g, sh)
        }
        // tray
        val tg = trayGeom(s)
        for (i in 0 until 3) {
            val sh = tray[i] ?: continue
            val tcx = tg[0] + i * tg[2] + tg[2] / 2
            drawTrayPiece(c, tcx, tg[1], sh, trayColor[i], i == selected, s)
        }
    }

    private fun drawGhostHint(c: Canvas, s: ScreenInfo, g: FloatArray, sh: Shape) {
        // subtle pulse on all valid cells so the player sees where it can go
        val a = 0.10f + 0.06f * Ease.pulse(timeAlive * 1.5f)
        for (row in 0 until n) for (col in 0 until n) if (fits(sh, col, row)) {
            val cx = g[0] + col * g[2] + g[2] / 2; val cy = g[1] + row * g[2] + g[2] / 2
            Draw.fillCircle(c, cx, cy, g[2] * 0.10f, Palette.alpha(accent, a + 0.15f))
        }
    }

    private fun drawTrayPiece(c: Canvas, cx: Float, cy: Float, sh: Shape, color: Int, sel: Boolean, s: ScreenInfo) {
        val u = s.px(0.045f)
        val offX = cx - sh.w * u / 2; val offY = cy - sh.h * u / 2
        if (sel) {
            Draw.roundRectCentered(c, cx, cy, s.px(0.22f), s.px(0.16f), s.px(0.04f), Palette.alpha(color, 0.14f))
            Draw.glow(c, cx, cy, s.px(0.13f), color, 0.25f)
        }
        for (cc in sh.cells) {
            Draw.roundRect(c, offX + cc[0] * u + u * 0.06f, offY + cc[1] * u + u * 0.06f, u * 0.88f, u * 0.88f, u * 0.2f, color)
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.08f, s.px(0.055f), Palette.text, Fonts.display)
    }

    companion object {
        private fun buildShapes(): List<Shape> {
            fun s(vararg p: Pair<Int, Int>) = Shape(p.map { intArrayOf(it.first, it.second) })
            return listOf(
                s(0 to 0),
                s(0 to 0, 1 to 0), s(0 to 0, 0 to 1),
                s(0 to 0, 1 to 0, 2 to 0), s(0 to 0, 0 to 1, 0 to 2),
                s(0 to 0, 1 to 0, 2 to 0, 3 to 0), s(0 to 0, 0 to 1, 0 to 2, 0 to 3),
                s(0 to 0, 1 to 0, 0 to 1, 1 to 1),                         // 2x2
                s(0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1, 0 to 2, 1 to 2, 2 to 2), // 3x3
                s(0 to 0, 0 to 1, 1 to 1),                                  // L corners
                s(1 to 0, 0 to 1, 1 to 1),
                s(0 to 0, 1 to 0, 0 to 1),
                s(0 to 0, 1 to 0, 1 to 1),
                s(0 to 0, 0 to 1, 0 to 2, 1 to 2),                          // L
                s(1 to 0, 1 to 1, 0 to 2, 1 to 2),
                s(0 to 0, 1 to 0, 2 to 0, 1 to 1),                          // T
                s(1 to 0, 0 to 1, 1 to 1, 2 to 1),
                s(0 to 1, 1 to 1, 1 to 0, 2 to 0)                           // S
            )
        }

        fun meta() = GameMeta(
            id = "block_blast",
            title = "Cubo",
            tagline = "Fit & clear lines",
            category = Category.PUZZLE,
            tier = Tier.BIG,
            accent = Palette.cyan,
            featured = true,
            icon = IconDrawer { c, cx, cy, size, _ ->
                val u = size * 0.16f
                Draw.roundRectCentered(c, cx - u, cy - u, u * 0.9f, u * 0.9f, u * 0.2f, Palette.cyan)
                Draw.roundRectCentered(c, cx, cy - u, u * 0.9f, u * 0.9f, u * 0.2f, Palette.cyan)
                Draw.roundRectCentered(c, cx - u, cy, u * 0.9f, u * 0.9f, u * 0.2f, Palette.cyan)
                Draw.roundRectCentered(c, cx + u, cy + u, u * 0.9f, u * 0.9f, u * 0.2f, Palette.pink)
                Draw.roundRectCentered(c, cx + u, cy, u * 0.9f, u * 0.9f, u * 0.2f, Palette.pink)
            },
            create = { BlockBlastGame() }
        )
    }
}
