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

/**
 * Pairs — a concentration / memory-match board. Flip two cards; matching symbols stay, mismatches
 * flip back. Clear the board to spawn a fresh, harder one. Race the 75-second clock.
 */
class PairsGame : ArcadeGame(meta()) {

    private val cols = 4
    private val rows = 4
    private val n = cols * rows
    private val glyphs = arrayOf("●", "■", "▲", "◆", "★", "✚", "✦", "☾")
    private val cols8 = intArrayOf(
        Palette.mint, Palette.pink, Palette.amber, Palette.cyan,
        Palette.violet, Palette.lime, Palette.orange, Palette.blue
    )

    private val symbol = IntArray(n)
    private val faceUp = BooleanArray(n)
    private val matched = BooleanArray(n)
    private val flipFx = FloatArray(n)
    private var first = -1
    private var second = -1
    private var waitT = 0f
    private var boards = 0
    private var combo = 0
    private var timeLeft = 75f

    override val controlHint = "FLIP TWO TO MATCH"
    override val gameOverTitle = "TIME UP"

    override fun onReset() { boards = 0; combo = 0; timeLeft = 75f; newBoard() }

    private fun newBoard() {
        val ids = ArrayList<Int>()
        for (i in 0 until n / 2) { ids.add(i); ids.add(i) }
        // Fisher–Yates shuffle
        for (i in ids.indices.reversed()) { val j = Mathx.rand(i + 1); val t = ids[i]; ids[i] = ids[j]; ids[j] = t }
        for (i in 0 until n) { symbol[i] = ids[i]; faceUp[i] = false; matched[i] = false; flipFx[i] = 0f }
        first = -1; second = -1; waitT = 0f
    }

    private fun boardSide(s: ScreenInfo) = if (s.isRound) s.minDim * 0.70f else s.minDim * 0.84f
    private fun boardTop(s: ScreenInfo) = s.h * 0.55f - boardSide(s) / 2f
    private fun cell(s: ScreenInfo) = boardSide(s) / cols

    override fun onStep(dt: Float) {
        timeLeft -= dt
        for (i in 0 until n) if (flipFx[i] > 0f) flipFx[i] -= dt
        if (waitT > 0f) {
            waitT -= dt
            if (waitT <= 0f) { faceUp[first] = false; faceUp[second] = false; first = -1; second = -1 }
        }
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver() }
    }

    override fun onTapWorld(x: Float, y: Float) {
        if (waitT > 0f) return
        val s = sc; val cs = cell(s); val x0 = s.cx - boardSide(s) / 2f; val y0 = boardTop(s)
        val col = ((x - x0) / cs).toInt(); val row = ((y - y0) / cs).toInt()
        if (col !in 0 until cols || row !in 0 until rows) return
        val idx = row * cols + col
        if (matched[idx] || faceUp[idx]) return
        faceUp[idx] = true; flipFx[idx] = 0.3f; engine.sfx.blip(); engine.haptics.light()
        if (first == -1) { first = idx; return }
        second = idx
        if (symbol[first] == symbol[second]) {
            matched[first] = true; matched[second] = true
            combo++; addScore(20 + combo * 5)
            engine.sfx.coin(); engine.haptics.medium()
            val cx = x0 + col * cs + cs / 2; val cy = y0 + row * cs + cs / 2
            particles.burst(cx, cy, 16, cols8[symbol[idx]], sc.px(0.6f), sc.px(0.02f), 0.5f)
            first = -1; second = -1
            if ((0 until n).all { matched[it] }) {            // board cleared
                boards++; addScore(120); timeLeft += 8f
                engine.sfx.success(); engine.haptics.success(); flash(Palette.mint, 0.25f)
                newBoard()
            }
        } else {
            combo = 0; waitT = 0.75f; engine.sfx.hit()
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val cs = cell(s); val x0 = s.cx - boardSide(s) / 2f; val y0 = boardTop(s)
        val pad = cs * 0.10f
        for (i in 0 until n) {
            val col = i % cols; val row = i / cols
            val cx = x0 + col * cs + cs / 2; val cy = y0 + row * cs + cs / 2
            val side = cs - pad * 2
            val pop = 1f + 0.12f * flipFx[i].coerceIn(0f, 1f)
            if (matched[i]) {
                Draw.roundRectCentered(c, cx, cy, side, side, side * 0.22f, Palette.alpha(cols8[symbol[i]], 0.18f))
                Draw.textCentered(c, glyphs[symbol[i]], cx, cy, side * 0.5f, Palette.alpha(cols8[symbol[i]], 0.5f), Fonts.display)
            } else if (faceUp[i]) {
                Draw.roundRectCentered(c, cx, cy, side * pop, side * pop, side * 0.22f, Palette.alpha(cols8[symbol[i]], 0.92f))
                Draw.textCentered(c, glyphs[symbol[i]], cx, cy, side * 0.55f, Palette.bg, Fonts.display)
            } else {
                Draw.roundRectCentered(c, cx, cy, side, side, side * 0.22f, Palette.panelHi)
                Draw.roundRectStroke(c, cx - side / 2, cy - side / 2, side, side, side * 0.22f, Palette.alpha(Palette.line, 0.8f), s.dp(1.4f))
                Draw.fillCircle(c, cx, cy, side * 0.10f, Palette.alpha(accent, 0.4f))
            }
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val barW = s.px(0.4f); val frac = (timeLeft / 75f).coerceIn(0f, 1f)
        Draw.roundRect(c, s.cx - barW / 2, s.h * 0.20f, barW, s.dp(5f), s.dp(3f), Palette.track)
        Draw.roundRect(c, s.cx - barW / 2, s.h * 0.20f, barW * frac, s.dp(5f), s.dp(3f), Palette.lerp(Palette.red, accent, frac))
        if (combo > 1) Draw.textCentered(c, "x$combo", s.cx, s.h * 0.88f, s.px(0.045f), accent, Fonts.bodyBold)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.125f, s.px(0.065f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "pairs",
            title = "Pairs",
            tagline = "Match the symbols",
            category = Category.BRAIN,
            tier = Tier.MINI,
            accent = Palette.amber,
            icon = IconDrawer { c, cx, cy, size, color ->
                val u = size * 0.26f
                Draw.roundRectCentered(c, cx - u * 0.7f, cy - u * 0.7f, u, u, u * 0.2f, color)
                Draw.roundRectCentered(c, cx + u * 0.7f, cy + u * 0.7f, u, u, u * 0.2f, color)
                Draw.roundRectCentered(c, cx + u * 0.7f, cy - u * 0.7f, u, u, u * 0.2f, Palette.alpha(color, 0.35f))
                Draw.roundRectCentered(c, cx - u * 0.7f, cy + u * 0.7f, u, u, u * 0.2f, Palette.alpha(color, 0.35f))
            },
            create = { PairsGame() }
        )
    }
}
