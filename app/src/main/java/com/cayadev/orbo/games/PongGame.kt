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
import kotlin.math.sqrt

/** Pong — a circular-court duel against a sharpening AI. Return the ball to score; miss and it's
 * over. Slip one past the AI for bonus points. */
class PongGame : ArcadeGame(meta()) {

    private var ballX = 0f; private var ballY = 0f
    private var vx = 0f; private var vy = 0f
    private var px = 0f; private var targetPx = 0f
    private var aiX = 0f
    private var speed = 0f
    private val pw get() = sc.px(0.22f)
    private val aw get() = sc.px(0.2f)
    private val ballR get() = sc.px(0.035f)
    private val playerY get() = sc.h * 0.86f
    private val aiY get() = sc.h * 0.14f
    private var serveT = 0f

    override val controlHint = "DRAG TO MOVE PADDLE"

    override fun onReset() {
        px = sc.cx; targetPx = sc.cx; aiX = sc.cx
        speed = sc.px(0.6f); serve(toPlayer = false)
    }

    private fun serve(toPlayer: Boolean) {
        ballX = sc.cx; ballY = sc.cy
        val ang = Mathx.rand(-0.5f, 0.5f)
        vx = Mathx.sinf(ang) * speed
        vy = (if (toPlayer) 1f else -1f) * speed
        serveT = 0.5f
    }

    private fun halfW(y: Float): Float {
        if (!sc.isRound) return sc.w / 2f - sc.px(0.02f)
        val dy = y - sc.cy; val v = sc.r * sc.r - dy * dy
        return if (v <= 0) sc.px(0.05f) else sqrt(v) - sc.px(0.02f)
    }

    override fun onPress(x: Float, y: Float) { targetPx = x }
    override fun onDrag(x: Float, y: Float) { targetPx = x }

    override fun onStep(dt: Float) {
        speed = Mathx.lerp(sc.px(0.6f), sc.px(1.25f), (score / 30f).coerceIn(0f, 1f))
        // player paddle
        val hwP = halfW(playerY)
        targetPx = targetPx.coerceIn(sc.cx - hwP + pw / 2, sc.cx + hwP - pw / 2)
        px = Mathx.approach(px, targetPx, 20f, dt)

        // ai paddle (imperfect)
        val aiSkill = Mathx.lerp(0.55f, 0.92f, (score / 40f).coerceIn(0f, 1f))
        val hwA = halfW(aiY)
        val aiTarget = (if (vy < 0) ballX else sc.cx).coerceIn(sc.cx - hwA + aw / 2, sc.cx + hwA - aw / 2)
        aiX = Mathx.approach(aiX, aiTarget, aiSkill * 12f, dt)

        if (serveT > 0f) { serveT -= dt }

        ballX += vx * dt; ballY += vy * dt

        // circular wall bounce
        val hwB = halfW(ballY)
        if (ballX < sc.cx - hwB + ballR) { ballX = sc.cx - hwB + ballR; vx = abs(vx); engine.sfx.click() }
        if (ballX > sc.cx + hwB - ballR) { ballX = sc.cx + hwB - ballR; vx = -abs(vx); engine.sfx.click() }

        // player paddle collision
        if (vy > 0 && ballY + ballR >= playerY && ballY < playerY + sc.px(0.05f)) {
            if (abs(ballX - px) < pw / 2 + ballR) {
                ballY = playerY - ballR; vy = -abs(vy)
                vx += (ballX - px) / (pw / 2) * speed * 0.6f
                addScore(1); engine.sfx.blip(); engine.haptics.light()
                particles.burst(ballX, playerY, 8, accent, sc.px(0.4f), sc.px(0.012f), 0.3f)
            } else if (ballY - ballR > playerY + sc.px(0.04f)) {
                miss(); return
            }
        }
        // ai paddle collision
        if (vy < 0 && ballY - ballR <= aiY && ballY > aiY - sc.px(0.05f)) {
            if (abs(ballX - aiX) < aw / 2 + ballR) {
                ballY = aiY + ballR; vy = abs(vy)
                vx += (ballX - aiX) / (aw / 2) * speed * 0.5f
                engine.sfx.beep(300f, 0.05f, 0.3f)
            } else if (ballY + ballR < aiY - sc.px(0.04f)) {
                // AI missed → bonus, re-serve toward player
                addScore(10); engine.sfx.coin(); engine.haptics.success()
                particles.burst(ballX, aiY, 16, Palette.gold, sc.px(0.6f), sc.px(0.016f), 0.5f)
                serve(toPlayer = true)
            }
        }
        // safety: ball past player bottom
        if (ballY - ballR > sc.h) miss()
    }

    private fun miss() {
        shake(sc.px(0.025f)); flash(Palette.red, 0.4f); engine.sfx.fail(); engine.haptics.heavy()
        gameOver()
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            // center line
            Draw.line(c, s.cx - halfW(s.cy), s.cy, s.cx + halfW(s.cy), s.cy, Palette.alpha(Palette.line, 0.6f), s.dp(1.5f))
            Draw.strokeCircle(c, s.cx, s.cy, s.px(0.08f), Palette.alpha(Palette.line, 0.6f), s.dp(1.5f))
            // paddles
            drawPaddle(c, aiX, aiY, aw, Palette.pink)
            drawPaddle(c, px, playerY, pw, accent)
            // ball
            Draw.glow(c, ballX, ballY, ballR * 2.2f, Palette.text, 0.4f)
            Draw.fillCircle(c, ballX, ballY, ballR, Palette.text)
        }
    }

    private fun drawPaddle(c: Canvas, x: Float, y: Float, w: Float, col: Int) {
        val h = sc.px(0.035f)
        Draw.glow(c, x, y, w * 0.6f, col, 0.25f)
        Draw.roundRectCentered(c, x, y, w, h, h * 0.5f, col)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.cy - s.px(0.16f), s.px(0.07f), Palette.alpha(Palette.text, 0.5f), Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "pong",
            title = "Rebound",
            tagline = "Duel the AI",
            category = Category.CLASSIC,
            tier = Tier.MINI,
            accent = Palette.mint,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx - size * 0.28f, cy - size * 0.18f, size * 0.12f, size * 0.3f, size * 0.04f, Palette.pink)
                Draw.roundRectCentered(c, cx + size * 0.28f, cy + size * 0.18f, size * 0.12f, size * 0.3f, size * 0.04f, color)
                Draw.fillCircle(c, cx, cy, size * 0.08f, Palette.text)
            },
            create = { PongGame() }
        )
    }
}
