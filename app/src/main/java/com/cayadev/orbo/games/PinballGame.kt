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
import kotlin.math.hypot

/**
 * Pinball — a round-table take on the classic. Gravity drags the ball down the bowl; tap the left or
 * right side to fire that flipper and keep it alive. Smack the bumpers for points. The wall is solid
 * all the way around — the only way to lose the ball is down the central drain mouth at the bottom,
 * which the two flippers guard. Three balls per game.
 */
class PinballGame : ArcadeGame(meta()) {

    private var bx = 0f; private var by = 0f; private var vx = 0f; private var vy = 0f
    private var leftAng = 0f; private var rightAng = 0f
    private var prevLeft = 0f; private var prevRight = 0f
    private var leftActive = false; private var rightActive = false
    private var balls = 3
    private var launchT = 0f

    private class Bumper(val ang: Float, val rad: Float, val r: Float)
    private val bumpers = ArrayList<Bumper>()

    override val controlHint = "TAP LEFT / RIGHT TO FLIP"
    override val gameOverTitle = "GAME OVER"

    private fun arena(s: ScreenInfo) = if (s.isRound) s.r * 0.97f else s.minDim * 0.46f
    private fun ballR(s: ScreenInfo) = s.px(0.035f)
    private fun flipLen(s: ScreenInfo) = arena(s) * 0.34f
    private fun flipThick(s: ScreenInfo) = s.px(0.02f)

    // Central drain mouth at the bottom: the ball is lost only when it slips between the flipper
    // tips and falls past this line near the centre. Everything else is solid wall.
    private fun drainY(s: ScreenInfo) = s.cy + arena(s) * 0.82f
    private fun drainHalfW(s: ScreenInfo) = arena(s) * 0.16f

    // Flippers pivot near the wall at the drain edges and reach in toward the bottom centre, so a
    // ball heading for the drain has to get past one of them.
    private val DOWN = Math.PI.toFloat() / 2f
    private fun pivotRad(s: ScreenInfo) = arena(s) * 0.78f
    private fun pivotL(s: ScreenInfo): FloatArray { val a = DOWN + 0.66f; val fr = pivotRad(s); return floatArrayOf(s.cx + Mathx.cosf(a) * fr, s.cy + Mathx.sinf(a) * fr) }
    private fun pivotR(s: ScreenInfo): FloatArray { val a = DOWN - 0.66f; val fr = pivotRad(s); return floatArrayOf(s.cx + Mathx.cosf(a) * fr, s.cy + Mathx.sinf(a) * fr) }
    private val lRest = 0.46f; private val lUp = -0.42f          // left flipper tip direction (radians)
    private val rRest = Math.PI.toFloat() - 0.46f; private val rUp = Math.PI.toFloat() + 0.42f

    override fun onReset() {
        balls = 3
        bumpers.clear()
        bumpers.add(Bumper(-1.57f, 0f, sc.px(0.055f)))               // exact centre
        bumpers.add(Bumper(-2.4f, arena(sc) * 0.45f, sc.px(0.05f)))  // upper-left
        bumpers.add(Bumper(-0.75f, arena(sc) * 0.45f, sc.px(0.05f))) // upper-right
        leftAng = lRest; rightAng = rRest; prevLeft = lRest; prevRight = rRest
        spawnBall()
    }

    private fun spawnBall() {
        val s = sc
        bx = s.cx + arena(s) * 0.4f; by = s.cy - arena(s) * 0.45f
        vx = -s.px(0.08f); vy = s.px(0.05f); launchT = 0.5f
    }

    override fun onPress(x: Float, y: Float) { if (x < sc.cx) leftActive = true else rightActive = true }
    override fun onRelease(x: Float, y: Float) { leftActive = false; rightActive = false }

