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
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.game.ArcadeGame
import kotlin.math.abs

/** Gem Swap — a 60-second match-3 rush. Swap neighbours to line up 3+ gems; cascades multiply your
 * score. Keep the chains coming before the clock runs out. */
class GemSwapGame : ArcadeGame(meta()) {

    private val n = 6
    private val cells = n * n
    private val K = 5
    private val grid = IntArray(cells)
    private val pop = FloatArray(cells)
    private var selected = -1
    private var timeLeft = 60f
    private var combo = 0

    private val gemColors = intArrayOf(Palette.red, Palette.mint, Palette.blue, Palette.amber, Palette.violet)

    override val controlHint = "SWAP TO MATCH 3+"
    override val gameOverTitle = "TIME UP"

    override fun onReset() {
        for (i in 0 until cells) { grid[i] = Mathx.rand(K); pop[i] = 0f }
        // settle any initial matches without scoring
        var guard = 0
        while (resolveOnce(scoring = false) && guard++ < 40) { /* settle */ }
        if (!hasMoves()) reshuffle()
        selected = -1; timeLeft = 60f; combo = 0
    }

    override fun onStep(dt: Float) {
        timeLeft -= dt
        for (i in 0 until cells) if (pop[i] > 0f) pop[i] -= dt * 3.2f
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver() }
    }

    private fun geom(s: ScreenInfo): FloatArray {
        val side = if (s.isRound) s.minDim * 0.68f else s.minDim * 0.86f
        val cell = side / n
        return floatArrayOf(s.cx - side / 2, s.cy - side / 2, cell)
    }

    private fun cellAt(x: Float, y: Float): Int {
        val g = geom(sc); val col = ((x - g[0]) / g[2]).toInt(); val row = ((y - g[1]) / g[2]).toInt()
        return if (col in 0 until n && row in 0 until n) row * n + col else -1
    }

    override fun onTapWorld(x: Float, y: Float) {
        val idx = cellAt(x, y); if (idx < 0) return
        if (selected < 0) { selected = idx; engine.sfx.click(); return }
        if (idx == selected) { selected = -1; return }
        if (adjacent(idx, selected)) {
            trySwap(selected, idx); selected = -1
        } else { selected = idx; engine.sfx.click() }
    }

    private fun adjacent(a: Int, b: Int): Boolean {
        val ax = a % n; val ay = a / n; val bx = b % n; val by = b / n
        return abs(ax - bx) + abs(ay - by) == 1
    }

    private fun trySwap(a: Int, b: Int) {
        val t = grid[a]; grid[a] = grid[b]; grid[b] = t
        if (matchSet().isNotEmpty()) {
            combo = 0
            engine.sfx.blip(); engine.haptics.light()
            var guard = 0
            while (resolveOnce(scoring = true) && guard++ < 40) { /* cascade */ }
            if (!hasMoves()) reshuffle()
        } else {
            // swap back
            val t2 = grid[a]; grid[a] = grid[b]; grid[b] = t2
            engine.sfx.hit(); engine.haptics.medium()
        }
    }

    private fun matchSet(): HashSet<Int> {
        val set = HashSet<Int>()
        // horizontal
        for (row in 0 until n) {
            var runStart = 0
            for (col in 1..n) {
                val same = col < n && grid[row * n + col] == grid[row * n + runStart] && grid[row * n + col] >= 0
                if (!same) {
                    if (col - runStart >= 3) for (k in runStart until col) set.add(row * n + k)
                    runStart = col
                }
            }
        }
        // vertical
        for (col in 0 until n) {
            var runStart = 0
            for (row in 1..n) {
                val same = row < n && grid[row * n + col] == grid[runStart * n + col] && grid[row * n + col] >= 0
                if (!same) {
                    if (row - runStart >= 3) for (k in runStart until row) set.add(k * n + col)
                    runStart = row
                }
            }
        }
        return set
    }

    /** Clears matches once, applies gravity + refill. Returns true if anything cleared. */
    private fun resolveOnce(scoring: Boolean): Boolean {
        val set = matchSet()
        if (set.isEmpty()) return false
        if (scoring) {
            combo++
            addScore(set.size * (10 + combo * 4))
            val g = geom(sc)
            for (idx in set) {
                particles.burst(g[0] + (idx % n) * g[2] + g[2] / 2, g[1] + (idx / n) * g[2] + g[2] / 2,
                    6, gemColors[grid[idx].coerceIn(0, K - 1)], sc.px(0.35f), sc.px(0.012f), 0.4f)
            }
            engine.sfx.coin()
        }
        for (idx in set) grid[idx] = -1
        // gravity per column
        for (col in 0 until n) {
            var write = n - 1
            for (row in n - 1 downTo 0) {
                if (grid[row * n + col] >= 0) {
                    grid[write * n + col] = grid[row * n + col]
                    if (write != row) grid[row * n + col] = -1
                    write--
                }
            }
            // refill
            for (row in write downTo 0) { grid[row * n + col] = Mathx.rand(K); pop[row * n + col] = 1f }
        }
        return true
    }

    private fun hasMoves(): Boolean {
        for (i in 0 until cells) {
            val x = i % n; val y = i / n
            if (x + 1 < n) { swap(i, i + 1); val m = matchSet().isNotEmpty(); swap(i, i + 1); if (m) return true }
            if (y + 1 < n) { swap(i, i + n); val m = matchSet().isNotEmpty(); swap(i, i + n); if (m) return true }
        }
        return false
    }
    private fun swap(a: Int, b: Int) { val t = grid[a]; grid[a] = grid[b]; grid[b] = t }

    private fun reshuffle() {
        var guard = 0
        do {
            for (i in 0 until cells) grid[i] = Mathx.rand(K)
            var g2 = 0
            while (resolveOnce(scoring = false) && g2++ < 40) {}
        } while (!hasMoves() && guard++ < 20)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val g = geom(s); val ox = g[0]; val oy = g[1]; val cell = g[2]
        for (i in 0 until cells) {
            val v = grid[i]; if (v < 0) continue
            val cx = ox + (i % n) * cell + cell / 2
            val cy = oy + (i / n) * cell + cell / 2
            val popScale = if (pop[i] > 0f) Ease.outBack(1f - pop[i].coerceIn(0f, 1f)) else 1f
            val rr = cell * 0.38f * popScale
            val sel = i == selected
            val col = gemColors[v.coerceIn(0, K - 1)]
            if (sel) Draw.roundRectCentered(c, cx, cy, cell * 0.94f, cell * 0.94f, cell * 0.2f, Palette.alpha(Palette.text, 0.12f))
            Draw.glow(c, cx, cy, rr * 1.5f, col, 0.18f)
            drawGem(c, cx, cy, rr, col, v)
            if (sel) Draw.roundRectStroke(c, cx - cell * 0.46f, cy - cell * 0.46f, cell * 0.92f, cell * 0.92f, cell * 0.2f, Palette.text, s.dp(2f))
        }
    }

    private fun drawGem(c: Canvas, cx: Float, cy: Float, r: Float, col: Int, shape: Int) {
        when (shape % 5) {
            0 -> Draw.fillCircle(c, cx, cy, r, col)
            1 -> Draw.roundRectCentered(c, cx, cy, r * 1.7f, r * 1.7f, r * 0.4f, col)
            2 -> { // diamond
                val p = Draw.paint; p.reset(); p.isAntiAlias = true; p.color = col
                val path = android.graphics.Path()
                path.moveTo(cx, cy - r); path.lineTo(cx + r, cy); path.lineTo(cx, cy + r); path.lineTo(cx - r, cy); path.close()
                c.drawPath(path, p)
            }
            3 -> { // triangle
                val p = Draw.paint; p.reset(); p.isAntiAlias = true; p.color = col
                val path = android.graphics.Path()
                path.moveTo(cx, cy - r); path.lineTo(cx + r * 0.9f, cy + r * 0.7f); path.lineTo(cx - r * 0.9f, cy + r * 0.7f); path.close()
                c.drawPath(path, p)
            }
            else -> { Draw.fillCircle(c, cx, cy, r, col); Draw.fillCircle(c, cx, cy, r * 0.5f, Palette.shade(col, -0.25f)) }
        }
        Draw.fillCircle(c, cx - r * 0.3f, cy - r * 0.3f, r * 0.16f, Palette.alpha(Palette.text, 0.5f))
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val frac = (timeLeft / 60f).coerceIn(0f, 1f)
        Ui.edgeProgress(c, s, frac, Palette.lerp(Palette.red, accent, frac))
        if (combo > 1) Draw.textCentered(c, "x$combo", s.cx, s.h * 0.85f, s.px(0.05f), accent, Fonts.bodyBold)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.085f, s.px(0.05f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "gem_swap",
            title = "Gem Swap",
            tagline = "Match 3 rush",
            category = Category.PUZZLE,
            tier = Tier.MINI,
            accent = Palette.mint,
            icon = IconDrawer { c, cx, cy, size, _ ->
                Draw.fillCircle(c, cx - size * 0.2f, cy - size * 0.18f, size * 0.12f, Palette.red)
                Draw.roundRectCentered(c, cx + size * 0.18f, cy - size * 0.18f, size * 0.22f, size * 0.22f, size * 0.05f, Palette.mint)
                val p = Draw.paint; p.reset(); p.isAntiAlias = true; p.color = Palette.amber
                val path = android.graphics.Path()
                path.moveTo(cx, cy + size * 0.05f); path.lineTo(cx + size * 0.15f, cy + size * 0.22f); path.lineTo(cx, cy + size * 0.34f); path.lineTo(cx - size * 0.15f, cy + size * 0.22f); path.close()
                c.drawPath(path, p)
            },
            create = { GemSwapGame() }
        )
    }
}
