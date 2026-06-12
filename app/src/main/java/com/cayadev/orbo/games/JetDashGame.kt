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

/**
 * Jet Dash — hold to fire your jetpack and rise, release to fall. Thread the neon zapper gates and
 * scoop coins as the world races by faster and faster. One touch on a zapper ends the run. Continuous
 * thrust gives it a very different feel from a flap game.
 */
class JetDashGame : ArcadeGame(meta()) {

    private var py = 0f
    private var vy = 0f
    private var thrust = false
    private var scroll = 0f
    private var spawnX = 0f
    private var dist = 0f

    private class Gate(var x: Float, var gapY: Float, var gapH: Float, var scored: Boolean)
    private class Coin(var x: Float, var y: Float, var taken: Boolean)
    private val gates = ArrayList<Gate>()
    private val coins = ArrayList<Coin>()

    override val controlHint = "HOLD TO FLY"

    private fun playerX(s: ScreenInfo) = s.cx - s.px(0.22f)

    override fun onReset() {
        py = sc.cy; vy = 0f; thrust = false; scroll = 0f; dist = 0f
        gates.clear(); coins.clear()
        spawnX = sc.w + sc.px(0.2f)
        repeat(3) { spawnGate() }
    }

    private fun spawnGate() {
        val s = sc
        val gapH = Mathx.lerp(s.px(0.42f), s.px(0.28f), (dist / 4000f).coerceIn(0f, 1f))
        val margin = s.px(0.12f)
        val gapY = Mathx.rand(margin, s.h - margin - gapH)
        gates.add(Gate(spawnX, gapY, gapH, false))
        if (Mathx.chance(0.8f)) coins.add(Coin(spawnX + s.px(0.5f), gapY + gapH / 2f, false))
        spawnX += Mathx.rand(s.px(0.7f), s.px(0.95f))
    }

    override fun onPress(x: Float, y: Float) { thrust = true }
    override fun onRelease(x: Float, y: Float) { thrust = false }

    override fun onStep(dt: Float) {
        val s = sc
        dist += dt * 100f
        val speed = Mathx.lerp(s.px(0.55f), s.px(1.0f), (dist / 5000f).coerceIn(0f, 1f))
        scroll += speed * dt
        // physics
        vy += (if (thrust) -s.px(4.4f) else s.px(2.6f)) * dt
        vy = vy.coerceIn(-s.px(1.4f), s.px(1.6f))
        py += vy * dt
        if (py < s.px(0.05f)) { py = s.px(0.05f); vy = 0f }
        if (py > s.h - s.px(0.05f)) { py = s.h - s.px(0.05f); vy = 0f }
        if (thrust && Mathx.chance(0.5f))
            particles.spawn(playerX(s) - s.px(0.04f), py + s.px(0.05f), Mathx.rand(-20f, 20f), s.px(0.8f), 0.3f, s.px(0.012f), Palette.orange, 0f, 2f)

        val pr = s.px(0.045f); val px = playerX(s)
        val barW = s.px(0.06f)
        val it = gates.iterator()
        while (it.hasNext()) {
            val g = it.next()
            g.x -= speed * dt
            // collision
            if (px + pr > g.x && px - pr < g.x + barW) {
                if (py - pr < g.gapY || py + pr > g.gapY + g.gapH) { die(); return }
            }
            if (!g.scored && g.x + barW < px) { g.scored = true; addScore(1); engine.sfx.blip() }
            if (g.x < -barW * 2) it.remove()
        }
        val ci = coins.iterator()
        while (ci.hasNext()) {
            val co = ci.next()
            co.x -= speed * dt
            if (!co.taken && Mathx.dist(co.x, co.y, px, py) < pr + s.px(0.03f)) {
                co.taken = true; addScore(3); engine.sfx.coin(); engine.haptics.light()
                particles.burst(co.x, co.y, 8, Palette.gold, s.px(0.5f), s.px(0.012f), 0.35f)
            }
            if (co.x < -s.px(0.1f)) ci.remove()
        }
        if (spawnX - scroll < s.w + s.px(0.4f)) { /* keep buffer */ }
        if (gates.isNotEmpty() && gates.last().x < s.w * 0.6f) spawnGate()
    }

    private fun die() {
        engine.sfx.explosion(); engine.haptics.heavy(); flash(Palette.red, 0.5f); shake(sc.px(0.025f))
        particles.burst(playerX(sc), py, 26, Palette.orange, sc.px(1f), sc.px(0.02f), 0.5f)
        gameOver()
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            val barW = s.px(0.06f)
            for (g in gates) {
                // top + bottom zapper bars
                Draw.roundRect(c, g.x, -s.px(0.1f), barW, g.gapY + s.px(0.1f), barW * 0.3f, Palette.alpha(accent, 0.85f))
                Draw.roundRect(c, g.x, g.gapY + g.gapH, barW, s.h - (g.gapY + g.gapH) + s.px(0.1f), barW * 0.3f, Palette.alpha(accent, 0.85f))
                Draw.line(c, g.x + barW / 2, g.gapY, g.x + barW / 2, g.gapY - s.px(0.06f), Palette.text, s.dp(1.5f))
            }
            for (co in coins) if (!co.taken) {
                Draw.glow(c, co.x, co.y, s.px(0.04f), Palette.gold, 0.4f)
                Draw.fillCircle(c, co.x, co.y, s.px(0.022f), Palette.gold)
            }
            // player
            val px = playerX(s); val pr = s.px(0.045f)
            Draw.glow(c, px, py, pr * 1.6f, accent, 0.3f)
            Draw.roundRectCentered(c, px, py, pr * 1.5f, pr * 1.8f, pr * 0.5f, Palette.text)
            Draw.fillCircle(c, px + pr * 0.2f, py - pr * 0.2f, pr * 0.35f, accent)
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.115f, s.px(0.065f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "jet_dash",
            title = "Jet Dash",
            tagline = "Hold to fly",
            category = Category.ACTION,
            tier = Tier.MINI,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx, cy - size * 0.05f, size * 0.36f, size * 0.5f, size * 0.14f, Palette.text)
                Draw.fillCircle(c, cx + size * 0.08f, cy - size * 0.12f, size * 0.1f, color)
                for (i in 0 until 3) Draw.fillCircle(c, cx - size * 0.16f, cy + size * 0.22f + i * size * 0.12f, size * 0.06f - i * size * 0.012f, Palette.orange)
            },
            create = { JetDashGame() }
        )
    }
}
