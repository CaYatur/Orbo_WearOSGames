package com.cayadev.orbo.games

import android.graphics.Canvas
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.IconDrawer
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Ease
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Mathx
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.game.ArcadeGame

/** Mole Pop — whack the critters in 30 seconds. Grab gold, avoid the bombs, build a combo. */
class MolePopGame : ArcadeGame(meta()) {

    private enum class Kind { NORMAL, GOLD, BOMB }
    private val holesX = FloatArray(7)
    private val holesY = FloatArray(7)
    private val active = BooleanArray(7)
    private val kind = arrayOfNulls<Kind>(7)
    private val rise = FloatArray(7)        // 0 hidden .. 1 up
    private val up = BooleanArray(7)        // rising vs retracting
    private val life = FloatArray(7)        // time remaining up
    private val hitAnim = FloatArray(7)

    private var spawnT = 0f
    private var timeLeft = 30f
    private var combo = 0
    private var holeR = 0f
    private var moleR = 0f

    override val controlHint = "WHACK · 30 SECONDS"
    override val gameOverTitle = "TIME UP"
    override val showScoreHud = true

    private fun layout(s: ScreenInfo) {
        holeR = s.px(0.10f); moleR = s.px(0.085f)
        val ring = s.r * 0.55f
        for (i in 0 until 6) {
            val a = -90f + i * 60f
            holesX[i] = s.cx + Mathx.cosf(Math.toRadians(a.toDouble()).toFloat()) * ring
            holesY[i] = s.cy + Mathx.sinf(Math.toRadians(a.toDouble()).toFloat()) * ring
        }
        holesX[6] = s.cx; holesY[6] = s.cy
    }

    override fun resized(s: ScreenInfo) { layout(s) }

    override fun onReset() {
        layout(sc)
        for (i in 0 until 7) { active[i] = false; rise[i] = 0f; hitAnim[i] = 0f }
        spawnT = 0f; timeLeft = 30f; combo = 0
    }

    override fun onStep(dt: Float) {
        timeLeft -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver(); return }

        val diff = 1f - (timeLeft / 30f)
        spawnT -= dt
        if (spawnT <= 0f) {
            spawnT = Mathx.lerp(0.85f, 0.32f, diff)
            spawnMole(diff)
        }

