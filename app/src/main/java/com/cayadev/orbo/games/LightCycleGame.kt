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
import com.cayadev.orbo.game.ArcadeGame

/** Light Cycle — leave a wall of light and box in the AI rider before it boxes you. Turn with
 * taps (left/right) or swipes. Outlast the bot to bank a round and speed everything up. */
class LightCycleGame : ArcadeGame(meta()) {

    private val n = 19
    private val grid = IntArray(n * n)     // 0 empty, 1 player, 2 ai
    private var pCol = 0; private var pRow = 0; private var pdx = 1; private var pdy = 0
    private var ppdx = 1; private var ppdy = 0
    private var aCol = 0; private var aRow = 0; private var adx = -1; private var ady = 0
    private var stepTime = 0.12f
    private var stepTimer = 0f
    private var won = false
    private var winFx = 0f

    override val controlHint = "TAP L/R OR SWIPE"

    override fun onReset() { stepTime = 0.13f; resetArena() }

    private fun resetArena() {
        for (i in grid.indices) grid[i] = 0
        pCol = 3; pRow = n / 2; pdx = 1; pdy = 0; ppdx = 1; ppdy = 0
        aCol = n - 4; aRow = n / 2; adx = -1; ady = 0
        grid[pRow * n + pCol] = 1; grid[aRow * n + aCol] = 2
        stepTimer = 0f; won = false
    }

    private fun free(col: Int, row: Int): Boolean =
        col in 0 until n && row in 0 until n && grid[row * n + col] == 0

    override fun onTapWorld(x: Float, y: Float) {
        // relative turn: left half = turn left, right half = turn right
        if (x < sc.cx) turn(left = true) else turn(left = false)
    }

    override fun onFlick(dir: Swipe, vx: Float, vy: Float) {
        when (dir) {
            Swipe.LEFT -> setDir(-1, 0); Swipe.RIGHT -> setDir(1, 0)
            Swipe.UP -> setDir(0, -1); Swipe.DOWN -> setDir(0, 1)
        }
    }

    private fun turn(left: Boolean) {
        if (left) setDir(pdy, -pdx) else setDir(-pdy, pdx)
    }
    private fun setDir(dx: Int, dy: Int) {
        if (dx == -pdx && dy == -pdy) return  // no reverse
        ppdx = dx; ppdy = dy
    }

    override fun onStep(dt: Float) {
        if (winFx > 0f) winFx -= dt
        stepTimer += dt
        var guard = 0
        while (stepTimer >= stepTime && state == GState.PLAYING && guard++ < 4) {
            stepTimer -= stepTime
            tick()
        }
    }

    private fun tick() {
        // apply player's queued direction
        if (!(ppdx == -pdx && ppdy == -pdy)) { pdx = ppdx; pdy = ppdy }
        val pN = pCol + pdx; val pNr = pRow + pdy
        // AI chooses
        aiThink()
        val aN = aCol + adx; val aNr = aRow + ady

        val pCrash = !free(pN, pNr)
        val aCrash = !free(aN, aNr)
        val headOn = pN == aN && pNr == aNr

        if (pCrash || headOn) { lose(); return }
        if (aCrash) { winRound(); return }

        pCol = pN; pRow = pNr; grid[pRow * n + pCol] = 1
        aCol = aN; aRow = aNr; grid[aRow * n + aCol] = 2
    }

    private fun aiThink() {
        // keep going if safe, with some lookahead; otherwise turn to a free side
        val options = ArrayList<IntArray>()
        val straight = intArrayOf(adx, ady)
        val leftT = intArrayOf(ady, -adx)
        val rightT = intArrayOf(-ady, adx)
        for (o in listOf(straight, leftT, rightT)) {
            if (free(aCol + o[0], aRow + o[1])) options.add(o)
        }
        if (options.isEmpty()) return  // will crash
        // prefer straight most of the time; score options by open space ahead
        var best = options[0]; var bestSpace = -1
        for (o in options) {
            var space = 0; var cc = aCol; var rr = aRow
            while (space < 6 && free(cc + o[0], rr + o[1])) { cc += o[0]; rr += o[1]; space++ }
            val bias = if (o === straight) 1 else 0
            if (space + bias > bestSpace) { bestSpace = space + bias; best = o }
        }
        if (Mathx.chance(0.12f) && options.size > 1) best = options[Mathx.rand(options.size)]
        adx = best[0]; ady = best[1]
    }

