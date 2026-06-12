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
import kotlin.math.abs

/**
 * Sky Dive — fall through a stack of sky gates. Drag to line up the orb with each gate's opening as
 * it rushes up to meet you. Sail through to score; miss the gap and you bruise a wing — three misses
 * and the dive's over. Gaps shrink and gates speed up as you climb the combo.
 */
class SkyDiveGame : ArcadeGame(meta()) {

    private class Gate(var x: Float, var gapHalf: Float, var y: Float, var done: Boolean)
    private val gates = ArrayList<Gate>()
    private var px = 0f
    private var lives = 3
    private var dist = 0f

    override val controlHint = "DRAG TO LINE UP THE GAP"

    private fun playerY(s: ScreenInfo) = s.h * 0.32f
    private fun span(s: ScreenInfo) = if (s.isRound) s.minDim * 0.36f else s.w * 0.46f

    override fun onReset() {
        px = sc.cx; lives = 3; dist = 0f; gates.clear()
        var y = sc.h + sc.px(0.3f)
        repeat(3) { gates.add(newGate(y)); y += sc.px(0.55f) }
    }

    private fun newGate(y: Float): Gate {
        val s = sc
        val gh = Mathx.lerp(s.px(0.16f), s.px(0.085f), (dist / 4000f).coerceIn(0f, 1f))
        val cx = Mathx.rand(s.cx - span(s) + gh, s.cx + span(s) - gh)
        return Gate(cx, gh, y, false)
    }

    override fun onPress(x: Float, y: Float) { px = x.coerceIn(sc.cx - span(sc), sc.cx + span(sc)) }
    override fun onDrag(x: Float, y: Float) { px = x.coerceIn(sc.cx - span(sc), sc.cx + span(sc)) }

    override fun onStep(dt: Float) {
        val s = sc
        dist += dt * 100f
        val speed = Mathx.lerp(s.px(0.7f), s.px(1.3f), (dist / 5000f).coerceIn(0f, 1f))
        val py = playerY(s); val pr = s.px(0.04f)
        val it = gates.iterator()
        while (it.hasNext()) {
            val g = it.next()
            g.y -= speed * dt
            if (!g.done && g.y <= py) {
                g.done = true
                if (abs(px - g.x) <= g.gapHalf - pr) {
                    addScore(10); engine.sfx.coin(); engine.haptics.light()
                    particles.burst(px, py, 12, accent, s.px(0.6f), s.px(0.014f), 0.4f)
                } else {
                    lives--; engine.sfx.hit(); engine.haptics.heavy(); flash(Palette.red, 0.3f); shake(s.px(0.014f))
                    if (lives <= 0) { gameOver(); return }
                }
            }
            if (g.y < -s.px(0.2f)) it.remove()
        }
        if (gates.isNotEmpty() && gates.maxByOrNull { it.y }!!.y < s.h - s.px(0.1f))
            gates.add(newGate(s.h + s.px(0.25f)))
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            val barTh = s.px(0.04f)
            for (g in gates) {
                // left + right gate posts
                Draw.roundRect(c, s.cx - span(s) - s.px(0.1f), g.y - barTh / 2, (g.x - g.gapHalf) - (s.cx - span(s)) + s.px(0.1f), barTh, barTh / 2, Palette.alpha(accent, 0.8f))
                val rx = g.x + g.gapHalf
                Draw.roundRect(c, rx, g.y - barTh / 2, (s.cx + span(s)) - rx + s.px(0.1f), barTh, barTh / 2, Palette.alpha(accent, 0.8f))
            }
            // player
            val py = playerY(s); val pr = s.px(0.04f)
            Draw.glow(c, px, py, pr * 1.8f, Palette.text, 0.4f)
            Draw.fillCircle(c, px, py, pr, Palette.text)
            Draw.fillCircle(c, px, py + pr * 1.6f, pr * 0.4f, Palette.alpha(Palette.text, 0.4f))
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.05f); val ly = s.h * 0.9f; val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, ly, s.px(0.016f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, ly, s.px(0.014f), Palette.alpha(Palette.red, 0.35f), s.dp(1.4f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.12f, s.px(0.06f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "sky_dive",
            title = "Sky Dive",
            tagline = "Thread the gates",
            category = Category.ARCADE,
            tier = Tier.MINI,
            accent = Palette.blue,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRect(c, cx - size * 0.5f, cy + size * 0.1f, size * 0.32f, size * 0.1f, size * 0.04f, color)
                Draw.roundRect(c, cx + size * 0.18f, cy + size * 0.1f, size * 0.32f, size * 0.1f, size * 0.04f, color)
                Draw.fillCircle(c, cx, cy - size * 0.2f, size * 0.12f, Palette.text)
            },
            create = { SkyDiveGame() }
        )
    }
}
