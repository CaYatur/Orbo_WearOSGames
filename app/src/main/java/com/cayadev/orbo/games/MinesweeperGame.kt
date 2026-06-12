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
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame

/** Minesweeper — bite-size 5×5 boards. Tap to reveal, long-press to flag. Clear a board to bank a
 * streak; each clear adds another mine. One wrong tap ends the run. */
class MinesweeperGame : ArcadeGame(meta()) {

    private val r = 5
    private val cells = r * r
    private val mine = BooleanArray(cells)
    private val shown = BooleanArray(cells)
    private val flag = BooleanArray(cells)
    private val count = IntArray(cells)
    private var firstClick = true
    private var mines = 4
    private var revealedCount = 0
    private var boards = 0

    override val controlHint = "TAP REVEAL · HOLD FLAG"

    override fun onReset() { mines = 4; boards = 0; newBoard() }

    override fun onStep(dt: Float) {}  // turn-based; no per-frame simulation

    private fun newBoard() {
        for (i in 0 until cells) { mine[i] = false; shown[i] = false; flag[i] = false; count[i] = 0 }
        firstClick = true; revealedCount = 0
    }

    private fun placeMines(safe: Int) {
        var placed = 0
        val forbidden = neighbors(safe).toMutableSet().apply { add(safe) }
        while (placed < mines.coerceAtMost(cells - forbidden.size)) {
            val idx = Mathx.rand(cells)
            if (!mine[idx] && idx !in forbidden) { mine[idx] = true; placed++ }
        }
        for (i in 0 until cells) count[i] = neighbors(i).count { mine[it] }
    }

    private fun neighbors(idx: Int): List<Int> {
        val cx = idx % r; val cy = idx / r
        val list = ArrayList<Int>(8)
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = cx + dx; val ny = cy + dy
            if (nx in 0 until r && ny in 0 until r) list.add(ny * r + nx)
        }
        return list
    }

    private fun geom(s: ScreenInfo): FloatArray {
        val side = s.px(0.62f); val cell = side / r
        return floatArrayOf(s.cx - side / 2, s.cy - side / 2, cell)
    }

    private fun cellAt(x: Float, y: Float): Int {
        val g = geom(sc); val col = ((x - g[0]) / g[2]).toInt(); val row = ((y - g[1]) / g[2]).toInt()
        return if (col in 0 until r && row in 0 until r) row * r + col else -1
    }

    override fun onTapWorld(x: Float, y: Float) {
        val idx = cellAt(x, y); if (idx < 0) return
        if (flag[idx] || shown[idx]) return
        if (firstClick) { placeMines(idx); firstClick = false }
        reveal(idx)
    }

    override fun onLongPressWorld(x: Float, y: Float) {
        val idx = cellAt(x, y); if (idx < 0 || shown[idx]) return
        flag[idx] = !flag[idx]; engine.sfx.click(); engine.haptics.medium()
    }

    private fun reveal(idx: Int) {
        if (shown[idx] || flag[idx]) return
        if (mine[idx]) {
            shown[idx] = true
            shake(sc.px(0.025f)); flash(Palette.red, 0.4f); engine.sfx.explosion(); engine.haptics.heavy()
            for (i in 0 until cells) if (mine[i]) shown[i] = true
            gameOver(); return
        }
        // flood fill
        val stack = ArrayDeque<Int>(); stack.addLast(idx)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (shown[cur] || flag[cur] || mine[cur]) continue
            shown[cur] = true; revealedCount++
            val g = geom(sc)
            particles.spawn(g[0] + (cur % r) * g[2] + g[2] / 2, g[1] + (cur / r) * g[2] + g[2] / 2,
                0f, 0f, 0.25f, sc.px(0.01f), accent)
            if (count[cur] == 0) for (nb in neighbors(cur)) if (!shown[nb]) stack.addLast(nb)
        }
        engine.sfx.blip(); engine.haptics.light()
        if (revealedCount >= cells - mines) solved()
    }

    private fun solved() {
        boards++; addScore(10 + mines * 2)
        engine.sfx.success(); engine.haptics.success()
        particles.burst(sc.cx, sc.cy, 26, Palette.mint, sc.px(0.8f), sc.px(0.02f), 0.7f)
        mines = (mines + 1).coerceAtMost(cells / 2)
        newBoard()
    }

    private val numCols = intArrayOf(Palette.textDim, Palette.blue, Palette.mint, Palette.pink, Palette.violet, Palette.amber, Palette.cyan, Palette.text, Palette.red)

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val g = geom(s); val ox = g[0]; val oy = g[1]; val cell = g[2]
        for (i in 0 until cells) {
            val cx = ox + (i % r) * cell + cell / 2
            val cy = oy + (i / r) * cell + cell / 2
            val sz = cell * 0.9f
            if (shown[i]) {
                if (mine[i]) {
                    Draw.roundRectCentered(c, cx, cy, sz, sz, sz * 0.18f, Palette.alpha(Palette.red, 0.4f))
                    Draw.fillCircle(c, cx, cy, sz * 0.26f, Palette.red)
                } else {
                    Draw.roundRectCentered(c, cx, cy, sz, sz, sz * 0.18f, Palette.alpha(Palette.panel, 0.7f))
                    if (count[i] > 0) Draw.textCentered(c, "${count[i]}", cx, cy, cell * 0.46f, numCols[count[i]], Fonts.display)
                }
            } else {
                Draw.roundRectCentered(c, cx, cy, sz, sz, sz * 0.18f, Palette.panelHi)
                Draw.roundRectCentered(c, cx, cy - sz * 0.06f, sz * 0.96f, sz * 0.4f, sz * 0.18f, Palette.alpha(Palette.text, 0.06f))
                if (flag[i]) {
                    Draw.line(c, cx - sz * 0.18f, cy + sz * 0.22f, cx - sz * 0.18f, cy - sz * 0.24f, Palette.textDim, s.dp(2.5f))
                    val p = Draw.paint; p.reset(); p.isAntiAlias = true; p.color = Palette.red
                    val path = android.graphics.Path()
                    path.moveTo(cx - sz * 0.18f, cy - sz * 0.24f); path.lineTo(cx + sz * 0.16f, cy - sz * 0.12f); path.lineTo(cx - sz * 0.18f, cy); path.close()
                    c.drawPath(path, p)
                }
            }
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val flagged = flag.count { it }
        Draw.textCentered(c, "♦ ${(mines - flagged).coerceAtLeast(0)}", s.cx, s.h * 0.16f, s.px(0.05f), Palette.red, Fonts.mono)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.085f, s.px(0.05f), Palette.alpha(Palette.text, 0.8f), Fonts.mono)
    }

    companion object {
        fun meta() = GameMeta(
            id = "minesweeper",
            title = "Mines",
            tagline = "Clear the board",
            category = Category.PUZZLE,
            tier = Tier.MINI,
            accent = Palette.blue,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx, cy, size * 0.6f, size * 0.6f, size * 0.1f, Palette.alpha(color, 0.25f))
                Draw.fillCircle(c, cx, cy, size * 0.16f, Palette.red)
                for (i in 0 until 4) {
                    val a = i * 1.5708f + 0.78f
                    Draw.line(c, cx, cy, cx + Mathx.cosf(a) * size * 0.26f, cy + Mathx.sinf(a) * size * 0.26f, Palette.red, size * 0.04f)
                }
            },
            create = { MinesweeperGame() }
        )
    }
}
