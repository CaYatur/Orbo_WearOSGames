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
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Bubble Shooter — aim and fire bubbles into the cluster; line up three of a colour to pop them and
 * any bubbles left dangling fall too. Drag to aim, release to shoot. Clear the board for a fresh,
 * deeper one; let the stack reach the base line and it's over. The board is a square that fits the
 * round face on every device.
 */
class BubbleShooterGame : ArcadeGame(meta()) {

    private val C = 6
    private val R = 7
    private val palette = intArrayOf(Palette.mint, Palette.pink, Palette.amber, Palette.cyan, Palette.violet)
    private val grid = Array(R) { IntArray(C) { -1 } }
    private var curColor = 0
    private var aim = -Math.PI.toFloat() / 2f
    private var aiming = false

    private var moving = false
    private var bx = 0f; private var by = 0f; private var bvx = 0f; private var bvy = 0f
    private var bColor = 0
    private var boards = 0

    override val controlHint = "DRAG TO AIM · RELEASE TO FIRE"
    override val gameOverTitle = "BOARD JAMMED"

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.66f else s.minDim * 0.88f
    private fun cellD(s: ScreenInfo) = side(s) / R
    private fun gridLeft(s: ScreenInfo) = s.cx - (cellD(s) * C) / 2f
    private fun gridTop(s: ScreenInfo) = s.cy - side(s) / 2f
    private fun cellCx(s: ScreenInfo, row: Int, col: Int) = gridLeft(s) + cellD(s) * col + cellD(s) / 2f
    private fun cellCy(s: ScreenInfo, row: Int, col: Int) = gridTop(s) + cellD(s) * row + cellD(s) / 2f
    private fun bubR(s: ScreenInfo) = cellD(s) * 0.46f
    private fun shooterX(s: ScreenInfo) = s.cx
    private fun shooterY(s: ScreenInfo) = s.h * 0.93f

    override fun onReset() {
        boards = 0
        buildBoard(3)
        curColor = randExistingColor()
        moving = false; aiming = false; aim = -Math.PI.toFloat() / 2f
    }

    private fun buildBoard(initRows: Int) {
        for (r in 0 until R) for (c in 0 until C) grid[r][c] = if (r < initRows) Mathx.rand(palette.size) else -1
    }

    private fun randExistingColor(): Int {
        val present = HashSet<Int>()
        for (r in 0 until R) for (c in 0 until C) if (grid[r][c] >= 0) present.add(grid[r][c])
        return if (present.isEmpty()) Mathx.rand(palette.size) else present.toList()[Mathx.rand(present.size)]
    }

    override fun onPress(x: Float, y: Float) { setAim(x, y) }
    override fun onDrag(x: Float, y: Float) { setAim(x, y) }
    private fun setAim(x: Float, y: Float) {
        if (moving) return
        val a = Mathx.angle(x - shooterX(sc), y - shooterY(sc))
        // only allow upward aiming
        aim = a.coerceIn(-Math.PI.toFloat() + 0.25f, -0.25f)
        aiming = true
    }

    override fun onRelease(x: Float, y: Float) {
        if (moving) { aiming = false; return }
        fire()
        aiming = false
    }

    private fun fire() {
        val s = sc
        moving = true; bColor = curColor
        bx = shooterX(s); by = shooterY(s)
        val speed = s.px(2.4f)
        bvx = Mathx.cosf(aim) * speed; bvy = Mathx.sinf(aim) * speed
        engine.sfx.blip(); engine.haptics.light()
    }

    override fun onStep(dt: Float) {
        if (!moving) return
        val s = sc; val r = bubR(s)
        bx += bvx * dt; by += bvy * dt
        val gl = gridLeft(s); val gr = gl + cellD(s) * C
        if (bx < gl + r) { bx = gl + r; bvx = abs(bvx) }
        if (bx > gr - r) { bx = gr - r; bvx = -abs(bvx) }
        // hit top
        if (by < gridTop(s) + r) { snap(); return }
        // hit a bubble
        for (rr in 0 until R) for (cc in 0 until C) if (grid[rr][cc] >= 0) {
            if (hypot((bx - cellCx(s, rr, cc)).toDouble(), (by - cellCy(s, rr, cc)).toDouble()).toFloat() < cellD(s) * 0.92f) { snap(); return }
        }
        if (by > s.h + r) { moving = false; curColor = randExistingColor() } // safety
    }

    private fun snap() {
        val s = sc
        // nearest empty cell that touches an existing bubble or the top row
        var best = -1; var bestD = Float.MAX_VALUE
        for (rr in 0 until R) for (cc in 0 until C) if (grid[rr][cc] < 0 && attachable(rr, cc)) {
            val d = hypot((bx - cellCx(s, rr, cc)).toDouble(), (by - cellCy(s, rr, cc)).toDouble()).toFloat()
            if (d < bestD) { bestD = d; best = rr * C + cc }
        }
        if (best < 0) { moving = false; curColor = randExistingColor(); return }
        val pr = best / C; val pc = best % C
        grid[pr][pc] = bColor
        moving = false

        val group = floodSame(pr, pc, bColor)
        if (group.size >= 3) {
            for (idx in group) { val gr = idx / C; val gc = idx % C; grid[gr][gc] = -1
                particles.burst(cellCx(s, gr, gc), cellCy(s, gr, gc), 8, palette[bColor], s.px(0.5f), s.px(0.012f), 0.35f) }
            addScore(group.size * 10)
            engine.sfx.success(); engine.haptics.success()
            dropFloating()
        } else {
            engine.sfx.click(); engine.haptics.tick()
        }

        // win / lose checks
        if (isEmpty()) { boards++; addScore(150); engine.sfx.powerup(); flash(accent, 0.25f); buildBoard((3 + boards).coerceAtMost(5)) }
        else if ((0 until C).any { grid[R - 1][it] >= 0 }) { gameOver(); return }
        curColor = randExistingColor()
    }

