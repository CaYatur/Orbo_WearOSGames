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
 * Key Tiles — a "piano tiles" reflex runner. Rows of four cells scroll down; exactly one cell per
 * row is lit. Always tap the lowest lit tile before it slides off the bottom. A wrong tap or a
 * missed tile ends the run. Tempo rises the further you get. Centered square board → round-safe.
 */
class KeyTilesGame : ArcadeGame(meta()) {

    private val lanes = 4
    private val visibleRows = 4
    private val laneOf = ArrayList<Int>()   // active lane per row index
    private var cleared = 0                  // lowest un-tapped row
    private var dist = 0f                    // downward scroll, in rows
    private var speed = 0f                   // rows / second
    private var tapFx = 0f
    private var tapX = 0f
    private var tapY = 0f

    override val controlHint = "TAP THE DARK TILE"

    override fun onReset() {
        laneOf.clear(); cleared = 0; dist = 0f; speed = 1.7f; tapFx = 0f
    }

    private fun laneFor(k: Int): Int {
        while (laneOf.size <= k) {
            var l = Mathx.rand(lanes)
            // avoid three identical lanes in a row (keeps it readable)
            if (laneOf.size >= 2 && laneOf[laneOf.size - 1] == l && laneOf[laneOf.size - 2] == l)
                l = (l + 1 + Mathx.rand(lanes - 1)) % lanes
            laneOf.add(l)
        }
        return laneOf[k]
    }

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.66f else s.minDim * 0.88f
    private fun left(s: ScreenInfo) = s.cx - side(s) / 2f
    private fun top(s: ScreenInfo) = s.cy - side(s) / 2f
    private fun unit(s: ScreenInfo) = side(s) / visibleRows
    private fun rowTopY(s: ScreenInfo, k: Int) = top(s) + (dist - k) * unit(s)

    override fun onStep(dt: Float) {
        if (tapFx > 0f) tapFx -= dt
        dist += speed * dt
        // miss: the lowest un-tapped tile has fully passed the bottom edge
        if (rowTopY(sc, cleared) > top(sc) + side(sc)) {
            engine.haptics.heavy(); flash(Palette.red, 0.4f); shake(sc.px(0.02f))
            gameOver()
        }
    }

    override fun onTapWorld(x: Float, y: Float) {
        val s = sc; val l = left(s); val t = top(s); val sd = side(s)
        if (x < l || x > l + sd || y < t || y > t + sd) return     // ignore taps off the board
        val lane = ((x - l) / unit(s)).toInt().coerceIn(0, lanes - 1)
        if (lane == laneFor(cleared)) {
            addScore(1); cleared++
            speed = (speed + 0.07f).coerceAtMost(5.6f)
            tapX = x; tapY = y; tapFx = 0.25f
            engine.sfx.blip(); engine.haptics.light()
            particles.burst(x, rowTopY(s, cleared - 1) + unit(s) / 2f, 8, accent, s.px(0.4f), s.px(0.012f), 0.32f)
        } else {
            engine.sfx.fail(); engine.haptics.heavy(); flash(Palette.red, 0.4f); shake(sc.px(0.02f))
            gameOver()
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val l = left(s); val t = top(s); val sd = side(s); val u = unit(s)

        Draw.withFaceClip(c, s) {
            // faint lane guides
            for (i in 1 until lanes) Draw.line(c, l + i * u, t, l + i * u, t + sd, Palette.alpha(Palette.line, 0.5f), s.dp(1f))
            // tiles
            val kFrom = cleared - 1
            val kTo = cleared + visibleRows + 1
            for (k in kFrom..kTo) {
                if (k < 0) continue
                val ry = rowTopY(s, k)
                if (ry > t + sd + u || ry < t - u) continue
                val lane = laneFor(k)
                val cx = l + lane * u + u / 2f
                val cy = ry + u / 2f
                val pad = u * 0.07f
                if (k < cleared) {
                    Draw.roundRectCentered(c, cx, cy, u - pad * 2, u - pad * 2, u * 0.14f, Palette.alpha(accent, 0.18f))
                } else {
                    val isTarget = k == cleared
                    val col = if (isTarget) accent else Palette.alpha(accent, 0.82f)
                    if (isTarget) Draw.glow(c, cx, cy, u * 0.62f, accent, 0.28f)
                    Draw.roundRectCentered(c, cx, cy, u - pad * 2, u - pad * 2, u * 0.14f, col)
                }
            }
            // board frame
            Draw.roundRectStroke(c, l, t, sd, sd, u * 0.14f, Palette.alpha(accent, 0.22f), s.dp(1.4f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.085f, s.px(0.06f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "key_tiles",
            title = "Key Tiles",
            tagline = "Tap the dark tiles",
            category = Category.REFLEX,
            tier = Tier.MINI,
            accent = Palette.mint,
            icon = IconDrawer { c, cx, cy, size, color ->
                val u = size * 0.30f
                for (r in -1..1) {
                    val lit = (r + 2) % 2 == 0
                    val lane = if (r == -1) -1 else if (r == 0) 1 else 0
                    Draw.roundRectCentered(c, cx + lane * u, cy + r * u, u * 0.84f, u * 0.84f, u * 0.16f,
                        if (lit) color else Palette.alpha(color, 0.22f))
                }
            },
            create = { KeyTilesGame() }
        )
    }
}
