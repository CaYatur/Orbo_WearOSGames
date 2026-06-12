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
 * Water Sort — pour the liquids until each tube holds a single colour. Tap a tube to pick it up, tap
 * another to pour the top colour across (only onto a match or an empty tube). Sort the set to score
 * and gain time; the puzzles grow more colourful as you go. Beat the clock.
 */
class WaterSortGame : ArcadeGame(meta()) {

    private val cap = 4
    private var colorsN = 3
    private val tubes = ArrayList<ArrayList<Int>>()
    private var selected = -1
    private var timeLeft = 150f
    private var solvedCount = 0

    override val controlHint = "TAP TUBE · TAP TO POUR"
    override val gameOverTitle = "TIME UP"

    override fun onReset() { colorsN = 3; timeLeft = 150f; solvedCount = 0; build() }

    private fun build() {
        tubes.clear(); selected = -1
        val tokens = ArrayList<Int>()
        for (c in 0 until colorsN) repeat(cap) { tokens.add(c) }
        do {
            for (i in tokens.indices.reversed()) { val j = Mathx.rand(i + 1); val t = tokens[i]; tokens[i] = tokens[j]; tokens[j] = t }
        } while (alreadySorted(tokens))
        for (c in 0 until colorsN) { val t = ArrayList<Int>(); for (k in 0 until cap) t.add(tokens[c * cap + k]); tubes.add(t) }
        repeat(2) { tubes.add(ArrayList()) }
    }

    private fun alreadySorted(tokens: List<Int>): Boolean {
        for (c in 0 until colorsN) {
            val base = tokens[c * cap]
            for (k in 1 until cap) if (tokens[c * cap + k] != base) return false
        }
        return true
    }

    private fun topRun(t: ArrayList<Int>): Int {
        if (t.isEmpty()) return 0
        val col = t.last(); var n = 0
        for (i in t.indices.reversed()) { if (t[i] == col) n++ else break }
        return n
    }

    private fun pour(from: Int, to: Int) {
        val a = tubes[from]; val b = tubes[to]
        if (a.isEmpty() || from == to) return
        val col = a.last()
        if (b.isNotEmpty() && b.last() != col) { engine.sfx.fail(); engine.haptics.medium(); return }
        var moveN = topRun(a).coerceAtMost(cap - b.size)
        if (moveN <= 0) { engine.sfx.fail(); engine.haptics.medium(); return }
        repeat(moveN) { b.add(a.removeAt(a.size - 1)) }
        engine.sfx.blip(); engine.haptics.light()
        checkSolved()
    }

    private fun tubeSorted(t: ArrayList<Int>) = t.isEmpty() || (t.size == cap && t.all { it == t[0] })

    private fun checkSolved() {
        if (tubes.all { tubeSorted(it) }) {
            solvedCount++
            addScore(100 + colorsN * 30); timeLeft += 25f
            engine.sfx.success(); engine.haptics.success(); flash(accent, 0.25f)
            colorsN = (3 + solvedCount / 2).coerceAtMost(6)
            build()
        }
    }

    override fun onStep(dt: Float) {
        timeLeft -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver() }
    }

    private fun cols(): Int = if (tubes.size <= 4) tubes.size else (tubes.size + 1) / 2
    private fun rowsN(): Int = if (tubes.size <= 4) 1 else 2
    private fun tubeW(s: ScreenInfo) = (if (s.isRound) s.minDim * 0.62f else s.minDim * 0.82f) / cols()
    private fun tubeH(s: ScreenInfo) = s.px(0.34f)

    private fun tubePos(s: ScreenInfo, i: Int): FloatArray {
        val row = if (rowsN() == 1) 0 else i / cols()
        val col = if (rowsN() == 1) i else i % cols()
        val tw = tubeW(s)
        val startX = s.cx - (cols() * tw) / 2f + tw / 2f
        val baseY = if (rowsN() == 1) s.cy else s.cy + (row - 0.5f) * (tubeH(s) + s.px(0.06f))
        return floatArrayOf(startX + col * tw, baseY)
    }

    override fun onTapWorld(x: Float, y: Float) {
        val s = sc
        var hit = -1
        for (i in tubes.indices) {
            val p = tubePos(s, i)
            if (kotlin.math.abs(x - p[0]) < tubeW(s) * 0.45f && kotlin.math.abs(y - p[1]) < tubeH(s) * 0.6f) { hit = i; break }
        }
        if (hit < 0) return
        if (selected < 0) { if (tubes[hit].isNotEmpty()) { selected = hit; engine.sfx.click() } }
        else if (selected == hit) { selected = -1; engine.sfx.click() }
        else { pour(selected, hit); selected = -1 }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val tw = tubeW(s); val th = tubeH(s); val segH = th / cap
        for (i in tubes.indices) {
            val p = tubePos(s, i); val cxp = p[0]; val cyp = p[1]
            val lift = if (selected == i) -s.px(0.04f) else 0f
            val topY = cyp - th / 2f + lift; val w = tw * 0.7f
            // glass
            Draw.roundRect(c, cxp - w / 2, topY, w, th, w * 0.25f, Palette.alpha(Palette.panelHi, 0.5f))
            // liquid segments (bottom up)
            val t = tubes[i]
            for (k in t.indices) {
                val segY = topY + th - (k + 1) * segH
                val col = Palette.accents[t[k] % Palette.accents.size]
                val r = if (k == 0) w * 0.22f else 0f
                Draw.roundRect(c, cxp - w / 2 + s.dp(2f), segY, w - s.dp(4f), segH - s.dp(1f), r, col)
            }
            Draw.roundRectStroke(c, cxp - w / 2, topY, w, th, w * 0.25f, Palette.alpha(if (selected == i) accent else Palette.line, 0.9f), s.dp(2f))
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        Ui.edgeProgress(c, s, timeLeft / 150f, Palette.lerp(Palette.red, accent, timeLeft / 150f))
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.1f, s.px(0.055f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "water_sort",
            title = "Water Sort",
            tagline = "Sort the colours",
            category = Category.PUZZLE,
            tier = Tier.BIG,
            accent = Palette.cyan,
            featured = true,
            icon = IconDrawer { c, cx, cy, size, _ ->
                val cols = intArrayOf(Palette.pink, Palette.amber, Palette.mint)
                for (t in 0 until 2) {
                    val x = cx + (t - 0.5f) * size * 0.5f
                    Draw.roundRect(c, x - size * 0.16f, cy - size * 0.4f, size * 0.32f, size * 0.8f, size * 0.1f, Palette.alpha(Palette.panelHi, 0.6f))
                    for (k in 0 until 3) Draw.roundRect(c, x - size * 0.13f, cy + size * 0.4f - (k + 1) * size * 0.22f, size * 0.26f, size * 0.2f, 0f, cols[(t + k) % 3])
                }
            },
            create = { WaterSortGame() }
        )
    }
}
