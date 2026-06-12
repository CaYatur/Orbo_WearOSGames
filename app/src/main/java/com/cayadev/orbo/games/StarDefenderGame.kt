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
 * Star Defender — a compact space-shooter. Drag to slide your ship; it auto-fires upward. Wipe out
 * the descending formation before it reaches your line, and dodge the bombs the invaders drop. Each
 * cleared wave is faster and deeper. Three lives. Everything stays inside the round face.
 */
class StarDefenderGame : ArcadeGame(meta()) {

    private val cols = 5
    private var rows = 3
    private val alive = Array(6) { BooleanArray(cols) }
    private var formX = 0f
    private var formY = 0f
    private var formDir = 1f
    private var formSpeed = 0f
    private var shipX = 0f
    private var fireTimer = 0f
    private var lives = 3

    private class Shot(var x: Float, var y: Float, var bomb: Boolean)
    private val shots = ArrayList<Shot>()

    override val controlHint = "DRAG TO MOVE · AUTO-FIRE"

    private fun fieldW(s: ScreenInfo) = if (s.isRound) s.minDim * 0.62f else s.minDim * 0.8f
    private fun colW(s: ScreenInfo) = fieldW(s) / cols
    private fun playerY(s: ScreenInfo) = s.h * 0.84f
    private fun enemyX(s: ScreenInfo, col: Int) = s.cx - fieldW(s) / 2f + colW(s) * (col + 0.5f) + formX
    private fun enemyY(s: ScreenInfo, row: Int) = s.h * 0.2f + colW(s) * row + formY

    override fun onReset() {
        rows = 3; formX = 0f; formY = 0f; formDir = 1f; formSpeed = sc.px(0.12f)
        shipX = sc.cx; fireTimer = 0f; lives = 3; shots.clear()
        for (r in 0 until rows) for (cc in 0 until cols) alive[r][cc] = true
    }

    private fun newWave() {
        rows = (rows + 1).coerceAtMost(5)
        formX = 0f; formY = 0f; formDir = 1f
        formSpeed = (formSpeed + sc.px(0.04f))
        for (r in 0 until rows) for (cc in 0 until cols) alive[r][cc] = true
    }

    override fun onPress(x: Float, y: Float) { shipX = clampShip(x) }
    override fun onDrag(x: Float, y: Float) { shipX = clampShip(x) }
    private fun clampShip(x: Float): Float {
        val s = sc; val half = fieldW(s) / 2f
        return x.coerceIn(s.cx - half, s.cx + half)
    }

    override fun onStep(dt: Float) {
        val s = sc
        // auto fire
        fireTimer -= dt
        if (fireTimer <= 0f) { shots.add(Shot(shipX, playerY(s) - s.px(0.05f), false)); fireTimer = 0.34f; engine.sfx.click() }
        // move formation
        formX += formDir * formSpeed * dt
        var anyAlive = false
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var lowY = 0f
        for (r in 0 until rows) for (cc in 0 until cols) if (alive[r][cc]) {
            anyAlive = true
            val ex = enemyX(s, cc); minX = minOf(minX, ex); maxX = maxOf(maxX, ex)
            lowY = maxOf(lowY, enemyY(s, r))
        }
        if (!anyAlive) { newWave(); return }
        val half = fieldW(s) / 2f
        if (maxX > s.cx + half) { formDir = -1f; formY += colW(s) * 0.5f }
        else if (minX < s.cx - half) { formDir = 1f; formY += colW(s) * 0.5f }
        if (lowY >= playerY(s) - s.px(0.06f)) { lives = 0; loseLife(true) ; return }
        // enemies occasionally drop bombs
        if (Mathx.chance(0.012f + rows * 0.002f)) {
            val living = ArrayList<Pair<Int, Int>>()
            for (r in 0 until rows) for (cc in 0 until cols) if (alive[r][cc]) living.add(r to cc)
            if (living.isNotEmpty()) {
                val (r, cc) = living[Mathx.rand(living.size)]
                shots.add(Shot(enemyX(s, cc), enemyY(s, r), true))
            }
        }
        // move shots
        val er = colW(s) * 0.36f
        val it = shots.iterator()
        while (it.hasNext()) {
            val sh = it.next()
            if (sh.bomb) {
                sh.y += s.px(0.55f) * dt
                if (sh.y > s.h + s.px(0.1f)) { it.remove(); continue }
                if (Mathx.dist(sh.x, sh.y, shipX, playerY(s)) < s.px(0.06f)) { it.remove(); loseLife(false); continue }
            } else {
                sh.y -= s.px(1.05f) * dt
                if (sh.y < -s.px(0.05f)) { it.remove(); continue }
                var hit = false
                for (r in 0 until rows) { for (cc in 0 until cols) {
                    if (alive[r][cc] && Mathx.dist(sh.x, sh.y, enemyX(s, cc), enemyY(s, r)) < er) {
                        alive[r][cc] = false; addScore(10 + rows * 2)
                        engine.sfx.hit(); engine.haptics.light()
                        particles.burst(enemyX(s, cc), enemyY(s, r), 12, accent, s.px(0.6f), s.px(0.014f), 0.4f)
                        hit = true; break
                    }
                } ; if (hit) break }
                if (hit) it.remove()
            }
        }
    }

