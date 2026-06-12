package com.cayadev.orbo.games.geoleap

import android.graphics.Canvas
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Ease
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Loc
import com.cayadev.orbo.engine.Mathx
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.Particles
import com.cayadev.orbo.engine.Scene
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.engine.Ui
import kotlin.math.abs
import kotlin.random.Random

/**
 * Geo Leap — the flagship rhythm-runner. An auto-running cube leaps over spikes and onto blocks to
 * a pulsing beat. Instant retries (Geometry-Dash style), per-level progress, escalating speed and
 * density. This is the [GeoLeapRunner]; [GeoLeapHub] is the level picker.
 */
class GeoLeapRunner(private val level: Int) : Scene() {

    private enum class St { READY, PLAYING, DEAD, COMPLETE }
    private var st = St.READY

    private class Ob(val x: Float, val type: Int, val w: Float, val hgt: Float) // 0 spike,1 block,2 spikeTriple unit
    private val obs = ArrayList<Ob>()
    private var levelLen = 0f

    private var dist = 0f
    private var py = 0f
    private var vy = 0f
    private var rot = 0f
    private var grounded = true
    private var holding = false
    private var attempts = 1
    private var best = 0
    private var deadT = 0f
    private var beat = 0f
    private var shakeMag = 0f
    private var flashA = 0f
    private var bgHue = 0f
    private var appBackCaptured = false
    private val particles = Particles()

    private val def = LevelDefs.levels[level.coerceIn(0, LevelDefs.levels.size - 1)]
    private val pxScreen get() = sc.px(0.30f)
    private val groundY get() = sc.h * 0.70f
    private val pr get() = sc.px(0.052f)
    private val speed get() = sc.px(def.speed)
    private val jumpV get() = -sc.px(1.28f)
    private val grav get() = sc.px(3.5f)

    override fun enter() {
        best = engine.scores.getInt("geo_best_$level", 0)
        buildLevel()
        toReady()
    }

    private fun toReady() { st = St.READY; dist = 0f; py = groundY - pr; vy = 0f; grounded = true; rot = 0f }

    private fun start() {
        st = St.PLAYING; dist = 0f; py = groundY - pr; vy = 0f; grounded = true; rot = 0f
        particles.clear(); engine.sfx.blip()
    }

    /**
     * Deterministic, guaranteed-clearable level builder. Seeded by the level index so the layout is
     * identical every attempt and on every resolution. Every obstacle is followed by a flat landing
     * zone of at least one full jump air-distance, so a perfectly-timed jump can always clear it —
     * no more "impossible" sequences. All distances are in fractions of the min screen dimension.
     */
    private fun buildLevel() {
        obs.clear()
        val rng = Random(level * 0x9E3779B1L + 1234567L)
        // Horizontal distance covered by a single jump (air time = 2*|jumpV|/grav ≈ 0.731s).
        val airDist = def.speed * 0.731f
        var xf = 1.35f                                   // cursor (off the right edge)
        repeat(def.beats) {
            val roll = rng.nextInt(100)
            val tEnd = def.tripleChance
            val dEnd = tEnd + def.doubleChance
            val bEnd = dEnd + def.blockChance
            when {
                roll < tEnd -> {                          // triple spikes
                    val sw = 0.080f
                    for (k in 0 until 3) obs.add(Ob(sc.px(xf + k * sw), 0, sc.px(sw), sc.px(0.105f)))
                    xf += sw * 3
                }
                roll < dEnd -> {                          // double spikes
                    val sw = 0.082f
                    for (k in 0 until 2) obs.add(Ob(sc.px(xf + k * sw), 0, sc.px(sw), sc.px(0.105f)))
                    xf += sw * 2
                }
                roll < bEnd -> {                          // landable block (height always < jump apex)
                    val bw = 0.14f + rng.nextFloat() * 0.07f
                    val bh = 0.095f + rng.nextFloat() * 0.05f
                    obs.add(Ob(sc.px(xf), 1, sc.px(bw), sc.px(bh)))
                    xf += bw
                }
                else -> {                                 // single spike
                    val sw = 0.085f
                    obs.add(Ob(sc.px(xf), 0, sc.px(sw), sc.px(0.11f)))
                    xf += sw
                }
            }
            // Safe flat landing/run-up before the next obstacle (>= one jump reach).
            xf += airDist * (def.gapMinF + rng.nextFloat() * (def.gapMaxF - def.gapMinF))
        }
        levelLen = sc.px(xf + 0.6f)
    }

