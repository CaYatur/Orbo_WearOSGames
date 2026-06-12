package com.cayadev.orbo.games

import android.graphics.Canvas
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.IconDrawer
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Loc
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame

/**
 * Peg Jump — the classic cross-board solitaire. Tap a peg, then an empty hole two steps away to jump
 * over (and remove) the peg between. Clear as many as you can; finishing on a single peg is the holy
 * grail. The board ends when no jumps remain — your score is pegs removed.
 */
class PegSolitaireGame : ArcadeGame(meta()) {

    private val N = 7
    private val peg = Array(N) { BooleanArray(N) }
    private var selected = -1
    private var removed = 0

    override val controlHint = "TAP PEG · TAP HOLE TO JUMP"
    override val gameOverTitle = "NO MOVES"

    private fun valid(r: Int, c: Int): Boolean {
        if (r < 0 || c < 0 || r >= N || c >= N) return false
        val cornerR = r < 2 || r > 4; val cornerC = c < 2 || c > 4
        return !(cornerR && cornerC)
    }

    override fun onReset() {
        removed = 0; selected = -1
        for (r in 0 until N) for (c in 0 until N) peg[r][c] = valid(r, c)
        peg[3][3] = false
    }

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.68f else s.minDim * 0.86f
    private fun cell(s: ScreenInfo) = side(s) / N
    private fun left(s: ScreenInfo) = s.cx - side(s) / 2f
    private fun top(s: ScreenInfo) = s.cy - side(s) / 2f
    private fun cx(s: ScreenInfo, c: Int) = left(s) + cell(s) * (c + 0.5f)
    private fun cy(s: ScreenInfo, r: Int) = top(s) + cell(s) * (r + 0.5f)

    override fun onStep(dt: Float) {}

    override fun onTapWorld(x: Float, y: Float) {
        val s = sc; val cs = cell(s)
        val c = ((x - left(s)) / cs).toInt(); val r = ((y - top(s)) / cs).toInt()
        if (!valid(r, c)) return
        if (selected < 0) {
            if (peg[r][c]) { selected = r * N + c; engine.sfx.blip(); engine.haptics.light() }
            return
        }
        val sr = selected / N; val scc = selected % N
        if (r == sr && c == scc) { selected = -1; engine.sfx.click(); return }
        // attempt jump
        val dr = r - sr; val dc = c - scc
        if (((dr == 0 && kotlin.math.abs(dc) == 2) || (dc == 0 && kotlin.math.abs(dr) == 2)) && !peg[r][c]) {
            val mr = sr + dr / 2; val mc = scc + dc / 2
            if (peg[mr][mc]) {
                peg[sr][scc] = false; peg[mr][mc] = false; peg[r][c] = true
                removed++; addScoreTo()
                engine.sfx.coin(); engine.haptics.medium()
                particles.burst(cx(s, mc), cy(s, mr), 10, accent, s.px(0.5f), s.px(0.014f), 0.4f)
                selected = -1
                checkEnd()
                return
            }
        }
        // re-select another peg
        if (peg[r][c]) { selected = r * N + c; engine.sfx.blip() } else { selected = -1 }
    }

    private fun addScoreTo() { score = removed * 10 }

    private fun countPegs(): Int { var n = 0; for (r in 0 until N) for (c in 0 until N) if (peg[r][c]) n++; return n }

    private fun anyMove(): Boolean {
        for (r in 0 until N) for (c in 0 until N) if (peg[r][c]) {
            val dirs = arrayOf(intArrayOf(0, 2), intArrayOf(0, -2), intArrayOf(2, 0), intArrayOf(-2, 0))
            for (d in dirs) {
                val nr = r + d[0]; val nc = c + d[1]; val mr = r + d[0] / 2; val mc = c + d[1] / 2
                if (valid(nr, nc) && !peg[nr][nc] && peg[mr][mc]) return true
            }
        }
        return false
    }

    private fun checkEnd() {
        if (countPegs() == 1) {
            score = removed * 10 + 500
            engine.sfx.success(); engine.haptics.success(); flash(accent, 0.3f)
            gameOver()
        } else if (!anyMove()) {
            engine.sfx.fail(); gameOver()
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val cs = cell(s); val pr = cs * 0.34f
        for (r in 0 until N) for (col in 0 until N) if (valid(r, col)) {
            val x = cx(s, col); val y = cy(s, r)
            Draw.fillCircle(c, x, y, pr * 0.7f, Palette.panel)
            Draw.strokeCircle(c, x, y, pr * 0.7f, Palette.alpha(accent, 0.18f), s.dp(1.2f))
            if (peg[r][col]) {
                val sel = selected == r * N + col
                if (sel) Draw.glow(c, x, y, pr * 1.6f, accent, 0.4f)
                Draw.fillCircle(c, x, y, pr, if (sel) accent else Palette.lerp(accent, Palette.text, 0.1f))
                Draw.fillCircle(c, x - pr * 0.3f, y - pr * 0.3f, pr * 0.3f, Palette.alpha(Palette.text, 0.3f))
            }
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        Draw.textCentered(c, "${countPegs()}", s.cx, s.h * 0.92f, s.px(0.045f), Palette.textDim, Fonts.mono)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.08f, s.px(0.055f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "peg_jump",
            title = "Peg Jump",
            tagline = "Leave one peg",
            category = Category.PUZZLE,
            tier = Tier.MINI,
            accent = Palette.amber,
            icon = IconDrawer { c, cx, cy, size, color ->
                val u = size * 0.26f
                for (r in -1..1) for (cc in -1..1) {
                    val filled = !(r == 0 && cc == 0)
                    if (filled) Draw.fillCircle(c, cx + cc * u, cy + r * u, u * 0.34f, color)
                    else Draw.strokeCircle(c, cx + cc * u, cy + r * u, u * 0.34f, Palette.alpha(color, 0.4f), size * 0.03f)
                }
            },
            create = { PegSolitaireGame() }
        )
    }
}