    private fun loseLife(crushed: Boolean) {
        lives--
        engine.sfx.explosion(); engine.haptics.heavy(); flash(Palette.red, 0.4f); shake(sc.px(0.02f))
        particles.burst(shipX, playerY(sc), 24, Palette.red, sc.px(0.9f), sc.px(0.02f), 0.5f)
        if (lives <= 0 || crushed) gameOver()
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            val er = colW(s) * 0.32f
            for (r in 0 until rows) for (cc in 0 until cols) if (alive[r][cc]) {
                val ex = enemyX(s, cc); val ey = enemyY(s, r)
                val col = Palette.accents[(r) % Palette.accents.size]
                Draw.roundRectCentered(c, ex, ey, er * 1.6f, er * 1.2f, er * 0.3f, col)
                Draw.fillCircle(c, ex - er * 0.4f, ey - er * 0.1f, er * 0.2f, Palette.bg)
                Draw.fillCircle(c, ex + er * 0.4f, ey - er * 0.1f, er * 0.2f, Palette.bg)
            }
            // shots
            for (sh in shots) {
                if (sh.bomb) { Draw.fillCircle(c, sh.x, sh.y, s.px(0.018f), Palette.red) }
                else Draw.roundRectCentered(c, sh.x, sh.y, s.px(0.012f), s.px(0.05f), s.px(0.006f), accent)
            }
            // ship
            val py = playerY(s); val sw = s.px(0.07f)
            Draw.glow(c, shipX, py, sw, accent, 0.3f)
            val p = android.graphics.Path()
            p.moveTo(shipX, py - sw * 0.9f); p.lineTo(shipX - sw * 0.8f, py + sw * 0.6f); p.lineTo(shipX + sw * 0.8f, py + sw * 0.6f); p.close()
            Draw.paint.reset(); Draw.paint.isAntiAlias = true; Draw.paint.color = accent
            c.drawPath(p, Draw.paint)
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.05f); val ly = s.h * 0.95f; val startX = s.cx - gap
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, ly, s.px(0.016f), accent)
            else Draw.strokeCircle(c, startX + i * gap, ly, s.px(0.014f), Palette.alpha(accent, 0.35f), s.dp(1.4f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.115f, s.px(0.06f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "star_defender",
            title = "Star Defender",
            tagline = "Clear the invaders",
            category = Category.ACTION,
            tier = Tier.MINI,
            accent = Palette.mint,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx, cy - size * 0.2f, size * 0.5f, size * 0.32f, size * 0.1f, color)
                val p = android.graphics.Path()
                p.moveTo(cx, cy + size * 0.05f); p.lineTo(cx - size * 0.3f, cy + size * 0.4f); p.lineTo(cx + size * 0.3f, cy + size * 0.4f); p.close()
                Draw.paint.reset(); Draw.paint.isAntiAlias = true; Draw.paint.color = Palette.cyan
                c.drawPath(p, Draw.paint)
            },
            create = { StarDefenderGame() }
        )
    }
}
