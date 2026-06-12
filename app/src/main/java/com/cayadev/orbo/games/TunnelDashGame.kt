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
 * Tunnel Dash — steer the orb along a winding, narrowing corridor. Drag left and right to keep it
 * between the walls as the tunnel rushes past faster and faster. Scrape a wall and the run ends.
 * Distance survived is your score.
 */
class TunnelDashGame : ArcadeGame(meta()) {

    private var px = 0f
    private var scroll = 0f
    private var dist = 0f
    private var scoreT = 0f

    override val controlHint = "DRAG TO STEER"

    private fun playerY(s: ScreenInfo) = s.h * 0.78f
    private fun boardHalf(s: ScreenInfo) = if (s.isRound) s.minDim * 0.33f else s.w * 0.46f
    private fun width(s: ScreenInfo) = Mathx.lerp(s.px(0.38f), s.px(0.19f), (dist / 6000f).coerceIn(0f, 1f))

    private fun centerAt(s: ScreenInfo, wy: Float): Float {
        val amp1 = s.minDim * 0.16f; val amp2 = s.minDim * 0.09f
        val c = s.cx + Mathx.sinf(wy * 0.009f) * amp1 + Mathx.sinf(wy * 0.0037f + 0.8f) * amp2
        val w = width(s); val bh = boardHalf(s)
        return c.coerceIn(s.cx - bh + w / 2f, s.cx + bh - w / 2f)
    }

    override fun onReset() { px = sc.cx; scroll = 0f; dist = 0f; scoreT = 0f }

    override fun onPress(x: Float, y: Float) { px = x }
    override fun onDrag(x: Float, y: Float) { px = x }

    override fun onStep(dt: Float) {
        val s = sc
        dist += dt * 100f
        val speed = Mathx.lerp(s.px(0.7f), s.px(1.4f), (dist / 6000f).coerceIn(0f, 1f))
        scroll += speed * dt
        scoreT += dt; if (scoreT >= 0.16f) { scoreT = 0f; addScore(1) }
        val pr = s.px(0.04f)
        val center = centerAt(s, scroll + playerY(s)); val w = width(s)
        px = px.coerceIn(s.cx - boardHalf(s), s.cx + boardHalf(s))
        if (px < center - w / 2f + pr || px > center + w / 2f - pr) {
            engine.sfx.explosion(); engine.haptics.heavy(); flash(Palette.red, 0.5f); shake(s.px(0.025f))
            particles.burst(px, playerY(s), 24, Palette.red, s.px(0.9f), s.px(0.02f), 0.5f)
            gameOver()
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            val step = s.px(0.05f)
            var y = 0f
            while (y <= s.h) {
                val center = centerAt(s, scroll + y); val w = width(s)
                Draw.roundRect(c, -s.px(0.05f), y, center - w / 2f + s.px(0.05f), step + s.dp(1f), 0f, Palette.alpha(accent, 0.16f))
                Draw.roundRect(c, center + w / 2f, y, s.w - (center + w / 2f) + s.px(0.05f), step + s.dp(1f), 0f, Palette.alpha(accent, 0.16f))
                // wall edges
                val c2 = centerAt(s, scroll + y + step); val w2 = width(s)
                Draw.line(c, center - w / 2f, y, c2 - w2 / 2f, y + step, accent, s.dp(2f))
                Draw.line(c, center + w / 2f, y, c2 + w2 / 2f, y + step, accent, s.dp(2f))
                y += step
            }
            val pr = s.px(0.04f); val py = playerY(s)
            Draw.glow(c, px, py, pr * 1.8f, Palette.text, 0.4f)
            Draw.fillCircle(c, px, py, pr, Palette.text)
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.11f, s.px(0.06f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "tunnel_dash",
            title = "Tunnel Dash",
            tagline = "Steer the corridor",
            category = Category.ARCADE,
            tier = Tier.MINI,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.line(c, cx - size * 0.3f, cy - size * 0.5f, cx - size * 0.4f, cy + size * 0.5f, color, size * 0.06f)
                Draw.line(c, cx + size * 0.3f, cy - size * 0.5f, cx + size * 0.2f, cy + size * 0.5f, color, size * 0.06f)
                Draw.fillCircle(c, cx - size * 0.05f, cy + size * 0.2f, size * 0.1f, Palette.text)
            },
            create = { TunnelDashGame() }
        )
    }
}
