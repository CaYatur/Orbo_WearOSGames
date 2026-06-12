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
 * Pin Wheel — fire pins into the spinning hub. Each pin sticks where it lands; thread them in without
 * ever letting two collide. The hub spins faster and flips direction the more pins bristle from it.
 * A single clash ends the run.
 */
class PinWheelGame : ArcadeGame(meta()) {

    private val pins = ArrayList<Float>()   // angle stored relative to hub
    private var hubRot = 0f
    private var hubSpeed = 0f
    private var flightP = -1f                // -1 = idle, else 0..1
    private val minGap = 0.17f
    private val contact = Math.PI.toFloat() / 2f   // bottom of hub (screen +y)

    override val controlHint = "TAP TO FIRE A PIN"

    private fun hubR(s: ScreenInfo) = s.px(0.13f)
    private fun pinLen(s: ScreenInfo) = s.px(0.12f)

    override fun onReset() {
        pins.clear(); hubRot = 0f; hubSpeed = 1.6f; flightP = -1f
        repeat(3) { pins.add(Mathx.rand(0f, Mathx.TAU)) }
    }

    private fun diff(a: Float, b: Float): Float {
        var d = (a - b) % Mathx.TAU
        if (d < -Math.PI) d += Mathx.TAU
        if (d > Math.PI) d -= Mathx.TAU
        return abs(d)
    }

    override fun onTapWorld(x: Float, y: Float) {
        if (flightP >= 0f) return
        flightP = 0f
        engine.sfx.tone(com.cayadev.orbo.engine.Sfx.Wave.SQUARE, 520f, 760f, 0.06f, 0.4f)
    }

    override fun onStep(dt: Float) {
        hubRot = (hubRot + hubSpeed * dt) % Mathx.TAU
        if (flightP >= 0f) {
            flightP += dt * 7f
            if (flightP >= 1f) {
                flightP = -1f
                val stored = contact - hubRot
                var clash = false
                for (p in pins) { if (diff(p + hubRot, contact) < minGap) { clash = true; break } }
                if (clash) {
                    engine.sfx.explosion(); engine.haptics.heavy(); flash(Palette.red, 0.5f); shake(sc.px(0.025f))
                    val a = contact
                    particles.burst(sc.cx + Mathx.cosf(a) * hubR(sc), sc.cy + Mathx.sinf(a) * hubR(sc), 22, Palette.red, sc.px(0.9f), sc.px(0.02f), 0.5f)
                    gameOver()
                } else {
                    pins.add(stored); addScore(1)
                    engine.sfx.hit(); engine.haptics.light()
                    hubSpeed = (abs(hubSpeed) + 0.12f)
                    if (Mathx.chance(0.18f)) hubSpeed = -hubSpeed
                }
            }
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val hr = hubR(s); val pl = pinLen(s)
        // stuck pins
        for (p in pins) {
            val a = p + hubRot
            val x1 = s.cx + Mathx.cosf(a) * hr; val y1 = s.cy + Mathx.sinf(a) * hr
            val x2 = s.cx + Mathx.cosf(a) * (hr + pl); val y2 = s.cy + Mathx.sinf(a) * (hr + pl)
            Draw.line(c, x1, y1, x2, y2, Palette.alpha(accent, 0.9f), s.dp(3f))
            Draw.fillCircle(c, x2, y2, s.px(0.018f), accent)
        }
        // hub
        Draw.glow(c, s.cx, s.cy, hr * 1.3f, accent, 0.22f)
        Draw.fillCircle(c, s.cx, s.cy, hr, Palette.panelHi)
        Draw.strokeCircle(c, s.cx, s.cy, hr, Palette.alpha(accent, 0.6f), s.dp(2f))
        Draw.textCentered(c, "$score", s.cx, s.cy, hr * 0.7f, Palette.text, Fonts.display)
        // flying pin (from bottom, rising toward hub)
        if (flightP >= 0f) {
            val launchY = s.h * 0.95f
            val targetY = s.cy + hr + pl
            val y2 = Mathx.lerp(launchY, targetY, flightP)
            Draw.line(c, s.cx, y2, s.cx, y2 + pl, Palette.text, s.dp(3f))
            Draw.fillCircle(c, s.cx, y2, s.px(0.018f), Palette.text)
        } else {
            // ready indicator at bottom
            val ry = s.h * 0.92f
            Draw.line(c, s.cx, ry, s.cx, ry + pinLen(s) * 0.8f, Palette.alpha(Palette.text, 0.6f), s.dp(3f))
            Draw.fillCircle(c, s.cx, ry, s.px(0.016f), Palette.alpha(Palette.text, 0.7f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) { /* score sits inside the hub */ }

    companion object {
        fun meta() = GameMeta(
            id = "pin_wheel",
            title = "Pin Wheel",
            tagline = "Fire the pins",
            category = Category.ARCADE,
            tier = Tier.MINI,
            accent = Palette.amber,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.fillCircle(c, cx, cy, size * 0.18f, Palette.panelHi)
                Draw.strokeCircle(c, cx, cy, size * 0.18f, color, size * 0.04f)
                for (i in 0 until 5) {
                    val a = i * Mathx.TAU / 5f
                    Draw.line(c, cx + Mathx.cosf(a) * size * 0.18f, cy + Mathx.sinf(a) * size * 0.18f,
                        cx + Mathx.cosf(a) * size * 0.42f, cy + Mathx.sinf(a) * size * 0.42f, color, size * 0.04f)
                }
            },
            create = { PinWheelGame() }
        )
    }
}
