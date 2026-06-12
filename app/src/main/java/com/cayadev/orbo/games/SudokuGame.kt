package com.cayadev.orbo.games

import android.graphics.Canvas
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.IconDrawer
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Loc
import com.cayadev.orbo.engine.Mathx
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame

/**
 * Sudoku — bite-size 4×4 and 6×6 grids against the clock. Tap a cell, tap a number to place it. Every
 * row, column and box must hold each digit once. Conflicts glow red. Solve grids to bank points and
 * buy time; the clock is the only way to lose.
 */
class SudokuGame : ArcadeGame(meta()) {

    private var n = 4
    private var boxH = 2
    private var boxW = 2
    private lateinit var solution: IntArray
    private lateinit var cellv: IntArray
    private lateinit var fixed: BooleanArray
    private var selected = -1
    private var timeLeft = 180f
    private var solvedCount = 0

    override val controlHint = "TAP CELL · TAP NUMBER"
    override val gameOverTitle = "TIME UP"

    override fun onReset() { timeLeft = 180f; solvedCount = 0; newPuzzle(true) }

    private fun newPuzzle(small: Boolean) {
        if (small) { n = 4; boxH = 2; boxW = 2 } else { n = 6; boxH = 2; boxW = 3 }
        solution = IntArray(n * n)
        // base valid pattern, then shuffle bands/digits for variety
        fun pat(r: Int, c: Int) = (boxW * (r % boxH) + r / boxH + c) % n
        val rPerm = shuffleBands(n / boxH, boxH)
        val cPerm = shuffleBands(n / boxW, boxW)
        val digit = (0 until n).toMutableList(); for (i in digit.indices.reversed()) { val j = Mathx.rand(i + 1); val t = digit[i]; digit[i] = digit[j]; digit[j] = t }
        for (r in 0 until n) for (c in 0 until n) solution[r * n + c] = digit[pat(rPerm[r], cPerm[c])] + 1
        cellv = IntArray(n * n)
        fixed = BooleanArray(n * n)
        val keep = if (n == 4) 8 else 16
        val idxs = (0 until n * n).toMutableList(); for (i in idxs.indices.reversed()) { val j = Mathx.rand(i + 1); val t = idxs[i]; idxs[i] = idxs[j]; idxs[j] = t }
        for (k in 0 until keep) { val id = idxs[k]; cellv[id] = solution[id]; fixed[id] = true }
        selected = -1
    }

    private fun shuffleBands(bands: Int, sz: Int): IntArray {
        val bandOrder = (0 until bands).toMutableList(); for (i in bandOrder.indices.reversed()) { val j = Mathx.rand(i + 1); val t = bandOrder[i]; bandOrder[i] = bandOrder[j]; bandOrder[j] = t }
        val out = IntArray(bands * sz)
        var p = 0
        for (b in bandOrder) {
            val inner = (0 until sz).toMutableList(); for (i in inner.indices.reversed()) { val j = Mathx.rand(i + 1); val t = inner[i]; inner[i] = inner[j]; inner[j] = t }
            for (s in inner) { out[p++] = b * sz + s }
        }
        return out
    }

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.56f else s.minDim * 0.72f
    private fun top(s: ScreenInfo) = s.h * 0.15f
    private fun left(s: ScreenInfo) = s.cx - side(s) / 2f
    private fun cell(s: ScreenInfo) = side(s) / n
    private fun padY(s: ScreenInfo) = s.h * 0.86f
    private fun padBtnW(s: ScreenInfo) = (if (s.isRound) s.minDim * 0.62f else s.minDim * 0.8f) / (n + 1)

