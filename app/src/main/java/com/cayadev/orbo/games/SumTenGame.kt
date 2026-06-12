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
 * Sum Ten — clear the grid by tapping tiles that add up to exactly ten. Hit ten and they vanish for
 * points and fresh numbers; overshoot and the selection resets. Chain quick tens for combo bonuses.
 * Beat the clock.
 */
class SumTenGame : ArcadeGame(meta()) {

    private val n = 4
    private val vals = IntArray(n * n)
    private val sel = BooleanArray(n * n)
    private var sum = 0
    private var timeLeft = 90f
    private var combo = 0
    private var comboT = 0f

    override val controlHint = "TAP TILES THAT SUM TO 10"
    override val gameOverTitle = "TIME UP"

    override fun onReset() {
        timeLeft = 90f; combo = 0; comboT = 0f; sum = 0
        for (i in 0 until n * n) { vals[i] = Mathx.randRange(1, 9); sel[i] = false }
    }

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.62f else s.minDim * 0.8f
    private fun left(s: ScreenInfo) = s.cx - side(s) / 2f
    private fun top(s: ScreenInfo) = s.cy - side(s) / 2f + s.h * 0.03f
    private fun cell(s: ScreenInfo) = side(s) / n

    override fun onStep(dt: Float) {
        if (comboT > 0f) { comboT -= dt; if (comboT <= 0f) combo = 0 }
        timeLeft -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver() }
    }

    override fun onTapWorld(x: Float, y: Float) {
        val s = sc; val cs = cell(s)
        val c = ((x - left(s)) / cs).toInt(); val r = ((y - top(s)) / cs).toInt()
        if (r !in 0 until n || c !in 0 until n) return
        val idx = r * n + c
        if (sel[idx]) { sel[idx] = false; sum -= vals[idx]; engine.sfx.click(); return }
        sel[idx] = true; sum += vals[idx]
        engine.sfx.click(); engine.haptics.tick()
        if (sum == 10) {
            combo++; comboT = 2f
            var cleared = 0
            for (i in 0 until n * n) if (sel[i]) { vals[i] = Mathx.randRange(1, 9); sel[i] = false; cleared++
                particles.burst(left(s) + (i % n) * cs + cs / 2, top(s) + (i / n) * cs + cs / 2, 8, accent, s.px(0.5f), s.px(0.012f), 0.35f) }
            addScore(cleared * 10 * (1 + combo / 3))
            sum = 0; engine.sfx.success(); engine.haptics.success(); flash(accent, 0.15f)
        } else if (sum > 10) {
            for (i in 0 until n * n) sel[i] = false
            sum = 0; combo = 0
            engine.sfx.hit(); engine.haptics.medium(); flash(Palette.red, 0.15f)
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
            val on = sel[i]
            if (on) Draw.glow(c, x + cs / 2, y + cs / 2, cs * 0.5f, accent, 0.3f)
            Draw.roundRect(c, x + s.dp(2f), y + s.dp(2f), cs - s.dp(4f), cs - s.dp(4f), cs * 0.14f, if (on) accent else Palette.panelHi)
            Draw.textCentered(c, "${vals[i]}", x + cs / 2, y + cs / 2, cs * 0.46f, if (on) Palette.bg else Palette.text, Fonts.display)
        }
        // sum indicator
        val col = if (sum == 10) accent else if (sum > 7) Palette.amber else Palette.textDim
        Draw.textCentered(c, "$sum", s.cx, s.h * 0.92f, s.px(0.07f), col, Fonts.display)
        if (combo > 1) Draw.textCentered(c, "x$combo", s.cx + s.px(0.18f), s.h * 0.92f, s.px(0.045f), accent, Fonts.bodyBold)
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        Ui.edgeProgress(c, s, timeLeft / 90f, Palette.lerp(Palette.red, accent, timeLeft / 90f))
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.08f, s.px(0.05f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "sum_ten",
            title = "Sum Ten",
            tagline = "Add up to ten",
            category = Category.BRAIN,
            tier = Tier.MINI,
            accent = Palette.amber,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx - size * 0.24f, cy, size * 0.34f, size * 0.34f, size * 0.07f, color)
                Draw.textCentered(c, "6", cx - size * 0.24f, cy, size * 0.24f, Palette.bg, Fonts.display)
                Draw.textCentered(c, "+", cx, cy, size * 0.2f, color, Fonts.display)
                Draw.roundRectCentered(c, cx + size * 0.26f, cy, size * 0.34f, size * 0.34f, size * 0.07f, Palette.alpha(color, 0.6f))
                Draw.textCentered(c, "4", cx + size * 0.26f, cy, size * 0.24f, Palette.bg, Fonts.display)
            },
            create = { SumTenGame() }
        )
    }
}
