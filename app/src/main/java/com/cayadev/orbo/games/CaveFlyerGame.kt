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
 * Cave Flyer — guide the spark through an ever-narrowing cavern. Hold to climb, release to dive. The
 * cave walls are a smooth, deterministic terrain that tightens the deeper you go. Brush a wall and
 * it's over. Distance is your score.
 */
class CaveFlyerGame : ArcadeGame(meta()) {

    private var py = 0f
    private var vy = 0f
    private var thrust = false
    private var scroll = 0f
    private var dist = 0f
    private var scoreT = 0f

    override val controlHint = "HOLD TO CLIMB"

    private fun playerX(s: ScreenInfo) = s.cx - s.px(0.18f)

    private fun centerAt(s: ScreenInfo, wx: Float): Float {
        val amp1 = s.px(0.13f); val amp2 = s.px(0.07f)
        var c = s.cy + Mathx.sinf(wx * 0.011f) * amp1 + Mathx.sinf(wx * 0.0042f + 1.3f) * amp2
        val g = gapAt(s)
        val lo = s.px(0.07f) + g / 2f
        val hi = s.h - s.px(0.07f) - g / 2f
        return c.coerceIn(lo, hi)
    }

    private fun gapAt(s: ScreenInfo): Float =
        Mathx.lerp(s.px(0.42f), s.px(0.2f), (dist / 6000f).coerceIn(0f, 1f))

    override fun onReset() {
        py = sc.cy; vy = 0f; thrust = false; scroll = 0f; dist = 0f; scoreT = 0f
    }

    override fun onPress(x: Float, y: Float) { thrust = true }
    override fun onRelease(x: Float, y: Float) { thrust = false }

    override fun onStep(dt: Float) {
        val s = sc
        dist += dt * 100f
        val speed = Mathx.lerp(s.px(0.55f), s.px(1.05f), (dist / 6000f).coerceIn(0f, 1f))
        scroll += speed * dt
        vy += (if (thrust) -s.px(3.6f) else s.px(2.6f)) * dt
        vy = vy.coerceIn(-s.px(1.3f), s.px(1.5f))
        py += vy * dt
        if (thrust && Mathx.chance(0.6f))
            particles.spawn(playerX(s) - s.px(0.03f), py, Mathx.rand(-15f, 15f), s.px(0.5f), 0.28f, s.px(0.01f), accent, 0f, 2f)

        scoreT += dt
        if (scoreT >= 0.18f) { scoreT = 0f; addScore(1) }

        val wx = scroll + playerX(s)
        val center = centerAt(s, wx); val gap = gapAt(s); val pr = s.px(0.035f)
        if (py < center - gap / 2f + pr || py > center + gap / 2f - pr) {
            engine.sfx.explosion(); engine.haptics.heavy(); flash(Palette.red, 0.5f); shake(sc.px(0.025f))
            particles.burst(playerX(s), py, 26, Palette.red, sc.px(1f), sc.px(0.02f), 0.5f)
            gameOver()
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            val step = s.px(0.05f)
            var x = 0f
            while (x <= s.w) {
                val wx = scroll + x
                val center = centerAt(s, wx); val gap = gapAt(s)
                val topH = center - gap / 2f
                val botY = center + gap / 2f
                Draw.roundRect(c, x, -s.px(0.05f), step + s.dp(1f), topH + s.px(0.05f), 0f, Palette.alpha(accent, 0.18f))
                Draw.line(c, x, topH, x + step, centerAt(s, scroll + x + step) - gapAt(s) / 2f, accent, s.dp(2f))
                Draw.roundRect(c, x, botY, step + s.dp(1f), s.h - botY + s.px(0.05f), 0f, Palette.alpha(accent, 0.18f))
                Draw.line(c, x, botY, x + step, centerAt(s, scroll + x + step) + gapAt(s) / 2f, accent, s.dp(2f))
                x += step
            }
            // player
            val px = playerX(s); val pr = s.px(0.035f)
            Draw.glow(c, px, py, pr * 1.8f, accent, 0.4f)
            Draw.fillCircle(c, px, py, pr, Palette.text)
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.1f, s.px(0.06f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "cave_flyer",
            title = "Cave Flyer",
            tagline = "Hold to climb",
            category = Category.ACTION,
            tier = Tier.MINI,
            accent = Palette.violet,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRect(c, cx - size * 0.5f, cy - size * 0.5f, size, size * 0.28f, size * 0.05f, Palette.alpha(color, 0.4f))
                Draw.roundRect(c, cx - size * 0.5f, cy + size * 0.22f, size, size * 0.28f, size * 0.05f, Palette.alpha(color, 0.4f))
                Draw.fillCircle(c, cx - size * 0.1f, cy, size * 0.1f, Palette.text)
            },
            create = { CaveFlyerGame() }
        )
    }
}
