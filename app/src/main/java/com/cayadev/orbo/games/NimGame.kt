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

/**
 * Nim — take sticks, don't be greedy. Tap a stick to remove it and everything above it in that pile.
 * Take the very last stick to win. The AI plays the perfect XOR strategy, so you must too — boards
 * are dealt from winning positions, so a clean game is always beatable.
 */
class NimGame : ArcadeGame(meta()) {

    private val heaps = IntArray(3)
    private var turn = 1
    private var ended = false
    private var endTimer = 0f
    private var aiTimer = 0f
    private var resultWin = 0

    override val controlHint = "TAKE THE LAST STICK TO WIN"

    override fun onReset() { deal(); turn = 1; ended = false; endTimer = 0f; aiTimer = 0f; resultWin = 0 }

    private fun deal() {
        do { for (i in heaps.indices) heaps[i] = Mathx.randRange(1, 7) } while (xorAll() == 0)
    }

    private fun xorAll(): Int { var x = 0; for (h in heaps) x = x xor h; return x }
    private fun total(): Int { var t = 0; for (h in heaps) t += h; return t }

    private fun take(heap: Int, leave: Int, who: Int) {
        if (heap !in heaps.indices || leave >= heaps[heap] || leave < 0) return
        heaps[heap] = leave
        engine.sfx.click(); engine.haptics.light()
        if (total() == 0) { resultWin = who; endGame(); return }
        turn = 3 - who
    }

    private fun endGame() {
        ended = true
        if (resultWin == 1) { addScore(80); engine.sfx.success(); engine.haptics.success(); flash(accent, 0.25f) }
        else { engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.25f) }
        endTimer = 1.8f
    }

    private fun aiMove() {
        val x = xorAll()
        if (x != 0) {
            for (i in heaps.indices) { val target = heaps[i] xor x; if (target < heaps[i]) { take(i, target, 2); return } }
        }
        // losing position (shouldn't happen from a winning deal if AI never errs) -> take 1 from largest
        var big = 0; for (i in heaps.indices) if (heaps[i] > heaps[big]) big = i
        if (heaps[big] > 0) take(big, heaps[big] - 1, 2)
    }

    override fun onStep(dt: Float) {
        if (ended) { endTimer -= dt; if (endTimer <= 0f) { if (resultWin == 1) onReset() else gameOver() }; return }
        if (turn == 2) { aiTimer += dt; if (aiTimer > 0.6f) { aiTimer = 0f; aiMove() } }
    }

    private fun heapX(s: ScreenInfo, i: Int) = s.cx + (i - 1) * (if (s.isRound) s.minDim * 0.26f else s.minDim * 0.3f)
    private fun baseY(s: ScreenInfo) = s.h * 0.72f
    private fun stickH(s: ScreenInfo) = s.px(0.07f)
    private fun stickW(s: ScreenInfo) = s.px(0.04f)

    override fun onTapWorld(x: Float, y: Float) {
        if (ended || turn != 1) return
        val s = sc
        for (i in heaps.indices) {
            val hx = heapX(s, i)
            if (kotlin.math.abs(x - hx) > stickW(s) * 2.2f) continue
            // sticks stacked upward from baseY; stick j center at baseY - j*stickH - stickH/2
            for (j in 0 until heaps[i]) {
                val cy = baseY(s) - j * stickH(s) - stickH(s) / 2
                if (kotlin.math.abs(y - cy) < stickH(s) * 0.55f) { take(i, j, 1); return }
            }
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val sh = stickH(s); val sw = stickW(s)
        for (i in heaps.indices) {
            val hx = heapX(s, i)
            Draw.roundRectCentered(c, hx, baseY(s) + sh * 0.4f, sw * 3.2f, sh * 0.3f, sh * 0.1f, Palette.alpha(accent, 0.3f))
            for (j in 0 until heaps[i]) {
                val cy = baseY(s) - j * sh - sh / 2
                Draw.glow(c, hx, cy, sw, accent, 0.12f)
                Draw.roundRectCentered(c, hx, cy, sw, sh * 0.86f, sw * 0.4f, accent)
                Draw.fillCircle(c, hx, cy - sh * 0.3f, sw * 0.45f, Palette.shade(accent, 0.3f))
            }
        }
        if (ended)
            Draw.textCentered(c, Loc.t(if (resultWin == 1) "YOU WIN" else "AI WINS"), s.cx, s.h * 0.88f, s.px(0.055f),
                if (resultWin == 1) accent else Palette.red, Fonts.display)
        else Draw.textCentered(c, Loc.t(if (turn == 1) "YOUR TURN" else "AI…"), s.cx, s.h * 0.88f, s.px(0.04f),
            if (turn == 1) accent else Palette.textDim, Fonts.mono, letterSpacing = 0.08f)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.13f, s.px(0.06f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "nim",
            title = "Nim",
            tagline = "Take the last stick",
            category = Category.STRATEGY,
            tier = Tier.MINI,
            accent = Palette.lime,
            icon = IconDrawer { c, cx, cy, size, color ->
                for (i in 0 until 3) {
                    val hx = cx + (i - 1) * size * 0.3f
                    for (j in 0 until (i + 1)) Draw.roundRectCentered(c, hx, cy + size * 0.35f - j * size * 0.22f, size * 0.08f, size * 0.18f, size * 0.03f, color)
                }
            },
            create = { NimGame() }
        )
    }
}