        for (i in 0 until 7) {
            if (hitAnim[i] > 0f) hitAnim[i] -= dt
            if (!active[i]) continue
            if (up[i]) {
                rise[i] = (rise[i] + dt * 6f).coerceAtMost(1f)
                life[i] -= dt
                if (life[i] <= 0f) up[i] = false
            } else {
                rise[i] -= dt * 6f
                if (rise[i] <= 0f) { rise[i] = 0f; active[i] = false }
            }
        }
    }

    private fun spawnMole(diff: Float) {
        val empties = (0 until 7).filter { !active[it] }
        if (empties.isEmpty()) return
        val h = empties[Mathx.rand(empties.size)]
        active[h] = true; up[h] = true; rise[h] = 0f
        life[h] = Mathx.lerp(1.4f, 0.7f, diff)
        kind[h] = when {
            Mathx.chance(0.16f) -> Kind.BOMB
            Mathx.chance(0.12f) -> Kind.GOLD
            else -> Kind.NORMAL
        }
    }

    /** Y of the mole's drawn centre for the current rise (matches [drawHole]). */
    private fun moleCenterY(i: Int): Float {
        val r = Ease.outCubic(rise[i].coerceIn(0f, 1f))
        return holesY[i] + holeR * 0.4f - r * (moleR * 1.4f + holeR * 0.4f)
    }

    override fun onTapWorld(x: Float, y: Float) {
        var best = -1; var bestD = Float.MAX_VALUE
        for (i in 0 until 7) {
            if (!active[i] || rise[i] < 0.4f) continue
            // Hit-test the mole where it's actually drawn (raised above the hole), generously,
            // and still accept taps that land on the hole itself.
            val d = minOf(Mathx.dist(x, y, holesX[i], moleCenterY(i)), Mathx.dist(x, y, holesX[i], holesY[i]))
            if (d < moleR * 1.7f && d < bestD) { bestD = d; best = i }
        }
        if (best < 0) { combo = 0; return }
        val i = best
        when (kind[i]) {
            Kind.BOMB -> {
                combo = 0; timeLeft = (timeLeft - 3f).coerceAtLeast(0f)
                flash(Palette.red, 0.4f); shake(sc.px(0.025f)); engine.sfx.explosion(); engine.haptics.heavy()
                particles.burst(holesX[i], holesY[i], 22, Palette.red, sc.px(0.8f), sc.px(0.02f), 0.6f)
                active[i] = false; rise[i] = 0f
            }
            else -> {
                combo++
                val pts = (if (kind[i] == Kind.GOLD) 50 else 10) + combo * 2
                addScore(pts)
                val col = if (kind[i] == Kind.GOLD) Palette.gold else accent
                if (kind[i] == Kind.GOLD) engine.sfx.coin() else engine.sfx.blip()
                engine.haptics.light()
                particles.burst(holesX[i], holesY[i], 16, col, sc.px(0.6f), sc.px(0.018f), 0.5f)
                hitAnim[i] = 0.25f
                active[i] = false; rise[i] = 0f; up[i] = false
            }
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, 0xFF0A1410.toInt(), Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            for (i in 0 until 7) drawHole(c, s, i)
        }
    }

    private fun drawHole(c: Canvas, s: ScreenInfo, i: Int) {
        val hx = holesX[i]; val hy = holesY[i]
        // hole shadow
        Draw.paint.reset(); Draw.paint.isAntiAlias = true; Draw.paint.color = 0xFF05080A.toInt()
        c.drawOval(hx - holeR, hy - holeR * 0.45f, hx + holeR, hy + holeR * 0.55f, Draw.paint)
        Draw.paint.color = Palette.alpha(Palette.line, 0.5f); Draw.paint.style = android.graphics.Paint.Style.STROKE; Draw.paint.strokeWidth = s.dp(2f)
        c.drawOval(hx - holeR, hy - holeR * 0.45f, hx + holeR, hy + holeR * 0.55f, Draw.paint)
        Draw.paint.style = android.graphics.Paint.Style.FILL

        if (active[i] && rise[i] > 0.01f) {
            val r = Ease.outCubic(rise[i])
            val moleY = hy + holeR * 0.4f - r * (moleR * 1.4f + holeR * 0.4f)
            val save = c.save()
            c.clipRect(hx - holeR, hy - holeR * 2f, hx + holeR, hy + holeR * 0.45f)
            val k = kind[i] ?: Kind.NORMAL
            val col = when (k) { Kind.GOLD -> Palette.gold; Kind.BOMB -> Palette.red; else -> accent }
            Draw.glow(c, hx, moleY, moleR * 1.6f, col, 0.3f)
            Draw.fillCircle(c, hx, moleY, moleR, col)
            Draw.fillCircle(c, hx, moleY, moleR * 0.7f, Palette.shade(col, 0.3f))
            // eyes
            Draw.fillCircle(c, hx - moleR * 0.32f, moleY - moleR * 0.1f, moleR * 0.13f, Palette.bg)
            Draw.fillCircle(c, hx + moleR * 0.32f, moleY - moleR * 0.1f, moleR * 0.13f, Palette.bg)
            if (k == Kind.BOMB) {
                val rr = moleR * 0.3f
                Draw.line(c, hx - rr, moleY + moleR * 0.25f, hx + rr, moleY + moleR * 0.25f, Palette.bg, s.dp(2.5f))
            } else {
                Draw.fillCircle(c, hx, moleY + moleR * 0.35f, moleR * 0.12f, Palette.bg)
            }
            c.restoreToCount(save)
        }
        // front lip
        Draw.paint.color = Palette.lerp(Palette.panel, Palette.bg, 0.3f)
        c.drawOval(hx - holeR, hy + holeR * 0.15f, hx + holeR, hy + holeR * 0.55f, Draw.paint)
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        // timer ring around the dial edge
        val frac = (timeLeft / 30f).coerceIn(0f, 1f)
        Ui.edgeProgress(c, s, frac, Palette.lerp(Palette.red, accent, frac))
        Draw.textCentered(c, "${timeLeft.toInt() + 1}", s.cx, s.h * 0.20f, s.px(0.05f), Palette.textDim, Fonts.mono)
        if (combo > 1) Draw.textCentered(c, "x$combo", s.cx, s.h * 0.80f, s.px(0.05f), accent, Fonts.bodyBold)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.125f, s.px(0.08f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "mole_pop",
            title = "Mole Pop",
            tagline = "Whack in 30s",
            category = Category.REFLEX,
            tier = Tier.MINI,
            accent = Palette.lime,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.paint.reset(); Draw.paint.isAntiAlias = true; Draw.paint.color = 0xFF05080A.toInt()
                c.drawOval(cx - size * 0.36f, cy + size * 0.02f, cx + size * 0.36f, cy + size * 0.34f, Draw.paint)
                Draw.fillCircle(c, cx, cy - size * 0.02f, size * 0.26f, color)
                Draw.fillCircle(c, cx - size * 0.1f, cy - size * 0.05f, size * 0.05f, Palette.bg)
                Draw.fillCircle(c, cx + size * 0.1f, cy - size * 0.05f, size * 0.05f, Palette.bg)
            },
            create = { MolePopGame() }
        )
    }
}
