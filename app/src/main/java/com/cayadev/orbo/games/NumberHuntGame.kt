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
 * Number Hunt — a Schulte grid. Tap the numbers in order, 1, 2, 3… as fast as you can. Finish a grid
 * to score and grow the next one; a wrong tap costs time. Race the clock across ever-larger grids to
 * train your peripheral vision and focus.
 */
class NumberHuntGame : ArcadeGame(meta()) {

    private var n = 3
    private lateinit var vals: IntArray
    private var next = 1
    private var timeLeft = 90f
    private var wrongFx = 0f

    override val controlHint = "TAP 1, 2, 3 … IN ORDER"
    override val gameOverTitle = "TIME UP"

    override fun onReset() { n = 3; timeLeft = 90f; newGrid() }

    private fun newGrid() {
        vals = IntArray(n * n) { it + 1 }
        for (i in vals.indices.reversed()) { val j = Mathx.rand(i + 1); val t = vals[i]; vals[i] = vals[j]; vals[j] = t }
        next = 1
    }

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.66f else s.minDim * 0.84f
    private fun left(s: ScreenInfo) = s.cx - side(s) / 2f
    private fun top(s: ScreenInfo) = s.cy - side(s) / 2f + s.h * 0.02f
    private fun cell(s: ScreenInfo) = side(s) / n

    override fun onStep(dt: Float) {
        if (wrongFx > 0f) wrongFx -= dt
        timeLeft -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver() }
    }

    override fun onTapWorld(x: Float, y: Float) {
        val s = sc; val cs = cell(s)
        val c = ((x - left(s)) / cs).toInt(); val r = ((y - top(s)) / cs).toInt()
        if (r !in 0 until n || c !in 0 until n) return
        val idx = r * n + c
        if (vals[idx] == next) {
            next++; addScore(5)
            engine.sfx.coin(); engine.haptics.light()
            particles.burst(left(s) + c * cs + cs / 2, top(s) + r * cs + cs / 2, 8, accent, s.px(0.4f), s.px(0.012f), 0.3f)
            if (next > n * n) {
                addScore(50 + n * 20); timeLeft += 12f
                engine.sfx.success(); engine.haptics.success(); flash(accent, 0.2f)
                n = (n + 1).coerceAtMost(5); newGrid()
            }
        } else {
            timeLeft -= 2f; wrongFx = 0.3f
            engine.sfx.hit(); engine.haptics.medium(); flash(Palette.red, 0.2f)
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
            val done = vals[i] < next
            Draw.roundRect(c, x + s.dp(2f), y + s.dp(2f), cs - s.dp(4f), cs - s.dp(4f), cs * 0.12f,
                if (done) Palette.alpha(accent, 0.18f) else Palette.panelHi)
            Draw.textCentered(c, "${vals[i]}", x + cs / 2, y + cs / 2, cs * 0.42f,
                if (done) Palette.alpha(accent, 0.5f) else Palette.text, Fonts.display)
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        Ui.edgeProgress(c, s, timeLeft / 90f, if (wrongFx > 0f) Palette.red else Palette.lerp(Palette.red, accent, timeLeft / 90f))
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "${next.coerceAtMost(n * n)}→", s.cx, s.h * 0.07f, s.px(0.05f), accent, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "number_hunt",
            title = "Number Hunt",
            tagline = "Find them in order",
            category = Category.BRAIN,
            tier = Tier.MINI,
            accent = Palette.lime,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.textCentered(c, "1", cx - size * 0.25f, cy - size * 0.22f, size * 0.42f, color, Fonts.display)
                Draw.textCentered(c, "2", cx + size * 0.27f, cy + size * 0.05f, size * 0.42f, Palette.alpha(color, 0.7f), Fonts.display)
                Draw.textCentered(c, "3", cx - size * 0.05f, cy + size * 0.28f, size * 0.42f, Palette.alpha(color, 0.5f), Fonts.display)
            },
            create = { NumberHuntGame() }
        )
    }
}
