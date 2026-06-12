package com.cayadev.orbo.games

import android.graphics.Canvas
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.IconDrawer
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Loc
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame

/**
 * Reversi — outflank the AI on the 8×8 board. Tap a highlighted square to drop your disc and flip the
 * line between it and your other discs. Hold the most discs when the board fills to win, score and
 * play on; lose and the run ends.
 */
class ReversiGame : ArcadeGame(meta()) {

    private val N = 8
    private val board = IntArray(N * N)        // 0 empty, 1 player, 2 ai
    private var turn = 1
    private var ended = false
    private var endTimer = 0f
    private var aiTimer = 0f
    private var resultWin = 0
    private val dr = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)
    private val dc = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
    private val weight = intArrayOf(
        120, -20, 20, 5, 5, 20, -20, 120,
        -20, -40, -5, -5, -5, -5, -40, -20,
        20, -5, 15, 3, 3, 15, -5, 20,
        5, -5, 3, 3, 3, 3, -5, 5,
        5, -5, 3, 3, 3, 3, -5, 5,
        20, -5, 15, 3, 3, 15, -5, 20,
        -20, -40, -5, -5, -5, -5, -40, -20,
        120, -20, 20, 5, 5, 20, -20, 120
    )

    override val controlHint = "TAP A HIGHLIGHTED SQUARE"

    override fun onReset() {
        board.fill(0)
        board[3 * N + 3] = 2; board[3 * N + 4] = 1; board[4 * N + 3] = 1; board[4 * N + 4] = 2
        turn = 1; ended = false; endTimer = 0f; aiTimer = 0f; resultWin = 0
    }

    private fun inb(r: Int, c: Int) = r in 0 until N && c in 0 until N

    private fun flips(cell: Int, who: Int): List<Int> {
        if (board[cell] != 0) return emptyList()
        val r0 = cell / N; val c0 = cell % N; val other = 3 - who
        val out = ArrayList<Int>()
        for (d in 0 until 8) {
            val run = ArrayList<Int>()
            var r = r0 + dr[d]; var c = c0 + dc[d]
            while (inb(r, c) && board[r * N + c] == other) { run.add(r * N + c); r += dr[d]; c += dc[d] }
            if (run.isNotEmpty() && inb(r, c) && board[r * N + c] == who) out.addAll(run)
        }
        return out
    }

    private fun legal(who: Int): List<Int> = (0 until N * N).filter { board[it] == 0 && flips(it, who).isNotEmpty() }

    private fun apply(cell: Int, who: Int) {
        val f = flips(cell, who)
        board[cell] = who
        for (x in f) board[x] = who
        engine.sfx.click(); engine.haptics.light()
        nextTurn(who)
    }

    private fun nextTurn(who: Int) {
        val other = 3 - who
        if (legal(other).isNotEmpty()) turn = other
        else if (legal(who).isNotEmpty()) turn = who
        else endGame()
    }

    private fun endGame() {
        ended = true
        var p = 0; var a = 0
        for (v in board) { if (v == 1) p++ else if (v == 2) a++ }
        resultWin = if (p > a) 1 else if (a > p) 2 else 0
        if (resultWin == 1) { addScore(120); engine.sfx.success(); engine.haptics.success(); flash(accent, 0.25f) }
        else { engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.25f) }
        endTimer = 2f
    }

    private fun aiMove() {
        val moves = legal(2)
        if (moves.isEmpty()) { nextTurn(2); return }
        var best = moves[0]; var bestV = Int.MIN_VALUE
        for (m in moves) {
            val v = weight[m] + flips(m, 2).size
            if (v > bestV) { bestV = v; best = m }
        }
        apply(best, 2)
    }

    override fun onStep(dt: Float) {
        if (ended) { endTimer -= dt; if (endTimer <= 0f) { if (resultWin == 1) onReset() else gameOver() }; return }
        if (turn == 2) { aiTimer += dt; if (aiTimer > 0.5f) { aiTimer = 0f; aiMove() } }
    }

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.64f else s.minDim * 0.84f
    private fun left(s: ScreenInfo) = s.cx - side(s) / 2f
    private fun top(s: ScreenInfo) = s.cy - side(s) / 2f + s.h * 0.02f
    private fun cell(s: ScreenInfo) = side(s) / N

    override fun onTapWorld(x: Float, y: Float) {
        if (ended || turn != 1) return
        val s = sc; val cs = cell(s)
        val c = ((x - left(s)) / cs).toInt(); val r = ((y - top(s)) / cs).toInt()
        if (!inb(r, c)) return
        val cell = r * N + c
        if (board[cell] == 0 && flips(cell, 1).isNotEmpty()) apply(cell, 1)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val cs = cell(s); val l = left(s); val t = top(s)
        Draw.roundRect(c, l - s.dp(2f), t - s.dp(2f), cs * N + s.dp(4f), cs * N + s.dp(4f), s.dp(4f), Palette.alpha(0xFF0C3A24.toInt(), 0.9f))
        for (i in 0 until N * N) {
            val r = i / N; val col = i % N
            val x = l + col * cs; val y = t + r * cs
            Draw.roundRectStroke(c, x + s.dp(0.5f), y + s.dp(0.5f), cs - s.dp(1f), cs - s.dp(1f), s.dp(1f), Palette.alpha(Palette.line, 0.5f), s.dp(1f))
            val v = board[i]
            if (v != 0) {
                val color = if (v == 1) accent else Palette.text
                Draw.fillCircle(c, x + cs / 2, y + cs / 2, cs * 0.38f, color)
                Draw.fillCircle(c, x + cs / 2 - cs * 0.1f, y + cs / 2 - cs * 0.1f, cs * 0.12f, Palette.alpha(Palette.text, 0.25f))
            }
        }
        if (!ended && turn == 1) for (m in legal(1)) {
            val r = m / N; val col = m % N
            Draw.fillCircle(c, l + col * cs + cs / 2, t + r * cs + cs / 2, cs * 0.12f, Palette.alpha(accent, 0.5f))
        }
        // disc count + result
        var p = 0; var a = 0; for (v in board) { if (v == 1) p++ else if (v == 2) a++ }
        Draw.textCentered(c, "$p", s.cx - s.px(0.16f), s.h * 0.95f, s.px(0.05f), accent, Fonts.display)
        Draw.textCentered(c, "$a", s.cx + s.px(0.16f), s.h * 0.95f, s.px(0.05f), Palette.text, Fonts.display)
        if (ended)
            Draw.textCentered(c, Loc.t(if (resultWin == 1) "YOU WIN" else if (resultWin == 2) "AI WINS" else "DRAW"), s.cx, s.h * 0.88f, s.px(0.05f),
                if (resultWin == 1) accent else Palette.red, Fonts.display)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.06f, s.px(0.045f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "reversi",
            title = "Reversi",
            tagline = "Flip and flank",
            category = Category.STRATEGY,
            tier = Tier.BIG,
            accent = Palette.mint,
            featured = true,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx, cy, size * 0.9f, size * 0.9f, size * 0.1f, Palette.alpha(0xFF0C3A24.toInt(), 0.9f))
                Draw.fillCircle(c, cx - size * 0.18f, cy - size * 0.18f, size * 0.16f, Palette.text)
                Draw.fillCircle(c, cx + size * 0.18f, cy + size * 0.18f, size * 0.16f, Palette.text)
                Draw.fillCircle(c, cx + size * 0.18f, cy - size * 0.18f, size * 0.16f, color)
                Draw.fillCircle(c, cx - size * 0.18f, cy + size * 0.18f, size * 0.16f, color)
            },
            create = { ReversiGame() }
        )
    }
}
