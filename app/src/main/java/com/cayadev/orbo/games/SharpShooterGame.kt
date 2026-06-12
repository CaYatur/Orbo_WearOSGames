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

/**
 * Sharp Shooter — targets streak across the face; tap to pop them. Smaller and faster targets are
 * worth more. Red bombs are mixed in: tap one and you lose precious seconds. Score as much as you
 * can before the 45-second clock runs out. Targets always cross through the visible round face.
 */
class SharpShooterGame : ArcadeGame(meta()) {

    private class Tgt(var x: Float, var y: Float, var vx: Float, var vy: Float, var r: Float, var bomb: Boolean, var pts: Int)

    private val tgts = ArrayList<Tgt>()
    private var spawnTimer = 0f
    private var timeLeft = 45f
    private var combo = 0
    private var comboT = 0f

    override val controlHint = "TAP TARGETS · AVOID BOMBS"
    override val gameOverTitle = "TIME UP"

    override fun onReset() { tgts.clear(); spawnTimer = 0.3f; timeLeft = 45f; combo = 0; comboT = 0f }

    private fun spawnR(s: ScreenInfo) = s.r * 1.18f

    private fun spawn() {
        val s = sc
        val sr = spawnR(s)
        val a = Mathx.rand(0f, Mathx.TAU)
        val sx = s.cx + Mathx.cosf(a) * sr
        val sy = s.cy + Mathx.sinf(a) * sr
        // aim toward a point near the centre so it crosses the face
        val tx = s.cx + Mathx.rand(-s.r * 0.4f, s.r * 0.4f)
        val ty = s.cy + Mathx.rand(-s.r * 0.4f, s.r * 0.4f)
        val ang = Mathx.angle(tx - sx, ty - sy)
        val diff = (45f - timeLeft) / 45f
        val speed = Mathx.rand(s.px(0.4f), s.px(0.85f)) * (1f + diff * 0.6f)
        val r = Mathx.rand(s.px(0.05f), s.px(0.085f))
        val bomb = Mathx.chance(0.16f + diff * 0.12f)
        val pts = ((s.px(0.085f) / r) * 10f).toInt() + (speed / s.px(0.4f) * 4f).toInt()
        tgts.add(Tgt(sx, sy, Mathx.cosf(ang) * speed, Mathx.sinf(ang) * speed, r, bomb, pts))
    }

    override fun onStep(dt: Float) {
        timeLeft -= dt
        if (comboT > 0f) { comboT -= dt; if (comboT <= 0f) combo = 0 }
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver(); return }
        spawnTimer -= dt
        val interval = Mathx.lerp(0.95f, 0.42f, (45f - timeLeft) / 45f)
        if (spawnTimer <= 0f) { spawn(); spawnTimer = interval }
        val sr = spawnR(sc) * 1.25f
        val it = tgts.iterator()
        while (it.hasNext()) {
            val t = it.next()
            t.x += t.vx * dt; t.y += t.vy * dt
            if (Mathx.dist(t.x, t.y, sc.cx, sc.cy) > sr) it.remove()
        }
    }

    override fun onTapWorld(x: Float, y: Float) {
        var best: Tgt? = null
        for (t in tgts) {
            if (Mathx.dist(x, y, t.x, t.y) <= t.r + sc.px(0.025f)) {
                if (best == null || Mathx.dist(x, y, t.x, t.y) < Mathx.dist(x, y, best!!.x, best!!.y)) best = t
            }
        }
        val t = best ?: return
        tgts.remove(t)
        if (t.bomb) {
            timeLeft = (timeLeft - 4f).coerceAtLeast(0f); combo = 0
            engine.sfx.explosion(); engine.haptics.heavy(); flash(Palette.red, 0.4f); shake(sc.px(0.022f))
            particles.burst(t.x, t.y, 22, Palette.red, sc.px(0.9f), sc.px(0.02f), 0.5f)
        } else {
            combo++; comboT = 1.5f
            addScore(t.pts * (1 + combo / 5))
            engine.sfx.coin(); engine.haptics.light()
            particles.burst(t.x, t.y, 14, accent, sc.px(0.7f), sc.px(0.016f), 0.45f)
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            for (t in tgts) {
                if (t.bomb) {
                    Draw.glow(c, t.x, t.y, t.r * 1.6f, Palette.red, 0.35f)
                    Draw.fillCircle(c, t.x, t.y, t.r, Palette.red)
                    Draw.fillCircle(c, t.x, t.y, t.r * 0.5f, Palette.alpha(Palette.bg, 0.8f))
                    Draw.line(c, t.x - t.r * 0.35f, t.y, t.x + t.r * 0.35f, t.y, Palette.text, s.dp(2.4f))
                    Draw.line(c, t.x, t.y - t.r * 0.35f, t.x, t.y + t.r * 0.35f, Palette.text, s.dp(2.4f))
                } else {
                    Draw.glow(c, t.x, t.y, t.r * 1.5f, accent, 0.28f)
                    Draw.strokeCircle(c, t.x, t.y, t.r, accent, s.dp(3f))
                    Draw.strokeCircle(c, t.x, t.y, t.r * 0.55f, Palette.alpha(accent, 0.7f), s.dp(2f))
                    Draw.fillCircle(c, t.x, t.y, t.r * 0.16f, accent)
                }
            }
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        Ui.edgeProgress(c, s, timeLeft / 45f, Palette.lerp(Palette.red, accent, timeLeft / 45f))
        if (combo > 1) Draw.textCentered(c, "x$combo", s.cx, s.h * 0.85f, s.px(0.05f), accent, Fonts.bodyBold)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.13f, s.px(0.07f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "sharp_shooter",
            title = "Sharp Shooter",
            tagline = "Pop the targets",
            category = Category.REFLEX,
            tier = Tier.MINI,
            accent = Palette.orange,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.strokeCircle(c, cx, cy, size * 0.38f, color, size * 0.06f)
                Draw.line(c, cx - size * 0.5f, cy, cx - size * 0.2f, cy, color, size * 0.05f)
                Draw.line(c, cx + size * 0.2f, cy, cx + size * 0.5f, cy, color, size * 0.05f)
                Draw.line(c, cx, cy - size * 0.5f, cx, cy - size * 0.2f, color, size * 0.05f)
                Draw.line(c, cx, cy + size * 0.2f, cx, cy + size * 0.5f, color, size * 0.05f)
                Draw.fillCircle(c, cx, cy, size * 0.08f, Palette.red)
            },
            create = { SharpShooterGame() }
        )
    }
}
