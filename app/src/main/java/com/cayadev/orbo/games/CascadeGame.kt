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
import com.cayadev.orbo.engine.Swipe
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame

/**
 * Cascade — falling-block stacking. Tap the left or right side to slide the piece, swipe up to rotate
 * and swipe down to slam it home. Complete a full row to clear it and score; the drop speeds up as
 * the lines pile on. Top out and the game's over.
 */
class CascadeGame : ArcadeGame(meta()) {

    private val C = 6
    private val R = 12
    private val grid = Array(R) { IntArray(C) { -1 } }

    // 7 tetromino types, each = list of rotations, each rotation = 4 (x,y) cells in a 4x4 box
    private val shapes: Array<Array<Array<IntArray>>> = buildShapes()
    private val colors = intArrayOf(Palette.cyan, Palette.amber, Palette.violet, Palette.lime, Palette.pink, Palette.blue, Palette.orange)

    private var type = 0
    private var rot = 0
    private var px = 0
    private var py = 0
    private var nextType = 0
    private var fallTimer = 0f
    private var lines = 0

    override val controlHint = "TAP MOVE · SWIPE UP ROTATE · DOWN DROP"
    override val gameOverTitle = "TOPPED OUT"

    override fun onReset() {
        for (r in 0 until R) for (c in 0 until C) grid[r][c] = -1
        lines = 0; fallTimer = 0f
        nextType = Mathx.rand(7); spawn()
    }

    private fun spawn() {
        type = nextType; nextType = Mathx.rand(7); rot = 0
        px = (C / 2) - 2; py = 0
        if (collides(px, py, rot)) { engine.sfx.fail(); gameOver() }
    }

    private fun cells(p: Int = px, q: Int = py, r: Int = rot): Array<IntArray> = shapes[type][r]

    private fun collides(p: Int, q: Int, r: Int): Boolean {
        for (cell in shapes[type][r]) {
            val x = p + cell[0]; val y = q + cell[1]
            if (x < 0 || x >= C || y >= R) return true
            if (y >= 0 && grid[y][x] >= 0) return true
        }
        return false
    }

    override fun onStep(dt: Float) {
        val interval = Mathx.lerp(0.75f, 0.18f, (lines / 40f).coerceIn(0f, 1f))
        fallTimer += dt
        if (fallTimer >= interval) { fallTimer = 0f; step() }
    }

    private fun step() {
        if (!collides(px, py + 1, rot)) py++ else lock()
    }

    private fun lock() {
        for (cell in shapes[type][rot]) {
            val x = px + cell[0]; val y = py + cell[1]
            if (y < 0) { engine.sfx.fail(); gameOver(); return }
            grid[y][x] = type
        }
        engine.sfx.click(); engine.haptics.light()
        clearLines()
        spawn()
    }

    private fun clearLines() {
        var cleared = 0
        var r = R - 1
        while (r >= 0) {
            if ((0 until C).all { grid[r][it] >= 0 }) {
                for (rr in r downTo 1) for (cc in 0 until C) grid[rr][cc] = grid[rr - 1][cc]
                for (cc in 0 until C) grid[0][cc] = -1
                cleared++
            } else r--
        }
        if (cleared > 0) {
            lines += cleared
            addScore(intArrayOf(0, 100, 300, 600, 1000)[cleared.coerceIn(0, 4)])
            engine.sfx.success(); engine.haptics.success(); flash(accent, 0.2f)
        }
    }

    override fun onTapWorld(x: Float, y: Float) {
        if (x < sc.cx) tryMove(-1) else tryMove(1)
    }
    private fun tryMove(d: Int) { if (!collides(px + d, py, rot)) { px += d; engine.sfx.click(); engine.haptics.tick() } }

