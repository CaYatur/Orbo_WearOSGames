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

/** Number Order — a Schulte-style focus drill: tap 1→9 in order, again and again, against the
 * clock. Wrong taps cost time. How many can you clear in 40 seconds? */
class NumberOrderGame : ArcadeGame(meta()) {

    private val n = 9            // 3x3
    private val values = IntArray(n)
    private val tapped = BooleanArray(n)
    private var need = 1
    private var timeLeft = 40f
    private val wrongFx = FloatArray(n)
    private var boards = 0

    override val controlHint = "TAP 1 TO 9 IN ORDER"
    override val gameOverTitle = "TIME UP"

    override fun onReset() { timeLeft = 40f; boards = 0; shuffleBoard() }

    private fun shuffleBoard() {
        for (i in 0 until n) values[i] = i + 1
        for (i in n - 1 downTo 1) { val j = Mathx.rand(i + 1); val tmp = values[i]; values[i] = values[j]; values[j] = tmp }
        for (i in 0 until n) { tapped[i] = false; wrongFx[i] = 0f }
        need = 1
    }

    private fun cellGeom(s: ScreenInfo): FloatArray {
        val size = s.px(0.66f)
        val x0 = s.cx - size / 2; val y0 = s.cy - size / 2
        return floatArrayOf(x0, y0, size / 3f)
    }

    override fun onStep(dt: Float) {
        timeLeft -= dt
        for (i in 0 until n) if (wrongFx[i] > 0f) wrongFx[i] -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver() }
    }

    override fun onTapWorld(x: Float, y: Float) {
        val g = cellGeom(sc); val cell = g[2]
        val cx = ((x - g[0]) / cell).toInt(); val cy = ((y - g[1]) / cell).toInt()
        if (cx !in 0..2 || cy !in 0..2) return
        val idx = cy * 3 + cx
        if (tapped[idx]) return
        if (values[idx] == need) {
            tapped[idx] = true; need++
            addScore(5); engine.sfx.blip(); engine.haptics.light()
            val px = g[0] + cx * cell + cell / 2; val py = g[1] + cy * cell + cell / 2
            particles.burst(px, py, 8, accent, sc.px(0.4f), sc.px(0.012f), 0.35f)
            if (need > n) { boards++; addScore(20); engine.sfx.coin(); shuffleBoard() }
        } else {
            wrongFx[idx] = 0.3f; timeLeft = (timeLeft - 1.5f).coerceAtLeast(0f)
            engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.2f)
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val g = cellGeom(s); val x0 = g[0]; val y0 = g[1]; val cell = g[2]
        for (i in 0 until n) {
            val cx = x0 + (i % 3) * cell + cell / 2
            val cy = y0 + (i / 3) * cell + cell / 2
            val sz = cell * 0.86f
            val done = tapped[i]
            val isNext = values[i] == need
            val baseCol = when {
                done -> Palette.alpha(accent, 0.2f)
                wrongFx[i] > 0f -> Palette.alpha(Palette.red, 0.5f)
                else -> Palette.panelHi
            }
            Draw.roundRectCentered(c, cx, cy, sz, sz, sz * 0.2f, baseCol)
            if (isNext && !done) Draw.roundRectStroke(c, cx - sz / 2, cy - sz / 2, sz, sz, sz * 0.2f, Palette.alpha(accent, 0.6f), s.dp(2.5f))
            if (!done) Draw.textCentered(c, "${values[i]}", cx, cy, cell * 0.42f, if (isNext) accent else Palette.text, Fonts.display)
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val frac = (timeLeft / 40f).coerceIn(0f, 1f)
        Ui.edgeProgress(c, s, frac, Palette.lerp(Palette.red, accent, frac))
        Draw.textCentered(c, "${Loc.t("NEXT")}  $need", s.cx, s.h * 0.84f, s.px(0.05f), accent, Fonts.mono)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.13f, s.px(0.06f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "number_order",
            title = "Number Rush",
            tagline = "Tap 1-9 fast",
            category = Category.BRAIN,
            tier = Tier.MINI,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx, cy, size * 0.62f, size * 0.62f, size * 0.12f, Palette.alpha(color, 0.18f))
                Draw.textCentered(c, "1", cx - size * 0.16f, cy - size * 0.14f, size * 0.28f, color, Fonts.display)
                Draw.textCentered(c, "2", cx + size * 0.16f, cy - size * 0.14f, size * 0.28f, Palette.textDim, Fonts.display)
                Draw.textCentered(c, "3", cx, cy + size * 0.16f, size * 0.28f, Palette.textDim, Fonts.display)
            },
            create = { NumberOrderGame() }
        )
    }
}
