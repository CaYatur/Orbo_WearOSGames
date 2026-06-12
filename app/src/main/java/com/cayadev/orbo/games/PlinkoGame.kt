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
import kotlin.math.hypot

/**
 * Plinko — drop chips down a field of pegs and watch them rattle into the prize slots. Aim by tapping
 * where you want the chip to fall. The rare edge slots pay big and refund a chip; the centre pays
 * little. Run out of chips and it's over.
 */
class PlinkoGame : ArcadeGame(meta()) {

    private class Chip(var x: Float, var y: Float, var vx: Float, var vy: Float)
    private val chips = ArrayList<Chip>()
    private val pegRows = 6
    private val cols = 6
    private val bins = 9
    private val values = intArrayOf(40, 10, 4, 2, 1, 2, 4, 10, 40)
    private var ballsLeft = 12
    private var landFx = 0f
    private var landBin = -1

    override val controlHint = "TAP TO DROP A CHIP"
    override val gameOverTitle = "RESULTS"

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.66f else s.minDim * 0.92f
    private fun left(s: ScreenInfo) = s.cx - side(s) / 2f
    private fun top(s: ScreenInfo) = s.cy - side(s) / 2f
    private fun rowGap(s: ScreenInfo) = side(s) / (pegRows + 2.2f)
    private fun colGap(s: ScreenInfo) = side(s) / cols
    private fun pegR(s: ScreenInfo) = colGap(s) * 0.1f
    private fun chipR(s: ScreenInfo) = colGap(s) * 0.2f
    private fun binTop(s: ScreenInfo) = top(s) + side(s) * 0.84f

    private fun pegPos(s: ScreenInfo, r: Int, col: Int): FloatArray {
        val off = if (r % 2 == 1) colGap(s) * 0.5f else 0f
        val x = left(s) + colGap(s) * col + off
        val y = top(s) + rowGap(s) * (r + 1.3f)
        return floatArrayOf(x, y)
    }

    override fun onReset() { chips.clear(); ballsLeft = 12; landFx = 0f; landBin = -1 }

    override fun onTapWorld(x: Float, y: Float) {
        if (ballsLeft <= 0) return
        if (y > binTop(sc)) return
        ballsLeft--
        val s = sc
        val dx = x.coerceIn(left(s) + chipR(s) * 2, left(s) + side(s) - chipR(s) * 2)
        chips.add(Chip(dx, top(s) + rowGap(s) * 0.3f, Mathx.rand(-s.px(0.05f), s.px(0.05f)), 0f))
        engine.sfx.blip(); engine.haptics.light()
    }

    override fun onStep(dt: Float) {
        if (landFx > 0f) landFx -= dt
        val s = sc; val pr = pegR(s); val cr = chipR(s); val g = s.px(3.2f)
        val it = chips.iterator()
        while (it.hasNext()) {
            val ch = it.next()
            ch.vy += g * dt
            ch.x += ch.vx * dt; ch.y += ch.vy * dt
            // walls
            if (ch.x < left(s) + cr) { ch.x = left(s) + cr; ch.vx = kotlin.math.abs(ch.vx) * 0.6f }
            if (ch.x > left(s) + side(s) - cr) { ch.x = left(s) + side(s) - cr; ch.vx = -kotlin.math.abs(ch.vx) * 0.6f }
            // pegs
            for (r in 0 until pegRows) {
                val nPeg = cols + (if (r % 2 == 1) 0 else 1)
                for (col in 0 until nPeg) {
                    val p = pegPos(s, r, col)
                    val d = hypot((ch.x - p[0]).toDouble(), (ch.y - p[1]).toDouble()).toFloat()
                    val minD = pr + cr
                    if (d < minD && d > 0.0001f) {
                        val nx = (ch.x - p[0]) / d; val ny = (ch.y - p[1]) / d
                        ch.x = p[0] + nx * minD; ch.y = p[1] + ny * minD
                        val dot = ch.vx * nx + ch.vy * ny
                        ch.vx -= 2f * dot * nx * 0.55f
                        ch.vy -= 2f * dot * ny * 0.55f
                        ch.vx += Mathx.rand(-s.px(0.12f), s.px(0.12f))
                        engine.sfx.tone(com.cayadev.orbo.engine.Sfx.Wave.SINE, 880f, 880f, 0.03f, 0.2f)
                    }
                }
            }
            // landed
            if (ch.y > binTop(s) + chipR(s)) {
                val bw = side(s) / bins
                val idx = (((ch.x - left(s)) / bw).toInt()).coerceIn(0, bins - 1)
                val v = values[idx]
                addScore(v); landFx = 0.5f; landBin = idx
                if (v >= 20) { ballsLeft++; engine.sfx.success() } else engine.sfx.coin()
                engine.haptics.medium()
                particles.burst(ch.x, binTop(s), 12, accent, s.px(0.5f), s.px(0.014f), 0.4f)
                it.remove()
            }
        }
        if (ballsLeft <= 0 && chips.isEmpty()) gameOver()
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            // pegs
            for (r in 0 until pegRows) {
                val nPeg = cols + (if (r % 2 == 1) 0 else 1)
                for (col in 0 until nPeg) {
                    val p = pegPos(s, r, col)
                    Draw.fillCircle(c, p[0], p[1], pegR(s), Palette.alpha(accent, 0.7f))
                }
            }
            // bins
            val bw = side(s) / bins; val by = binTop(s)
            for (i in 0 until bins) {
                val bx = left(s) + bw * i
                val hot = landFx > 0f && i == landBin
                val col = Palette.lerp(Palette.panel, accent, if (values[i] >= 20) 0.6f else 0.15f)
                Draw.roundRect(c, bx + bw * 0.08f, by, bw * 0.84f, side(s) * 0.16f, bw * 0.12f,
                    if (hot) Palette.alpha(accent, 0.8f) else col)
                Draw.textCentered(c, "${values[i]}", bx + bw / 2, by + side(s) * 0.08f, bw * 0.4f,
                    if (values[i] >= 20) Palette.gold else Palette.text, Fonts.bodyBold)
            }
            // chips
            for (ch in chips) {
                Draw.glow(c, ch.x, ch.y, chipR(s) * 1.5f, Palette.gold, 0.3f)
                Draw.fillCircle(c, ch.x, ch.y, chipR(s), Palette.gold)
            }
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        Draw.textCentered(c, "● $ballsLeft", s.cx, s.h * 0.95f, s.px(0.05f), Palette.gold, Fonts.bodyBold)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.05f, s.px(0.05f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "plinko",
            title = "Plinko",
            tagline = "Drop the chips",
            category = Category.ARCADE,
            tier = Tier.MINI,
            accent = Palette.violet,
            icon = IconDrawer { c, cx, cy, size, color ->
                for (r in 0 until 3) for (col in 0..r) {
                    Draw.fillCircle(c, cx + (col - r / 2f) * size * 0.26f, cy - size * 0.3f + r * size * 0.24f, size * 0.05f, Palette.alpha(color, 0.8f))
                }
                Draw.fillCircle(c, cx, cy - size * 0.42f, size * 0.09f, Palette.gold)
            },
            create = { PlinkoGame() }
        )
    }
}
