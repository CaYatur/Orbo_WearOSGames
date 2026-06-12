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
import com.cayadev.orbo.engine.Swipe
import com.cayadev.orbo.game.ArcadeGame

/** Snake Neo — a glossy take on the classic. Smoothly gliding neon body, golden apples, rising
 * speed and full juice. Turn with taps (left/right) or swipes. */
class SnakeNeoGame : ArcadeGame(meta()) {

    private val gn = 15
    private data class Cell(val x: Int, val y: Int)
    private val snake = ArrayList<Cell>()
    private var old = ArrayList<Cell>()
    private var dx = 1; private var dy = 0
    private var pdx = 1; private var pdy = 0
    private var foodX = 0; private var foodY = 0
    private var golden = false
    private var stepTime = 0.16f
    private var moveT = 0f
    private var grow = 0
    private var foodPulse = 0f

    override val controlHint = "TAP L/R OR SWIPE"

    override fun onReset() {
        snake.clear()
        val mid = gn / 2
        snake.add(Cell(mid, mid)); snake.add(Cell(mid - 1, mid)); snake.add(Cell(mid - 2, mid))
        old = ArrayList(snake)
        dx = 1; dy = 0; pdx = 1; pdy = 0
        stepTime = 0.16f; moveT = 0f; grow = 0
        placeFood()
    }

    private fun placeFood() {
        // Board full → no room left for food: the player has maxed the grid, so end the run
        // (also prevents an infinite search loop when every cell is occupied).
        val free = gn * gn - snake.size
        if (free <= 0) { die(); return }
        var ok = false
        while (!ok) {
            foodX = Mathx.rand(gn); foodY = Mathx.rand(gn)
            ok = snake.none { it.x == foodX && it.y == foodY }
        }
        golden = Mathx.chance(0.16f)
    }

    override fun onTapWorld(x: Float, y: Float) {
        if (x < sc.cx) setDir(dy, -dx) else setDir(-dy, dx)  // relative turn
    }
    override fun onFlick(dir: Swipe, vx: Float, vy: Float) {
        when (dir) {
            Swipe.LEFT -> setDir(-1, 0); Swipe.RIGHT -> setDir(1, 0)
            Swipe.UP -> setDir(0, -1); Swipe.DOWN -> setDir(0, 1)
        }
    }
    private fun setDir(nx: Int, ny: Int) { if (nx == -dx && ny == -dy) return; pdx = nx; pdy = ny }

    override fun onStep(dt: Float) {
        foodPulse += dt
        stepTime = Mathx.lerp(0.16f, 0.066f, (snake.size / 40f).coerceIn(0f, 1f))
        moveT += dt / stepTime
        while (moveT >= 1f) { moveT -= 1f; step() ; if (state != GState.PLAYING) return }
    }

    private fun step() {
        dx = pdx; dy = pdy
        old = ArrayList(snake)
        val head = snake[0]
        val nx = head.x + dx; val ny = head.y + dy
        if (nx < 0 || ny < 0 || nx >= gn || ny >= gn) { die(); return }
        if (snake.any { it.x == nx && it.y == ny } && !(nx == snake.last().x && ny == snake.last().y && grow == 0)) { die(); return }
        snake.add(0, Cell(nx, ny))
        if (nx == foodX && ny == foodY) {
            val pts = if (golden) 50 else 10
            addScore(pts); grow += if (golden) 2 else 1
            engine.sfx.coin(); engine.haptics.light()
            val g = geom(sc)
            particles.burst(g[0] + nx * g[2] + g[2] / 2, g[1] + ny * g[2] + g[2] / 2, if (golden) 18 else 10,
                if (golden) Palette.gold else accent, sc.px(0.5f), sc.px(0.014f), 0.45f)
            placeFood()
        }
        if (grow > 0) grow-- else snake.removeAt(snake.size - 1)
    }

    private fun die() {
        shake(sc.px(0.03f)); flash(Palette.red, 0.45f); engine.sfx.explosion(); engine.haptics.heavy()
        val g = geom(sc); val h = snake[0]
        particles.burst(g[0] + h.x * g[2] + g[2] / 2, g[1] + h.y * g[2] + g[2] / 2, 26, accent, sc.px(0.8f), sc.px(0.02f), 0.7f)
        gameOver()
    }

