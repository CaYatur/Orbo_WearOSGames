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
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.game.ArcadeGame
import kotlin.math.abs

/** Slide Puzzle — order the tiles 1→8 by sliding into the gap. Solve as many boards as you can in
 * 90 seconds; faster solves bank bigger bonuses. */
class SlidePuzzleGame : ArcadeGame(meta()) {

    private val n = 3
    private val tiles = IntArray(n * n)
    private var blank = n * n - 1
    private var timeLeft = 90f
    private var moves = 0
    private var boardTime = 0f
    private var solves = 0
    private val slide = FloatArray(n * n)   // slide-in animation per tile

    override val controlHint = "TAP TILE TO SLIDE"
    override val gameOverTitle = "TIME UP"

    override fun onReset() { timeLeft = 90f; solves = 0; shuffle() }

    private fun shuffle() {
        for (i in 0 until n * n) tiles[i] = (i + 1) % (n * n)   // 1..8,0
        blank = n * n - 1
        var prev = -1
        repeat(80) {
            val opts = neighbors(blank).filter { it != prev }
            val pick = opts[Mathx.rand(opts.size)]
            tiles[blank] = tiles[pick]; tiles[pick] = 0; prev = blank; blank = pick
        }
        if (isSolved()) shuffle()
        moves = 0; boardTime = 0f
    }

    private fun neighbors(idx: Int): List<Int> {
        val x = idx % n; val y = idx / n; val list = ArrayList<Int>(4)
        if (x > 0) list.add(idx - 1); if (x < n - 1) list.add(idx + 1)
        if (y > 0) list.add(idx - n); if (y < n - 1) list.add(idx + n)
        return list
    }

    private fun isSolved(): Boolean {
        for (i in 0 until n * n - 1) if (tiles[i] != i + 1) return false
        return tiles[n * n - 1] == 0
    }

    override fun onStep(dt: Float) {
        timeLeft -= dt; boardTime += dt
        for (i in slide.indices) if (slide[i] > 0f) slide[i] -= dt * 5f
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver() }
    }

    private fun geom(s: ScreenInfo): FloatArray {
        val side = s.px(0.6f); val cell = side / n
        return floatArrayOf(s.cx - side / 2, s.cy - side / 2, cell)
    }

    override fun onTapWorld(x: Float, y: Float) {
        val g = geom(sc); val col = ((x - g[0]) / g[2]).toInt(); val row = ((y - g[1]) / g[2]).toInt()
        if (col !in 0 until n || row !in 0 until n) return
        val idx = row * n + col
        if (idx in neighbors(blank)) {
            tiles[blank] = tiles[idx]; tiles[idx] = 0
            slide[blank] = 1f
            blank = idx; moves++
            engine.sfx.click(); engine.haptics.light()
            if (isSolved()) onSolved()
        }
    }

    private fun onSolved() {
        solves++
        val speedBonus = (60f - boardTime).toInt().coerceAtLeast(0)
        addScore(50 + speedBonus)
        engine.sfx.success(); engine.haptics.success()
        particles.burst(sc.cx, sc.cy, 28, accent, sc.px(0.9f), sc.px(0.02f), 0.7f)
        shuffle()
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val g = geom(s); val ox = g[0]; val oy = g[1]; val cell = g[2]
        Draw.roundRect(c, ox - cell * 0.1f, oy - cell * 0.1f, cell * n + cell * 0.2f, cell * n + cell * 0.2f, cell * 0.25f, Palette.alpha(Palette.panel, 0.5f))
        for (i in 0 until n * n) {
            val v = tiles[i]; if (v == 0) continue
            val cx = ox + (i % n) * cell + cell / 2
            val cy = oy + (i / n) * cell + cell / 2
            val sz = cell * 0.9f
            val correct = v == i + 1
            val col = if (correct) Palette.lerp(accent, Palette.mint, 0.3f) else Palette.panelHi
            val pop = 1f - 0.06f * slide[i].coerceIn(0f, 1f)
            Draw.roundRectCentered(c, cx, cy, sz * pop, sz * pop, sz * 0.18f, col)
            Draw.roundRectCentered(c, cx, cy - sz * 0.04f, sz * 0.94f, sz * 0.4f, sz * 0.18f, Palette.alpha(Palette.text, 0.08f))
            Draw.textCentered(c, "$v", cx, cy, cell * 0.42f, if (correct) Palette.bg else Palette.text, Fonts.display)
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val frac = (timeLeft / 90f).coerceIn(0f, 1f)
        Ui.edgeProgress(c, s, frac, Palette.lerp(Palette.red, accent, frac))
        Draw.textCentered(c, "${Loc.t("MOVES")} $moves", s.cx, s.h * 0.84f, s.px(0.04f), Palette.textDim, Fonts.mono)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.115f, s.px(0.055f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "slide_puzzle",
            title = "Slide Puzzle",
            tagline = "Order the tiles",
            category = Category.PUZZLE,
            tier = Tier.MINI,
            accent = Palette.violet,
            icon = IconDrawer { c, cx, cy, size, color ->
                val cell = size * 0.28f
                for (gy in 0 until 2) for (gx in 0 until 2) {
                    if (gx == 1 && gy == 1) continue
                    Draw.roundRectCentered(c, cx + (gx - 0.5f) * cell, cy + (gy - 0.5f) * cell, cell * 0.9f, cell * 0.9f, cell * 0.2f, Palette.alpha(color, 0.7f))
                }
            },
            create = { SlidePuzzleGame() }
        )
    }
}
