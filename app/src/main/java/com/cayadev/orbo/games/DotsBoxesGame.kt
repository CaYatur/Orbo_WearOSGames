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
import com.cayadev.orbo.game.ArcadeGame

/**
 * Dots & Boxes — draw lines to close more boxes than the AI. Tap between two dots to draw an edge;
 * complete a box and you go again. When the grid is full, the bigger score wins, banks points and
 * deals a new board. Lose and the run ends.
 */
class DotsBoxesGame : ArcadeGame(meta()) {

    private val B = 4
    private val drawnH = BooleanArray((B + 1) * B)
    private val drawnV = BooleanArray(B * (B + 1))
    private val owner = IntArray(B * B)
    private var turn = 1
    private var ended = false
    private var endTimer = 0f
    private var aiTimer = 0f
    private var resultWin = 0

    override val controlHint = "TAP BETWEEN DOTS TO DRAW"

    override fun onReset() {
        drawnH.fill(false); drawnV.fill(false); owner.fill(0)
        turn = 1; ended = false; endTimer = 0f; aiTimer = 0f; resultWin = 0
    }

    private fun hIdx(r: Int, c: Int) = r * B + c
    private fun vIdx(r: Int, c: Int) = r * (B + 1) + c
    private fun boxSides(br: Int, bc: Int): Int {
        var n = 0
        if (drawnH[hIdx(br, bc)]) n++
        if (drawnH[hIdx(br + 1, bc)]) n++
        if (drawnV[vIdx(br, bc)]) n++
        if (drawnV[vIdx(br, bc + 1)]) n++
        return n
    }

    private fun allDrawn(): Boolean { for (b in drawnH) if (!b) return false; for (b in drawnV) if (!b) return false; return true }

    private fun claim(who: Int): Int {
        var n = 0
        for (br in 0 until B) for (bc in 0 until B) if (owner[br * B + bc] == 0 && boxSides(br, bc) == 4) { owner[br * B + bc] = who; n++ }
        return n
    }

    private fun playH(r: Int, c: Int, who: Int) { if (drawnH[hIdx(r, c)]) return; drawnH[hIdx(r, c)] = true; finishMove(who) }
    private fun playV(r: Int, c: Int, who: Int) { if (drawnV[vIdx(r, c)]) return; drawnV[vIdx(r, c)] = true; finishMove(who) }

    private fun finishMove(who: Int) {
        val got = claim(who)
        engine.sfx.click(); engine.haptics.light()
        if (got > 0) { engine.sfx.coin(); flash(accent, 0.12f); if (allDrawn()) endGame() /* turn stays who */ }
        else { if (allDrawn()) endGame() else turn = 3 - who }
    }

