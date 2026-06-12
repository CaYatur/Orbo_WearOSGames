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
 * Color Match — read the WORD (ignore its ink colour) and tap the matching swatch before the
 * timer runs out. A Stroop-style brain-reflex test with combos and rising speed.
 */
class ColorMatchGame : ArcadeGame(meta()) {

    private data class Swatch(val name: String, val color: Int)
    private val palette = listOf(
        Swatch("RED", Palette.red), Swatch("GREEN", Palette.lime), Swatch("BLUE", Palette.blue),
        Swatch("AMBER", Palette.amber), Swatch("PURPLE", Palette.violet), Swatch("CYAN", Palette.cyan)
    )

    private var wordIndex = 0
    private var inkColor = Palette.text
    private val options = IntArray(4)   // indices into palette
    private var correctSlot = 0
    private var timeLimit = 2.4f
    private var timeLeft = 2.4f
    private var combo = 0
    private var lives = 3
    private var picked = -1
    private var pickT = 0f
    private var appear = 0f

    override val controlHint = "TAP THE WORD'S COLOUR"

    override fun onReset() { combo = 0; lives = 3; nextRound() }

    private fun nextRound() {
        val diff = (score / 600f).coerceIn(0f, 1f)
        timeLimit = Mathx.lerp(2.4f, 1.0f, diff)
        timeLeft = timeLimit
        picked = -1; appear = 0f
        wordIndex = Mathx.rand(palette.size)
        inkColor = if (Mathx.chance(0.6f)) palette[Mathx.rand(palette.size)].color else Palette.text
        correctSlot = Mathx.rand(4)
        val used = HashSet<Int>()
        used.add(wordIndex)
        for (slot in 0 until 4) {
            if (slot == correctSlot) { options[slot] = wordIndex; continue }
            var idx = Mathx.rand(palette.size)
            while (used.contains(idx)) idx = Mathx.rand(palette.size)
            used.add(idx); options[slot] = idx
        }
    }

    override fun onStep(dt: Float) {
        if (appear < 1f) appear = (appear + dt * 5f).coerceAtMost(1f)
        if (picked >= 0) { pickT += dt; if (pickT > 0.18f) nextRound(); return }
        timeLeft -= dt
        if (timeLeft <= 0f) loseLife()
    }

    private fun loseLife() {
        lives--; combo = 0
        engine.haptics.medium(); engine.sfx.hit(); flash(Palette.red, 0.3f); shake(sc.px(0.015f))
        if (lives <= 0) gameOver() else nextRound()
    }

    private fun buttonRect(s: ScreenInfo, slot: Int): FloatArray {
        val sz = s.px(0.30f)
        val gap = s.px(0.035f)
        val col = slot % 2; val row = slot / 2
        val cx = s.cx + (col - 0.5f) * (sz + gap)
        val cy = s.h * 0.60f + (row - 0.5f) * (sz + gap)
        return floatArrayOf(cx, cy, sz)
    }

    override fun onTapWorld(x: Float, y: Float) {
        if (picked >= 0) return
        for (slot in 0 until 4) {
            val r = buttonRect(sc, slot)
            val half = r[2] / 2f
            if (x in (r[0] - half)..(r[0] + half) && y in (r[1] - half)..(r[1] + half)) {
                picked = slot; pickT = 0f
                if (slot == correctSlot) {
                    combo++
                    addScore(15 + combo * 3)
                    engine.haptics.light(); engine.sfx.coin()
                    particles.burst(r[0], r[1], 16, palette[options[slot]].color, sc.px(0.6f), sc.px(0.02f), 0.5f)
                } else {
                    loseLife()
                }
                return
            }
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return

        // prompt word
        val word = Loc.t(palette[wordIndex].name)
        Draw.textCentered(c, word, s.cx, s.h * 0.30f, s.px(0.13f), inkColor, Fonts.display, appear, 0.04f)

        // timer bar
        val frac = (timeLeft / timeLimit).coerceIn(0f, 1f)
        val barW = s.px(0.4f)
        val barCol = Palette.lerp(Palette.red, accent, frac)
        Draw.roundRect(c, s.cx - barW / 2, s.h * 0.38f, barW, s.dp(5f), s.dp(3f), Palette.track)
        Draw.roundRect(c, s.cx - barW / 2, s.h * 0.38f, barW * frac, s.dp(5f), s.dp(3f), barCol)

        // options
        for (slot in 0 until 4) {
            val r = buttonRect(s, slot)
            val half = r[2] / 2f
            val col = palette[options[slot]].color
            val sel = picked == slot
            val correct = slot == correctSlot
            val scl = if (sel) (1f - 0.06f * (pickT / 0.18f).coerceIn(0f, 1f)) else 1f
            val h2 = half * scl
            Draw.glow(c, r[0], r[1], h2 * 1.5f, col, 0.25f)
            Draw.roundRectCentered(c, r[0], r[1], h2 * 2, h2 * 2, h2 * 0.45f, Palette.alpha(col, 0.9f))
            if (picked >= 0 && correct) {
                Draw.roundRectStroke(c, r[0] - h2, r[1] - h2, h2 * 2, h2 * 2, h2 * 0.45f, Palette.text, s.dp(3f))
            }
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.05f); val y = s.h * 0.205f
        val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, y, s.px(0.017f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, y, s.px(0.015f), Palette.alpha(Palette.red, 0.35f), s.dp(1.5f))
        }
        if (combo > 1) Draw.textCentered(c, "x$combo", s.cx, s.h * 0.83f, s.px(0.05f), accent, Fonts.bodyBold)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.125f, s.px(0.075f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "color_match",
            title = "Color Match",
            tagline = "Read the word",
            category = Category.BRAIN,
            tier = Tier.MINI,
            accent = Palette.violet,
            icon = IconDrawer { c, cx, cy, size, _ ->
                val r = size * 0.22f
                Draw.fillCircle(c, cx - r, cy - r, r * 0.9f, Palette.red)
                Draw.fillCircle(c, cx + r, cy - r, r * 0.9f, Palette.lime)
                Draw.fillCircle(c, cx - r, cy + r, r * 0.9f, Palette.blue)
                Draw.fillCircle(c, cx + r, cy + r, r * 0.9f, Palette.amber)
            },
            create = { ColorMatchGame() }
        )
    }
}