    // ---- input ----
    override fun onDown(x: Float, y: Float) {
        appBackCaptured = Ui.hitAppBack(sc, x, y)
        if (appBackCaptured) return
        holding = true
        when (st) {
            St.READY -> start()
            St.PLAYING -> tryJump()
            St.COMPLETE -> { engine.scores.setBool("geo_done_$level", true); engine.pop() }
            St.DEAD -> {}
        }
    }
    override fun onUp(x: Float, y: Float) {
        if (appBackCaptured) return
        holding = false
    }
    override fun onTap(x: Float, y: Float) {
        if (appBackCaptured) {
            appBackCaptured = false
            engine.back()
        }
    }
    override fun onBack(): Boolean { return false } // app back button pops to the hub

    private fun tryJump() {
        if (grounded) {
            vy = jumpV; grounded = false
            engine.sfx.jump(); engine.haptics.light()
            particles.burst(pxScreen, py + pr, 6, def.accent, sc.px(0.3f), sc.px(0.012f), 0.3f)
        }
    }

    override fun update(dt: Float) {
        beat += dt
        if (shakeMag > 0.2f) shakeMag = Mathx.approach(shakeMag, 0f, 11f, dt) else shakeMag = 0f
        if (flashA > 0f) flashA = (flashA - dt * 2.4f).coerceAtLeast(0f)
        particles.update(dt)
        bgHue += dt * 6f

        when (st) {
            St.PLAYING -> stepPlay(dt)
            St.DEAD -> { deadT -= dt; if (deadT <= 0f) start() }
            else -> {}
        }
    }

    private fun stepPlay(dt: Float) {
        dist += speed * dt
        if (holding && grounded) tryJump()

        // physics
        vy += grav * dt
        py += vy * dt
        if (!grounded) rot += dt * 6.0f

        // determine surface under player
        var surface = groundY
        var sideDeath = false
        for (o in obs) {
            val sx = pxScreen + (o.x - dist)
            if (o.type == 1) {
                val left = sx; val right = sx + o.w; val top = groundY - o.hgt
                val pLeft = pxScreen - pr; val pRight = pxScreen + pr
                if (pRight > left && pLeft < right) {
                    if (py + pr <= top + pr * 0.55f) { if (top < surface) surface = top }
                    else sideDeath = true
                }
            } else { // spike
                val cx = sx + o.w / 2
                val top = groundY - o.hgt
                if (abs(pxScreen - cx) < o.w * 0.52f + pr * 0.5f && py + pr > top + o.hgt * 0.32f) sideDeath = true
            }
            if (sx < -sc.w) continue
        }
        if (sideDeath) { die(); return }

        if (py + pr >= surface) {
            if (vy >= 0f) { py = surface - pr; vy = 0f; if (!grounded) { grounded = true; rot = Math.round(rot / 90.0).toFloat() * 90f / 57.2958f } }
        } else grounded = false
        if (py + pr < surface - 1f) grounded = false

        val prog = (dist / levelLen * 100f).toInt().coerceIn(0, 100)
        if (prog > best) { best = prog; engine.scores.setInt("geo_best_$level", best) }

        if (dist >= levelLen) complete()
    }

    private fun die() {
        st = St.DEAD; deadT = 0.45f; attempts++
        shakeMag = sc.px(0.03f); flashA = 0.5f
        engine.sfx.explosion(); engine.haptics.heavy()
        particles.burst(pxScreen, py, 30, def.accent, sc.px(1.0f), sc.px(0.022f), 0.8f, gravity = sc.px(1.5f))
    }

    private fun complete() {
        st = St.COMPLETE
        engine.scores.setInt("geo_best_$level", 100)
        engine.scores.setBool("geo_done_$level", true)
        engine.scores.submit("geo_leap", (level + 1) * 100)
        engine.sfx.success(); engine.haptics.success()
        particles.burst(sc.cx, sc.cy, 40, def.accent, sc.px(1.1f), sc.px(0.02f), 1.0f)
    }

    override fun render(c: Canvas, s: ScreenInfo) {
        // animated gradient sky
        val top = Palette.lerp(def.sky1, def.sky2, 0.5f + 0.5f * Mathx.sinf(bgHue * 0.2f))
        Draw.vGradientRect(c, 0f, 0f, s.w, s.h, top, Palette.bg)

        val save = c.save()
        if (shakeMag > 0.2f) c.translate(Mathx.rand(-shakeMag, shakeMag), Mathx.rand(-shakeMag, shakeMag))
        Draw.withFaceClip(c, s) {
            drawWorld(c, s)
        }
        c.restoreToCount(save)

        if (flashA > 0f) Draw.background(c, Palette.alpha(Palette.text, flashA * 0.6f))

        when (st) {
            St.READY -> drawReady(c, s)
            St.PLAYING, St.DEAD -> drawHud(c, s)
            St.COMPLETE -> drawComplete(c, s)
        }
        Ui.appBackButton(c, s, beat, def.accent)
    }

