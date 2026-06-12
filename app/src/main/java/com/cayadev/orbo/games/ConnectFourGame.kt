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
 * Connect Four — line up four discs before the AI does. Tap a column to drop your disc; the AI
 * answers instantly. Win to score and keep your streak alive; a loss ends the run. The board is sized
 * to sit comfortably inside a round face.
 */
class ConnectFourGame : ArcadeGame(meta()) {

    private val COLS = 7
    private val ROWS = 6
    private val board = IntArray(COLS * ROWS)   // 0 empty, 1 player, 2 ai
    private var turn = 1
    private var winner = 0
    private val winLine = ArrayList<Int>()
    private var aiTimer = 0f
    private var dropCol = -1
    private var dropFx = 0f
    private var wins = 0

    override val controlHint = "TAP A COLUMN TO DROP"

    override fun onReset() { board.fill(0); turn = 1; winner = 0; winLine.clear(); aiTimer = 0f; dropFx = 0f }

    private fun idx(r: Int, c: Int) = r * COLS + c
    private fun lowestEmpty(c: Int): Int { for (r in ROWS - 1 downTo 0) if (board[idx(r, c)] == 0) return r; return -1 }
    private fun colFull(c: Int) = board[idx(0, c)] != 0
    private fun boardFull(): Boolean { for (c in 0 until COLS) if (!colFull(c)) return false; return true }

    private fun place(c: Int, who: Int): Boolean {
        val r = lowestEmpty(c); if (r < 0) return false
        board[idx(r, c)] = who; dropCol = c; dropFx = 0.3f
        return true
    }

