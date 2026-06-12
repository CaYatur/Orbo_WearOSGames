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
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.game.ArcadeGame
import kotlin.math.abs

/** Rhythm Tap — notes glide down to the beat line; tap in time for Perfect hits and rising combos.
 * Miss too many and the track cuts out. Tempo climbs as your score does. */
class RhythmTapGame : ArcadeGame(meta()) {

    private class Note(var y: Float, var judged: Boolean = false, var hue: Int)

    private val notes = ArrayList<Note>()
    private var beatT = 0f
    private var interval = 0.72f
    private var noteSpeed = 0f
    private var combo = 0
    private var health = 1f
    private var judgement = ""
    private var judgeT = 0f
    private var judgeCol = Palette.mint
    private var lineFlash = 0f
    private val palette = intArrayOf(Palette.cyan, Palette.pink, Palette.violet, Palette.mint, Palette.amber)
    private val lineY get() = sc.h * 0.78f
    private val spawnY get() = sc.h * 0.06f

    override val controlHint = "TAP ON THE BEAT"

    override fun onReset() {
        notes.clear(); beatT = 0.6f; interval = 0.72f
        noteSpeed = (lineY - spawnY) / 1.1f
        combo = 0; health = 1f; judgeT = 0f; lineFlash = 0f
    }

    override fun onStep(dt: Float) {
        val diff = (score / 1500f).coerceIn(0f, 1f)
        interval = Mathx.lerp(0.72f, 0.34f, diff)
        noteSpeed = (lineY - spawnY) / Mathx.lerp(1.15f, 0.8f, diff)

        if (judgeT > 0f) judgeT -= dt
        if (lineFlash > 0f) lineFlash -= dt * 3f

        beatT -= dt
        if (beatT <= 0f) { beatT = interval; notes.add(Note(spawnY, hue = palette[Mathx.rand(palette.size)])) }

        val it = notes.iterator()
        while (it.hasNext()) {
            val n = it.next()
            n.y += noteSpeed * dt
            if (!n.judged && n.y > lineY + sc.px(0.06f)) {
                // passed without tap
                n.judged = true; registerMiss()
                it.remove()
            } else if (n.judged && n.y > sc.h) it.remove()
        }
    }

    override fun onTapWorld(x: Float, y: Float) {
        // find nearest un-judged note to the line
        var best: Note? = null; var bestD = Float.MAX_VALUE
        for (n in notes) if (!n.judged) { val d = abs(n.y - lineY); if (d < bestD) { bestD = d; best = n } }
        lineFlash = 1f
        val window = sc.px(0.12f)
        if (best == null || bestD > window) {
            registerMiss(); return
        }
        best.judged = true
        val perfectWin = sc.px(0.04f)
        if (bestD < perfectWin) {
            combo++; addScore(100 + combo * 5); judge(Loc.t("PERFECT"), Palette.mint)
            engine.sfx.beep(523f + (combo % 8) * 40f, 0.12f, 0.5f)
            particles.burst(sc.cx, lineY, 16, best.hue, sc.px(0.6f), sc.px(0.016f), 0.45f)
            health = (health + 0.02f).coerceAtMost(1f)
        } else {
            combo++; addScore(40 + combo * 2); judge(Loc.t("GOOD"), Palette.amber)
            engine.sfx.beep(440f, 0.1f, 0.45f)
        }
        engine.haptics.light()
    }

    private fun registerMiss() {
        combo = 0; judge(Loc.t("MISS"), Palette.red)
        health -= 0.16f
        engine.sfx.hit(); engine.haptics.medium(); flash(Palette.red, 0.18f)
        if (health <= 0f) { health = 0f; gameOver() }
    }

    private fun judge(text: String, col: Int) { judgement = text; judgeCol = col; judgeT = 0.5f }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.vGradientRect(c, 0f, 0f, s.w, s.h, 0xFF0B0716.toInt(), Palette.bg)
        Draw.vignette(c, s, Palette.alpha(Palette.bg, 0f), Palette.bg)
        if (state == GState.READY) return

        Draw.withFaceClip(c, s) {
            // track edges
            val trackW = s.px(0.5f)
            Draw.vGradientRect(c, s.cx - trackW / 2, spawnY, trackW, lineY - spawnY,
                Palette.alpha(Palette.violet, 0.06f), Palette.alpha(Palette.violet, 0.14f))

            // hit line
            val lf = 0.5f + 0.5f * lineFlash
            Draw.glow(c, s.cx, lineY, trackW * 0.7f, accent, 0.2f + 0.3f * lineFlash)
            Draw.roundRect(c, s.cx - trackW / 2, lineY - s.dp(4f), trackW, s.dp(8f), s.dp(4f), Palette.alpha(accent, lf))
            Draw.strokeCircle(c, s.cx, lineY, s.px(0.11f), Palette.alpha(accent, 0.5f), s.dp(2f))

            // notes
            for (n in notes) {
                if (n.judged) continue
                val r = s.px(0.058f)
                Draw.glow(c, s.cx, n.y, r * 1.8f, n.hue, 0.3f)
                Draw.fillCircle(c, s.cx, n.y, r, n.hue)
                Draw.fillCircle(c, s.cx, n.y, r * 0.55f, Palette.shade(n.hue, 0.4f))
            }
        }

        // judgement
        if (judgeT > 0f) {
            Draw.textCentered(c, judgement, s.cx, s.h * 0.62f, s.px(0.07f), judgeCol, Fonts.display, (judgeT / 0.5f).coerceIn(0f, 1f))
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        // health arc
        Ui.edgeProgress(c, s, health.coerceIn(0f, 1f), Palette.lerp(Palette.red, Palette.mint, health))
        if (combo > 1) Draw.textCentered(c, "x$combo", s.cx, s.h * 0.30f, s.px(0.055f), accent, Fonts.bodyBold)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.135f, s.px(0.06f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "rhythm_tap",
            title = "Rhythm Tap",
            tagline = "Tap to the beat",
            category = Category.REFLEX,
            tier = Tier.MINI,
            accent = Palette.pink,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.fillCircle(c, cx, cy - size * 0.16f, size * 0.12f, Palette.cyan)
                Draw.fillCircle(c, cx, cy + size * 0.08f, size * 0.12f, Palette.violet)
                Draw.roundRectCentered(c, cx, cy + size * 0.3f, size * 0.5f, size * 0.07f, size * 0.035f, color)
            },
            create = { RhythmTapGame() }
        )
    }
}
