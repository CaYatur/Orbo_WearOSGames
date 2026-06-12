package com.cayadev.orbo.games

import android.graphics.Canvas
import android.graphics.Paint
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.IconDrawer
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Mathx
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame

/**
 * Nonogram — read the number clues to paint the hidden picture on a 5×5 grid. Tap a cell to fill it,
 * long-press to mark a cross. Match every row and column clue to solve the grid, bank points and earn
 * time. Beat the clock across as many pictures as you can.
 */
class NonogramGame : ArcadeGame(meta()) {

    private val N = 5
    private val sol = Array(N) { BooleanArray(N) }
    private val fill = Array(N) { IntArray(N) }   // 0 empty, 1 filled, 2 cross
    private lateinit var rowClues: Array<List<Int>>
    private lateinit var colClues: Array<List<Int>>
    private var timeLeft = 150f
    private var solvedCount = 0

    override val controlHint = "TAP FILL · HOLD CROSS"
    override val gameOverTitle = "TIME UP"

    override fun onReset() { timeLeft = 150f; solvedCount = 0; newPuzzle() }

    private fun newPuzzle() {
        do {
            for (r in 0 until N) for (c in 0 until N) sol[r][c] = Mathx.chance(0.55f)
        } while (countFilled() < 6 || countFilled() > 18)
        for (r in 0 until N) for (c in 0 until N) fill[r][c] = 0
        rowClues = Array(N) { r -> runs(BooleanArray(N) { sol[r][it] }) }
        colClues = Array(N) { c -> runs(BooleanArray(N) { sol[it][c] }) }
    }

    private fun countFilled(): Int { var n = 0; for (r in 0 until N) for (c in 0 until N) if (sol[r][c]) n++; return n }

    private fun runs(line: BooleanArray): List<Int> {
        val out = ArrayList<Int>(); var cur = 0
        for (b in line) { if (b) cur++ else if (cur > 0) { out.add(cur); cur = 0 } }
        if (cur > 0) out.add(cur)
        if (out.isEmpty()) out.add(0)
        return out
    }

    private fun curRuns(line: BooleanArray) = runs(line)

    private fun clueW(s: ScreenInfo) = s.minDim * 0.16f
    private fun clueH(s: ScreenInfo) = s.minDim * 0.16f
    private fun gridSide(s: ScreenInfo) = if (s.isRound) s.minDim * 0.5f else s.minDim * 0.64f
    private fun blockX(s: ScreenInfo) = s.cx - (clueW(s) + gridSide(s)) / 2f
    private fun blockY(s: ScreenInfo) = s.cy - (clueH(s) + gridSide(s)) / 2f + s.h * 0.02f
    private fun gridX(s: ScreenInfo) = blockX(s) + clueW(s)
    private fun gridY(s: ScreenInfo) = blockY(s) + clueH(s)
    private fun cell(s: ScreenInfo) = gridSide(s) / N

    override fun onStep(dt: Float) {
        timeLeft -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver() }
    }

    private fun cellAt(x: Float, y: Float): Int {
        val s = sc; val cs = cell(s)
        val c = ((x - gridX(s)) / cs).toInt(); val r = ((y - gridY(s)) / cs).toInt()
        return if (r in 0 until N && c in 0 until N) r * N + c else -1
    }

    override fun onTapWorld(x: Float, y: Float) {
        val id = cellAt(x, y); if (id < 0) return
        val r = id / N; val c = id % N
        fill[r][c] = if (fill[r][c] == 1) 0 else 1
        engine.sfx.click(); engine.haptics.tick()
        checkSolved()
    }

    override fun onLongPressWorld(x: Float, y: Float) {
        val id = cellAt(x, y); if (id < 0) return
        val r = id / N; val c = id % N
        fill[r][c] = if (fill[r][c] == 2) 0 else 2
        engine.sfx.blip(); engine.haptics.light()
    }

    private fun checkSolved() {
        for (r in 0 until N) if (curRuns(BooleanArray(N) { fill[r][it] == 1 }) != rowClues[r]) return
        for (c in 0 until N) if (curRuns(BooleanArray(N) { fill[it][c] == 1 }) != colClues[c]) return
        solvedCount++
        addScore(120); timeLeft += 20f
        engine.sfx.success(); engine.haptics.success(); flash(accent, 0.25f)
        newPuzzle()
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val cs = cell(s); val gx = gridX(s); val gy = gridY(s)
        // grid cells
        for (r in 0 until N) for (col in 0 until N) {
            val x = gx + col * cs; val y = gy + r * cs
            Draw.roundRect(c, x + s.dp(1f), y + s.dp(1f), cs - s.dp(2f), cs - s.dp(2f), cs * 0.1f, Palette.panel)
            when (fill[r][col]) {
                1 -> Draw.roundRect(c, x + s.dp(1f), y + s.dp(1f), cs - s.dp(2f), cs - s.dp(2f), cs * 0.1f, accent)
                2 -> { Draw.line(c, x + cs * 0.3f, y + cs * 0.3f, x + cs * 0.7f, y + cs * 0.7f, Palette.alpha(Palette.red, 0.6f), s.dp(2f))
                       Draw.line(c, x + cs * 0.7f, y + cs * 0.3f, x + cs * 0.3f, y + cs * 0.7f, Palette.alpha(Palette.red, 0.6f), s.dp(2f)) }
            }
        }
        // row clues (right-aligned to the left of grid)
        for (r in 0 until N) {
            val str = rowClues[r].joinToString(" ")
            Draw.text(c, str, gx - s.dp(4f), gy + r * cs + cs * 0.62f, cs * 0.42f, Palette.textDim, Paint.Align.RIGHT, Fonts.mono)
        }
        // col clues (stacked above grid)
        for (col in 0 until N) {
            val list = colClues[col]
            for (k in list.indices) {
                Draw.textCentered(c, "${list[k]}", gx + col * cs + cs / 2, gy - clueH(s) + (k + (3 - list.size)) * cs * 0.34f + cs * 0.2f, cs * 0.4f, Palette.textDim, Fonts.mono)
            }
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        Ui.edgeProgress(c, s, timeLeft / 150f, Palette.lerp(Palette.red, accent, timeLeft / 150f))
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.07f, s.px(0.045f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "nonogram",
            title = "Nonogram",
            tagline = "Paint by numbers",
            category = Category.PUZZLE,
            tier = Tier.MINI,
            accent = Palette.lime,
            icon = IconDrawer { c, cx, cy, size, color ->
                val u = size * 0.24f
                val pat = arrayOf(intArrayOf(1,0,1), intArrayOf(0,1,0), intArrayOf(1,1,1))
                for (r in 0 until 3) for (cc in 0 until 3) {
                    Draw.roundRectCentered(c, cx + (cc - 1) * u, cy + (r - 1) * u, u * 0.86f, u * 0.86f, u * 0.16f,
                        if (pat[r][cc] == 1) color else Palette.alpha(color, 0.18f))
                }
            },
            create = { NonogramGame() }
        )
    }
}
