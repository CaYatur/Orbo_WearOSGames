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
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame

/**
 * Gomoku — five in a row on a 9×9 board. Tap an intersection to place your stone; the AI weighs every
 * threat and answers. Beat it to score and keep the streak; lose and the run ends. Built to fit a
 * round watch face.
 */
class GomokuGame : ArcadeGame(meta()) {

    private val N = 9
    private val board = IntArray(N * N)
    private var turn = 1
    private var winner = 0
    private val winLine = ArrayList<Int>()
    private var aiTimer = 0f
    private var endTimer = 0f
    private var last = -1
    private val dirs = arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1))

    override val controlHint = "TAP TO PLACE A STONE"

    override fun onReset() { board.fill(0); turn = 1; winner = 0; winLine.clear(); aiTimer = 0f; endTimer = 0f; last = -1 }

    private fun inb(r: Int, c: Int) = r in 0 until N && c in 0 until N

    private fun winAt(r: Int, c: Int, who: Int): List<Int> {
        for (d in dirs) {
            val cells = ArrayList<Int>(); cells.add(r * N + c)
            var fr = r + d[0]; var fc = c + d[1]
            while (inb(fr, fc) && board[fr * N + fc] == who) { cells.add(fr * N + fc); fr += d[0]; fc += d[1] }
            var br = r - d[0]; var bc = c - d[1]
            while (inb(br, bc) && board[br * N + bc] == who) { cells.add(br * N + bc); br -= d[0]; bc -= d[1] }
            if (cells.size >= 5) return cells
        }
        return emptyList()
    }

    private fun lineScore(r: Int, c: Int, who: Int): Int {
        var total = 0
        for (d in dirs) {
            var fcount = 0; var fr = r + d[0]; var fc = c + d[1]
            while (inb(fr, fc) && board[fr * N + fc] == who) { fcount++; fr += d[0]; fc += d[1] }
            val fOpen = inb(fr, fc) && board[fr * N + fc] == 0
            var bcount = 0; var br = r - d[0]; var bc = c - d[1]
            while (inb(br, bc) && board[br * N + bc] == who) { bcount++; br -= d[0]; bc -= d[1] }
            val bOpen = inb(br, bc) && board[br * N + bc] == 0
            val count = 1 + fcount + bcount
            val ends = (if (fOpen) 1 else 0) + (if (bOpen) 1 else 0)
            total += when {
                count >= 5 -> 1000000
                count == 4 && ends == 2 -> 50000
                count == 4 && ends == 1 -> 6000
                count == 3 && ends == 2 -> 3000
                count == 3 && ends == 1 -> 200
                count == 2 && ends == 2 -> 120
                count == 2 && ends == 1 -> 20
                else -> ends * 2
            }
        }
        return total
    }

    private fun hasNeighbor(cell: Int): Boolean {
        val r = cell / N; val c = cell % N
        for (dr in -1..1) for (dc in -1..1) { if (dr == 0 && dc == 0) continue; val nr = r + dr; val nc = c + dc; if (inb(nr, nc) && board[nr * N + nc] != 0) return true }
        return false
    }

    private fun aiMove() {
        var bestCell = -1; var bestVal = -1.0; var anyStone = false
        for (i in 0 until N * N) if (board[i] != 0) { anyStone = true; break }
        for (i in 0 until N * N) {
            if (board[i] != 0) continue
            if (anyStone && !hasNeighbor(i)) continue
            val r = i / N; val c = i % N
            board[i] = 2; val aiS = lineScore(r, c, 2).toDouble(); board[i] = 0
            board[i] = 1; val plS = lineScore(r, c, 1).toDouble(); board[i] = 0
            val centerBias = -(kotlin.math.abs(r - N / 2) + kotlin.math.abs(c - N / 2)) * 0.5
            val v = maxOf(aiS * 1.0, plS * 0.95) + centerBias + Mathx.rand(0f, 2f)
            if (v > bestVal) { bestVal = v; bestCell = i }
        }
        if (bestCell < 0) bestCell = (0 until N * N).firstOrNull { board[it] == 0 } ?: return
        commit(bestCell, 2)
    }

    private fun commit(cell: Int, who: Int) {
        board[cell] = who; last = cell
        engine.sfx.click(); engine.haptics.light()
        val r = cell / N; val c = cell % N
        val line = winAt(r, c, who)
        if (line.isNotEmpty()) { winner = who; winLine.clear(); winLine.addAll(line); onEnd() }
        else if ((0 until N * N).none { board[it] == 0 }) { winner = 0; endTimer = 1.2f }
        else turn = if (who == 1) 2 else 1
    }

    private fun onEnd() {
        if (winner == 1) { addScore(150); engine.sfx.success(); engine.haptics.success(); flash(accent, 0.25f) }
        else { engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.25f) }
        endTimer = 1.8f
    }

    override fun onStep(dt: Float) {
        if (winner != 0 || endTimer > 0f) {
            endTimer -= dt
            if (endTimer <= 0f) { if (winner == 2) gameOver() else onReset() }
            return
        }
        if (turn == 2) { aiTimer += dt; if (aiTimer > 0.4f) { aiTimer = 0f; aiMove() } }
    }

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.66f else s.minDim * 0.86f
    private fun left(s: ScreenInfo) = s.cx - side(s) / 2f
    private fun top(s: ScreenInfo) = s.cy - side(s) / 2f + s.h * 0.02f
    private fun cell(s: ScreenInfo) = side(s) / N

    override fun onTapWorld(x: Float, y: Float) {
        if (turn != 1 || winner != 0 || endTimer > 0f) return
        val s = sc; val cs = cell(s)
        val c = ((x - left(s)) / cs).toInt(); val r = ((y - top(s)) / cs).toInt()
        if (inb(r, c) && board[r * N + c] == 0) commit(r * N + c, 1)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val cs = cell(s); val l = left(s); val t = top(s)
        Draw.roundRect(c, l, t, cs * N, cs * N, s.dp(4f), Palette.alpha(0xFF2A2118.toInt(), 0.7f))
        for (i in 0 until N) {
            Draw.line(c, l + cs * 0.5f, t + i * cs + cs * 0.5f, l + cs * (N - 0.5f), t + i * cs + cs * 0.5f, Palette.alpha(Palette.line, 0.7f), s.dp(1f))
            Draw.line(c, l + i * cs + cs * 0.5f, t + cs * 0.5f, l + i * cs + cs * 0.5f, t + cs * (N - 0.5f), Palette.alpha(Palette.line, 0.7f), s.dp(1f))
        }
        for (i in 0 until N * N) if (board[i] != 0) {
            val r = i / N; val col = i % N
            val cx = l + col * cs + cs / 2; val cy = t + r * cs + cs / 2
            val color = if (board[i] == 1) accent else Palette.red
            Draw.glow(c, cx, cy, cs * 0.4f, color, 0.2f)
            Draw.fillCircle(c, cx, cy, cs * 0.38f, color)
            if (i == last) Draw.strokeCircle(c, cx, cy, cs * 0.38f, Palette.text, s.dp(1.5f))
            if (i in winLine) Draw.strokeCircle(c, cx, cy, cs * 0.4f, Palette.gold, s.dp(2.5f))
        }
        if (winner != 0)
            Draw.textCentered(c, Loc.t(if (winner == 1) "YOU WIN" else "AI WINS"), s.cx, s.h * 0.95f, s.px(0.05f),
                if (winner == 1) accent else Palette.red, Fonts.display)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.06f, s.px(0.045f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "gomoku",
            title = "Gomoku",
            tagline = "Five in a row",
            category = Category.STRATEGY,
            tier = Tier.MINI,
            accent = Palette.mint,
            icon = IconDrawer { c, cx, cy, size, color ->
                for (i in 0 until 4) Draw.line(c, cx - size * 0.4f, cy - size * 0.4f + i * size * 0.27f, cx + size * 0.4f, cy - size * 0.4f + i * size * 0.27f, Palette.alpha(color, 0.4f), size * 0.025f)
                for (i in 0 until 5) Draw.fillCircle(c, cx - size * 0.32f + i * size * 0.16f, cy + size * 0.32f - i * size * 0.16f, size * 0.08f, color)
            },
            create = { GomokuGame() }
        )
    }
}
