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

/** Spiral Drop — a Helix-style descent. The ball bounces down a tower of rings; drag to spin them
 * so the gap slides under the ball. Thread the gaps, never touch the red. */
class SpiralDropGame : ArcadeGame(meta()) {

    private class Ring(var y: Float, var gap: Float, var gapW: Float, var danger: Boolean,
                       var dgr: Float, var dgrW: Float, var passed: Boolean = false, var hue: Int)

    private val rings = ArrayList<Ring>()
    private var rot = 0f
    private var lastX = 0f
    private var by = 0f
    private var vy = 0f
    private var prevBy = 0f
    private var camY = 0f
    private var startY = 0f
    private var deepest = 0f
    private var highestSpawn = 0f
    private var scoreShadow = 0

    private val channelW get() = sc.px(0.66f)
    private val channelLeft get() = sc.cx - channelW / 2
    private val ballR get() = sc.px(0.05f)
    private val grav get() = sc.px(2.7f)
    private val bounceV get() = -sc.px(0.92f)
    private val ringGap get() = sc.px(0.2f)

    override val controlHint = "DRAG TO SPIN"

    override fun onReset() {
        rings.clear(); rot = 0f
        camY = 0f
        by = sc.h * 0.28f; prevBy = by; vy = 0f
        startY = by; deepest = by; scoreShadow = 0
        highestSpawn = sc.h * 0.5f
        repeat(8) { addRingBelow() }
    }

    private fun addRingBelow() {
        highestSpawn += ringGap
        val danger = Mathx.chance(0.4f)
        val gap = Mathx.rand(0f, 1f)
        var dgr = Mathx.rand(0f, 1f)
        // keep danger away from gap a bit
        if (abs(loopDiff(dgr, gap)) < 0.2f) dgr = loop01(gap + 0.5f)
        rings.add(Ring(highestSpawn, gap, 0.22f, danger, dgr, 0.16f, hue = Palette.accents[Mathx.rand(Palette.accents.size)]))
    }

    private fun loop01(v: Float): Float { var x = v % 1f; if (x < 0) x += 1f; return x }
    private fun loopDiff(a: Float, b: Float): Float { var d = (a - b) % 1f; if (d < -0.5f) d += 1f; if (d > 0.5f) d -= 1f; return d }
    private fun centerInRange(centerFrac: Float, rangeCenter: Float, rangeW: Float): Boolean =
        abs(loopDiff(centerFrac, loop01(rangeCenter + rot))) <= rangeW / 2

    override fun onPress(x: Float, y: Float) { lastX = x }
    override fun onDrag(x: Float, y: Float) { rot += (x - lastX) / channelW; lastX = x; rot = loop01(rot) }

    override fun onStep(dt: Float) {
        val diff = (score / 30f).coerceIn(0f, 1f)
        prevBy = by
        vy += grav * dt
        by += vy * dt

        // collisions with rings (only when falling)
        if (vy > 0) {
            for (r in rings) {
                if (r.passed) continue
                if (prevBy + ballR <= r.y && by + ballR >= r.y) {
                    val inGap = centerInRange(0.5f, r.gap, r.gapW)
                    if (inGap) {
                        r.passed = true; addScoreDeep()
                        engine.sfx.blip(); engine.haptics.light()
                        particles.burst(sc.cx, r.y, 8, r.hue, sc.px(0.4f), sc.px(0.012f), 0.3f)
                    } else {
                        if (r.danger && centerInRange(0.5f, r.dgr, r.dgrW)) { crash(); return }
                        // bounce
                        by = r.y - ballR; vy = bounceV
                        engine.sfx.beep(440f, 0.05f, 0.3f); engine.haptics.tick()
                        break
                    }
                }
            }
        }

        // camera follows
        val line = camY + sc.h * 0.32f
        if (by > line) camY = by - sc.h * 0.32f
        if (by > deepest) deepest = by

        // recycle/spawn
        val it = rings.iterator()
        while (it.hasNext()) { if (it.next().y < camY - sc.px(0.1f)) it.remove() }
        while (highestSpawn < camY + sc.h + ringGap) addRingBelow()
    }

