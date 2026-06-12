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
 * Pipes — rotate each tile until the whole network is sealed: no opening pointing at a wall or a
 * closed neighbour. Tiles lit by the source show your progress. Seal a board to score and gain time,
 * then a fresh tangle appears. The clock is the only enemy.
 */
class PipesGame : ArcadeGame(meta()) {

    private val N = 5
    private val mask = IntArray(N * N)            // bit0 up,1 right,2 down,3 left
    private var source = 0
    private var timeLeft = 150f
    private var solvedCount = 0
    private val dr = intArrayOf(-1, 0, 1, 0)
    private val dc = intArrayOf(0, 1, 0, -1)

    override val controlHint = "TAP TILE TO ROTATE"
    override val gameOverTitle = "TIME UP"

    override fun onReset() { timeLeft = 150f; solvedCount = 0; build() }

    private fun build() {
        for (i in 0 until N * N) mask[i] = 0
        source = (N / 2) * N + (N / 2)
        // random spanning tree (DFS)
        val visited = BooleanArray(N * N)
        val stack = ArrayDeque<Int>()
        stack.addLast(source); visited[source] = true
        while (stack.isNotEmpty()) {
            val cur = stack.last()
            val r = cur / N; val c = cur % N
            val opts = ArrayList<Int>()
            for (d in 0 until 4) {
                val nr = r + dr[d]; val nc = c + dc[d]
                if (nr in 0 until N && nc in 0 until N && !visited[nr * N + nc]) opts.add(d)
            }
            if (opts.isEmpty()) { stack.removeLast(); continue }
            val d = opts[Mathx.rand(opts.size)]
            val nr = r + dr[d]; val nc = c + dc[d]; val ni = nr * N + nc
            mask[cur] = mask[cur] or (1 shl d)
            mask[ni] = mask[ni] or (1 shl ((d + 2) % 4))
            visited[ni] = true; stack.addLast(ni)
        }
        // scramble
        var anyRot = false
        for (i in 0 until N * N) { val k = Mathx.rand(4); repeat(k) { mask[i] = rot(mask[i]) }; if (k > 0) anyRot = true }
        if (!anyRot || solved()) mask[0] = rot(mask[0])
    }

    private fun rot(m: Int): Int {
        var nm = 0
        for (d in 0 until 4) if (m and (1 shl d) != 0) nm = nm or (1 shl ((d + 1) % 4))
        return nm
    }

    private fun solved(): Boolean {
        for (i in 0 until N * N) {
            val r = i / N; val c = i % N
            for (d in 0 until 4) if (mask[i] and (1 shl d) != 0) {
                val nr = r + dr[d]; val nc = c + dc[d]
                if (nr !in 0 until N || nc !in 0 until N) return false
                if (mask[nr * N + nc] and (1 shl ((d + 2) % 4)) == 0) return false
            }
        }
        return true
    }

    private fun connectedSet(): BooleanArray {
        val con = BooleanArray(N * N)
        val stack = ArrayDeque<Int>(); stack.addLast(source); con[source] = true
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast(); val r = cur / N; val c = cur % N
            for (d in 0 until 4) if (mask[cur] and (1 shl d) != 0) {
                val nr = r + dr[d]; val nc = c + dc[d]
                if (nr in 0 until N && nc in 0 until N) {
                    val ni = nr * N + nc
                    if (!con[ni] && mask[ni] and (1 shl ((d + 2) % 4)) != 0) { con[ni] = true; stack.addLast(ni) }
                }
            }
        }
        return con
    }

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.64f else s.minDim * 0.82f
    private fun left(s: ScreenInfo) = s.cx - side(s) / 2f
    private fun top(s: ScreenInfo) = s.cy - side(s) / 2f
    private fun cell(s: ScreenInfo) = side(s) / N

    override fun onStep(dt: Float) {
        timeLeft -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver() }
    }

    override fun onTapWorld(x: Float, y: Float) {
        val s = sc; val cs = cell(s)
        val c = ((x - left(s)) / cs).toInt(); val r = ((y - top(s)) / cs).toInt()
        if (r !in 0 until N || c !in 0 until N) return
        mask[r * N + c] = rot(mask[r * N + c])
        engine.sfx.click(); engine.haptics.tick()
        if (solved()) {
            solvedCount++; addScore(150); timeLeft += 20f
            engine.sfx.success(); engine.haptics.success(); flash(accent, 0.25f)
            build()
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val cs = cell(s); val l = left(s); val t = top(s)
        val con = connectedSet()
        val pw = cs * 0.16f
        for (i in 0 until N * N) {
            val r = i / N; val cc = i % N
            val cx = l + cc * cs + cs / 2; val cy = t + r * cs + cs / 2
            Draw.roundRectCentered(c, cx, cy, cs - s.dp(2f), cs - s.dp(2f), cs * 0.1f, Palette.panel)
            val col = if (con[i]) accent else Palette.textDim
            // arms
            for (d in 0 until 4) if (mask[i] and (1 shl d) != 0) {
                val ex = cx + dc[d] * cs * 0.5f; val ey = cy + dr[d] * cs * 0.5f
                Draw.line(c, cx, cy, ex, ey, col, pw)
            }
            Draw.fillCircle(c, cx, cy, pw * 0.9f, col)
            if (i == source) { Draw.fillCircle(c, cx, cy, cs * 0.2f, Palette.gold); Draw.strokeCircle(c, cx, cy, cs * 0.2f, Palette.alpha(Palette.text, 0.3f), s.dp(1.5f)) }
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
            id = "pipes",
            title = "Pipes",
            tagline = "Seal the network",
            category = Category.PUZZLE,
            tier = Tier.MINI,
            accent = Palette.mint,
            icon = IconDrawer { c, cx, cy, size, color ->
                val u = size * 0.3f
                Draw.line(c, cx, cy, cx + u, cy, color, size * 0.08f)
                Draw.line(c, cx, cy, cx, cy - u, color, size * 0.08f)
                Draw.line(c, cx, cy, cx - u, cy, color, size * 0.08f)
                Draw.fillCircle(c, cx, cy, size * 0.1f, color)
                Draw.fillCircle(c, cx + u, cy, size * 0.07f, Palette.gold)
            },
            create = { PipesGame() }
        )
    }
}
