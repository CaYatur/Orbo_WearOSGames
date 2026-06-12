package com.cayadev.orbo.games

import android.graphics.Canvas
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
 * Flow — connect each pair of matching dots with a pipe, filling every cell with no crossings. Drag
 * from a dot to draw; drag back to erase. Solve the board to score and gain time. Boards are carved
 * from a single space-filling path, so every one is guaranteed solvable.
 */
class FlowConnectGame : ArcadeGame(meta()) {

    private val N = 5
    private var K = 5
    private val grid = IntArray(N * N) { -1 }
    private val epOf = IntArray(N * N) { -1 }
    private val ep = Array(8) { IntArray(2) }
    private val paths = Array(8) { ArrayList<Int>() }
    private var activeColor = -1
    private var lastCell = -1
    private var timeLeft = 150f
    private var solvedCount = 0

    override val controlHint = "DRAG TO CONNECT THE DOTS"
    override val gameOverTitle = "TIME UP"

    override fun onReset() { K = 5; timeLeft = 150f; solvedCount = 0; build() }

    private fun build() {
        for (i in 0 until N * N) { grid[i] = -1; epOf[i] = -1 }
        for (p in paths) p.clear()
        activeColor = -1; lastCell = -1
        // serpentine order over all cells
        val order = ArrayList<Int>()
        for (r in 0 until N) { if (r % 2 == 0) for (c in 0 until N) order.add(r * N + c) else for (c in N - 1 downTo 0) order.add(r * N + c) }
        // split into K segments each length >= 2
        val lengths = IntArray(K) { 2 }
        var rem = N * N - 2 * K
        while (rem > 0) { lengths[Mathx.rand(K)]++; rem-- }
        var pos = 0
        for (k in 0 until K) {
            val len = lengths[k]
            val a = order[pos]; val b = order[pos + len - 1]
            ep[k][0] = a; ep[k][1] = b; epOf[a] = k; epOf[b] = k
            grid[a] = k; grid[b] = k
            pos += len
        }
    }

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.64f else s.minDim * 0.82f
    private fun left(s: ScreenInfo) = s.cx - side(s) / 2f
    private fun top(s: ScreenInfo) = s.cy - side(s) / 2f
    private fun cell(s: ScreenInfo) = side(s) / N
    private fun cellAt(x: Float, y: Float): Int {
        val s = sc; val cs = cell(s)
        val c = ((x - left(s)) / cs).toInt(); val r = ((y - top(s)) / cs).toInt()
        return if (r in 0 until N && c in 0 until N) r * N + c else -1
    }
    private fun adjacent(a: Int, b: Int): Boolean {
        val ar = a / N; val ac = a % N; val br = b / N; val bc = b % N
        return (ar == br && kotlin.math.abs(ac - bc) == 1) || (ac == bc && kotlin.math.abs(ar - br) == 1)
    }

