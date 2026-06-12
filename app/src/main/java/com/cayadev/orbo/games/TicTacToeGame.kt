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
 * Tic-Tac-Toe — beat the AI to extend your win streak. The longer you survive, the sharper the
 * opponent plays (random blunders early, near-perfect minimax later). A draw or loss ends the run.
 */
class TicTacToeGame : ArcadeGame(meta()) {

    private val board = IntArray(9)  // 0 empty, 1 player(X), 2 ai(O)
    private var playerTurn = true
    private var aiTimer = 0f
    private var winLine = -1
    private var winColor = Palette.text
    private var roundFx = 0f

    override val controlHint = "TAP A CELL · YOU ARE X"

    override fun onReset() { newRound() }

    private fun newRound() {
        for (i in 0 until 9) board[i] = 0
        playerTurn = true; aiTimer = 0f; winLine = -1; roundFx = 0f
    }

    private fun gridGeom(s: ScreenInfo): FloatArray {
        val size = s.px(0.6f)
        val x0 = s.cx - size / 2f
        val y0 = s.cy - size / 2f
        return floatArrayOf(x0, y0, size / 3f)
    }

    override fun onStep(dt: Float) {
        if (roundFx > 0f) roundFx -= dt
        if (scheduleNext > 0f) {
            scheduleNext -= dt
            if (scheduleNext <= 0f && winner() == 1) newRound()
            return
        }
        if (!playerTurn && winner() == 0) {
            aiTimer -= dt
            if (aiTimer <= 0f) aiMove()
        }
    }

    override fun onTapWorld(x: Float, y: Float) {
        if (!playerTurn || winner() != 0) return
        val g = gridGeom(sc); val cell = g[2]
        val cx = ((x - g[0]) / cell).toInt(); val cy = ((y - g[1]) / cell).toInt()
        if (cx in 0..2 && cy in 0..2) {
            val idx = cy * 3 + cx
            if (board[idx] == 0) {
                board[idx] = 1; engine.sfx.click(); engine.haptics.light()
                playerTurn = false; aiTimer = 0.45f
                evaluate()
            }
        }
    }

    private fun aiMove() {
        val perfect = Mathx.chance((0.28f + score * 0.13f).coerceAtMost(0.97f))
        val move = if (perfect) bestMove() else randomMove()
        if (move >= 0) { board[move] = 2; engine.sfx.beep(300f, 0.08f, 0.35f) }
        playerTurn = true
        evaluate()
    }

    private fun randomMove(): Int {
        val empties = (0 until 9).filter { board[it] == 0 }
        return if (empties.isEmpty()) -1 else empties[Mathx.rand(empties.size)]
    }

    private fun bestMove(): Int {
        var best = -1; var bestScore = Int.MIN_VALUE
        for (i in 0 until 9) if (board[i] == 0) {
            board[i] = 2
            val sc2 = minimax(0, false)
            board[i] = 0
            if (sc2 > bestScore) { bestScore = sc2; best = i }
        }
        return best
    }

    private fun minimax(depth: Int, maximizing: Boolean): Int {
        when (winner()) {
            2 -> return 10 - depth
            1 -> return depth - 10
            3 -> return 0
        }
        return if (maximizing) {
            var b = Int.MIN_VALUE
            for (i in 0 until 9) if (board[i] == 0) { board[i] = 2; b = maxOf(b, minimax(depth + 1, false)); board[i] = 0 }
            b
        } else {
            var b = Int.MAX_VALUE
            for (i in 0 until 9) if (board[i] == 0) { board[i] = 1; b = minOf(b, minimax(depth + 1, true)); board[i] = 0 }
            b
        }
    }

    private val lines = arrayOf(
        intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
        intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
        intArrayOf(0, 4, 8), intArrayOf(2, 4, 6)
    )

    /** 0 none, 1 X, 2 O, 3 draw. */
    private fun winner(): Int {
        for (l in lines) {
            val a = board[l[0]]
            if (a != 0 && a == board[l[1]] && a == board[l[2]]) return a
        }
        return if (board.all { it != 0 }) 3 else 0
    }

