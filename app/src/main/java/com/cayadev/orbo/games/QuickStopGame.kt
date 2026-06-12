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
import kotlin.math.abs

/**
 * Quick Stop — a precision timing game. A marker ping-pongs across the gauge; tap to stop it as
 * close to the green zone's centre as you can. Bullseye hits score big and grow your streak; the
 * gauge then speeds up and the zone shrinks. Three misses (outside the zone) and you're done.
 */
class QuickStopGame : ArcadeGame(meta()) {

    private var pos = 0f          // 0..1 along the bar
    private var dir = 1f
    private var speed = 0f
    private var target = 0.5f     // 0..1
    private var band = 0.16f      // half-width of accept zone, in pos units
    private var lives = 3
    private var streak = 0
    private var resultFx = 0f
    private var lastPerfect = false

    override val controlHint = "TAP TO STOP IN THE ZONE"

    override fun onReset() { speed = 0.9f; band = 0.17f; lives = 3; streak = 0; resultFx = 0f; pos = 0f; dir = 1f; newTarget() }

    private fun newTarget() {
        target = Mathx.rand(band + 0.05f, 1f - band - 0.05f)
    }

    private fun barW(s: ScreenInfo) = if (s.isRound) s.minDim * 0.62f else s.w * 0.84f
    private fun barLeft(s: ScreenInfo) = s.cx - barW(s) / 2f
    private fun barY(s: ScreenInfo) = s.h * 0.55f

    override fun onStep(dt: Float) {
        if (resultFx > 0f) resultFx -= dt
        pos += dir * speed * dt
        if (pos > 1f) { pos = 1f; dir = -1f }
        if (pos < 0f) { pos = 0f; dir = 1f }
    }

    override fun onTapWorld(x: Float, y: Float) {
        val d = abs(pos - target)
        if (d <= band) {
            streak++
            val precision = 1f - d / band
            lastPerfect = precision > 0.8f
            val pts = (10 + precision * 40f).toInt() + streak * 2
            addScore(pts)
            engine.sfx.coin(); engine.haptics.light(); resultFx = 0.4f
            val s = sc
            particles.burst(barLeft(s) + pos * barW(s), barY(s), 12, accent, s.px(0.5f), s.px(0.016f), 0.4f)
            speed = (speed + 0.085f).coerceAtMost(3.2f)
            band = (band - 0.008f).coerceAtLeast(0.06f)
            newTarget()
        } else {
            lives--; streak = 0; lastPerfect = false
            engine.sfx.fail(); engine.haptics.heavy(); flash(Palette.red, 0.3f); shake(sc.px(0.012f))
            if (lives <= 0) gameOver() else { resultFx = 0.4f; newTarget() }
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val bw = barW(s); val bx = barLeft(s); val by = barY(s); val bh = s.px(0.05f)
        // track
        Draw.roundRect(c, bx, by - bh / 2, bw, bh, bh / 2, Palette.track)
        // zone
        val zx = bx + (target - band) * bw
        val zw = band * 2 * bw
        Draw.roundRect(c, zx, by - bh / 2, zw, bh, bh / 2, Palette.alpha(accent, 0.5f))
        Draw.line(c, bx + target * bw, by - bh, bx + target * bw, by + bh, Palette.alpha(accent, 0.8f), s.dp(1.5f))
        // marker
        val mx = bx + pos * bw
        Draw.glow(c, mx, by, s.px(0.05f), Palette.text, 0.4f)
        Draw.roundRectCentered(c, mx, by, s.px(0.03f), bh * 1.8f, s.px(0.012f), Palette.text)
        // lives
        val gap = s.px(0.05f); val ly = s.h * 0.85f; val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, ly, s.px(0.016f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, ly, s.px(0.014f), Palette.alpha(Palette.red, 0.35f), s.dp(1.4f))
        }
        if (resultFx > 0f && lastPerfect && streak > 0)
            Draw.textCentered(c, Loc.t("PERFECT"), s.cx, s.h * 0.4f, s.px(0.05f), accent, Fonts.bodyBold,
                resultFx.coerceIn(0f, 1f), 0.1f)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.27f, s.px(0.085f), Palette.text, Fonts.display)
        if (streak > 1) Draw.textCentered(c, "${Loc.t("STREAK")} $streak", s.cx, s.h * 0.345f, s.px(0.038f), accent, Fonts.mono, letterSpacing = 0.1f)
    }

    companion object {
        fun meta() = GameMeta(
            id = "quick_stop",
            title = "Quick Stop",
            tagline = "Stop on the mark",
            category = Category.REFLEX,
            tier = Tier.MINI,
            accent = Palette.amber,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx, cy, size * 0.8f, size * 0.16f, size * 0.08f, Palette.alpha(color, 0.3f))
                Draw.roundRectCentered(c, cx + size * 0.1f, cy, size * 0.24f, size * 0.16f, size * 0.06f, color)
                Draw.roundRectCentered(c, cx - size * 0.05f, cy, size * 0.07f, size * 0.34f, size * 0.03f, Palette.text)
            },
            create = { QuickStopGame() }
        )
    }
}