    override fun onStep(dt: Float) {
        timeLeft -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver() }
    }

    override fun onPress(x: Float, y: Float) {
        val c = cellAt(x, y); if (c < 0) return
        val col = grid[c]
        if (col < 0) { activeColor = -1; return }
        if (epOf[c] == col) {
            // fresh start from this endpoint
            for (cc in paths[col]) if (epOf[cc] != col) grid[cc] = -1
            paths[col].clear(); paths[col].add(c); grid[c] = col
            activeColor = col; lastCell = c; engine.sfx.click()
        } else {
            val idx = paths[col].indexOf(c)
            if (idx >= 0) {
                for (j in idx + 1 until paths[col].size) { val cc = paths[col][j]; if (epOf[cc] != col) grid[cc] = -1 }
                while (paths[col].size > idx + 1) paths[col].removeAt(paths[col].size - 1)
                activeColor = col; lastCell = c
            } else activeColor = -1
        }
    }

    override fun onDrag(x: Float, y: Float) {
        if (activeColor < 0) return
        val c = cellAt(x, y); if (c < 0 || c == lastCell) return
        if (!adjacent(c, lastCell)) return
        val path = paths[activeColor]
        if (path.size >= 2 && c == path[path.size - 2]) {
            val removed = path.removeAt(path.size - 1); if (epOf[removed] != activeColor) grid[removed] = -1
            lastCell = c; engine.haptics.tick(); return
        }
        val occ = grid[c]
        val isFar = epOf[c] == activeColor && c != path.first()
        if (occ == -1 || isFar) {
            path.add(c); grid[c] = activeColor; lastCell = c
            engine.sfx.click(); engine.haptics.tick()
            if (isFar) checkSolved()
        }
    }

    override fun onRelease(x: Float, y: Float) { activeColor = -1 }

    private fun sameNeighbors(cell: Int, col: Int): Int {
        val r = cell / N; val c = cell % N; var n = 0
        if (r > 0 && grid[(r - 1) * N + c] == col) n++
        if (r < N - 1 && grid[(r + 1) * N + c] == col) n++
        if (c > 0 && grid[r * N + (c - 1)] == col) n++
        if (c < N - 1 && grid[r * N + (c + 1)] == col) n++
        return n
    }

    private fun checkSolved() {
        for (i in 0 until N * N) if (grid[i] < 0) return
        for (k in 0 until K) {
            // endpoints degree 1, middles degree 2, connected
            if (sameNeighbors(ep[k][0], k) != 1 || sameNeighbors(ep[k][1], k) != 1) return
            for (i in 0 until N * N) if (grid[i] == k && epOf[i] != k && sameNeighbors(i, k) != 2) return
        }
        solvedCount++
        addScore(120); timeLeft += 20f
        engine.sfx.success(); engine.haptics.success(); flash(accent, 0.25f)
        build()
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val cs = cell(s); val l = left(s); val t = top(s)
        // grid backdrop
        for (r in 0 until N) for (col in 0 until N) {
            Draw.roundRect(c, l + col * cs + s.dp(1f), t + r * cs + s.dp(1f), cs - s.dp(2f), cs - s.dp(2f), cs * 0.08f, Palette.panel)
        }
        // pipes
        val pw = cs * 0.34f
        for (i in 0 until N * N) if (grid[i] >= 0) {
            val col = Palette.accents[grid[i] % Palette.accents.size]
            val r = i / N; val cc = i % N
            val cx = l + cc * cs + cs / 2; val cy = t + r * cs + cs / 2
            // draw connectors to same-color neighbors
            val dirs = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
            for (d in dirs) {
                val nr = r + d[0]; val nc = cc + d[1]
                if (nr in 0 until N && nc in 0 until N && grid[nr * N + nc] == grid[i]) {
                    // only draw if they are path-adjacent (avoid drawing across non-path same color) -> always ok for flow
                    Draw.line(c, cx, cy, cx + d[1] * cs * 0.5f, cy + d[0] * cs * 0.5f, col, pw)
                }
            }
            if (grid[i] >= 0 && epOf[i] < 0) Draw.fillCircle(c, cx, cy, pw * 0.55f, col)
        }
        // endpoints
        for (k in 0 until K) {
            val col = Palette.accents[k % Palette.accents.size]
            for (e in 0 until 2) {
                val id = ep[k][e]; val r = id / N; val cc = id % N
                val cx = l + cc * cs + cs / 2; val cy = t + r * cs + cs / 2
                Draw.fillCircle(c, cx, cy, cs * 0.3f, col)
                Draw.strokeCircle(c, cx, cy, cs * 0.3f, Palette.alpha(Palette.text, 0.3f), s.dp(1.5f))
            }
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        Ui.edgeProgress(c, s, timeLeft / 150f, Palette.lerp(Palette.red, accent, timeLeft / 150f))
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.09f, s.px(0.05f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "flow_connect",
            title = "Flow",
            tagline = "Connect the dots",
            category = Category.PUZZLE,
            tier = Tier.MINI,
            accent = Palette.pink,
            icon = IconDrawer { c, cx, cy, size, _ ->
                Draw.fillCircle(c, cx - size * 0.35f, cy - size * 0.35f, size * 0.13f, Palette.mint)
                Draw.fillCircle(c, cx + size * 0.35f, cy + size * 0.35f, size * 0.13f, Palette.mint)
                Draw.line(c, cx - size * 0.35f, cy - size * 0.35f, cx - size * 0.35f, cy + size * 0.35f, Palette.mint, size * 0.1f)
                Draw.line(c, cx - size * 0.35f, cy + size * 0.35f, cx + size * 0.35f, cy + size * 0.35f, Palette.mint, size * 0.1f)
                Draw.fillCircle(c, cx + size * 0.35f, cy - size * 0.35f, size * 0.13f, Palette.amber)
                Draw.fillCircle(c, cx - size * 0.05f, cy - size * 0.35f, size * 0.13f, Palette.amber)
            },
            create = { FlowConnectGame() }
        )
    }
}
