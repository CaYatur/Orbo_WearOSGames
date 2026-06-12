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
import kotlin.math.sqrt

/** Stack — time your taps to drop each slab dead-centre. Overhang gets sliced off; perfect drops
 * keep you wide and chain a combo. A rainbow tower that climbs as high as your timing allows. */
class StackGame : ArcadeGame(meta()) {

    private class Slab(var cx: Float, var w: Float)

    private val stack = ArrayList<Slab>()
    private var mx = 0f
    private var mw = 0f
    private var dir = 1f
    private var speed = 0f
    private var combo = 0
    private var blockH = 0f
    private val activeY get() = sc.h * 0.42f
    private var settle = 0f

    override val controlHint = "TAP TO DROP"

    override fun onReset() {
        blockH = sc.px(0.085f)
        stack.clear()
        stack.add(Slab(sc.cx, sc.px(0.42f)))
        mw = sc.px(0.42f); mx = sc.cx - halfW(activeY) + mw / 2; dir = 1f
        speed = sc.px(0.55f); combo = 0; settle = 0f
    }

    private fun halfW(y: Float): Float {
        if (!sc.isRound) return sc.w / 2f - sc.px(0.03f)
        val dy = y - sc.cy; val v = sc.r * sc.r - dy * dy
        return if (v <= 0) sc.px(0.1f) else sqrt(v) - sc.px(0.03f)
    }

    override fun onStep(dt: Float) {
        if (settle > 0f) settle -= dt
        speed = Mathx.lerp(sc.px(0.55f), sc.px(1.1f), (score / 40f).coerceIn(0f, 1f))
        mx += dir * speed * dt
        val hw = halfW(activeY)
        val lo = sc.cx - hw + mw / 2; val hi = sc.cx + hw - mw / 2
        if (mx < lo) { mx = lo; dir = 1f }
        if (mx > hi) { mx = hi; dir = -1f }
    }

    override fun onTapWorld(x: Float, y: Float) {
        val below = stack.last()
        val movL = mx - mw / 2; val movR = mx + mw / 2
        val belL = below.cx - below.w / 2; val belR = below.cx + below.w / 2
        val oL = maxOf(movL, belL); val oR = minOf(movR, belR)
        if (oR <= oL) {
            // missed completely
            shake(sc.px(0.03f)); flash(Palette.red, 0.4f); engine.sfx.explosion(); engine.haptics.heavy()
            particles.burst(mx, activeY, 24, layerColor(stack.size), sc.px(0.8f), sc.px(0.02f), 0.7f, gravity = sc.px(1.4f))
            gameOver(); return
        }
        val perfect = abs(mx - below.cx) < sc.px(0.012f)
        val newW: Float; val newCx: Float
        if (perfect) {
            newW = (below.w + sc.px(0.012f)).coerceAtMost(sc.px(0.42f)); newCx = below.cx
            combo++; addScore(2 + combo)
            engine.sfx.coin(); engine.haptics.medium()
            flash(Palette.mint, 0.18f)
            particles.burst(newCx, activeY, 14, Palette.mint, sc.px(0.6f), sc.px(0.016f), 0.5f)
        } else {
            newW = oR - oL; newCx = (oL + oR) / 2
            combo = 0; addScore(1)
            engine.sfx.blip(); engine.haptics.light()
            // overhang shard
            val shardX = if (movL < belL) (movL + oL) / 2 else (oR + movR) / 2
            particles.burst(shardX, activeY, 8, layerColor(stack.size), sc.px(0.3f), sc.px(0.014f), 0.6f, gravity = sc.px(1.6f))
        }
        stack.add(Slab(newCx, newW))
        mw = newW
        dir = if (Mathx.chance(0.5f)) 1f else -1f
        mx = if (dir > 0) sc.cx - halfW(activeY) + mw / 2 else sc.cx + halfW(activeY) - mw / 2
        settle = 0.12f
    }

    private fun layerColor(i: Int): Int {
        val hue = (i * 26f) % 360f
        return android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.6f, 1f))
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.vGradientRect(c, 0f, 0f, s.w, s.h, 0xFF0B0E1A.toInt(), Palette.bg)
        Draw.vignette(c, s, Palette.alpha(Palette.bg, 0f), Palette.bg)
        if (state == GState.READY) return

        val placed = stack.size
        Draw.withFaceClip(c, s) {
            for (i in stack.indices) {
                val sy = activeY + (placed - i) * blockH
                if (sy < -blockH || sy > s.h + blockH) continue
                drawSlab(c, stack[i].cx, sy, stack[i].w, layerColor(i))
            }
            // moving block
            val my = activeY - (if (settle > 0f) (settle / 0.12f) * blockH * 0.3f else 0f)
            drawSlab(c, mx, my, mw, layerColor(placed), glow = true)
        }
    }

    private fun drawSlab(c: Canvas, cx: Float, cy: Float, w: Float, col: Int, glow: Boolean = false) {
        if (glow) Draw.glow(c, cx, cy, w * 0.6f, col, 0.25f)
        Draw.roundRectCentered(c, cx, cy, w, blockH * 0.86f, blockH * 0.18f, col)
        Draw.roundRectCentered(c, cx, cy - blockH * 0.18f, w * 0.96f, blockH * 0.3f, blockH * 0.15f, Palette.alpha(Palette.text, 0.12f))
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.16f, s.px(0.1f), Palette.text, Fonts.display)
        if (combo > 1) Draw.textCentered(c, "${Loc.t("PERFECT")} x$combo", s.cx, s.h * 0.26f, s.px(0.04f), Palette.mint, Fonts.mono)
    }

    companion object {
        fun meta() = GameMeta(
            id = "stack",
            title = "Stack",
            tagline = "Build the tower",
            category = Category.ARCADE,
            tier = Tier.MINI,
            accent = Palette.orange,
            icon = IconDrawer { c, cx, cy, size, _ ->
                val w = size * 0.5f; val h = size * 0.13f
                Draw.roundRectCentered(c, cx, cy + h, w, h, h * 0.3f, Palette.pink)
                Draw.roundRectCentered(c, cx - size * 0.05f, cy, w * 0.8f, h, h * 0.3f, Palette.amber)
                Draw.roundRectCentered(c, cx + size * 0.04f, cy - h, w * 0.6f, h, h * 0.3f, Palette.mint)
            },
            create = { StackGame() }
        )
    }
}