    private fun lose() {
        shake(sc.px(0.03f)); flash(Palette.red, 0.45f); engine.sfx.explosion(); engine.haptics.heavy()
        val g = geom(sc)
        particles.burst(g[0] + pCol * g[2] + g[2] / 2, g[1] + pRow * g[2] + g[2] / 2, 24, Palette.cyan, sc.px(0.8f), sc.px(0.02f), 0.7f)
        gameOver()
    }

    private fun winRound() {
        addScore(1); won = true; winFx = 0.8f
        engine.sfx.success(); engine.haptics.success()
        val g = geom(sc)
        particles.burst(g[0] + aCol * g[2] + g[2] / 2, g[1] + aRow * g[2] + g[2] / 2, 22, Palette.pink, sc.px(0.8f), sc.px(0.02f), 0.7f)
        stepTime = (stepTime * 0.92f).coerceAtLeast(0.05f)
        resetArena()
    }

    private fun geom(s: ScreenInfo): FloatArray {
        val side = if (s.isRound) s.minDim * 0.69f else s.minDim * 0.9f
        val cell = side / n
        return floatArrayOf(s.cx - side / 2, s.cy - side / 2, cell)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, 0xFF06080F.toInt(), Palette.bg)
        if (state == GState.READY) return
        val g = geom(s); val ox = g[0]; val oy = g[1]; val cell = g[2]
        val side = cell * n
        // arena
        Draw.roundRect(c, ox - cell * 0.4f, oy - cell * 0.4f, side + cell * 0.8f, side + cell * 0.8f, cell, Palette.alpha(Palette.panel, 0.6f))
        Draw.roundRectStroke(c, ox - cell * 0.4f, oy - cell * 0.4f, side + cell * 0.8f, side + cell * 0.8f, cell, Palette.alpha(accent, 0.3f), s.dp(2f))
        // trails
        for (row in 0 until n) for (col in 0 until n) {
            val v = grid[row * n + col]
            if (v == 0) continue
            val col2 = if (v == 1) Palette.cyan else Palette.pink
            Draw.roundRect(c, ox + col * cell + cell * 0.08f, oy + row * cell + cell * 0.08f, cell * 0.84f, cell * 0.84f, cell * 0.18f, Palette.alpha(col2, 0.85f))
        }
        // heads
        head(c, ox, oy, cell, pCol, pRow, Palette.cyan)
        head(c, ox, oy, cell, aCol, aRow, Palette.pink)
    }

    private fun head(c: Canvas, ox: Float, oy: Float, cell: Float, col: Int, row: Int, color: Int) {
        val cx = ox + col * cell + cell / 2; val cy = oy + row * cell + cell / 2
        Draw.glow(c, cx, cy, cell * 1.3f, color, 0.5f)
        Draw.roundRectCentered(c, cx, cy, cell * 0.9f, cell * 0.9f, cell * 0.2f, color)
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (winFx > 0f) Draw.textCentered(c, Loc.t("ROUND WON"), s.cx, s.h * 0.88f, s.px(0.05f), Palette.mint, Fonts.bodyBold, (winFx / 0.8f).coerceIn(0f, 1f))
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "${Loc.t("WINS")} $score", s.cx, s.h * 0.075f, s.px(0.045f), Palette.alpha(Palette.text, 0.8f), Fonts.mono)
    }

    companion object {
        fun meta() = GameMeta(
            id = "light_cycle",
            title = "Grid Rider",
            tagline = "Box in the bot",
            category = Category.CLASSIC,
            tier = Tier.MINI,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, _ ->
                Draw.roundRectCentered(c, cx - size * 0.05f, cy, size * 0.5f, size * 0.08f, size * 0.03f, Palette.cyan)
                Draw.roundRectCentered(c, cx + size * 0.2f, cy - size * 0.15f, size * 0.08f, size * 0.4f, size * 0.03f, Palette.cyan)
                Draw.roundRectCentered(c, cx + size * 0.02f, cy + size * 0.2f, size * 0.4f, size * 0.08f, size * 0.03f, Palette.pink)
            },
            create = { LightCycleGame() }
        )
    }
}
