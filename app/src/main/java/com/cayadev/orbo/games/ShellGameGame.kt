package com.cayadev.orbo.games

import android.graphics.Canvas
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.IconDrawer
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Ease
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Loc
import com.cayadev.orbo.engine.Mathx
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame

/**
 * Shell Game — the carnival cups classic. Watch the ball drop under a cup, follow the cups as they
 * shuffle, then tap the one hiding the ball. Each correct guess speeds up and adds shuffles; a wrong
 * pick costs a life. Three lives. Pure tracking and nerve.
 *
 * All three cups share one colour (you can only win by tracking, not by spotting a colour), and a
 * swap arcs one cup cleanly over the other instead of clipping straight through it.
 */
class ShellGameGame : ArcadeGame(meta()) {

    private enum class Phase { SHOW, SHUFFLE, GUESS, RESULT }
    private var phase = Phase.SHOW

    private var ballSlot = 0
    private var lives = 3
    private var round = 0

    private var timer = 0f
    private var swapsLeft = 0
    private var swapA = -1; private var swapB = -1
    private var swapT = 0f; private var swapDur = 0.4f
    private var overIsA = true                      // which of the swapping cups arcs over the other
    private var lastA = -1; private var lastB = -1  // previous swap pair (avoid immediate repeats)
    private var chosen = -1
    private var lastCorrect = false

    private val cupColor = Palette.amber

    override val controlHint = "FOLLOW THE BALL · TAP A CUP"

    override fun onReset() { lives = 3; round = 0; startRound() }

    private fun startRound() {
        ballSlot = Mathx.rand(3)
        phase = Phase.SHOW; timer = SHOW_DUR; chosen = -1
    }

    private fun beginShuffle() {
        phase = Phase.SHUFFLE
        swapsLeft = (4 + round * 2).coerceAtMost(20)
        swapDur = Mathx.lerp(0.42f, 0.18f, (round / 12f).coerceIn(0f, 1f))
        lastA = -1; lastB = -1
        nextSwap()
    }

    private fun nextSwap() {
        if (swapsLeft <= 0) { phase = Phase.GUESS; swapA = -1; swapB = -1; return }
        swapsLeft--
        var a = Mathx.rand(3); var b = Mathx.rand(3)
        while (b == a) b = Mathx.rand(3)
        // don't replay the exact same pair back-to-back — that reads as a stutter, not a shuffle
        var guard = 0
        while (((a == lastA && b == lastB) || (a == lastB && b == lastA)) && guard++ < 6) {
            a = Mathx.rand(3); b = Mathx.rand(3); while (b == a) b = Mathx.rand(3)
        }
        swapA = a; swapB = b; swapT = 0f
        lastA = a; lastB = b
        overIsA = Mathx.chance(0.5f)
    }

    private fun commitSwap() {
        if (ballSlot == swapA) ballSlot = swapB else if (ballSlot == swapB) ballSlot = swapA
        engine.sfx.click()
    }

    override fun onStep(dt: Float) {
        when (phase) {
            Phase.SHOW -> { timer -= dt; if (timer <= 0f) beginShuffle() }
            Phase.SHUFFLE -> {
                swapT += dt / swapDur
                if (swapT >= 1f) { commitSwap(); nextSwap() }
            }
            Phase.RESULT -> {
                timer -= dt
                if (timer <= 0f) {
                    if (lastCorrect) { round++; startRound() }
                    else { if (lives <= 0) gameOver() else startRound() }
                }
            }
            else -> {}
        }
    }

    private fun spacing(s: ScreenInfo) = if (s.isRound) s.minDim * 0.24f else s.minDim * 0.28f
    private fun slotX(s: ScreenInfo, i: Int) = s.cx + (i - 1) * spacing(s)
    private fun cupY(s: ScreenInfo) = s.cy + s.px(0.04f)
    private fun cupW(s: ScreenInfo) = spacing(s) * 0.78f
    private fun cupH(s: ScreenInfo) = s.px(0.2f)
    private fun liftMax(s: ScreenInfo) = s.px(0.17f)

    /** Eased 0..1 raise factor while peeking the ball: pop up, hold, settle back down before the shuffle. */
    private fun showLiftFactor(): Float {
        val e = SHOW_DUR - timer
        val rise = Ease.outBack((e / 0.32f).coerceIn(0f, 1f))
        val fall = 1f - Ease.inCubic(((e - (SHOW_DUR - 0.30f)) / 0.30f).coerceIn(0f, 1f))
        return (rise * fall).coerceIn(0f, 1f)
    }

    /** Eased 0..1 raise factor for the reveal cups in RESULT (quick pop, then held). */
    private fun revealLiftFactor(): Float =
        Ease.outBack(((RESULT_DUR - timer) / 0.32f).coerceIn(0f, 1f)).coerceIn(0f, 1f)

