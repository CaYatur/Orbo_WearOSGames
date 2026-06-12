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
import kotlin.math.hypot

/** Brick Breaker — Arkanoid in the round. The ball ricochets off the dial rim; slide the paddle to
 * keep it alive, smash the bricks, grab power-ups (multiball, wide, slow) and climb the levels. */
class BrickBreakerGame : ArcadeGame(meta()) {

    private class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float)
    private class Brick(val cx: Float, val cy: Float, val w: Float, val h: Float, var hp: Int, val color: Int)
    private class Power(var x: Float, var y: Float, val type: Int) // 0 multi,1 wide,2 slow

    private val balls = ArrayList<Ball>()
    private val bricks = ArrayList<Brick>()
    private val powers = ArrayList<Power>()
    private var px = 0f; private var targetPx = 0f
    private var paddleW = 0f
    private var wideT = 0f
    private var slowT = 0f
    private var lives = 3
    private var level = 1
    private var speed = 0f
    private val paddleY get() = sc.h * 0.82f
    private val ballR get() = sc.px(0.028f)

    override val controlHint = "DRAG TO MOVE PADDLE"

    override fun onReset() {
        px = sc.cx; targetPx = sc.cx; paddleW = sc.px(0.22f)
        lives = 3; level = 1; wideT = 0f; slowT = 0f
        speed = sc.px(0.75f)
        buildLevel(); resetBall()
    }

    private fun buildLevel() {
        bricks.clear(); powers.clear()
        val cols = 6
        val rows = (3 + level).coerceAtMost(6)
        val gw = sc.px(if (sc.isRound) 0.66f else 0.86f); val bw = gw / cols
        val bh = sc.px(0.05f)
        val top = sc.h * 0.16f
        for (r in 0 until rows) for (cc in 0 until cols) {
            val bx = sc.cx - gw / 2 + cc * bw + bw / 2
            val by = top + r * (bh + sc.px(0.012f))
            if (!sc.insideFace(bx, by, bw * 0.4f)) continue
            val hp = if (r == 0 && level >= 2) 2 else 1
            val col = Palette.accents[(r + cc) % Palette.accents.size]
            bricks.add(Brick(bx, by, bw * 0.92f, bh, hp, col))
        }
    }

    private fun resetBall() {
        balls.clear()
        balls.add(Ball(px, paddleY - ballR * 2, Mathx.rand(-0.4f, 0.4f) * speed, -speed))
    }

    override fun onPress(x: Float, y: Float) { targetPx = x }
    override fun onDrag(x: Float, y: Float) { targetPx = x }

    private fun curPaddleW() = paddleW * (if (wideT > 0f) 1.6f else 1f)

    override fun onStep(dt: Float) {
        if (wideT > 0f) wideT -= dt
        if (slowT > 0f) slowT -= dt
        val pw = curPaddleW()
        val hw = halfW(paddleY)
        targetPx = targetPx.coerceIn(sc.cx - hw + pw / 2, sc.cx + hw - pw / 2)
        px = Mathx.approach(px, targetPx, 22f, dt)

        val spd = if (slowT > 0f) 0.6f else 1f
        val it = balls.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.x += b.vx * dt * spd; b.y += b.vy * dt * spd
            rimBounce(b)
            paddleBounce(b)
            brickCollide(b)
            if (b.y - ballR > sc.h * 0.97f) it.remove()
        }
        if (balls.isEmpty()) {
            lives--
            if (lives <= 0) { gameOver() } else { engine.sfx.fail(); engine.haptics.heavy(); resetBall() }
        }
        // powerups fall
        val pit = powers.iterator()
        while (pit.hasNext()) {
            val p = pit.next(); p.y += sc.px(0.4f) * dt
            if (p.y > sc.h) { pit.remove(); continue }
            if (abs(p.x - px) < curPaddleW() / 2 + sc.px(0.03f) && abs(p.y - paddleY) < sc.px(0.05f)) {
                applyPower(p.type); pit.remove()
            }
        }
        if (bricks.isEmpty()) {
            level++; addScore(50); engine.sfx.success(); engine.haptics.success()
            speed = (speed + sc.px(0.04f)).coerceAtMost(sc.px(1.1f))
            buildLevel(); resetBall()
        }
    }

    private fun halfW(y: Float): Float {
        if (!sc.isRound) return sc.w / 2f - sc.px(0.02f)
        val dy = y - sc.cy; val v = sc.r * sc.r - dy * dy
        return if (v <= 0) sc.px(0.05f) else kotlin.math.sqrt(v) - sc.px(0.02f)
    }

    private fun rimBounce(b: Ball) {
        if (!sc.isRound) {
            // Phone / square: bounce off the ACTUAL screen edges (left, right, top). The round "dial
            // rim" was a centred circle, which on a tall phone put the top wall mid-screen — above
            // the bricks, so the ball never reached them and nothing could be broken.
            val m = ballR + sc.px(0.01f)
            val topWall = sc.h * 0.135f
            if (b.x < m) { b.x = m; b.vx = abs(b.vx); engine.sfx.click() }
            if (b.x > sc.w - m) { b.x = sc.w - m; b.vx = -abs(b.vx); engine.sfx.click() }
            if (b.y < topWall) { b.y = topWall; b.vy = abs(b.vy); engine.sfx.click() }
            return
        }
        val limit = sc.r - ballR - sc.px(0.01f)
        val dxc = b.x - sc.cx; val dyc = b.y - sc.cy
        val dd = hypot(dxc, dyc)
        if (dd > limit) {
            val nx = dxc / dd; val ny = dyc / dd
            if (ny > 0.60f) return  // bottom "mouth" — paddle territory
            val dot = b.vx * nx + b.vy * ny
            b.vx -= 2 * dot * nx; b.vy -= 2 * dot * ny
            b.x = sc.cx + nx * limit; b.y = sc.cy + ny * limit
            engine.sfx.click()
        }
    }

    private fun paddleBounce(b: Ball) {
        if (b.vy > 0 && b.y + ballR >= paddleY && b.y < paddleY + sc.px(0.05f)) {
            val pw = curPaddleW()
            if (abs(b.x - px) < pw / 2 + ballR) {
                b.y = paddleY - ballR
                val off = (b.x - px) / (pw / 2)
                val sp = hypot(b.vx, b.vy)
                val ang = off * 1.0f - 1.5708f   // -90° ± spread
                b.vx = Mathx.cosf(ang) * sp; b.vy = Mathx.sinf(ang) * sp
                if (b.vy > -0.2f * sp) b.vy = -0.4f * sp
                engine.sfx.blip(); engine.haptics.light()
            }
        }
    }

    private fun brickCollide(b: Ball) {
        val it = bricks.iterator()
        while (it.hasNext()) {
            val br = it.next()
            if (abs(b.x - br.cx) < br.w / 2 + ballR && abs(b.y - br.cy) < br.h / 2 + ballR) {
                // determine side
                val overlapX = (br.w / 2 + ballR) - abs(b.x - br.cx)
                val overlapY = (br.h / 2 + ballR) - abs(b.y - br.cy)
                if (overlapX < overlapY) b.vx = -b.vx else b.vy = -b.vy
                br.hp--
                addScore(5)
                engine.sfx.beep(520f + br.hp * 80f, 0.04f, 0.3f); engine.haptics.tick()
                particles.burst(br.cx, br.cy, 8, br.color, sc.px(0.4f), sc.px(0.012f), 0.3f)
                if (br.hp <= 0) {
                    if (Mathx.chance(0.14f)) powers.add(Power(br.cx, br.cy, Mathx.rand(3)))
                    it.remove()
                }
                return
            }
        }
    }

    private fun applyPower(type: Int) {
        engine.sfx.powerup(); engine.haptics.medium()
        when (type) {
            0 -> { // multiball
                val cur = ArrayList(balls)
                for (b in cur) { if (balls.size < 6) balls.add(Ball(b.x, b.y, -b.vy * 0.4f + Mathx.rand(-0.2f, 0.2f) * speed, -abs(b.vy))) }
            }
            1 -> wideT = 9f
            2 -> slowT = 7f
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, 0xFF0A0C18.toInt(), Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            // rim
            if (s.isRound) Draw.strokeCircle(c, s.cx, s.cy, s.r - ballR - s.px(0.01f), Palette.alpha(Palette.line, 0.4f), s.dp(1.5f))
            for (br in bricks) {
                val col = if (br.hp >= 2) Palette.shade(br.color, 0.2f) else br.color
                Draw.roundRectCentered(c, br.cx, br.cy, br.w, br.h, br.h * 0.25f, col)
                Draw.roundRectCentered(c, br.cx, br.cy - br.h * 0.18f, br.w * 0.9f, br.h * 0.35f, br.h * 0.2f, Palette.alpha(Palette.text, 0.14f))
            }
            for (p in powers) {
                val col = when (p.type) { 0 -> Palette.cyan; 1 -> Palette.lime; else -> Palette.violet }
                Draw.glow(c, p.x, p.y, sc.px(0.05f), col, 0.4f)
                Draw.roundRectCentered(c, p.x, p.y, sc.px(0.06f), sc.px(0.06f), sc.px(0.02f), col)
                val ch = when (p.type) { 0 -> "M"; 1 -> "W"; else -> "S" }
                Draw.textCentered(c, ch, p.x, p.y, sc.px(0.035f), Palette.bg, Fonts.bodyBold)
            }
            // paddle
            val pw = curPaddleW()
            Draw.glow(c, px, paddleY, pw * 0.5f, accent, 0.3f)
            Draw.roundRectCentered(c, px, paddleY, pw, sc.px(0.04f), sc.px(0.02f), accent)
            // balls
            for (b in balls) {
                Draw.glow(c, b.x, b.y, ballR * 2.2f, Palette.text, 0.4f)
                Draw.fillCircle(c, b.x, b.y, ballR, Palette.text)
            }
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.045f); val y = s.h * 0.92f; val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, y, s.px(0.015f), accent)
            else Draw.strokeCircle(c, startX + i * gap, y, s.px(0.013f), Palette.alpha(accent, 0.35f), s.dp(1.5f))
        }
        Draw.textCentered(c, "${Loc.t("LV")} $level", s.cx, s.h * 0.10f, s.px(0.04f), Palette.textDim, Fonts.mono)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.07f, s.px(0.05f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "brick_breaker",
            title = "Neon Breaker",
            tagline = "Smash the wall",
            category = Category.ARCADE,
            tier = Tier.BIG,
            accent = Palette.orange,
            featured = true,
            icon = IconDrawer { c, cx, cy, size, color ->
                for (r in 0 until 2) for (cc in 0 until 3) {
                    Draw.roundRectCentered(c, cx + (cc - 1) * size * 0.24f, cy - size * 0.22f + r * size * 0.16f, size * 0.2f, size * 0.12f, size * 0.03f,
                        Palette.accents[(r + cc) % Palette.accents.size])
                }
                Draw.fillCircle(c, cx, cy + size * 0.12f, size * 0.08f, Palette.text)
                Draw.roundRectCentered(c, cx, cy + size * 0.3f, size * 0.4f, size * 0.08f, size * 0.04f, color)
            },
            create = { BrickBreakerGame() }
        )
    }
}