    override fun onStep(dt: Float) {
        timeLeft -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver() }
    }

    override fun onTapWorld(x: Float, y: Float) {
        val s = sc
        // number pad
        val bw = padBtnW(s); val totalW = bw * (n + 1); val startX = s.cx - totalW / 2f
        if (y in (padY(s) - bw * 0.6f)..(padY(s) + bw * 0.6f)) {
            val idx = ((x - startX) / bw).toInt()
            if (idx in 0..n) {
                if (selected >= 0 && !fixed[selected]) {
                    cellv[selected] = if (idx == n) 0 else idx + 1
                    engine.sfx.click(); engine.haptics.tick()
                    checkSolved()
                }
                return
            }
        }
        // board
        val cs = cell(s)
        val c = ((x - left(s)) / cs).toInt(); val r = ((y - top(s)) / cs).toInt()
        if (r in 0 until n && c in 0 until n) {
            selected = r * n + c; engine.sfx.blip()
        }
    }

    private fun conflict(idx: Int): Boolean {
        if (cellv[idx] == 0) return false
        val r = idx / n; val c = idx % n; val v = cellv[idx]
        for (k in 0 until n) {
            if (k != c && cellv[r * n + k] == v) return true
            if (k != r && cellv[k * n + c] == v) return true
        }
        val br = (r / boxH) * boxH; val bc = (c / boxW) * boxW
        for (rr in br until br + boxH) for (cc in bc until bc + boxW) {
            val i2 = rr * n + cc
            if (i2 != idx && cellv[i2] == v) return true
        }
        return false
    }

    private fun checkSolved() {
        for (i in 0 until n * n) if (cellv[i] == 0 || conflict(i)) return
        solvedCount++
        addScore(100 + n * 20); timeLeft += if (n == 4) 20f else 35f
        engine.sfx.success(); engine.haptics.success(); flash(accent, 0.25f)
        newPuzzle(solvedCount % 2 == 1)   // alternate 4x4 / 6x6
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val cs = cell(s); val l = left(s); val t = top(s)
        // cells
        for (i in 0 until n * n) {
            val r = i / n; val cc = i % n
            val x = l + cc * cs; val y = t + r * cs
            val sel = selected == i
            Draw.roundRect(c, x + s.dp(1f), y + s.dp(1f), cs - s.dp(2f), cs - s.dp(2f), cs * 0.08f,
                if (sel) Palette.alpha(accent, 0.25f) else Palette.panel)
            if (cellv[i] != 0) {
                val col = when { conflict(i) -> Palette.red; fixed[i] -> Palette.text; else -> accent }
                Draw.textCentered(c, "${cellv[i]}", x + cs / 2, y + cs / 2, cs * 0.55f, col, Fonts.display)
            }
        }
        // box borders
        for (bx in 0..(n / boxW)) Draw.line(c, l + bx * boxW * cs, t, l + bx * boxW * cs, t + n * cs, Palette.alpha(accent, 0.5f), s.dp(2f))
        for (by in 0..(n / boxH)) Draw.line(c, l, t + by * boxH * cs, l + n * cs, t + by * boxH * cs, Palette.alpha(accent, 0.5f), s.dp(2f))
        // number pad
        val bw = padBtnW(s); val totalW = bw * (n + 1); val startX = s.cx - totalW / 2f
        for (k in 0..n) {
            val bxp = startX + k * bw + bw / 2
            Draw.roundRectCentered(c, bxp, padY(s), bw * 0.84f, bw * 0.96f, bw * 0.16f, Palette.alpha(Palette.panelHi, 0.95f))
            val label = if (k == n) "✕" else "${k + 1}"
            Draw.textCentered(c, label, bxp, padY(s), bw * 0.5f, if (k == n) Palette.red else accent, Fonts.bodyBold)
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        Ui.edgeProgress(c, s, timeLeft / 180f, Palette.lerp(Palette.red, accent, timeLeft / 180f))
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.07f, s.px(0.045f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "sudoku",
            title = "Sudoku",
            tagline = "Fill the grid",
            category = Category.PUZZLE,
            tier = Tier.BIG,
            accent = Palette.blue,
            icon = IconDrawer { c, cx, cy, size, color ->
                val u = size * 0.26f
                Draw.roundRectStroke(c, cx - size * 0.4f, cy - size * 0.4f, size * 0.8f, size * 0.8f, size * 0.06f, Palette.alpha(color, 0.5f), size * 0.04f)
                Draw.line(c, cx, cy - size * 0.4f, cx, cy + size * 0.4f, Palette.alpha(color, 0.5f), size * 0.04f)
                Draw.line(c, cx - size * 0.4f, cy, cx + size * 0.4f, cy, Palette.alpha(color, 0.5f), size * 0.04f)
                Draw.textCentered(c, "4", cx - u * 0.7f, cy - u * 0.7f, u, color, Fonts.display)
                Draw.textCentered(c, "9", cx + u * 0.7f, cy + u * 0.7f, u, color, Fonts.display)
            },
            create = { SudokuGame() }
        )
    }
}
