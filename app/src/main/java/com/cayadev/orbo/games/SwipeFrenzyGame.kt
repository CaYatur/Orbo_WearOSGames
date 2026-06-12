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
import com.cayadev.orbo.engine.Swipe
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.game.ArcadeGame

/**
 * Swipe Frenzy — flick the arrow's direction before the timer empties. Each correct flick speeds the
 * next prompt; a wrong direction or running out of time costs a life. Three lives. A pure, frantic
 * directional-reflex drill that works the same on a round watch and a phone.
 */
class SwipeFrenzyGame : ArcadeGame(meta()) {

    private var cur = Swipe.UP
    private var timeLimit = 1.6f
    private var timeLeft = 1.6f
    private var lives = 3
    private var combo = 0
    private var enterT = 0f

    override val controlHint = "FLICK THE ARROW'S WAY"

    override fun onReset() { lives = 3; combo = 0; timeLimit = 1.6f; next() }

    private fun next() {
        cur = Swipe.values()[Mathx.rand(4)]
        timeLimit = Mathx.lerp(1.6f, 0.62f, (score / 700f).coerceIn(0f, 1f))
        timeLeft = timeLimit
        enterT = 0f
    }

    override fun onStep(dt: Float) {
        enterT += dt
        timeLeft -= dt
        if (timeLeft <= 0f) miss()
    }

    private fun miss() {
        lives--; combo = 0
        engine.sfx.fail(); engine.haptics.heavy(); flash(Palette.red, 0.3f); shake(sc.px(0.014f))
        if (lives <= 0) gameOver() else next()
    }

    override fun onFlick(dir: Swipe, vx: Float, vy: Float) {
        if (dir == cur) {
            combo++
            val bonus = (timeLeft / timeLimit * 25f).toInt()
            addScore(10 + bonus + combo)
            engine.sfx.blip(); engine.haptics.light()
            particles.burst(sc.cx, sc.cy, 12, accent, sc.px(0.6f), sc.px(0.016f), 0.4f)
            next()
        } else miss()
    }

    private fun arrow(c: Canvas, s: ScreenInfo, dir: Swipe, cx: Float, cy: Float, len: Float, col: Int) {
        val (dx, dy) = when (dir) {
            Swipe.UP -> 0f to -1f; Swipe.DOWN -> 0f to 1f
            Swipe.LEFT -> -1f to 0f; Swipe.RIGHT -> 1f to 0f
        }
        val tipX = cx + dx * len; val tipY = cy + dy * len
        val baseX = cx - dx * len; val baseY = cy - dy * len
        val w = s.dp(9f)
        Draw.line(c, baseX, baseY, tipX, tipY, col, w)
        // arrowhead (perpendicular)
        val px = -dy; val py = dx; val h = len * 0.5f
        Draw.line(c, tipX, tipY, tipX - dx * h + px * h, tipY - dy * h + py * h, col, w)
        Draw.line(c, tipX, tipY, tipX - dx * h - px * h, tipY - dy * h - py * h, col, w)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val pop = com.cayadev.orbo.engine.Ease.outBack((enterT / 0.18f).coerceIn(0f, 1f))
        val len = s.px(0.18f) * (0.85f + 0.15f * pop)
        Draw.glow(c, s.cx, s.cy, len * 1.6f, accent, 0.25f)
        arrow(c, s, cur, s.cx, s.cy, len, accent)
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val frac = (timeLeft / timeLimit).coerceIn(0f, 1f)
        Ui.edgeProgress(c, s, frac, Palette.lerp(Palette.red, accent, frac))
        val gap = s.px(0.05f); val ly = s.h * 0.84f; val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, ly, s.px(0.016f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, ly, s.px(0.014f), Palette.alpha(Palette.red, 0.35f), s.dp(1.4f))
        }
        if (combo > 1) Draw.textCentered(c, "x$combo", s.cx, s.h * 0.74f, s.px(0.05f), accent, Fonts.bodyBold)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.18f, s.px(0.06f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "swipe_frenzy",
            title = "Swipe Frenzy",
            tagline = "Flick the arrows",
            category = Category.REFLEX,
            tier = Tier.MINI,
            accent = Palette.lime,
            icon = IconDrawer { c, cx, cy, size, color ->
                val len = size * 0.34f; val w = size * 0.1f
                Draw.line(c, cx, cy + len, cx, cy - len, color, w)
                Draw.line(c, cx, cy - len, cx - len * 0.5f, cy - len * 0.5f, color, w)
                Draw.line(c, cx, cy - len, cx + len * 0.5f, cy - len * 0.5f, color, w)
            },
            create = { SwipeFrenzyGame() }
        )
    }
}
