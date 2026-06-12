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
import com.cayadev.orbo.engine.Swipe
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.game.ArcadeGame

/** Maze Runner — fresh procedurally-generated mazes. Swipe to glide to the next junction; reach the
 * glowing exit. Solve as many as you can in 90 seconds — bigger, faster solves score more. */
class MazeRunnerGame : ArcadeGame(meta()) {

    private var w = 7; private var h = 7
    private lateinit var walls: IntArray   // bit0 N, bit1 E, bit2 S, bit3 W (1 = wall)
    private var pcol = 0; private var prow = 0
    private var gcol = 0; private var grow = 0
    private val anim = ArrayList<IntArray>()
    private var animT = 0f
    private var moving = false
    private var timeLeft = 90f
    private var mazeTime = 0f
    private var solved = 0
    private val visited = HashSet<Int>()

    private val dirs = arrayOf(intArrayOf(0, -1), intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(-1, 0))

    override val controlHint = "SWIPE TO MOVE"
    override val gameOverTitle = "TIME UP"

    override fun onReset() { timeLeft = 90f; solved = 0; w = 7; h = 7; newMaze() }

    private fun newMaze() {
        walls = IntArray(w * h) { 0b1111 }
        val vis = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        var cur = 0; vis[0] = true; stack.addLast(0)
        while (stack.isNotEmpty()) {
            cur = stack.last()
            val cx = cur % w; val cy = cur / w
            val opts = ArrayList<Int>(4)
            for (d in 0 until 4) {
                val nx = cx + dirs[d][0]; val ny = cy + dirs[d][1]
                if (nx in 0 until w && ny in 0 until h && !vis[ny * w + nx]) opts.add(d)
            }
            if (opts.isEmpty()) { stack.removeLast(); continue }
            val d = opts[Mathx.rand(opts.size)]
            val nx = cx + dirs[d][0]; val ny = cy + dirs[d][1]
            walls[cur] = walls[cur] and (1 shl d).inv()
            walls[ny * w + nx] = walls[ny * w + nx] and (1 shl ((d + 2) % 4)).inv()
            vis[ny * w + nx] = true; stack.addLast(ny * w + nx)
        }
        pcol = 0; prow = 0; gcol = w - 1; grow = h - 1
        moving = false; anim.clear(); mazeTime = 0f
        visited.clear(); visited.add(0)
    }

    private fun canMove(col: Int, row: Int, d: Int): Boolean =
        (walls[row * w + col] and (1 shl d)) == 0

    override fun onFlick(dir: Swipe, vx: Float, vy: Float) {
        if (moving) return
        val d = when (dir) { Swipe.UP -> 0; Swipe.RIGHT -> 1; Swipe.DOWN -> 2; Swipe.LEFT -> 3 }
        if (!canMove(pcol, prow, d)) { engine.sfx.hit(); engine.haptics.tick(); return }
        val path = ArrayList<IntArray>()
        path.add(intArrayOf(pcol, prow))
        var cc = pcol; var rr = prow
        val left = (d + 3) % 4; val right = (d + 1) % 4
        while (true) {
            if (!canMove(cc, rr, d)) break
            cc += dirs[d][0]; rr += dirs[d][1]
            path.add(intArrayOf(cc, rr))
            visited.add(rr * w + cc)
            if (cc == gcol && rr == grow) break
            if (canMove(cc, rr, left) || canMove(cc, rr, right)) break  // stop at junction
        }
        if (path.size < 2) return
        anim.clear(); anim.addAll(path); animT = 0f; moving = true
        pcol = cc; prow = rr
        engine.sfx.swoosh(); engine.haptics.light()
    }