    /** Returns the winning line (4 cells) if [who] has 4-in-a-row through (r,c), else empty. */
    private fun lineThrough(r: Int, c: Int, who: Int): List<Int> {
        val dirs = arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1))
        for (d in dirs) {
            val cells = ArrayList<Int>(); cells.add(idx(r, c))
            for (s in 1..3) { val rr = r + d[0] * s; val cc = c + d[1] * s; if (rr in 0 until ROWS && cc in 0 until COLS && board[idx(rr, cc)] == who) cells.add(idx(rr, cc)) else break }
            for (s in 1..3) { val rr = r - d[0] * s; val cc = c - d[1] * s; if (rr in 0 until ROWS && cc in 0 until COLS && board[idx(rr, cc)] == who) cells.add(idx(rr, cc)) else break }
            if (cells.size >= 4) return cells
        }
        return emptyList()
    }

    private fun wouldWin(c: Int, who: Int): Boolean {
        val r = lowestEmpty(c); if (r < 0) return false
        board[idx(r, c)] = who
        val win = lineThrough(r, c, who).isNotEmpty()
        board[idx(r, c)] = 0
        return win
    }

    private fun aiMove() {
        // 1) win now  2) block player  3) avoid handing a win  4) prefer centre
        for (c in 0 until COLS) if (!colFull(c) && wouldWin(c, 2)) { commit(c, 2); return }
        for (c in 0 until COLS) if (!colFull(c) && wouldWin(c, 1)) { commit(c, 2); return }
        val order = intArrayOf(3, 2, 4, 1, 5, 0, 6)
        val safe = ArrayList<Int>()
        for (c in order) if (!colFull(c)) {
            // does dropping here let the player win on top?
            val r = lowestEmpty(c); board[idx(r, c)] = 2
            var gives = false
            if (r - 1 >= 0 && wouldWin(c, 1)) gives = true
            board[idx(r, c)] = 0
            if (!gives) safe.add(c)
        }
        val pick = if (safe.isNotEmpty()) safe[0] else order.first { !colFull(it) }
        commit(pick, 2)
    }

    private fun commit(c: Int, who: Int) {
        if (!place(c, who)) return
        val placedRow = firstFilledRow(c)
        val line = lineThrough(placedRow, c, who)
        if (line.isNotEmpty()) { winner = who; winLine.clear(); winLine.addAll(line); onEnd() }
        else if (boardFull()) { winner = 0; onEnd(); /* draw */ resetBoardSoon() }
        else turn = if (who == 1) 2 else 1
        engine.sfx.click(); engine.haptics.light()
    }

    private fun firstFilledRow(c: Int): Int { for (r in 0 until ROWS) if (board[idx(r, c)] != 0) return r; return 0 }

    private var endTimer = 0f
    private fun onEnd() {
        if (winner == 1) { wins++; addScore(100); engine.sfx.success(); engine.haptics.success(); flash(accent, 0.25f) }
        else if (winner == 2) { engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.25f) }
        endTimer = 1.6f
    }
    private fun resetBoardSoon() { endTimer = 1.2f }

    override fun onStep(dt: Float) {
        if (dropFx > 0f) dropFx -= dt
        if (winner != 0 || endTimer > 0f) {
            endTimer -= dt
            if (endTimer <= 0f) {
                if (winner == 2) gameOver()
                else onReset()   // player win or draw -> fresh board
            }
            return
        }
        if (turn == 2) { aiTimer += dt; if (aiTimer > 0.45f) { aiTimer = 0f; aiMove() } }
    }

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.66f else s.minDim * 0.92f
    private fun cellW(s: ScreenInfo) = side(s) / COLS
    private fun boardLeft(s: ScreenInfo) = s.cx - side(s) / 2f
    private fun boardTop(s: ScreenInfo) = s.cy - (cellW(s) * ROWS) / 2f + s.h * 0.02f

    override fun onTapWorld(x: Float, y: Float) {
        if (turn != 1 || winner != 0 || endTimer > 0f) return
        val s = sc; val cw = cellW(s)
        val c = ((x - boardLeft(s)) / cw).toInt()
        if (c in 0 until COLS && !colFull(c)) commit(c, 1)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val cw = cellW(s); val l = boardLeft(s); val t = boardTop(s)
        Draw.roundRect(c, l - s.dp(2f), t - s.dp(2f), cw * COLS + s.dp(4f), cw * ROWS + s.dp(4f), s.dp(6f), Palette.alpha(Palette.blue, 0.18f))
        for (r in 0 until ROWS) for (col in 0 until COLS) {
            val cx = l + col * cw + cw / 2; val cy = t + r * cw + cw / 2
            val v = board[idx(r, col)]
            val rad = cw * 0.38f
            when (v) {
                0 -> Draw.fillCircle(c, cx, cy, rad, Palette.bg)
                1 -> { Draw.glow(c, cx, cy, rad * 1.2f, accent, 0.2f); Draw.fillCircle(c, cx, cy, rad, accent) }
                2 -> { Draw.glow(c, cx, cy, rad * 1.2f, Palette.red, 0.2f); Draw.fillCircle(c, cx, cy, rad, Palette.red) }
            }
            if (winner != 0 && idx(r, col) in winLine) Draw.strokeCircle(c, cx, cy, rad, Palette.gold, s.dp(3f))
        }
        if (winner != 0)
            Draw.textCentered(c, Loc.t(if (winner == 1) "YOU WIN" else "AI WINS"), s.cx, s.h * 0.93f, s.px(0.055f),
                if (winner == 1) accent else Palette.red, Fonts.display)
        else if (turn == 2)
            Draw.textCentered(c, Loc.t("AI…"), s.cx, s.h * 0.93f, s.px(0.04f), Palette.textDim, Fonts.mono)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.06f, s.px(0.05f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "connect_four",
            title = "Connect Four",
            tagline = "Four in a row",
            category = Category.STRATEGY,
            tier = Tier.MINI,
            accent = Palette.amber,
            icon = IconDrawer { c, cx, cy, size, _ ->
                Draw.roundRectCentered(c, cx, cy, size * 0.9f, size * 0.78f, size * 0.1f, Palette.alpha(Palette.blue, 0.3f))
                for (i in 0 until 4) Draw.fillCircle(c, cx - size * 0.3f + i * size * 0.2f, cy + size * 0.1f - i * size * 0.06f, size * 0.08f, if (i % 2 == 0) Palette.amber else Palette.red)
            },
            create = { ConnectFourGame() }
        )
    }
}
