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
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.game.ArcadeGame

/**
 * Timber — chop the endless trunk. Tap the side with no branch to swing your axe; the trunk drops a
 * notch with every chop. Hit a branch on your side and you're squashed. Keep the timber-clock topped
 * up by chopping fast — let it empty and the day's done.
 */
class TimberGame : ArcadeGame(meta()) {

    // branch: 0 none, 1 left, 2 right
    private val seg = ArrayList<Int>()
    private val visible = 7
    private var side = 0          // 0 left, 1 right
    private var timeBar = 1f
    private var chopFx = 0f

    override val controlHint = "TAP THE SAFE SIDE"

    override fun onReset() {
        seg.clear(); side = 0; timeBar = 1f; chopFx = 0f
        seg.add(0); seg.add(0)
        while (seg.size < visible + 3) seg.add(genSeg())
    }

    private fun genSeg(): Int {
        // at most one branch; bias toward branches as score rises
        return if (Mathx.chance(0.45f)) (if (Mathx.chance(0.5f)) 1 else 2) else 0
    }

    override fun onTapWorld(x: Float, y: Float) { chop(if (x < sc.cx) 0 else 1) }

    private fun chop(s: Int) {
        side = s
        val branchSide = if (s == 0) 1 else 2
        // the segment that becomes the new base after this chop
        val nextBase = seg.getOrElse(1) { 0 }
        if (nextBase == branchSide) {
            engine.sfx.explosion(); engine.haptics.heavy(); flash(Palette.red, 0.5f); shake(sc.px(0.022f))
            gameOver(); return
        }
        seg.removeAt(0)
        seg.add(genSeg())
        addScore(1); chopFx = 0.18f
        timeBar = (timeBar + 0.07f).coerceAtMost(1f)
        engine.sfx.tone(com.cayadev.orbo.engine.Sfx.Wave.SQUARE, 300f, 200f, 0.05f, 0.4f)
        engine.haptics.light()
        particles.burst(sc.cx + (if (s == 0) -1 else 1) * sc.px(0.12f), sc.h * 0.7f, 8, accent, sc.px(0.5f), sc.px(0.012f), 0.3f)
    }

    override fun onStep(dt: Float) {
        if (chopFx > 0f) chopFx -= dt
        val drain = Mathx.lerp(0.13f, 0.32f, (score / 120f).coerceIn(0f, 1f))
        timeBar -= drain * dt
        if (timeBar <= 0f) { timeBar = 0f; engine.sfx.fail(); gameOver() }
    }

    private fun trunkW(s: ScreenInfo) = s.px(0.17f)
    private fun segH(s: ScreenInfo) = s.px(0.115f)
    private fun baseY(s: ScreenInfo) = s.h * 0.82f

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            val tw = trunkW(s); val sh = segH(s); val by = baseY(s)
            for (i in 0 until visible) {
                val b = seg.getOrElse(i) { 0 }
                val cy = by - i * sh
                Draw.roundRectCentered(c, s.cx, cy, tw, sh - s.dp(2f), tw * 0.12f, Palette.lerp(0xFF7A4B2A.toInt(), 0xFF935C34.toInt(), (i % 2).toFloat()))
                Draw.roundRectStroke(c, s.cx - tw / 2, cy - (sh - s.dp(2f)) / 2, tw, sh - s.dp(2f), tw * 0.12f, Palette.alpha(0xFF5A3720.toInt(), 0.8f), s.dp(1.5f))
                if (b == 1) Draw.roundRectCentered(c, s.cx - tw * 0.78f, cy, tw * 0.7f, sh * 0.5f, sh * 0.12f, Palette.lerp(accent, Palette.lime, 0.3f))
                if (b == 2) Draw.roundRectCentered(c, s.cx + tw * 0.78f, cy, tw * 0.7f, sh * 0.5f, sh * 0.12f, Palette.lerp(accent, Palette.lime, 0.3f))
            }
            // lumberjack
            val px = if (side == 0) s.cx - tw * 0.85f else s.cx + tw * 0.85f
            val py = by
            val pop = if (chopFx > 0f) 1.1f else 1f
            Draw.fillCircle(c, px, py - sh * 0.2f, sh * 0.18f * pop, Palette.text)
            Draw.roundRectCentered(c, px, py + sh * 0.1f, sh * 0.3f, sh * 0.4f, sh * 0.08f, accent)
            // axe
            val ax = if (side == 0) px + tw * 0.3f else px - tw * 0.3f
            Draw.line(c, px, py - sh * 0.1f, ax, py - sh * 0.25f, Palette.textDim, s.dp(2.5f))
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        Ui.edgeProgress(c, s, timeBar, Palette.lerp(Palette.red, accent, timeBar))
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.16f, s.px(0.075f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "timber",
            title = "Timber",
            tagline = "Chop the trunk",
            category = Category.ARCADE,
            tier = Tier.MINI,
            accent = Palette.amber,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx, cy, size * 0.34f, size * 0.9f, size * 0.05f, 0xFF935C34.toInt())
                Draw.roundRectCentered(c, cx - size * 0.4f, cy - size * 0.15f, size * 0.4f, size * 0.18f, size * 0.04f, color)
                Draw.roundRectCentered(c, cx + size * 0.4f, cy + size * 0.2f, size * 0.4f, size * 0.18f, size * 0.04f, color)
            },
            create = { TimberGame() }
        )
    }
}