    override fun onStep(dt: Float) {
        timeLeft -= dt; mazeTime += dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver(); return }
        if (moving) {
            animT += dt * 9f
            if (animT >= anim.size - 1) {
                moving = false; animT = (anim.size - 1).toFloat()
                if (pcol == gcol && prow == grow) onSolved()
            }
        }
    }

    private fun onSolved() {
        solved++
        val bonus = (40f - mazeTime).toInt().coerceAtLeast(0)
        addScore(40 + bonus + (w - 6) * 10)
        engine.sfx.success(); engine.haptics.success()
        val g = geom(sc)
        particles.burst(g[0] + gcol * g[2] + g[2] / 2, g[1] + grow * g[2] + g[2] / 2, 26, accent, sc.px(0.8f), sc.px(0.02f), 0.7f)
        if (w < 13) { w += 1; h += 1 }
        newMaze()
    }

    private fun geom(s: ScreenInfo): FloatArray {
        val side = if (s.isRound) s.minDim * 0.7f else s.minDim * 0.86f
        val cw = side / maxOf(w, h)
        val totalW = cw * w; val totalH = cw * h
        return floatArrayOf(s.cx - totalW / 2, s.cy - totalH / 2, cw)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val g = geom(s); val ox = g[0]; val oy = g[1]; val cw = g[2]
        val lw = s.dp(2.2f)
        // visited trail
        for (idx in visited) {
            val vx = idx % w; val vy = idx / w
            Draw.roundRectCentered(c, ox + vx * cw + cw / 2, oy + vy * cw + cw / 2, cw * 0.7f, cw * 0.7f, cw * 0.2f, Palette.alpha(accent, 0.10f))
        }
        // goal
        val gx = ox + gcol * cw + cw / 2; val gy = oy + grow * cw + cw / 2
        val gp = 0.6f + 0.4f * Mathx.sinf(timeAlive * 4f)
        Draw.glow(c, gx, gy, cw * 0.7f * gp, Palette.lime, 0.5f)
        Draw.fillCircle(c, gx, gy, cw * 0.26f, Palette.lime)
        Draw.textCentered(c, "★", gx, gy, cw * 0.34f, Palette.bg, Fonts.display)

        // walls
        for (row in 0 until h) for (col in 0 until w) {
            val wf = walls[row * w + col]
            val x0 = ox + col * cw; val y0 = oy + row * cw
            if (wf and 0b0001 != 0) Draw.line(c, x0, y0, x0 + cw, y0, Palette.line, lw)
            if (wf and 0b1000 != 0) Draw.line(c, x0, y0, x0, y0 + cw, Palette.line, lw)
            if (col == w - 1 && wf and 0b0010 != 0) Draw.line(c, x0 + cw, y0, x0 + cw, y0 + cw, Palette.line, lw)
            if (row == h - 1 && wf and 0b0100 != 0) Draw.line(c, x0, y0 + cw, x0 + cw, y0 + cw, Palette.line, lw)
        }

        // player (interpolated along path)
        var pxf = pcol.toFloat(); var pyf = prow.toFloat()
        if (moving && anim.size >= 2) {
            val seg = animT.toInt().coerceIn(0, anim.size - 2)
            val frac = (animT - seg).coerceIn(0f, 1f)
            pxf = Mathx.lerp(anim[seg][0].toFloat(), anim[seg + 1][0].toFloat(), frac)
            pyf = Mathx.lerp(anim[seg][1].toFloat(), anim[seg + 1][1].toFloat(), frac)
        }
        val pcx = ox + pxf * cw + cw / 2; val pcy = oy + pyf * cw + cw / 2
        Draw.glow(c, pcx, pcy, cw * 0.6f, accent, 0.5f)
        Draw.fillCircle(c, pcx, pcy, cw * 0.28f, accent)
        Draw.fillCircle(c, pcx, pcy, cw * 0.14f, Palette.shade(accent, 0.4f))
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val frac = (timeLeft / 90f).coerceIn(0f, 1f)
        Ui.edgeProgress(c, s, frac, Palette.lerp(Palette.red, accent, frac))
        Draw.textCentered(c, "${Loc.t("SOLVED")} $solved", s.cx, s.h * 0.90f, s.px(0.04f), Palette.textDim, Fonts.mono)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.085f, s.px(0.05f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "maze_runner",
            title = "Maze Dash",
            tagline = "Find the exit",
            category = Category.PUZZLE,
            tier = Tier.BIG,
            accent = Palette.cyan,
            featured = true,
            icon = IconDrawer { c, cx, cy, size, color ->
                val u = size * 0.16f
                Draw.line(c, cx - u * 2, cy - u * 2, cx + u * 2, cy - u * 2, Palette.line, size * 0.04f)
                Draw.line(c, cx - u * 2, cy, cx + u, cy, Palette.line, size * 0.04f)
                Draw.line(c, cx - u, cy - u * 2, cx - u, cy + u, Palette.line, size * 0.04f)
                Draw.line(c, cx + u, cy, cx + u, cy + u * 2, Palette.line, size * 0.04f)
                Draw.fillCircle(c, cx - u * 1.5f, cy - u, size * 0.06f, color)
                Draw.fillCircle(c, cx + u * 1.6f, cy + u * 1.5f, size * 0.06f, Palette.lime)
            },
            create = { MazeRunnerGame() }
        )
    }
}
