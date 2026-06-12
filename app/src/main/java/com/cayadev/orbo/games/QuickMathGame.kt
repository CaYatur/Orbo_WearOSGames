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

/** Quick Math — solve the equation and tap the right answer before the bar empties. Speeds up,
 * mixes in subtraction and multiplication, and rewards combos. */
class QuickMathGame : ArcadeGame(meta()) {

    private var prompt = ""
    private val opts = IntArray(4)
    private var correct = 0
    private var timeLimit = 4f
    private var timeLeft = 4f
    private var combo = 0
    private var lives = 3
    private var picked = -1
    private var pickT = 0f

    override val controlHint = "TAP THE ANSWER"

    override fun onReset() { combo = 0; lives = 3; nextQuestion() }

    private fun nextQuestion() {
        val diff = (score / 400f).coerceIn(0f, 1f)
        timeLimit = Mathx.lerp(4f, 1.8f, diff)
        timeLeft = timeLimit; picked = -1
        val maxN = (8 + diff * 30).toInt()
        val op = if (diff > 0.5f) Mathx.rand(3) else Mathx.rand(2)
        val a: Int; val b: Int; val ans: Int; val sym: String
        when (op) {
            0 -> { a = Mathx.rand(maxN) + 1; b = Mathx.rand(maxN) + 1; ans = a + b; sym = "+" }
            1 -> { val x = Mathx.rand(maxN) + 1; val y = Mathx.rand(maxN) + 1; val hi = maxOf(x, y); val lo = minOf(x, y); a = hi; b = lo; ans = hi - lo; sym = "−" }
            else -> { a = Mathx.rand(9) + 2; b = Mathx.rand(9) + 2; ans = a * b; sym = "×" }
        }
        prompt = "$a $sym $b"
        correct = Mathx.rand(4)
        val used = HashSet<Int>(); used.add(ans)
        for (i in 0 until 4) {
            if (i == correct) { opts[i] = ans; continue }
            var w = ans + (Mathx.rand(13) - 6)
            var guard = 0
            while ((w < 0 || used.contains(w)) && guard++ < 20) w = ans + (Mathx.rand(19) - 9)
            used.add(w); opts[i] = w
        }
    }

    override fun onStep(dt: Float) {
        if (picked >= 0) { pickT += dt; if (pickT > 0.2f) nextQuestion(); return }
        timeLeft -= dt
        if (timeLeft <= 0f) loseLife()
    }

    private fun loseLife() {
        lives--; combo = 0; engine.sfx.hit(); engine.haptics.medium(); flash(Palette.red, 0.3f); shake(sc.px(0.015f))
        if (lives <= 0) gameOver() else nextQuestion()
    }

    private fun btn(s: ScreenInfo, slot: Int): FloatArray {
        val sz = s.px(0.32f); val gap = s.px(0.03f)
        val col = slot % 2; val row = slot / 2
        val cx = s.cx + (col - 0.5f) * (sz + gap)
        val cy = s.h * 0.62f + (row - 0.5f) * (sz * 0.62f + gap)
        return floatArrayOf(cx, cy, sz, sz * 0.6f)
    }

    override fun onTapWorld(x: Float, y: Float) {
        if (picked >= 0) return
        for (slot in 0 until 4) {
            val b = btn(sc, slot)
            if (x in (b[0] - b[2] / 2)..(b[0] + b[2] / 2) && y in (b[1] - b[3] / 2)..(b[1] + b[3] / 2)) {
                picked = slot; pickT = 0f
                if (slot == correct) {
                    combo++; addScore(20 + combo * 4); engine.sfx.coin(); engine.haptics.light()
                    particles.burst(b[0], b[1], 14, accent, sc.px(0.5f), sc.px(0.016f), 0.4f)
                } else loseLife()
                return
            }
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return

        Draw.textCentered(c, prompt, s.cx, s.h * 0.30f, s.px(0.12f), Palette.text, Fonts.display, letterSpacing = 0.02f)
        Draw.textCentered(c, "=", s.cx, s.h * 0.40f, s.px(0.06f), Palette.textDim, Fonts.display)

        // timer bar
        val frac = (timeLeft / timeLimit).coerceIn(0f, 1f)
        val barW = s.px(0.4f)
        Draw.roundRect(c, s.cx - barW / 2, s.h * 0.45f, barW, s.dp(5f), s.dp(3f), Palette.track)
        Draw.roundRect(c, s.cx - barW / 2, s.h * 0.45f, barW * frac, s.dp(5f), s.dp(3f), Palette.lerp(Palette.red, accent, frac))

        for (slot in 0 until 4) {
            val b = btn(s, slot)
            val sel = picked == slot; val isCorrect = slot == correct
            val col = when {
                picked >= 0 && isCorrect -> Palette.mint
                sel && !isCorrect -> Palette.red
                else -> Palette.panelHi
            }
            Draw.roundRectCentered(c, b[0], b[1], b[2], b[3], b[3] * 0.3f, col)
            Draw.roundRectStroke(c, b[0] - b[2] / 2, b[1] - b[3] / 2, b[2], b[3], b[3] * 0.3f, Palette.alpha(accent, 0.25f), s.dp(1.5f))
            Draw.textCentered(c, "${opts[slot]}", b[0], b[1], b[3] * 0.5f, Palette.text, Fonts.display)
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.05f); val y = s.h * 0.20f; val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, y, s.px(0.017f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, y, s.px(0.015f), Palette.alpha(Palette.red, 0.35f), s.dp(1.5f))
        }
        if (combo > 1) Draw.textCentered(c, "x$combo", s.cx, s.h * 0.86f, s.px(0.045f), accent, Fonts.bodyBold)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.125f, s.px(0.06f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "quick_math",
            title = "Quick Math",
            tagline = "Solve fast",
            category = Category.BRAIN,
            tier = Tier.MINI,
            accent = Palette.amber,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.textCentered(c, "+", cx - size * 0.18f, cy - size * 0.16f, size * 0.34f, color, Fonts.display)
                Draw.textCentered(c, "×", cx + size * 0.18f, cy - size * 0.16f, size * 0.32f, Palette.pink, Fonts.display)
                Draw.textCentered(c, "−", cx - size * 0.18f, cy + size * 0.18f, size * 0.34f, Palette.cyan, Fonts.display)
                Draw.textCentered(c, "=", cx + size * 0.18f, cy + size * 0.18f, size * 0.3f, Palette.textDim, Fonts.display)
            },
            create = { QuickMathGame() }
        )
    }
}