    private fun attachable(r: Int, c: Int): Boolean {
        if (r == 0) return true
        for (d in neigh(r, c)) if (grid[d / C][d % C] >= 0) return true
        return false
    }

    private fun neigh(r: Int, c: Int): List<Int> {
        val l = ArrayList<Int>()
        if (r > 0) l.add((r - 1) * C + c)
        if (r < R - 1) l.add((r + 1) * C + c)
        if (c > 0) l.add(r * C + (c - 1))
        if (c < C - 1) l.add(r * C + (c + 1))
        return l
    }

    private fun floodSame(r: Int, c: Int, color: Int): List<Int> {
        val out = ArrayList<Int>(); val seen = HashSet<Int>()
        val stack = ArrayDeque<Int>(); stack.add(r * C + c); seen.add(r * C + c)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast(); out.add(cur)
            for (d in neigh(cur / C, cur % C)) if (d !in seen && grid[d / C][d % C] == color) { seen.add(d); stack.add(d) }
        }
        return out
    }

    private fun dropFloating() {
        val connected = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        for (c in 0 until C) if (grid[0][c] >= 0) { stack.add(c); connected.add(c) }
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            for (d in neigh(cur / C, cur % C)) if (d !in connected && grid[d / C][d % C] >= 0) { connected.add(d); stack.add(d) }
        }
        for (r in 0 until R) for (c in 0 until C) if (grid[r][c] >= 0 && (r * C + c) !in connected) {
            particles.burst(cellCx(sc, r, c), cellCy(sc, r, c), 8, palette[grid[r][c]], sc.px(0.6f), sc.px(0.012f), 0.45f, sc.px(1.5f))
            grid[r][c] = -1; addScore(20)
        }
    }

    private fun isEmpty(): Boolean { for (r in 0 until R) for (c in 0 until C) if (grid[r][c] >= 0) return false; return true }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            val r = bubR(s)
            // base / danger line
            val dy = cellCy(s, R - 1, 0) + cellD(s) / 2f
            Draw.line(c, gridLeft(s), dy, gridLeft(s) + cellD(s) * C, dy, Palette.alpha(Palette.red, 0.5f), s.dp(2f))
            for (rr in 0 until R) for (cc in 0 until C) if (grid[rr][cc] >= 0) {
                val col = palette[grid[rr][cc]]
                val x = cellCx(s, rr, cc); val y = cellCy(s, rr, cc)
                Draw.fillCircle(c, x, y, r, col)
                Draw.fillCircle(c, x - r * 0.3f, y - r * 0.3f, r * 0.28f, Palette.alpha(Palette.text, 0.3f))
            }
            // aim guide
            if (!moving) {
                var ax = shooterX(s); var ay = shooterY(s)
                val dx = Mathx.cosf(aim); val dyv = Mathx.sinf(aim)
                var step = 0
                while (step < 8) {
                    ax += dx * s.px(0.07f); ay += dyv * s.px(0.07f)
                    Draw.fillCircle(c, ax, ay, s.px(0.008f), Palette.alpha(Palette.text, 0.4f))
                    step++
                }
            }
            // moving bubble
            if (moving) { Draw.glow(c, bx, by, r * 1.4f, palette[bColor], 0.3f); Draw.fillCircle(c, bx, by, r, palette[bColor]) }
            // shooter
            Draw.fillCircle(c, shooterX(s), shooterY(s), r * 1.1f, Palette.panelHi)
            Draw.fillCircle(c, shooterX(s), shooterY(s), r * 0.85f, palette[curColor])
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.07f, s.px(0.05f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "bubble_shooter",
            title = "Bubble Shooter",
            tagline = "Pop bubble clusters",
            category = Category.ARCADE,
            tier = Tier.BIG,
            accent = Palette.cyan,
            featured = true,
            icon = IconDrawer { c, cx, cy, size, _ ->
                val cols = intArrayOf(Palette.mint, Palette.pink, Palette.amber)
                val rr = size * 0.16f
                Draw.fillCircle(c, cx - rr * 1.1f, cy - rr, rr, cols[0])
                Draw.fillCircle(c, cx + rr * 1.1f, cy - rr, rr, cols[1])
                Draw.fillCircle(c, cx, cy - rr * 2.9f, rr, cols[2])
                Draw.fillCircle(c, cx, cy + rr * 1.6f, rr * 0.9f, cols[1])
            },
            create = { BubbleShooterGame() }
        )
    }
}
