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

/** Orbit Dash — two linked dots spin around a core. Hold left/right to rotate them through the
 * falling bars. Both must survive. A Duet-flavoured nerve test. */
class OrbitDashGame : ArcadeGame(meta()) {

    private class Bar(var top: Float, var h: Float, var left: Float, var right: Float, var passed: Boolean = false, var col: Int)

    private var theta = 0f
    private var dir = 0f
    private val omega = 3.7f
    private val bars = ArrayList<Bar>()
    private var spawnT = 0f
    private var fall = 0f
    private val ocy get() = sc.h * 0.58f
    private val orbitR get() = sc.px(0.2f)
    private val dotR get() = sc.px(0.042f)

    override val controlHint = "HOLD LEFT / RIGHT"

    override fun onReset() {
        theta = 0f; dir = 0f; bars.clear(); spawnT = 0.6f; fall = sc.px(0.5f)
    }

    override fun onPress(x: Float, y: Float) { dir = if (x < sc.cx) -1f else 1f }
    override fun onDrag(x: Float, y: Float) { dir = if (x < sc.cx) -1f else 1f }
    override fun onRelease(x: Float, y: Float) { dir = 0f }

    private fun dotAx() = sc.cx + Mathx.cosf(theta) * orbitR
    private fun dotAy() = ocy + Mathx.sinf(theta) * orbitR
    private fun dotBx() = sc.cx - Mathx.cosf(theta) * orbitR
    private fun dotBy() = ocy - Mathx.sinf(theta) * orbitR

    override fun onStep(dt: Float) {
        val diff = (score / 40f).coerceIn(0f, 1f)
        fall = Mathx.lerp(sc.px(0.5f), sc.px(1.0f), diff)
        theta += dir * omega * dt

        spawnT -= dt
        if (spawnT <= 0f) {
            spawnT = Mathx.lerp(1.1f, 0.5f, diff)
            spawnBar()
        }

        val colLeft = sc.cx - orbitR - dotR
        val colRight = sc.cx + orbitR + dotR
        val it = bars.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.top += fall * dt
            if (!b.passed && b.top > ocy + orbitR + dotR + sc.px(0.02f)) {
                b.passed = true; addScore(1); engine.sfx.blip()
                particles.burst(sc.cx, b.top - b.h, 8, b.col, sc.px(0.3f), sc.px(0.012f), 0.3f)
            }
            if (b.top - b.h > sc.h + sc.px(0.1f)) { it.remove(); continue }
            // collision vs both dots
            if (hits(b, dotAx(), dotAy()) || hits(b, dotBx(), dotBy())) { crash(); return }
        }
        // keep bar column in case unused warnings
        if (colLeft > colRight) return
    }

    private fun hits(b: Bar, dx: Float, dy: Float): Boolean =
        dx in b.left..b.right && dy in (b.top - b.h)..b.top

    private fun spawnBar() {
        val colLeft = sc.cx - orbitR - dotR
        val colRight = sc.cx + orbitR + dotR
        val w = sc.px(Mathx.rand(0.13f, 0.2f))
        val bx = Mathx.rand(colLeft + w / 2, colRight - w / 2)
        val h = sc.px(0.1f)
        val col = if (Mathx.chance(0.5f)) Palette.cyan else Palette.pink
        bars.add(Bar(-sc.px(0.02f), h, bx - w / 2, bx + w / 2, col = col))
    }

    private fun crash() {
        shake(sc.px(0.03f)); flash(Palette.red, 0.45f); engine.sfx.explosion(); engine.haptics.heavy()
        particles.burst(sc.cx, ocy, 28, accent, sc.px(0.9f), sc.px(0.02f), 0.7f)
        gameOver()
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            // bars
            for (b in bars) {
                Draw.roundRect(c, b.left, b.top - b.h, b.right - b.left, b.h, s.px(0.02f), Palette.alpha(b.col, 0.85f))
                Draw.roundRectStroke(c, b.left, b.top - b.h, b.right - b.left, b.h, s.px(0.02f), Palette.alpha(Palette.text, 0.15f), s.dp(1.5f))
            }
            // orbit ring + core
            Draw.strokeCircle(c, s.cx, ocy, orbitR, Palette.alpha(Palette.line, 0.7f), s.dp(1.5f))
            Draw.fillCircle(c, s.cx, ocy, s.px(0.03f), Palette.panelHi)
            // dots
            Draw.glow(c, dotAx(), dotAy(), dotR * 2f, Palette.cyan, 0.45f)
            Draw.fillCircle(c, dotAx(), dotAy(), dotR, Palette.cyan)
            Draw.glow(c, dotBx(), dotBy(), dotR * 2f, Palette.pink, 0.45f)
            Draw.fillCircle(c, dotBx(), dotBy(), dotR, Palette.pink)
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.135f, s.px(0.075f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "orbit_dash",
            title = "Orbit Dash",
            tagline = "Spin through bars",
            category = Category.REFLEX,
            tier = Tier.MINI,
            accent = Palette.cyan,
            icon = IconDrawer { c, cx, cy, size, _ ->
                Draw.strokeCircle(c, cx, cy, size * 0.3f, Palette.alpha(Palette.line, 0.9f), size * 0.04f)
                Draw.fillCircle(c, cx + size * 0.3f, cy, size * 0.1f, Palette.cyan)
                Draw.fillCircle(c, cx - size * 0.3f, cy, size * 0.1f, Palette.pink)
            },
            create = { OrbitDashGame() }
        )
    }
}
