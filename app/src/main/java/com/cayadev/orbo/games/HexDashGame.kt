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
 * Hex Dash — a relentless radial dodge. Walls close in from the rim, each with one open sector. Hold
 * the left or right side of the screen to spin your marker into the gap before the wall arrives. It
 * gets faster the longer you last. Pure round-face adrenaline.
 */
class HexDashGame : ArcadeGame(meta()) {

    private val sectors = 6
    private val sectorAng = Mathx.TAU / 6f
    private var pAng = 0f
    private var rotDir = 0f
    private var fieldRot = 0f

    private class Wall(var gap: Int, var dist: Float, var resolved: Boolean, var safe: Boolean)
    private val walls = ArrayList<Wall>()
    private var spawnTimer = 0f

    override val controlHint = "HOLD LEFT / RIGHT TO SPIN"

    private fun playerR(s: ScreenInfo) = if (s.isRound) s.r * 0.5f else s.minDim * 0.3f
    private fun edgeR(s: ScreenInfo) = if (s.isRound) s.r * 1.02f else s.minDim * 0.62f
    private fun coreR(s: ScreenInfo) = s.px(0.09f)

    override fun onReset() {
        pAng = -sectorAng * 1.5f; rotDir = 0f; fieldRot = 0f; walls.clear(); spawnTimer = 0f
        spawnWall(edgeR(sc))
    }

    private fun spawnWall(dist: Float) { walls.add(Wall(Mathx.rand(sectors), dist, false, false)) }

    override fun onPress(x: Float, y: Float) { rotDir = if (x < sc.cx) -1f else 1f }
    override fun onRelease(x: Float, y: Float) { rotDir = 0f }

    private fun playerSector(): Int {
        var a = (pAng - fieldRot) % Mathx.TAU
        if (a < 0) a += Mathx.TAU
        return (a / sectorAng).toInt().coerceIn(0, sectors - 1)
    }

    override fun onStep(dt: Float) {
        val s = sc
        val diff = (timeAlive / 60f).coerceIn(0f, 1.5f)
        val rotSpeed = 3.4f
        pAng += rotDir * rotSpeed * dt
        fieldRot += 0.25f * dt * (1f + diff * 0.3f)
        val wallSpeed = Mathx.lerp(s.px(0.3f), s.px(0.62f), diff.coerceIn(0f, 1f))

        spawnTimer -= dt
        val spacing = Mathx.lerp(1.2f, 0.6f, diff.coerceIn(0f, 1f))
        if (spawnTimer <= 0f) { spawnWall(edgeR(s)); spawnTimer = spacing }

        val pr = playerR(s)
        val it = walls.iterator()
        while (it.hasNext()) {
            val w = it.next()
            w.dist -= wallSpeed * dt
            if (!w.resolved && w.dist <= pr) {
                w.resolved = true
                if (playerSector() == w.gap) { w.safe = true; addScore(1); engine.sfx.blip() }
                else {
                    engine.sfx.explosion(); engine.haptics.heavy(); flash(Palette.red, 0.5f); shake(s.px(0.025f))
                    particles.burst(s.cx + Mathx.cosf(pAng) * pr, s.cy + Mathx.sinf(pAng) * pr, 24, Palette.red, s.px(0.9f), s.px(0.02f), 0.5f)
                    gameOver(); return
                }
            }
            if (w.dist <= coreR(s) * 0.8f) it.remove()
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val pr = playerR(s)
        // walls
        for (w in walls) {
            if (w.dist <= coreR(s)) continue
            val thick = (s.px(0.05f)).coerceAtMost(w.dist * 0.5f)
            for (sec in 0 until sectors) {
                if (sec == w.gap) continue
                val startDeg = Math.toDegrees((sec * sectorAng + fieldRot).toDouble()).toFloat()
                val sweep = Math.toDegrees(sectorAng.toDouble()).toFloat()
                Draw.arc(c, s.cx, s.cy, w.dist, startDeg + 1f, sweep - 2f, Palette.alpha(accent, 0.85f), thick, android.graphics.Paint.Cap.BUTT)
            }
        }
        // core
        Draw.fillCircle(c, s.cx, s.cy, coreR(s), Palette.panelHi)
        Draw.strokeCircle(c, s.cx, s.cy, coreR(s), Palette.alpha(accent, 0.7f), s.dp(2f))
        // player marker (triangle pointing outward)
        val px = s.cx + Mathx.cosf(pAng) * pr; val py = s.cy + Mathx.sinf(pAng) * pr
        Draw.glow(c, px, py, s.px(0.05f), Palette.text, 0.4f)
        val p = android.graphics.Path()
        val nx = Mathx.cosf(pAng); val ny = Mathx.sinf(pAng)
        val tx = -ny; val ty = nx; val sz = s.px(0.04f)
        p.moveTo(px + nx * sz, py + ny * sz)
        p.lineTo(px - nx * sz * 0.4f + tx * sz, py - ny * sz * 0.4f + ty * sz)
        p.lineTo(px - nx * sz * 0.4f - tx * sz, py - ny * sz * 0.4f - ty * sz)
        p.close()
        Draw.paint.reset(); Draw.paint.isAntiAlias = true; Draw.paint.color = Palette.text
        c.drawPath(p, Draw.paint)
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.cy, s.px(0.06f), Palette.alpha(Palette.text, 0.85f), Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "hex_dash",
            title = "Hex Dash",
            tagline = "Spin to the gap",
            category = Category.ARCADE,
            tier = Tier.BIG,
            accent = Palette.pink,
            featured = true,
            icon = IconDrawer { c, cx, cy, size, color ->
                for (sec in 0 until 6) {
                    if (sec == 1) continue
                    Draw.arc(c, cx, cy, size * 0.38f, sec * 60f + 2f, 56f, color, size * 0.07f, android.graphics.Paint.Cap.BUTT)
                }
                Draw.fillCircle(c, cx, cy, size * 0.1f, Palette.text)
            },
            create = { HexDashGame() }
        )
    }
}