    private fun endGame() {
        ended = true
        var p = 0; var a = 0
        for (o in owner) { if (o == 1) p++ else if (o == 2) a++ }
        resultWin = if (p > a) 1 else if (a > p) 2 else 0
        if (resultWin == 1) { addScore(120); engine.sfx.success(); engine.haptics.success(); flash(accent, 0.25f) }
        else { engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.25f) }
        endTimer = 2f
    }

    // ---- AI ----
    private fun edgeCompletes(type: Int, r: Int, c: Int): Boolean {
        // does drawing this edge complete a box?
        val before = drawnH.clone() to drawnV.clone()
        if (type == 0) drawnH[hIdx(r, c)] = true else drawnV[vIdx(r, c)] = true
        var done = false
        for (br in 0 until B) for (bc in 0 until B) if (owner[br * B + bc] == 0 && boxSides(br, bc) == 4) done = true
        drawnH.indices.forEach { drawnH[it] = before.first[it] }
        drawnV.indices.forEach { drawnV[it] = before.second[it] }
        return done
    }

    private fun edgeUnsafe(type: Int, r: Int, c: Int): Boolean {
        // would drawing make any box reach 3 sides (handing it to opponent)?
        if (type == 0) drawnH[hIdx(r, c)] = true else drawnV[vIdx(r, c)] = true
        var unsafe = false
        for (br in 0 until B) for (bc in 0 until B) if (owner[br * B + bc] == 0 && boxSides(br, bc) == 3) unsafe = true
        if (type == 0) drawnH[hIdx(r, c)] = false else drawnV[vIdx(r, c)] = false
        return unsafe
    }

    private fun aiMove() {
        val completing = ArrayList<IntArray>(); val safe = ArrayList<IntArray>(); val any = ArrayList<IntArray>()
        for (r in 0..B) for (c in 0 until B) if (!drawnH[hIdx(r, c)]) classify(0, r, c, completing, safe, any)
        for (r in 0 until B) for (c in 0..B) if (!drawnV[vIdx(r, c)]) classify(1, r, c, completing, safe, any)
        val pick = when {
            completing.isNotEmpty() -> completing[Mathx.rand(completing.size)]
            safe.isNotEmpty() -> safe[Mathx.rand(safe.size)]
            any.isNotEmpty() -> any[Mathx.rand(any.size)]
            else -> return
        }
        if (pick[0] == 0) playH(pick[1], pick[2], 2) else playV(pick[1], pick[2], 2)
    }

    private fun classify(type: Int, r: Int, c: Int, completing: ArrayList<IntArray>, safe: ArrayList<IntArray>, any: ArrayList<IntArray>) {
        val e = intArrayOf(type, r, c)
        any.add(e)
        if (edgeCompletes(type, r, c)) completing.add(e)
        else if (!edgeUnsafe(type, r, c)) safe.add(e)
    }

    override fun onStep(dt: Float) {
        if (ended) { endTimer -= dt; if (endTimer <= 0f) { if (resultWin == 1) onReset() else gameOver() }; return }
        if (turn == 2) { aiTimer += dt; if (aiTimer > 0.45f) { aiTimer = 0f; aiMove() } }
    }

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.6f else s.minDim * 0.78f
    private fun left(s: ScreenInfo) = s.cx - side(s) / 2f
    private fun top(s: ScreenInfo) = s.cy - side(s) / 2f + s.h * 0.02f
    private fun gap(s: ScreenInfo) = side(s) / B

    override fun onTapWorld(x: Float, y: Float) {
        if (ended || turn != 1) return
        val s = sc; val g = gap(s); val l = left(s); val t = top(s)
        var bestType = -1; var bestR = 0; var bestC = 0; var bestD = g * 0.45f
        for (r in 0..B) for (c in 0 until B) if (!drawnH[hIdx(r, c)]) {
            val mx = l + (c + 0.5f) * g; val my = t + r * g
            val d = Mathx.dist(x, y, mx, my); if (d < bestD) { bestD = d; bestType = 0; bestR = r; bestC = c }
        }
        for (r in 0 until B) for (c in 0..B) if (!drawnV[vIdx(r, c)]) {
            val mx = l + c * g; val my = t + (r + 0.5f) * g
            val d = Mathx.dist(x, y, mx, my); if (d < bestD) { bestD = d; bestType = 1; bestR = r; bestC = c }
        }
        if (bestType == 0) playH(bestR, bestC, 1) else if (bestType == 1) playV(bestR, bestC, 1)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val g = gap(s); val l = left(s); val t = top(s)
        // owned boxes
        for (br in 0 until B) for (bc in 0 until B) if (owner[br * B + bc] != 0) {
            val col = if (owner[br * B + bc] == 1) Palette.alpha(accent, 0.25f) else Palette.alpha(Palette.red, 0.25f)
            Draw.roundRect(c, l + bc * g + g * 0.12f, t + br * g + g * 0.12f, g * 0.76f, g * 0.76f, g * 0.1f, col)
        }
        // edges
        for (r in 0..B) for (cc in 0 until B) if (drawnH[hIdx(r, cc)])
            Draw.line(c, l + cc * g, t + r * g, l + (cc + 1) * g, t + r * g, accent, s.dp(3f))
        for (r in 0 until B) for (cc in 0..B) if (drawnV[vIdx(r, cc)])
            Draw.line(c, l + cc * g, t + r * g, l + cc * g, t + (r + 1) * g, accent, s.dp(3f))
        // dots
        for (r in 0..B) for (cc in 0..B) Draw.fillCircle(c, l + cc * g, t + r * g, s.px(0.012f), Palette.text)
        // labels
        var p = 0; var a = 0; for (o in owner) { if (o == 1) p++ else if (o == 2) a++ }
        Draw.textCentered(c, "$p", s.cx - s.px(0.16f), s.h * 0.95f, s.px(0.05f), accent, Fonts.display)
        Draw.textCentered(c, "$a", s.cx + s.px(0.16f), s.h * 0.95f, s.px(0.05f), Palette.red, Fonts.display)
        if (ended)
            Draw.textCentered(c, Loc.t(if (resultWin == 1) "YOU WIN" else if (resultWin == 2) "AI WINS" else "DRAW"), s.cx, s.h * 0.88f, s.px(0.05f),
                if (resultWin == 1) accent else Palette.red, Fonts.display)
        else if (turn == 2) Draw.textCentered(c, Loc.t("AI…"), s.cx, s.h * 0.88f, s.px(0.04f), Palette.textDim, Fonts.mono)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.06f, s.px(0.045f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "dots_boxes",
            title = "Dots & Boxes",
            tagline = "Close more boxes",
            category = Category.STRATEGY,
            tier = Tier.MINI,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                val g = size * 0.34f
                for (r in 0..2) for (cc in 0..2) Draw.fillCircle(c, cx + (cc - 1) * g, cy + (r - 1) * g, size * 0.06f, Palette.text)
                Draw.line(c, cx - g, cy - g, cx, cy - g, color, size * 0.05f)
                Draw.line(c, cx - g, cy - g, cx - g, cy, color, size * 0.05f)
                Draw.roundRectCentered(c, cx - g * 0.5f, cy - g * 0.5f, g * 0.5f, g * 0.5f, size * 0.03f, Palette.alpha(color, 0.3f))
            },
            create = { DotsBoxesGame() }
        )
    }
}