    override fun onFlick(dir: Swipe, vx: Float, vy: Float) {
        when (dir) {
            Swipe.UP -> { val nr = (rot + 1) % shapes[type].size; if (!collides(px, py, nr)) { rot = nr; engine.sfx.blip() } }
            Swipe.DOWN -> { while (!collides(px, py + 1, rot)) py++; lock(); engine.haptics.medium() }
            Swipe.LEFT -> tryMove(-1)
            Swipe.RIGHT -> tryMove(1)
        }
    }

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.82f else s.minDim * 0.92f
    private fun cell(s: ScreenInfo) = side(s) / R
    private fun left(s: ScreenInfo) = s.cx - cell(s) * C / 2f
    private fun top(s: ScreenInfo) = s.cy - side(s) / 2f

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            val cs = cell(s); val l = left(s); val t = top(s)
            Draw.roundRect(c, l - s.dp(2f), t - s.dp(2f), cs * C + s.dp(4f), cs * R + s.dp(4f), s.dp(4f), Palette.alpha(Palette.panel, 0.5f))
            for (r in 0 until R) for (col in 0 until C) if (grid[r][col] >= 0)
                block(c, s, l + col * cs, t + r * cs, cs, colors[grid[r][col]])
            // active piece
            for (cellp in shapes[type][rot]) {
                val x = px + cellp[0]; val y = py + cellp[1]
                if (y >= 0) block(c, s, l + x * cs, t + y * cs, cs, colors[type])
            }
        }
    }

    private fun block(c: Canvas, s: ScreenInfo, x: Float, y: Float, cs: Float, col: Int) {
        val pad = cs * 0.06f
        Draw.roundRect(c, x + pad, y + pad, cs - pad * 2, cs - pad * 2, cs * 0.12f, col)
        Draw.roundRect(c, x + pad, y + pad, cs - pad * 2, (cs - pad * 2) * 0.4f, cs * 0.12f, Palette.alpha(Palette.text, 0.18f))
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        Draw.textCentered(c, "${Loc.t("LINES")} $lines", s.cx, s.h * 0.96f, s.px(0.035f), Palette.textDim, Fonts.mono)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.05f, s.px(0.05f), Palette.text, Fonts.display)
    }

    companion object {
        private fun buildShapes(): Array<Array<Array<IntArray>>> {
            fun p(vararg xy: Int): Array<IntArray> { val a = ArrayList<IntArray>(); var i = 0; while (i < xy.size) { a.add(intArrayOf(xy[i], xy[i + 1])); i += 2 }; return a.toTypedArray() }
            val I = arrayOf(p(0,1,1,1,2,1,3,1), p(2,0,2,1,2,2,2,3))
            val O = arrayOf(p(1,0,2,0,1,1,2,1))
            val T = arrayOf(p(0,1,1,1,2,1,1,0), p(1,0,1,1,1,2,2,1), p(0,1,1,1,2,1,1,2), p(1,0,1,1,1,2,0,1))
            val S = arrayOf(p(1,0,2,0,0,1,1,1), p(1,0,1,1,2,1,2,2))
            val Z = arrayOf(p(0,0,1,0,1,1,2,1), p(2,0,1,1,2,1,1,2))
            val L = arrayOf(p(0,1,1,1,2,1,2,0), p(1,0,1,1,1,2,2,2), p(0,2,0,1,1,1,2,1), p(0,0,1,0,1,1,1,2))
            val J = arrayOf(p(0,0,0,1,1,1,2,1), p(1,0,2,0,1,1,1,2), p(0,1,1,1,2,1,2,2), p(1,0,1,1,0,2,1,2))
            return arrayOf(I, O, T, S, Z, L, J)
        }

        fun meta() = GameMeta(
            id = "cascade",
            title = "Cascade",
            tagline = "Clear the lines",
            category = Category.PUZZLE,
            tier = Tier.BIG,
            accent = Palette.cyan,
            featured = true,
            icon = IconDrawer { c, cx, cy, size, _ ->
                val u = size * 0.22f
                val cols = intArrayOf(Palette.cyan, Palette.amber, Palette.violet, Palette.pink)
                val pts = arrayOf(intArrayOf(-1,0), intArrayOf(0,0), intArrayOf(1,0), intArrayOf(0,1))
                for (i in pts.indices) Draw.roundRectCentered(c, cx + pts[i][0] * u, cy + pts[i][1] * u - u * 0.3f, u * 0.9f, u * 0.9f, u * 0.15f, cols[i])
            },
            create = { CascadeGame() }
        )
    }
}