    private fun geom(s: ScreenInfo): FloatArray {
        val side = if (s.isRound) s.minDim * 0.69f else s.minDim * 0.9f
        val cell = side / gn
        return floatArrayOf(s.cx - side / 2, s.cy - side / 2, cell)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, 0xFF06100C.toInt(), Palette.bg)
        if (state == GState.READY) return
        val g = geom(s); val ox = g[0]; val oy = g[1]; val cell = g[2]
        val side = cell * gn
        Draw.roundRect(c, ox - cell * 0.3f, oy - cell * 0.3f, side + cell * 0.6f, side + cell * 0.6f, cell * 0.8f, Palette.alpha(Palette.panel, 0.5f))
        Draw.roundRectStroke(c, ox - cell * 0.3f, oy - cell * 0.3f, side + cell * 0.6f, side + cell * 0.6f, cell * 0.8f, Palette.alpha(accent, 0.25f), s.dp(2f))

        // food
        val fcx = ox + foodX * cell + cell / 2; val fcy = oy + foodY * cell + cell / 2
        val fcol = if (golden) Palette.gold else Palette.red
        val fp = 1f + 0.12f * Mathx.sinf(foodPulse * 6f)
        Draw.glow(c, fcx, fcy, cell * 0.9f * fp, fcol, 0.4f)
        Draw.fillCircle(c, fcx, fcy, cell * 0.32f * fp, fcol)
        if (golden) Draw.textCentered(c, "★", fcx, fcy, cell * 0.4f, Palette.bg, Fonts.display)

        // snake (interpolated)
        val p = moveT.coerceIn(0f, 1f)
        for (i in snake.indices.reversed()) {
            val cur = snake[i]
            val from = if (i < old.size) old[i] else old.lastOrNull() ?: cur
            val ix = Mathx.lerp(from.x.toFloat(), cur.x.toFloat(), p)
            val iy = Mathx.lerp(from.y.toFloat(), cur.y.toFloat(), p)
            val cx = ox + ix * cell + cell / 2; val cy = oy + iy * cell + cell / 2
            val tHue = i.toFloat() / snake.size
            val col = Palette.lerp(accent, Palette.violet, tHue * 0.7f)
            val r = cell * (if (i == 0) 0.48f else 0.42f)
            if (i == 0) Draw.glow(c, cx, cy, r * 2f, accent, 0.4f)
            Draw.roundRectCentered(c, cx, cy, r * 2, r * 2, r * 0.6f, col)
            if (i == 0) {
                // eyes
                val ex = dx * cell * 0.16f; val ey = dy * cell * 0.16f
                val px = -dy * cell * 0.16f; val py = dx * cell * 0.16f
                Draw.fillCircle(c, cx + ex + px, cy + ey + py, cell * 0.08f, Palette.bg)
                Draw.fillCircle(c, cx + ex - px, cy + ey - py, cell * 0.08f, Palette.bg)
            }
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.075f, s.px(0.05f), Palette.alpha(Palette.text, 0.85f), Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "snake_neo",
            title = "Snake Neo",
            tagline = "Classic, reborn",
            category = Category.CLASSIC,
            tier = Tier.BIG,
            accent = Palette.mint,
            featured = true,
            icon = IconDrawer { c, cx, cy, size, color ->
                var x = cx - size * 0.28f; var y = cy + size * 0.18f
                val step = size * 0.16f
                val pts = arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(1, -1), intArrayOf(2, -1), intArrayOf(2, -2))
                for ((i, pp) in pts.withIndex()) {
                    Draw.roundRectCentered(c, x + pp[0] * step, y + pp[1] * step, step * 0.9f, step * 0.9f, step * 0.3f,
                        Palette.lerp(color, Palette.violet, i / 5f))
                }
                Draw.fillCircle(c, cx + size * 0.22f, cy - size * 0.2f, size * 0.1f, Palette.red)
            },
            create = { SnakeNeoGame() }
        )
    }
}