    private fun evaluate() {
        val w = winner()
        if (w == 0) return
        winLine = lines.indexOfFirst { val a = board[it[0]]; a != 0 && a == board[it[1]] && a == board[it[2]] }
        when (w) {
            1 -> {  // player won this round
                winColor = accent
                addScore(1); engine.sfx.success(); engine.haptics.success()
                roundFx = 0.9f
                particles.burst(sc.cx, sc.cy, 24, accent, sc.px(0.8f), sc.px(0.02f), 0.7f)
                // continue streak after a short beat — handled by tapping/auto
                scheduleNext = 1.0f
            }
            2 -> { winColor = Palette.red; engine.sfx.fail(); flash(Palette.red, 0.4f); gameOver() }
            3 -> { winColor = Palette.textDim; engine.sfx.fail(); gameOver() }
        }
    }

    private var scheduleNext = 0f

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return

        val g = gridGeom(s); val x0 = g[0]; val y0 = g[1]; val cell = g[2]
        // grid lines
        for (i in 1..2) {
            Draw.line(c, x0 + cell * i, y0, x0 + cell * i, y0 + cell * 3, Palette.line, s.dp(3f))
            Draw.line(c, x0, y0 + cell * i, x0 + cell * 3, y0 + cell * i, Palette.line, s.dp(3f))
        }
        // marks
        for (i in 0 until 9) {
            val cx = x0 + (i % 3) * cell + cell / 2
            val cy = y0 + (i / 3) * cell + cell / 2
            val r = cell * 0.28f
            when (board[i]) {
                1 -> {
                    Draw.line(c, cx - r, cy - r, cx + r, cy + r, accent, cell * 0.10f)
                    Draw.line(c, cx + r, cy - r, cx - r, cy + r, accent, cell * 0.10f)
                }
                2 -> Draw.strokeCircle(c, cx, cy, r, Palette.pink, cell * 0.10f)
            }
        }
        // winning line
        if (winLine in lines.indices) {
            val l = lines[winLine]
            val ax = x0 + (l[0] % 3) * cell + cell / 2; val ay = y0 + (l[0] / 3) * cell + cell / 2
            val bx = x0 + (l[2] % 3) * cell + cell / 2; val by = y0 + (l[2] / 3) * cell + cell / 2
            Draw.line(c, ax, ay, bx, by, winColor, cell * 0.06f)
        }
        if (roundFx > 0f) {
            Draw.textCentered(c, Loc.t("WIN!"), s.cx, s.h * 0.84f, s.px(0.07f) * (1f + (0.9f - roundFx)), accent, Fonts.display, roundFx.coerceIn(0f, 1f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "${Loc.t("STREAK")} $score", s.cx, s.h * 0.12f, s.px(0.05f), Palette.alpha(Palette.text, 0.85f), Fonts.mono)
    }

    companion object {
        fun meta() = GameMeta(
            id = "tictactoe",
            title = "Tic-Tac-Toe",
            tagline = "Beat the AI",
            category = Category.CLASSIC,
            tier = Tier.MINI,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                val s2 = size * 0.34f
                Draw.line(c, cx - s2, cy - s2 / 3, cx + s2, cy - s2 / 3, Palette.alpha(color, 0.5f), size * 0.04f)
                Draw.line(c, cx - s2, cy + s2 / 3, cx + s2, cy + s2 / 3, Palette.alpha(color, 0.5f), size * 0.04f)
                Draw.line(c, cx - s2 / 3, cy - s2, cx - s2 / 3, cy + s2, Palette.alpha(color, 0.5f), size * 0.04f)
                Draw.line(c, cx + s2 / 3, cy - s2, cx + s2 / 3, cy + s2, Palette.alpha(color, 0.5f), size * 0.04f)
                Draw.line(c, cx - s2 * 0.75f, cy - s2 * 0.75f, cx - s2 * 0.1f, cy - s2 * 0.1f, color, size * 0.05f)
                Draw.line(c, cx - s2 * 0.1f, cy - s2 * 0.75f, cx - s2 * 0.75f, cy - s2 * 0.1f, color, size * 0.05f)
                Draw.strokeCircle(c, cx + s2 * 0.42f, cy + s2 * 0.42f, s2 * 0.32f, Palette.pink, size * 0.05f)
            },
            create = { TicTacToeGame() }
        )
    }
}