    override fun onStep(dt: Float) {
        val s = sc
        if (launchT > 0f) launchT -= dt
        prevLeft = leftAng; prevRight = rightAng
        leftAng = Mathx.approach(leftAng, if (leftActive) lUp else lRest, 24f, dt)
        rightAng = Mathx.approach(rightAng, if (rightActive) rUp else rRest, 24f, dt)

        vy += s.px(2.0f) * dt
        bx += vx * dt; by += vy * dt
        val r = ballR(s); val ar = arena(s)

        // drain — only down the central mouth, below the flippers
        if (launchT <= 0f && by > drainY(s) && kotlin.math.abs(bx - s.cx) < drainHalfW(s)) { drain(); return }

        // solid circular wall everywhere — a ball hugging the wall can never slip off the side
        val d = Mathx.dist(bx, by, s.cx, s.cy)
        if (d > ar - r && d > 0.0001f) {
            val nx = (s.cx - bx) / d; val ny = (s.cy - by) / d   // inward normal
            val dot = vx * nx + vy * ny
            vx = (vx - 2f * dot * nx) * 0.78f
            vy = (vy - 2f * dot * ny) * 0.78f
            bx = s.cx - nx * (ar - r); by = s.cy - ny * (ar - r)
            engine.sfx.tone(com.cayadev.orbo.engine.Sfx.Wave.SINE, 200f, 200f, 0.04f, 0.2f)
        }

        // bumpers
        for (b in bumpers) {
            val bxp = s.cx + Mathx.cosf(b.ang) * b.rad; val byp = s.cy + Mathx.sinf(b.ang) * b.rad
            val dd = hypot((bx - bxp).toDouble(), (by - byp).toDouble()).toFloat()
            if (dd < b.r + r && dd > 0.0001f) {
                val nx = (bx - bxp) / dd; val ny = (by - byp) / dd
                bx = bxp + nx * (b.r + r); by = byp + ny * (b.r + r)
                val dot = vx * nx + vy * ny
                vx = (vx - 2f * dot * nx) * 0.8f + nx * s.px(0.6f)
                vy = (vy - 2f * dot * ny) * 0.8f + ny * s.px(0.6f)
                addScore(10); engine.sfx.coin(); engine.haptics.tick()
                particles.burst(bxp, byp, 8, accent, s.px(0.6f), s.px(0.012f), 0.3f)
            }
        }

        // flippers
        collideFlipper(pivotL(s), leftAng, prevLeft, dt, leftActive)
        collideFlipper(pivotR(s), rightAng, prevRight, dt, rightActive)

        // clamp speed
        val sp = hypot(vx.toDouble(), vy.toDouble()).toFloat(); val mx = s.px(2.4f)
        if (sp > mx) { vx = vx / sp * mx; vy = vy / sp * mx }
    }

    private fun collideFlipper(pivot: FloatArray, ang: Float, prevAng: Float, dt: Float, active: Boolean) {
        val s = sc; val r = ballR(s); val len = flipLen(s); val th = flipThick(s)
        val tipX = pivot[0] + Mathx.cosf(ang) * len; val tipY = pivot[1] + Mathx.sinf(ang) * len
        // closest point on segment pivot->tip to ball
        val dx = tipX - pivot[0]; val dy = tipY - pivot[1]
        val l2 = dx * dx + dy * dy
        var t = if (l2 > 0f) ((bx - pivot[0]) * dx + (by - pivot[1]) * dy) / l2 else 0f
        t = t.coerceIn(0f, 1f)
        val cxp = pivot[0] + dx * t; val cyp = pivot[1] + dy * t
        val dd = hypot((bx - cxp).toDouble(), (by - cyp).toDouble()).toFloat()
        if (dd < r + th && dd > 0.0001f) {
            val nx = (bx - cxp) / dd; val ny = (by - cyp) / dd
            bx = cxp + nx * (r + th); by = cyp + ny * (r + th)
            val dot = vx * nx + vy * ny
            vx = (vx - 2f * dot * nx) * 0.55f
            vy = (vy - 2f * dot * ny) * 0.55f
            // kick from flipper swing: while held it swings up, launching the ball outward along the
            // contact normal (which points from the flipper surface to the ball).
            val omega = kotlin.math.abs((ang - prevAng) / dt)
            if (active && omega > 1.5f) {
                val kick = (omega * (t * len) * 0.5f).coerceAtMost(s.px(3.2f))
                vx += nx * kick; vy += ny * kick
            }
            engine.sfx.click(); engine.haptics.light()
        }
    }

