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
 * Memory Matrix — a flash of lit tiles, then reproduce the pattern from memory. Get them all to grow
 * the grid and the pattern; a wrong tile costs a life. Three lives. Trains spatial recall.
 */
class MemoryMatrixGame : ArcadeGame(meta()) {

    private enum class Phase { SHOW, RECALL }
    private var phase = Phase.SHOW
    private var n = 3
    private lateinit var lit: BooleanArray
    private lateinit var found: BooleanArray
    private var showT = 0f
    private var lives = 3
    private var litCount = 0
    private var foundCount = 0
    private var level = 0
    private var wrongIdx = -1
    private var wrongFx = 0f

    override val controlHint = "MEMORISE THEN TAP THE TILES"

    override fun onReset() { n = 3; level = 0; lives = 3; newRound() }

    private fun newRound() {
        lit = BooleanArray(n * n); found = BooleanArray(n * n)
        litCount = (n + level / 2 + 1).coerceAtMost(n * n - 1)
        val idxs = (0 until n * n).toMutableList()
        for (i in idxs.indices.reversed()) { val j = Mathx.rand(i + 1); val t = idxs[i]; idxs[i] = idxs[j]; idxs[j] = t }
        for (k in 0 until litCount) lit[idxs[k]] = true
        foundCount = 0; phase = Phase.SHOW; showT = 1.1f + litCount * 0.12f; wrongIdx = -1
    }

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.62f else s.minDim * 0.8f
    private fun left(s: ScreenInfo) = s.cx - side(s) / 2f
    private fun top(s: ScreenInfo) = s.cy - side(s) / 2f + s.h * 0.02f
    private fun cell(s: ScreenInfo) = side(s) / n

    override fun onStep(dt: Float) {
        if (wrongFx > 0f) wrongFx -= dt
        if (phase == Phase.SHOW) { showT -= dt; if (showT <= 0f) phase = Phase.RECALL }
    }

    override fun onTapWorld(x: Float, y: Float) {
        if (phase != Phase.RECALL) return
        val s = sc; val cs = cell(s)
        val c = ((x - left(s)) / cs).toInt(); val r = ((y - top(s)) / cs).toInt()
        if (r !in 0 until n || c !in 0 until n) return
        val idx = r * n + c
        if (found[idx]) return
        if (lit[idx]) {
            found[idx] = true; foundCount++
            addScore(10); engine.sfx.coin(); engine.haptics.light()
            particles.burst(left(s) + c * cs + cs / 2, top(s) + r * cs + cs / 2, 8, accent, s.px(0.4f), s.px(0.012f), 0.3f)
            if (foundCount >= litCount) {
                addScore(40 + level * 10)
                engine.sfx.success(); engine.haptics.success(); flash(accent, 0.2f)
                level++
                if (level % 3 == 0 && n < 5) n++
                newRound()
            }
        } else {
            lives--; wrongIdx = idx; wrongFx = 0.4f
            engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.25f); shake(s.px(0.012f))
            if (lives <= 0) gameOver()
            else { phase = Phase.SHOW; showT = 0.9f }   // brief re-show as a mercy
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val cs = cell(s); val l = left(s); val t = top(s)
        for (i in 0 until n * n) {
            val r = i / n; val col = i % n
            val x = l + col * cs; val y = t + r * cs
            val showLit = (phase == Phase.SHOW && lit[i]) || found[i]
            val isWrong = wrongIdx == i && wrongFx > 0f
            val color = when { isWrong -> Palette.red; showLit -> accent; else -> Palette.panelHi }
            if (showLit) Draw.glow(c, x + cs / 2, y + cs / 2, cs * 0.5f, accent, 0.3f)
            Draw.roundRect(c, x + s.dp(2f), y + s.dp(2f), cs - s.dp(4f), cs - s.dp(4f), cs * 0.14f, color)
        }
        if (phase == Phase.SHOW)
            Draw.textCentered(c, Loc.t("WATCH"), s.cx, s.h * 0.93f, s.px(0.04f), Palette.textDim, Fonts.mono, letterSpacing = 0.18f)
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.05f); val ly = s.h * 0.1f; val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, ly, s.px(0.015f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, ly, s.px(0.013f), Palette.alpha(Palette.red, 0.3f), s.dp(1.2f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.05f, s.px(0.045f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "memory_matrix",
            title = "Memory Matrix",
            tagline = "Recall the pattern",
            category = Category.BRAIN,
            tier = Tier.MINI,
            accent = Palette.mint,
            icon = IconDrawer { c, cx, cy, size, color ->
                val u = size * 0.28f
                val pat = arrayOf(intArrayOf(1,0,1), intArrayOf(0,1,0), intArrayOf(1,0,0))
                for (r in 0 until 3) for (cc in 0 until 3)
                    Draw.roundRectCentered(c, cx + (cc - 1) * u, cy + (r - 1) * u, u * 0.86f, u * 0.86f, u * 0.18f,
                        if (pat[r][cc] == 1) color else Palette.alpha(color, 0.18f))
            },
            create = { MemoryMatrixGame() }
        )
    }
}
