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
 * Color Switch — the orb climbs forever; each colour gate only lets through a matching orb. Tap to
 * cycle the orb's colour and line it up before the gate arrives. One colour clash and the climb ends.
 * Deterministic and fair — the upcoming gates are always visible.
 */
class ColorSwitchGame : ArcadeGame(meta()) {

    private val cols = intArrayOf(Palette.mint, Palette.pink, Palette.amber, Palette.cyan)
    private var ballColor = 0
    private var scroll = 0f

    private class Gate(var y: Float, var color: Int, var rainbow: Boolean, var passed: Boolean)
    private val gates = ArrayList<Gate>()

    override val controlHint = "TAP TO CYCLE COLOUR"

    private fun ballY(s: ScreenInfo) = s.h * 0.62f

    override fun onReset() {
        ballColor = 0; scroll = 0f; gates.clear()
        var y = -sc.px(0.2f)
        repeat(5) { gates.add(newGate(y)); y -= sc.px(0.5f) }
    }

    private fun newGate(y: Float): Gate {
        val rainbow = Mathx.chance(0.12f)
        return Gate(y, Mathx.rand(4), rainbow, false)
    }

    override fun onTapWorld(x: Float, y: Float) {
        ballColor = (ballColor + 1) % 4
        engine.sfx.click(); engine.haptics.tick()
    }

    override fun onStep(dt: Float) {
        val s = sc
        val speed = Mathx.lerp(s.px(0.45f), s.px(0.95f), (score / 80f).coerceIn(0f, 1f))
        scroll += speed * dt
        val by = ballY(s); val br = s.px(0.045f)
        val it = gates.iterator()
        while (it.hasNext()) {
            val g = it.next()
            val gy = g.y + scroll
            if (!g.passed && gy >= by) {
                g.passed = true
                if (g.rainbow || g.color == ballColor) {
                    addScore(if (g.rainbow) 3 else 1)
                    engine.sfx.coin(); engine.haptics.light()
                    particles.burst(s.cx, by, 14, if (g.rainbow) Palette.gold else cols[ballColor], s.px(0.6f), s.px(0.014f), 0.4f)
                } else {
                    engine.sfx.explosion(); engine.haptics.heavy(); flash(Palette.red, 0.5f); shake(s.px(0.025f))
                    particles.burst(s.cx, by, 24, Palette.red, s.px(0.9f), s.px(0.02f), 0.5f)
                    gameOver(); return
                }
            }
            if (gy > s.h + s.px(0.2f)) it.remove()
        }
        // keep the column filled upward
        val topGate = gates.minByOrNull { it.y + scroll }
        if (topGate == null || (topGate.y + scroll) > -s.px(0.1f)) {
            val ny = (gates.minOfOrNull { it.y } ?: -scroll) - sc.px(0.5f)
            gates.add(newGate(ny))
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            val barTh = s.px(0.055f)
            for (g in gates) {
                val gy = g.y + scroll
                if (gy < -barTh || gy > s.h + barTh) continue
                if (g.rainbow) {
                    val seg = s.w / 4f
                    for (i in 0 until 4) Draw.roundRect(c, i * seg, gy - barTh / 2, seg, barTh, 0f, cols[i])
                } else {
                    Draw.roundRect(c, -s.px(0.1f), gy - barTh / 2, s.w + s.px(0.2f), barTh, barTh * 0.3f, g.color.let { cols[it] })
                }
            }
            // ball
            val by = ballY(s); val br = s.px(0.045f)
            Draw.glow(c, s.cx, by, br * 1.8f, cols[ballColor], 0.4f)
            Draw.fillCircle(c, s.cx, by, br, cols[ballColor])
            Draw.strokeCircle(c, s.cx, by, br, Palette.alpha(Palette.text, 0.3f), s.dp(2f))
        }
        // colour cycle hint (current + next)
        val cy = s.h * 0.9f
        for (i in 0 until 4) {
            val cxx = s.cx + (i - 1.5f) * s.px(0.08f)
            val on = i == ballColor
            Draw.fillCircle(c, cxx, cy, if (on) s.px(0.025f) else s.px(0.015f), cols[i])
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.13f, s.px(0.07f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "color_switch",
            title = "Color Switch",
            tagline = "Match the gate",
            category = Category.ARCADE,
            tier = Tier.MINI,
            accent = Palette.pink,
            icon = IconDrawer { c, cx, cy, size, _ ->
                val cols = intArrayOf(Palette.mint, Palette.pink, Palette.amber, Palette.cyan)
                Draw.roundRect(c, cx - size * 0.5f, cy - size * 0.32f, size, size * 0.16f, size * 0.04f, cols[1])
                Draw.roundRect(c, cx - size * 0.5f, cy + size * 0.16f, size, size * 0.16f, size * 0.04f, cols[3])
                Draw.fillCircle(c, cx, cy, size * 0.16f, cols[3])
            },
            create = { ColorSwitchGame() }
        )
    }
}