    private fun drain() {
        balls--
        engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.3f); shake(sc.px(0.015f))
        if (balls <= 0) gameOver() else spawnBall()
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val ar = arena(s)
        // table ring with a gap at the bottom for the drain mouth
        val gapDeg = 14f
        Draw.arc(c, s.cx, s.cy, ar, 90f + gapDeg, 360f - 2f * gapDeg, Palette.alpha(accent, 0.3f), s.dp(3f))
        // drain mouth (red) — the only opening
        Draw.arc(c, s.cx, s.cy, ar, 90f - gapDeg, 2f * gapDeg, Palette.alpha(Palette.red, 0.55f), s.dp(3f))
        // bumpers
        for (b in bumpers) {
            val bxp = s.cx + Mathx.cosf(b.ang) * b.rad; val byp = s.cy + Mathx.sinf(b.ang) * b.rad
            Draw.glow(c, bxp, byp, b.r * 1.3f, accent, 0.25f)
            Draw.fillCircle(c, bxp, byp, b.r, Palette.panelHi)
            Draw.strokeCircle(c, bxp, byp, b.r, Palette.alpha(accent, 0.7f), s.dp(2f))
            Draw.fillCircle(c, bxp, byp, b.r * 0.4f, accent)
        }
        // flippers
        drawFlipper(c, s, pivotL(s), leftAng)
        drawFlipper(c, s, pivotR(s), rightAng)
        // ball
        Draw.glow(c, bx, by, ballR(s) * 1.5f, Palette.text, 0.3f)
        Draw.fillCircle(c, bx, by, ballR(s), Palette.text)
        Draw.fillCircle(c, bx - ballR(s) * 0.3f, by - ballR(s) * 0.3f, ballR(s) * 0.3f, Palette.alpha(Palette.bg, 0.4f))
    }

    private fun drawFlipper(c: Canvas, s: ScreenInfo, pivot: FloatArray, ang: Float) {
        val len = flipLen(s)
        val tipX = pivot[0] + Mathx.cosf(ang) * len; val tipY = pivot[1] + Mathx.sinf(ang) * len
        Draw.line(c, pivot[0], pivot[1], tipX, tipY, accent, flipThick(s) * 2f)
        Draw.fillCircle(c, pivot[0], pivot[1], flipThick(s) * 1.2f, Palette.shade(accent, -0.2f))
        Draw.fillCircle(c, tipX, tipY, flipThick(s), Palette.shade(accent, 0.15f))
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.045f); val ly = s.cy + s.px(0.06f); val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < balls) Draw.fillCircle(c, startX + i * gap, ly, s.px(0.014f), Palette.text)
            else Draw.strokeCircle(c, startX + i * gap, ly, s.px(0.012f), Palette.alpha(Palette.text, 0.3f), s.dp(1.2f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.cy - s.px(0.04f), s.px(0.09f), Palette.alpha(Palette.text, 0.85f), Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "pinball",
            title = "Pinball",
            tagline = "Keep it alive",
            category = Category.ARCADE,
            tier = Tier.MINI,
            accent = Palette.orange,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.strokeCircle(c, cx, cy, size * 0.42f, Palette.alpha(color, 0.5f), size * 0.05f)
                Draw.fillCircle(c, cx - size * 0.15f, cy - size * 0.1f, size * 0.1f, color)
                Draw.line(c, cx - size * 0.28f, cy + size * 0.3f, cx, cy + size * 0.18f, color, size * 0.06f)
                Draw.line(c, cx + size * 0.28f, cy + size * 0.3f, cx, cy + size * 0.18f, color, size * 0.06f)
            },
            create = { PinballGame() }
        )
    }
}