    private fun drawWorld(c: Canvas, s: ScreenInfo) {
        // ground
        Draw.roundRect(c, 0f, groundY, s.w, s.h - groundY, 0f, Palette.lerp(def.accent, Palette.bg, 0.7f))
        Draw.line(c, 0f, groundY, s.w, groundY, def.accent, s.dp(2.5f))
        // grid lines on ground scrolling
        val gridStep = s.px(0.12f)
        var gx = -((dist % gridStep))
        while (gx < s.w) {
            Draw.line(c, gx, groundY, gx, s.h, Palette.alpha(def.accent, 0.12f), s.dp(1f))
            gx += gridStep
        }

        // obstacles
        for (o in obs) {
            val sx = pxScreen + (o.x - dist)
            if (sx > s.w + s.px(0.3f) || sx < -s.px(0.3f)) continue
            if (o.type == 1) {
                val top = groundY - o.hgt
                Draw.roundRect(c, sx, top, o.w, o.hgt, s.px(0.012f), Palette.lerp(def.accent, Palette.text, 0.1f))
                Draw.roundRectStroke(c, sx, top, o.w, o.hgt, s.px(0.012f), Palette.alpha(Palette.bg, 0.5f), s.dp(2f))
            } else {
                val p = Draw.paint; p.reset(); p.isAntiAlias = true; p.color = def.spike
                val path = android.graphics.Path()
                path.moveTo(sx, groundY); path.lineTo(sx + o.w / 2, groundY - o.hgt); path.lineTo(sx + o.w, groundY); path.close()
                c.drawPath(path, p)
                p.color = Palette.alpha(Palette.text, 0.25f)
                path.rewind(); path.moveTo(sx + o.w * 0.3f, groundY); path.lineTo(sx + o.w / 2, groundY - o.hgt); path.lineTo(sx + o.w * 0.5f, groundY); path.close()
                c.drawPath(path, p)
            }
        }

        // player cube
        if (st != St.DEAD) {
            val save = c.save()
            c.rotate(Math.toDegrees(rot.toDouble()).toFloat(), pxScreen, py)
            Draw.glow(c, pxScreen, py, pr * 2f, def.accent, 0.5f)
            Draw.roundRectCentered(c, pxScreen, py, pr * 2, pr * 2, pr * 0.28f, def.accent)
            Draw.roundRectCentered(c, pxScreen, py, pr * 1.1f, pr * 1.1f, pr * 0.2f, Palette.shade(def.accent, -0.25f))
            c.restoreToCount(save)
        }
        particles.render(c)
    }

    private fun drawReady(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.alpha(Palette.bg, 0.55f))
        Draw.textCentered(c, def.name, s.cx, s.h * 0.36f, s.px(0.1f), def.accent, Fonts.display, letterSpacing = 0.04f)
        Draw.textCentered(c, "${Loc.t("BEST")}  $best%", s.cx, s.h * 0.47f, s.px(0.045f), Palette.text, Fonts.mono)
        val pulse = 0.6f + 0.4f * Ease.pulse(beat * 1.5f)
        Ui.pill(c, s.cx, s.h * 0.64f, s.px(0.52f), s.px(0.135f), Loc.t("TAP TO START"), def.accent, true, 0.8f + 0.2f * pulse)
        Draw.textCentered(c, Loc.t("TAP / HOLD TO JUMP"), s.cx, s.h * 0.78f, s.px(0.034f), Palette.textDim, Fonts.mono, letterSpacing = 0.1f)
    }

    private fun drawHud(c: Canvas, s: ScreenInfo) {
        // progress bar (top arc)
        val prog = (dist / levelLen).coerceIn(0f, 1f)
        Ui.edgeProgress(c, s, prog, def.accent)
        Draw.textCentered(c, "${(prog * 100).toInt()}%", s.cx, s.h * 0.12f, s.px(0.055f), Palette.text, Fonts.display)
        Draw.textCentered(c, "${Loc.t("ATTEMPT")} $attempts", s.cx, s.h * 0.88f, s.px(0.032f), Palette.textDim, Fonts.mono)
    }

    private fun drawComplete(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.alpha(Palette.bg, 0.6f))
        val t = (beat % 10f)
        Draw.textCentered(c, Loc.t("LEVEL"), s.cx, s.h * 0.34f, s.px(0.05f), Palette.textDim, Fonts.mono, letterSpacing = 0.2f)
        Draw.textCentered(c, Loc.t("COMPLETE"), s.cx, s.h * 0.45f, s.px(0.09f), def.accent, Fonts.display)
        Draw.textCentered(c, "★ $attempts ${Loc.t("attempts")} ★", s.cx, s.h * 0.57f, s.px(0.04f), Palette.gold, Fonts.mono)
        Ui.pill(c, s.cx, s.h * 0.72f, s.px(0.5f), s.px(0.13f), Loc.t("CONTINUE"), def.accent, true)
    }

    companion object
}
