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
 * Color Rush — a pure-speed matching test. A target swatch glows at the top; tap the matching colour
 * among the four pads before the shrinking timer empties. Pads reshuffle every round so you can't
 * rely on muscle memory. Three lives; a wrong pad or a timeout costs one.
 */
class ColorRushGame : ArcadeGame(meta()) {

    private val pool = intArrayOf(Palette.mint, Palette.pink, Palette.amber, Palette.cyan, Palette.violet, Palette.orange)
    private val slot = IntArray(4)        // colour shown at each pad
    private var targetSlot = 0
    private var timeLimit = 2f
    private var timeLeft = 2f
    private var lives = 3
    private var combo = 0
    private var pressFx = -1
    private var pressT = 0f

    override val controlHint = "TAP THE MATCHING COLOUR"

    override fun onReset() { lives = 3; combo = 0; timeLimit = 2f; pressFx = -1; nextRound() }

    private fun nextRound() {
        // pick 4 distinct colours, place randomly
        val picks = pool.toMutableList()
        for (i in picks.indices.reversed()) {
            val j = Mathx.rand(i + 1); val tmp = picks[i]; picks[i] = picks[j]; picks[j] = tmp
        }
        for (i in 0 until 4) slot[i] = picks[i]
        targetSlot = Mathx.rand(4)
        timeLimit = Mathx.lerp(2f, 0.78f, (score / 600f).coerceIn(0f, 1f))
        timeLeft = timeLimit
    }

    private fun board(s: ScreenInfo) = if (s.isRound) s.minDim * 0.6f else s.minDim * 0.78f
    private fun boardCx(s: ScreenInfo) = s.cx
    private fun boardCy(s: ScreenInfo) = s.h * 0.6f
    private fun padCenter(s: ScreenInfo, i: Int): FloatArray {
        val b = board(s); val u = b / 2f
        val col = i % 2; val row = i / 2
        return floatArrayOf(boardCx(s) - u / 2f + col * u, boardCy(s) - u / 2f + row * u)
    }

    override fun onStep(dt: Float) {
        if (pressT > 0f) pressT -= dt
        timeLeft -= dt
        if (timeLeft <= 0f) loseLife(-1f, -1f)
    }

    private fun loseLife(x: Float, y: Float) {
        lives--; combo = 0
        engine.sfx.fail(); engine.haptics.heavy(); flash(Palette.red, 0.3f); shake(sc.px(0.014f))
        if (lives <= 0) gameOver() else nextRound()
    }

    override fun onTapWorld(x: Float, y: Float) {
        val s = sc; val u = board(s) / 2f
        for (i in 0 until 4) {
            val p = padCenter(s, i)
            if (x in (p[0] - u / 2)..(p[0] + u / 2) && y in (p[1] - u / 2)..(p[1] + u / 2)) {
                if (i == targetSlot) {
                    combo++
                    val bonus = (timeLeft / timeLimit * 30f).toInt()
                    addScore(10 + bonus + combo)
                    pressFx = i; pressT = 0.25f
                    engine.sfx.coin(); engine.haptics.light()
                    particles.burst(p[0], p[1], 12, slot[i], s.px(0.5f), s.px(0.016f), 0.4f)
                    nextRound()
                } else loseLife(x, y)
                return
            }
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return

        // target swatch
        val ty = s.h * 0.21f
        val tr = s.px(0.07f)
        Draw.glow(c, s.cx, ty, tr * 1.7f, slot[targetSlot], 0.4f)
        Draw.fillCircle(c, s.cx, ty, tr, slot[targetSlot])
        Draw.strokeCircle(c, s.cx, ty, tr, Palette.alpha(Palette.text, 0.4f), s.dp(2f))

        // pads
        val u = board(s) / 2f
        for (i in 0 until 4) {
            val p = padCenter(s, i)
            val pop = if (pressFx == i) 1f + 0.08f * (pressT / 0.25f).coerceIn(0f, 1f) else 1f
            val side = u * 0.86f * pop
            Draw.roundRectCentered(c, p[0], p[1], side, side, side * 0.2f, slot[i])
            Draw.roundRectStroke(c, p[0] - side / 2, p[1] - side / 2, side, side, side * 0.2f, Palette.alpha(Palette.text, 0.18f), s.dp(1.5f))
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val frac = (timeLeft / timeLimit).coerceIn(0f, 1f)
        com.cayadev.orbo.engine.Ui.edgeProgress(c, s, frac, Palette.lerp(Palette.red, accent, frac))
        if (combo > 1) Draw.textCentered(c, "x$combo", s.cx, s.h * 0.32f, s.px(0.05f), accent, Fonts.bodyBold)
        // lives
        val gap = s.px(0.05f); val ly = s.h * 0.88f; val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, ly, s.px(0.016f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, ly, s.px(0.014f), Palette.alpha(Palette.red, 0.35f), s.dp(1.4f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.085f, s.px(0.055f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "color_rush",
            title = "Color Rush",
            tagline = "Hit the colour",
            category = Category.REFLEX,
            tier = Tier.MINI,
            accent = Palette.pink,
            icon = IconDrawer { c, cx, cy, size, _ ->
                val cols = intArrayOf(Palette.mint, Palette.pink, Palette.amber, Palette.cyan)
                val u = size * 0.3f
                for (i in 0 until 4) {
                    val col = i % 2; val row = i / 2
                    Draw.roundRectCentered(c, cx - u / 2 + col * u, cy - u / 2 + row * u, u * 0.82f, u * 0.82f, u * 0.2f, cols[i])
                }
            },
            create = { ColorRushGame() }
        )
    }
}
