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
import com.cayadev.orbo.engine.Swipe
import com.cayadev.orbo.game.ArcadeGame

/** Twos — the addictive slide-and-merge number puzzle. Swipe to push the board; equal tiles fuse
 * and double. Chase 2048 and beyond until the grid jams. */
class TwosGame : ArcadeGame(meta()) {

    private val grid = IntArray(16)
    private val pop = FloatArray(16)
    private var reached2048 = false
    private var bestTile = 0

    override val controlHint = "SWIPE TO MERGE"
    override val gameOverTitle = "NO MOVES"

    override fun onReset() {
        for (i in 0 until 16) { grid[i] = 0; pop[i] = 0f }
        reached2048 = false; bestTile = 0
        spawn(); spawn()
    }

    private fun spawn() {
        val empty = (0 until 16).filter { grid[it] == 0 }
        if (empty.isEmpty()) return
        val idx = empty[Mathx.rand(empty.size)]
        grid[idx] = if (Mathx.chance(0.9f)) 2 else 4
        pop[idx] = 1f
    }

    override fun onStep(dt: Float) {
        for (i in 0 until 16) if (pop[i] > 0f) pop[i] -= dt * 3.4f
    }

    private fun lines(dir: Swipe): Array<IntArray> = when (dir) {
        Swipe.LEFT -> Array(4) { r -> intArrayOf(r * 4, r * 4 + 1, r * 4 + 2, r * 4 + 3) }
        Swipe.RIGHT -> Array(4) { r -> intArrayOf(r * 4 + 3, r * 4 + 2, r * 4 + 1, r * 4) }
        Swipe.UP -> Array(4) { col -> intArrayOf(col, col + 4, col + 8, col + 12) }
        Swipe.DOWN -> Array(4) { col -> intArrayOf(col + 12, col + 8, col + 4, col) }
    }

    override fun onFlick(dir: Swipe, vx: Float, vy: Float) {
        var moved = false
        for (line in lines(dir)) {
            val vals = ArrayList<Int>(4)
            for (idx in line) if (grid[idx] != 0) vals.add(grid[idx])
            val merged = ArrayList<Int>(4)
            var i = 0
            while (i < vals.size) {
                if (i + 1 < vals.size && vals[i] == vals[i + 1]) {
                    val v = vals[i] * 2
                    merged.add(v); addScore(v)
                    if (v >= bestTile) bestTile = v
                    if (v >= 2048 && !reached2048) { reached2048 = true; flash(Palette.gold, 0.5f); engine.sfx.success() }
                    i += 2
                } else { merged.add(vals[i]); i++ }
            }
            for (k in 0 until 4) {
                val nv = if (k < merged.size) merged[k] else 0
                if (grid[line[k]] != nv) {
                    moved = true
                    if (nv != 0 && (k >= vals.size || merged.size < vals.size)) pop[line[k]] = 0.8f
                }
                grid[line[k]] = nv
            }
        }
        if (moved) {
            engine.sfx.blip(); engine.haptics.light()
            spawn()
            if (!hasMoves()) { engine.sfx.fail(); gameOver() }
        } else {
            engine.sfx.click()
        }
    }

    private fun hasMoves(): Boolean {
        for (i in 0 until 16) {
            if (grid[i] == 0) return true
            val x = i % 4; val y = i / 4
            if (x < 3 && grid[i] == grid[i + 1]) return true
            if (y < 3 && grid[i] == grid[i + 4]) return true
        }
        return false
    }

    private fun tileColor(v: Int): Int = when (v) {
        2 -> 0xFF2A3142.toInt(); 4 -> 0xFF3A4358.toInt()
        8 -> Palette.blue; 16 -> Palette.cyan; 32 -> Palette.mint; 64 -> Palette.lime
        128 -> Palette.amber; 256 -> Palette.orange; 512 -> Palette.pink
        1024 -> Palette.violet; else -> Palette.gold
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val side = s.px(0.66f); val pad = side * 0.03f
        val cell = (side - pad * 5) / 4
        val ox = s.cx - side / 2; val oy = s.cy - side / 2
        Draw.roundRect(c, ox, oy, side, side, side * 0.06f, Palette.alpha(Palette.panel, 0.7f))
        for (i in 0 until 16) {
            val gx = i % 4; val gy = i / 4
            val cx = ox + pad + gx * (cell + pad) + cell / 2
            val cy = oy + pad + gy * (cell + pad) + cell / 2
            val v = grid[i]
            if (v == 0) { Draw.roundRectCentered(c, cx, cy, cell, cell, cell * 0.16f, Palette.alpha(Palette.bgSoft, 0.8f)); continue }
            val scl = if (pop[i] > 0f) Ease.outBack((1f - pop[i]).coerceIn(0f, 1f)) else 1f
            val col = tileColor(v)
            if (v >= 8) Draw.glow(c, cx, cy, cell * 0.7f * scl, col, 0.2f)
            Draw.roundRectCentered(c, cx, cy, cell * scl, cell * scl, cell * 0.16f, col)
            val txt = "$v"
            val ts = cell * (if (txt.length >= 4) 0.3f else if (txt.length == 3) 0.36f else 0.44f)
            Draw.textCentered(c, txt, cx, cy, ts * scl, if (v <= 4) Palette.text else Palette.bg, Fonts.display)
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.105f, s.px(0.055f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "twos",
            title = "Twos",
            tagline = "Merge to 2048",
            category = Category.PUZZLE,
            tier = Tier.BIG,
            accent = Palette.amber,
            featured = true,
            icon = IconDrawer { c, cx, cy, size, _ ->
                val cell = size * 0.32f
                Draw.roundRectCentered(c, cx - cell * 0.55f, cy - cell * 0.55f, cell, cell, cell * 0.18f, Palette.amber)
                Draw.roundRectCentered(c, cx + cell * 0.55f, cy - cell * 0.55f, cell, cell, cell * 0.18f, Palette.orange)
                Draw.roundRectCentered(c, cx - cell * 0.55f, cy + cell * 0.55f, cell, cell, cell * 0.18f, Palette.pink)
                Draw.roundRectCentered(c, cx + cell * 0.55f, cy + cell * 0.55f, cell, cell, cell * 0.18f, Palette.violet)
            },
            create = { TwosGame() }
        )
    }
}
