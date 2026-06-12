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
import com.cayadev.orbo.engine.Swipe
import com.cayadev.orbo.game.ArcadeGame

/**
 * Lane Runner — a three-lane dodger. Swipe or tap left/right to switch lanes, weave past the blocks
 * and grab the coins streaming toward you. The road speeds up the longer you survive. One crash ends
 * the run. Lanes sit inside the round face on every device.
 */
class LaneRunnerGame : ArcadeGame(meta()) {

    private var lane = 1
    private var laneVisual = 1f
    private var scroll = 0f
    private var dist = 0f
    private var spawnY = 0f

    private class Obj(var lane: Int, var y: Float, var coin: Boolean)
    private val objs = ArrayList<Obj>()

    override val controlHint = "SWIPE TO SWITCH LANES"

    private fun laneW(s: ScreenInfo) = (if (s.isRound) s.minDim * 0.5f else s.minDim * 0.66f) / 3f
    private fun laneX(s: ScreenInfo, l: Float) = s.cx + (l - 1f) * laneW(s)
    private fun playerY(s: ScreenInfo) = s.h * 0.8f

    override fun onReset() {
        lane = 1; laneVisual = 1f; scroll = 0f; dist = 0f; objs.clear(); spawnY = -sc.px(0.2f)
        repeat(3) { spawnRow(it * -sc.px(0.45f)) }
    }

    private fun spawnRow(atY: Float) {
        val s = sc
        // block 1-2 lanes (never all three), maybe a coin in a free lane
        val blocked = BooleanArray(3)
        val nBlock = if (Mathx.chance(0.4f)) 2 else 1
        repeat(nBlock) { blocked[Mathx.rand(3)] = true }
        for (l in 0 until 3) if (blocked[l]) objs.add(Obj(l, atY, false))
        val free = (0 until 3).filter { !blocked[it] }
        if (free.isNotEmpty() && Mathx.chance(0.6f)) objs.add(Obj(free[Mathx.rand(free.size)], atY, true))
    }

    override fun onFlick(dir: Swipe, vx: Float, vy: Float) {
        if (dir == Swipe.LEFT) move(-1)
        else if (dir == Swipe.RIGHT) move(1)
    }
    override fun onTapWorld(x: Float, y: Float) { if (x < sc.cx) move(-1) else move(1) }

    private fun move(d: Int) {
        val nl = (lane + d).coerceIn(0, 2)
        if (nl != lane) { lane = nl; engine.sfx.click(); engine.haptics.light() }
    }

    override fun onStep(dt: Float) {
        val s = sc
        dist += dt * 100f
        laneVisual = Mathx.approach(laneVisual, lane.toFloat(), 16f, dt)
        val speed = Mathx.lerp(s.px(0.9f), s.px(1.7f), (dist / 6000f).coerceIn(0f, 1f))
        scroll += speed * dt
        val px = laneX(s, laneVisual); val py = playerY(s); val pr = s.px(0.05f)
        val it = objs.iterator()
        while (it.hasNext()) {
            val o = it.next()
            o.y += speed * dt
            val ox = laneX(s, o.lane.toFloat())
            if (o.y > py - pr && o.y < py + pr && o.lane == lane) {
                if (o.coin) {
                    addScore(5); engine.sfx.coin(); engine.haptics.light()
                    particles.burst(ox, o.y, 8, Palette.gold, s.px(0.5f), s.px(0.012f), 0.35f)
                    it.remove(); continue
                } else {
                    engine.sfx.explosion(); engine.haptics.heavy(); flash(Palette.red, 0.5f); shake(s.px(0.025f))
                    particles.burst(px, py, 24, Palette.red, s.px(0.9f), s.px(0.02f), 0.5f)
                    gameOver(); return
                }
            }
            if (o.y > s.h + s.px(0.1f)) { if (!o.coin) addScore(1); it.remove() }
        }
        // keep spawning ahead
        val highest = objs.minByOrNull { it.y }?.y ?: 0f
        if (objs.isEmpty() || highest > -sc.px(0.3f)) spawnRow(highest - Mathx.rand(s.px(0.4f), s.px(0.6f)))
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            val lw = laneW(s)
            // lane guides
            for (l in 0..3) {
                val x = s.cx + (l - 1.5f) * lw
                Draw.line(c, x, 0f, x, s.h, Palette.alpha(Palette.line, 0.5f), s.dp(1f))
            }
            val bsz = lw * 0.72f
            for (o in objs) {
                val ox = laneX(s, o.lane.toFloat())
                if (o.coin) {
                    Draw.glow(c, ox, o.y, lw * 0.3f, Palette.gold, 0.4f)
                    Draw.fillCircle(c, ox, o.y, lw * 0.2f, Palette.gold)
                } else {
                    Draw.roundRectCentered(c, ox, o.y, bsz, bsz, bsz * 0.18f, Palette.red)
                    Draw.roundRectStroke(c, ox - bsz / 2, o.y - bsz / 2, bsz, bsz, bsz * 0.18f, Palette.alpha(Palette.text, 0.2f), s.dp(1.4f))
                }
            }
            // player
            val px = laneX(s, laneVisual); val py = playerY(s); val pr = s.px(0.05f)
            Draw.glow(c, px, py, pr * 1.5f, accent, 0.3f)
            Draw.roundRectCentered(c, px, py, pr * 1.6f, pr * 1.8f, pr * 0.4f, accent)
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.12f, s.px(0.065f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "lane_runner",
            title = "Lane Runner",
            tagline = "Weave the lanes",
            category = Category.ACTION,
            tier = Tier.MINI,
            accent = Palette.orange,
            icon = IconDrawer { c, cx, cy, size, color ->
                val lw = size * 0.3f
                for (l in 0..3) Draw.line(c, cx + (l - 1.5f) * lw, cy - size * 0.5f, cx + (l - 1.5f) * lw, cy + size * 0.5f, Palette.alpha(color, 0.4f), size * 0.03f)
                Draw.roundRectCentered(c, cx + lw, cy - size * 0.2f, lw * 0.6f, lw * 0.6f, size * 0.04f, Palette.red)
                Draw.roundRectCentered(c, cx - lw * 0.5f, cy + size * 0.25f, lw * 0.7f, lw * 0.8f, size * 0.05f, color)
            },
            create = { LaneRunnerGame() }
        )
    }
}
