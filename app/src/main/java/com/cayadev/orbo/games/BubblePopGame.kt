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
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.game.ArcadeGame

/** Bubble Pop — a 45-second pop-fest. Tap rising bubbles, snag gold, dodge the bombs. */
class BubblePopGame : ArcadeGame(meta()) {

    private enum class Kind { NORMAL, GOLD, BOMB }
    private class Bubble(var x: Float, var y: Float, var r: Float, var vy: Float,
                         var col: Int, var kind: Kind, var phase: Float, var sway: Float)

    private val bubbles = ArrayList<Bubble>()
    private var spawnT = 0f
    private var timeLeft = 45f
    private var combo = 0
    private var clock = 0f

    override val controlHint = "POP · 45 SECONDS"
    override val gameOverTitle = "TIME UP"

    private val palette = intArrayOf(Palette.cyan, Palette.pink, Palette.violet, Palette.mint, Palette.amber)

    override fun onReset() {
        bubbles.clear(); spawnT = 0f; timeLeft = 45f; combo = 0; clock = 0f
    }

    override fun onStep(dt: Float) {
        clock += dt
        timeLeft -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver(); return }

        val diff = 1f - (timeLeft / 45f)
        spawnT -= dt
        if (spawnT <= 0f) {
            spawnT = Mathx.lerp(0.62f, 0.26f, diff)
            spawn(diff)
        }

        val it = bubbles.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.y += b.vy * dt
            b.phase += dt * 2.2f
            if (b.y < -b.r * 2) it.remove()
        }
    }

    private fun spawn(diff: Float) {
        val r = sc.px(Mathx.rand(0.075f, 0.12f))
        val hw = sc.r * 0.8f
        val x = sc.cx + Mathx.rand(-hw, hw)
        val k = when {
            Mathx.chance(0.14f) -> Kind.BOMB
            Mathx.chance(0.12f) -> Kind.GOLD
            else -> Kind.NORMAL
        }
        val col = when (k) { Kind.GOLD -> Palette.gold; Kind.BOMB -> Palette.red; else -> palette[Mathx.rand(palette.size)] }
        val v = -sc.px(Mathx.lerp(0.28f, 0.5f, diff)) * Mathx.rand(0.85f, 1.2f)
        bubbles.add(Bubble(x, sc.h + r, r, v, col, k, Mathx.rand(0f, Mathx.TAU), Mathx.rand(sc.px(0.01f), sc.px(0.03f))))
    }

    override fun onTapWorld(x: Float, y: Float) {
        var best = -1; var bestD = Float.MAX_VALUE
        for (i in bubbles.indices) {
            val b = bubbles[i]
            val bx = b.x + Mathx.sinf(b.phase) * b.sway
            val d = Mathx.dist(x, y, bx, b.y)
            if (d < b.r * 1.15f && d < bestD) { bestD = d; best = i }
        }
        if (best < 0) { combo = 0; return }
        val b = bubbles[best]
        val bx = b.x + Mathx.sinf(b.phase) * b.sway
        when (b.kind) {
            Kind.BOMB -> {
                combo = 0; timeLeft = (timeLeft - 3f).coerceAtLeast(0f)
                flash(Palette.red, 0.4f); shake(sc.px(0.025f)); engine.sfx.explosion(); engine.haptics.heavy()
                particles.burst(bx, b.y, 24, Palette.red, sc.px(0.8f), sc.px(0.02f), 0.6f)
            }
            else -> {
                combo++
                addScore((if (b.kind == Kind.GOLD) 50 else 10) + combo * 2)
                if (b.kind == Kind.GOLD) engine.sfx.coin() else engine.sfx.blip()
                engine.haptics.light()
                particles.burst(bx, b.y, 16, b.col, b.r * 6f, sc.px(0.016f), 0.45f)
            }
        }
        bubbles.removeAt(best)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.vGradientRect(c, 0f, 0f, s.w, s.h, 0xFF071018.toInt(), Palette.bg)
        Draw.vignette(c, s, Palette.alpha(Palette.bg, 0f), Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            for (b in bubbles) {
                val bx = b.x + Mathx.sinf(b.phase) * b.sway
                Draw.glow(c, bx, b.y, b.r * 1.5f, b.col, 0.22f)
                Draw.fillCircle(c, bx, b.y, b.r, Palette.alpha(b.col, 0.28f))
                Draw.strokeCircle(c, bx, b.y, b.r, Palette.alpha(b.col, 0.95f), b.r * 0.10f)
                // highlight
                Draw.fillCircle(c, bx - b.r * 0.32f, b.y - b.r * 0.32f, b.r * 0.18f, Palette.alpha(Palette.text, 0.5f))
                if (b.kind == Kind.GOLD) Draw.textCentered(c, "★", bx, b.y, b.r * 0.9f, Palette.gold, Fonts.display)
                if (b.kind == Kind.BOMB) {
                    val rr = b.r * 0.34f
                    Draw.line(c, bx - rr, b.y - rr, bx + rr, b.y + rr, b.col, b.r * 0.14f)
                    Draw.line(c, bx + rr, b.y - rr, bx - rr, b.y + rr, b.col, b.r * 0.14f)
                }
            }
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val frac = (timeLeft / 45f).coerceIn(0f, 1f)
        Ui.edgeProgress(c, s, frac, Palette.lerp(Palette.red, accent, frac))
        if (combo > 1) Draw.textCentered(c, "x$combo", s.cx, s.h * 0.80f, s.px(0.05f), accent, Fonts.bodyBold)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.13f, s.px(0.08f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "bubble_pop",
            title = "Bubble Pop",
            tagline = "Pop the rising bubbles",
            category = Category.ARCADE,
            tier = Tier.MINI,
            accent = Palette.pink,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.strokeCircle(c, cx - size * 0.16f, cy + size * 0.12f, size * 0.18f, color, size * 0.05f)
                Draw.strokeCircle(c, cx + size * 0.18f, cy - size * 0.06f, size * 0.22f, Palette.alpha(color, 0.8f), size * 0.05f)
                Draw.fillCircle(c, cx + size * 0.1f, cy - size * 0.14f, size * 0.05f, Palette.alpha(Palette.text, 0.6f))
            },
            create = { BubblePopGame() }
        )
    }
}