    private fun addScoreDeep() { addScore(1); scoreShadow = score }

    private fun crash() {
        shake(sc.px(0.03f)); flash(Palette.red, 0.45f); engine.sfx.explosion(); engine.haptics.heavy()
        particles.burst(sc.cx, by, 24, Palette.red, sc.px(0.8f), sc.px(0.02f), 0.6f)
        gameOver()
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.vGradientRect(c, 0f, 0f, s.w, s.h, 0xFF0A0716.toInt(), Palette.bg)
        Draw.vignette(c, s, Palette.alpha(Palette.bg, 0f), Palette.bg)
        if (state == GState.READY) return

        Draw.withFaceClip(c, s) {
            // central pole
            Draw.roundRect(c, s.cx - s.dp(3f), 0f, s.dp(6f), s.h, s.dp(3f), Palette.alpha(Palette.line, 0.5f))
            for (r in rings) {
                val sy = r.y - camY
                if (sy < -ringGap || sy > s.h + ringGap) continue
                drawRing(c, s, r, sy)
            }
            // ball
            val sy = by - camY
            Draw.glow(c, s.cx, sy, ballR * 2.1f, accent, 0.5f)
            Draw.fillCircle(c, s.cx, sy, ballR, accent)
            Draw.fillCircle(c, s.cx, sy, ballR * 0.55f, Palette.shade(accent, 0.4f))
        }
    }

    private fun drawRing(c: Canvas, s: ScreenInfo, r: Ring, sy: Float) {
        val h = s.px(0.045f)
        val gapCenter = loop01(r.gap + rot)
        // solid = complement of gap on the loop
        val s0 = loop01(gapCenter + r.gapW / 2)
        val len = 1f - r.gapW
        drawLoopBar(c, s0, len, sy, h, Palette.lerp(r.hue, Palette.panelHi, 0.25f))
        // danger overlay
        if (r.danger) {
            val dCenter = loop01(r.dgr + rot)
            drawLoopBar(c, loop01(dCenter - r.dgrW / 2), r.dgrW, sy, h, Palette.red)
        }
    }

    private fun drawLoopBar(c: Canvas, startFrac: Float, lenFrac: Float, sy: Float, h: Float, col: Int) {
        val cw = channelW; val left = channelLeft
        var s0 = startFrac
        var remaining = lenFrac
        var guard = 0
        while (remaining > 0.0001f && guard++ < 3) {
            val end = (s0 + remaining).coerceAtMost(1f)
            val x0 = left + s0 * cw
            val x1 = left + end * cw
            Draw.roundRect(c, x0, sy - h / 2, (x1 - x0).coerceAtLeast(1f), h, h * 0.3f, col)
            val drawn = end - s0
            remaining -= drawn
            s0 = 0f
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        // rotation hint dial
        Draw.strokeCircle(c, s.cx, s.h * 0.12f, s.px(0.03f), Palette.alpha(accent, 0.4f), s.dp(2f))
        val a = -rot * Mathx.TAU
        Draw.fillCircle(c, s.cx + Mathx.cosf(a - 1.5708f) * s.px(0.03f), s.h * 0.12f + Mathx.sinf(a - 1.5708f) * s.px(0.03f), s.dp(3f), accent)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.20f, s.px(0.075f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "spiral_drop",
            title = "Spiral Drop",
            tagline = "Thread the gaps",
            category = Category.ARCADE,
            tier = Tier.MINI,
            accent = Palette.amber,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx, cy - size * 0.18f, size * 0.6f, size * 0.1f, size * 0.05f, Palette.violet)
                Draw.roundRectCentered(c, cx + size * 0.08f, cy + size * 0.02f, size * 0.5f, size * 0.1f, size * 0.05f, Palette.pink)
                Draw.fillCircle(c, cx - size * 0.12f, cy + size * 0.22f, size * 0.12f, color)
            },
            create = { SpiralDropGame() }
        )
    }
}
