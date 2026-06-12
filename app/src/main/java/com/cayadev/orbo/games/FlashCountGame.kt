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
 * Flash Count — a swarm of dots blinks for a heartbeat. How many were there? Pick the right number
 * from four choices. Right answers speed up and add dots; a wrong guess costs a life. Three lives.
 * Trains instant number sense.
 */
class FlashCountGame : ArcadeGame(meta()) {

    private enum class Phase { FLASH, ANSWER }
    private var phase = Phase.FLASH
    private val dots = ArrayList<FloatArray>()   // x,y
    private var count = 0
    private var flashT = 0f
    private val options = IntArray(4)
    private var lives = 3
    private var round = 0
    private var pickFx = -1
    private var pickT = 0f

    override val controlHint = "COUNT THE DOTS · PICK THE NUMBER"

    override fun onReset() { lives = 3; round = 0; newRound() }

    private fun newRound() {
        val s = sc
        count = Mathx.randRange(4 + round, 8 + round * 2).coerceAtMost(40)
        dots.clear()
        val r = if (s.isRound) s.r * 0.78f else s.minDim * 0.42f
        var tries = 0
        while (dots.size < count && tries < count * 30) {
            tries++
            val a = Mathx.rand(0f, Mathx.TAU); val rr = Mathx.rand(0f, r)
            val x = s.cx + Mathx.cosf(a) * rr; val y = s.cy + Mathx.sinf(a) * rr * (if (s.isRound) 1f else 1f)
            var ok = true
            for (d in dots) if (Mathx.dist(x, y, d[0], d[1]) < s.px(0.06f)) { ok = false; break }
            if (ok) dots.add(floatArrayOf(x, y))
        }
        count = dots.size
        flashT = Mathx.lerp(1.1f, 0.45f, (round / 12f).coerceIn(0f, 1f))
        phase = Phase.FLASH; pickFx = -1
        buildOptions()
    }

    private fun buildOptions() {
        val set = linkedSetOf(count)
        while (set.size < 4) {
            val off = Mathx.randRange(1, 3) * (if (Mathx.chance(0.5f)) 1 else -1)
            val v = count + off
            if (v > 0) set.add(v)
        }
        val list = set.toMutableList()
        for (i in list.indices.reversed()) { val j = Mathx.rand(i + 1); val t = list[i]; list[i] = list[j]; list[j] = t }
        for (i in 0 until 4) options[i] = list[i]
    }

    override fun onStep(dt: Float) {
        if (pickT > 0f) pickT -= dt
        if (phase == Phase.FLASH) { flashT -= dt; if (flashT <= 0f) phase = Phase.ANSWER }
    }

    private fun optRect(s: ScreenInfo, i: Int): FloatArray {
        val col = i % 2; val row = i / 2
        val bw = if (s.isRound) s.minDim * 0.26f else s.minDim * 0.34f
        val cx = s.cx + (col - 0.5f) * bw * 1.15f
        val cy = s.h * 0.56f + row * bw * 0.62f
        return floatArrayOf(cx, cy, bw * 0.52f, bw * 0.28f)
    }

    override fun onTapWorld(x: Float, y: Float) {
        if (phase != Phase.ANSWER) return
        for (i in 0 until 4) {
            val r = optRect(sc, i)
            if (kotlin.math.abs(x - r[0]) < r[2] && kotlin.math.abs(y - r[1]) < r[3]) {
                pickFx = i; pickT = 0.3f
                if (options[i] == count) {
                    addScore(20 + round * 5); round++
                    engine.sfx.success(); engine.haptics.success(); flash(accent, 0.15f)
                    newRound()
                } else {
                    lives--; engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.25f)
                    if (lives <= 0) gameOver() else newRound()
                }
                return
            }
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        if (phase == Phase.FLASH) {
            for (d in dots) { Draw.glow(c, d[0], d[1], s.px(0.04f), accent, 0.3f); Draw.fillCircle(c, d[0], d[1], s.px(0.022f), accent) }
            Draw.textCentered(c, Loc.t("COUNT!"), s.cx, s.h * 0.06f, s.px(0.045f), Palette.textDim, Fonts.mono, letterSpacing = 0.18f)
        } else {
            Draw.textCentered(c, "?", s.cx, s.h * 0.3f, s.px(0.16f), Palette.alpha(accent, 0.5f), Fonts.display)
            for (i in 0 until 4) {
                val r = optRect(s, i)
                val on = pickFx == i && pickT > 0f
                Draw.roundRectCentered(c, r[0], r[1], r[2] * 2, r[3] * 2, r[3] * 0.5f, if (on) accent else Palette.panelHi)
                Draw.roundRectStroke(c, r[0] - r[2], r[1] - r[3], r[2] * 2, r[3] * 2, r[3] * 0.5f, Palette.alpha(accent, 0.4f), s.dp(1.4f))
                Draw.textCentered(c, "${options[i]}", r[0], r[1], r[3] * 1.1f, if (on) Palette.bg else Palette.text, Fonts.display)
            }
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.05f); val ly = s.h * 0.92f; val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, ly, s.px(0.015f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, ly, s.px(0.013f), Palette.alpha(Palette.red, 0.3f), s.dp(1.2f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        if (phase == Phase.FLASH) return
        Draw.textCentered(c, "$score", s.cx, s.h * 0.08f, s.px(0.045f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "flash_count",
            title = "Flash Count",
            tagline = "How many dots?",
            category = Category.BRAIN,
            tier = Tier.MINI,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                val pts = arrayOf(intArrayOf(-1,-1), intArrayOf(1,-1), intArrayOf(0,0), intArrayOf(-1,1), intArrayOf(1,1))
                for (p in pts) Draw.fillCircle(c, cx + p[0] * size * 0.28f, cy + p[1] * size * 0.28f, size * 0.09f, color)
            },
            create = { FlashCountGame() }
        )
    }
}