    override fun onTapWorld(x: Float, y: Float) {
        if (phase != Phase.GUESS) return
        val s = sc
        // only accept taps inside the cups' vertical band — keeps score/HUD taps from picking a cup
        val top = cupY(s) - cupH(s) - s.px(0.06f)
        val bot = cupY(s) + s.px(0.08f)
        if (y < top || y > bot) return
        var slot = -1; var bestD = Float.MAX_VALUE
        for (i in 0 until 3) { val d = kotlin.math.abs(x - slotX(s, i)); if (d < bestD) { bestD = d; slot = i } }
        if (slot < 0 || bestD > cupW(s)) return
        chosen = slot
        lastCorrect = slot == ballSlot
        if (lastCorrect) {
            addScore(50 + round * 10)
            engine.sfx.success(); engine.haptics.success(); flash(accent, 0.2f)
            particles.burst(slotX(s, slot), cupY(s), 16, accent, s.px(0.6f), s.px(0.016f), 0.45f)
        } else {
            lives--
            engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.25f); shake(s.px(0.012f))
        }
        phase = Phase.RESULT; timer = RESULT_DUR
    }

    private fun drawCup(c: Canvas, s: ScreenInfo, x: Float, lift: Float) {
        val w = cupW(s); val h = cupH(s)
        val topY = cupY(s) - h - lift
        val r = w * 0.42f
        // contact light on the table while grounded, fading out as the cup is lifted
        val grounded = (1f - lift / liftMax(s)).coerceIn(0f, 1f)
        if (grounded > 0.02f) Draw.glow(c, x, cupY(s) + s.dp(2f), w * 0.72f, cupColor, 0.18f * grounded)
        // body
        Draw.roundRect(c, x - w / 2, topY, w, h, r, cupColor)
        // glossy upper sheen + bright top rim for a rounded, lit look
        Draw.roundRect(c, x - w / 2, topY, w, h * 0.5f, r, Palette.alpha(Palette.shade(cupColor, 0.55f), 0.45f))
        Draw.roundRect(c, x - w / 2, topY, w, h * 0.12f, r, Palette.alpha(Palette.text, 0.2f))
        // crisp outline so identical cups stay separated against the background
        Draw.roundRectStroke(c, x - w / 2, topY, w, h, r, Palette.alpha(Palette.bg, 0.55f), s.dp(1.5f))
    }

    private fun drawBall(c: Canvas, s: ScreenInfo, x: Float) {
        val bob = Mathx.sinf(timeAlive * 4.2f) * s.px(0.006f)
        val by = cupY(s) - s.px(0.045f) + bob
        val rad = s.px(0.036f)
        Draw.glow(c, x, by, s.px(0.06f), Palette.gold, 0.55f)
        Draw.fillCircle(c, x, by, rad, Palette.gold)
        Draw.fillCircle(c, x - rad * 0.3f, by - rad * 0.32f, rad * 0.32f, Palette.alpha(Palette.text, 0.75f))
    }

    /** Draw a single cup with its current phase transform. Kept allocation-free for smooth fps. */
    private fun drawSlotCup(c: Canvas, s: ScreenInfo, slot: Int) {
        var x = slotX(s, slot)
        var lift = 0f
        when (phase) {
            Phase.SHOW -> lift = liftMax(s) * showLiftFactor()
            Phase.RESULT -> if (slot == chosen || slot == ballSlot) lift = liftMax(s) * revealLiftFactor()
            Phase.SHUFFLE -> if (slot == swapA || slot == swapB) {
                val other = if (slot == swapA) swapB else swapA
                val tt = swapT.coerceIn(0f, 1f)
                x = Mathx.lerp(slotX(s, slot), slotX(s, other), Ease.inOutCubic(tt))
                val over = if (overIsA) swapA else swapB
                val arc = if (slot == over) s.px(0.16f) else s.px(0.02f)
                lift = Mathx.sinf(tt * Math.PI.toFloat()) * arc
            }
            else -> {}
        }
        drawCup(c, s, x, lift)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        // table line
        val tableY = cupY(s) + s.dp(2f)
        Draw.line(c, s.cx - spacing(s) * 2f, tableY, s.cx + spacing(s) * 2f, tableY, Palette.alpha(Palette.line, 0.8f), s.dp(2f))

        // ball is visible when cups lift in SHOW, or under its slot in RESULT
        val showBall = phase == Phase.SHOW || phase == Phase.RESULT
        if (showBall) drawBall(c, s, slotX(s, ballSlot))

        // draw order: the arcing cup is drawn last so it passes cleanly on top of the other
        val over = if (phase == Phase.SHUFFLE) (if (overIsA) swapA else swapB) else -1
        for (slot in 0 until 3) if (slot != over) drawSlotCup(c, s, slot)
        if (over >= 0) drawSlotCup(c, s, over)

        // status text
        when (phase) {
            Phase.RESULT -> {
                val msg = if (lastCorrect) Loc.t("GOOD") else Loc.t("MISS")
                Draw.textCentered(c, msg, s.cx, s.h * 0.3f, s.px(0.07f), if (lastCorrect) accent else Palette.red, Fonts.display)
            }
            Phase.SHOW -> Draw.textCentered(c, Loc.t("WATCH"), s.cx, s.h * 0.3f, s.px(0.05f), Palette.textDim, Fonts.mono, letterSpacing = 0.16f)
            Phase.GUESS -> Draw.textCentered(c, Loc.t("WHICH CUP?"), s.cx, s.h * 0.3f, s.px(0.05f), accent, Fonts.mono, letterSpacing = 0.1f)
            else -> {}
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.05f); val ly = s.h * 0.85f; val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, ly, s.px(0.016f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, ly, s.px(0.014f), Palette.alpha(Palette.red, 0.35f), s.dp(1.4f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.14f, s.px(0.06f), Palette.text, Fonts.display)
    }

    companion object {
        private const val SHOW_DUR = 1.2f
        private const val RESULT_DUR = 1.4f

        fun meta() = GameMeta(
            id = "shell_game",
            title = "Shell Game",
            tagline = "Find the ball",
            category = Category.BRAIN,
            tier = Tier.BIG,
            accent = Palette.amber,
            featured = true,
            icon = IconDrawer { c, cx, cy, size, _ ->
                for (i in 0 until 3) Draw.roundRect(c, cx + (i - 1) * size * 0.34f - size * 0.13f, cy - size * 0.2f, size * 0.26f, size * 0.4f, size * 0.1f, Palette.amber)
                Draw.fillCircle(c, cx, cy + size * 0.3f, size * 0.1f, Palette.gold)
            },
            create = { ShellGameGame() }
        )
    }
}
