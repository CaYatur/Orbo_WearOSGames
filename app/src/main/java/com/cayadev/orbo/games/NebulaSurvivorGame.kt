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
import com.cayadev.orbo.game.ArcadeGame
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Nebula Survivor — the second flagship. A bullet-heaven roguelite: steer with a floating joystick,
 * your ship auto-fires at the nearest foe, hoover up XP, and pick from three upgrades each level.
 * Endless escalating waves with periodic bosses. Survive as long as you can.
 */
class NebulaSurvivorGame : ArcadeGame(meta()) {

    private class Enemy(var x: Float, var y: Float, var hp: Float, var maxHp: Float, var r: Float,
                        var spd: Float, var dmg: Float, var kind: Int, var hitFx: Float = 0f)
    private class Bullet(var x: Float, var y: Float, var vx: Float, var vy: Float, var dmg: Float, var pierce: Int, var crit: Boolean)
    private class Gem(var x: Float, var y: Float, var v: Int)

    private var px = 0f; private var py = 0f
    private var joyActive = false; private var joyOx = 0f; private var joyOy = 0f; private var joyCx = 0f; private var joyCy = 0f

    private val enemies = ArrayList<Enemy>()
    private val bullets = ArrayList<Bullet>()
    private val gems = ArrayList<Gem>()

    private var hp = 100f; private var maxHp = 100f
    private var iFrames = 0f
    private var time = 0f
    private var kills = 0
    private var spawnT = 0f
    private var fireT = 0f
    private var bossT = 30f

    // upgradeable stats
    private var damage = 10f
    private var fireRate = 1.6f       // shots/sec
    private var multishot = 1
    private var pierce = 0
    private var bulletSpd = 0.9f      // *minDim/s
    private var moveSpd = 0.5f
    private var magnet = 0.14f
    private var critChance = 0.0f

    private var level = 1
    private var xp = 0; private var xpNeed = 5
    private var levelingUp = false
    private val choices = ArrayList<Upgrade>()

    private val pr get() = sc.px(0.045f)

    override val controlHint = "DRAG TO MOVE · AUTO-FIRE"
    override val showScoreHud = false

    private class Upgrade(val name: String, val desc: String, val color: Int, val apply: () -> Unit)

    override fun onReset() {
        px = sc.cx; py = sc.cy; joyActive = false
        enemies.clear(); bullets.clear(); gems.clear()
        hp = 100f; maxHp = 100f; iFrames = 0f; time = 0f; kills = 0
        spawnT = 0f; fireT = 0f; bossT = 30f
        damage = 10f; fireRate = 1.6f; multishot = 1; pierce = 0; bulletSpd = 0.9f
        moveSpd = 0.5f; magnet = 0.14f; critChance = 0f
        level = 1; xp = 0; xpNeed = 5; levelingUp = false
    }

    // ---- input ----
    override fun onPress(x: Float, y: Float) {
        if (levelingUp) { pickCard(x, y); return }
        joyActive = true; joyOx = x; joyOy = y; joyCx = x; joyCy = y
    }
    override fun onDrag(x: Float, y: Float) { if (!levelingUp) { joyCx = x; joyCy = y } }
    override fun onRelease(x: Float, y: Float) { joyActive = false }
    override fun onTapWorld(x: Float, y: Float) { if (levelingUp) pickCard(x, y) }

    override fun onStep(dt: Float) {
        if (levelingUp) return
        time += dt
        if (iFrames > 0f) iFrames -= dt

        // movement
        if (joyActive) {
            val dx = joyCx - joyOx; val dy = joyCy - joyOy
            val d = hypot(dx, dy)
            val dead = sc.px(0.02f); val maxR = sc.px(0.13f)
            if (d > dead) {
                val mag = ((d - dead) / (maxR - dead)).coerceIn(0f, 1f)
                val nx = dx / d; val ny = dy / d
                px += nx * sc.px(moveSpd) * mag * dt
                py += ny * sc.px(moveSpd) * mag * dt
            }
        }
        clampToFace()

        // spawning
        val diff = time / 60f
        spawnT -= dt
        if (spawnT <= 0f) {
            spawnT = Mathx.lerp(1.1f, 0.3f, diff.coerceIn(0f, 1f))
            spawnEnemy(diff)
        }
        bossT -= dt
        if (bossT <= 0f) { bossT = 45f; spawnBoss(diff) }

        // firing
        fireT -= dt
        if (fireT <= 0f && enemies.isNotEmpty()) {
            fireT = 1f / fireRate
            fireAtNearest()
        }

        updateBullets(dt)
        updateEnemies(dt)
        updateGems(dt)

        if (hp <= 0f) { hp = 0f; die() }
    }

    private fun clampToFace() {
        val lim = (if (sc.isRound) sc.r else sc.minDim / 2) - pr - sc.px(0.01f)
        val dx = px - sc.cx; val dy = py - sc.cy; val d = hypot(dx, dy)
        if (d > lim) { px = sc.cx + dx / d * lim; py = sc.cy + dy / d * lim }
    }

    private fun spawnEnemy(diff: Float) {
        val a = Mathx.rand(0f, Mathx.TAU)
        val rr = (if (sc.isRound) sc.r else sc.minDim / 2) + sc.px(0.06f)
        val ex = sc.cx + Mathx.cosf(a) * rr; val ey = sc.cy + Mathx.sinf(a) * rr
        val kind = when { Mathx.chance(0.18f + diff * 0.1f) -> 1; Mathx.chance(0.12f) -> 2; else -> 0 }
        val baseHp = (12f + time * 0.7f)
        when (kind) {
            1 -> enemies.add(Enemy(ex, ey, baseHp * 0.6f, baseHp * 0.6f, sc.px(0.03f), sc.px(0.34f), 8f, 1))
            2 -> enemies.add(Enemy(ex, ey, baseHp * 2.2f, baseHp * 2.2f, sc.px(0.06f), sc.px(0.13f), 16f, 2))
            else -> enemies.add(Enemy(ex, ey, baseHp, baseHp, sc.px(0.042f), sc.px(0.2f), 10f, 0))
        }
    }

    private fun spawnBoss(diff: Float) {
        val a = Mathx.rand(0f, Mathx.TAU)
        val rr = (if (sc.isRound) sc.r else sc.minDim / 2) + sc.px(0.08f)
        val ex = sc.cx + Mathx.cosf(a) * rr; val ey = sc.cy + Mathx.sinf(a) * rr
        val bhp = 120f + time * 4f
        enemies.add(Enemy(ex, ey, bhp, bhp, sc.px(0.1f), sc.px(0.1f), 24f, 3))
        engine.sfx.fail(); engine.haptics.heavy()
    }

    private fun fireAtNearest() {
        var best: Enemy? = null; var bd = Float.MAX_VALUE
        for (e in enemies) { val d = Mathx.dist2(px, py, e.x, e.y); if (d < bd) { bd = d; best = e } }
        val target = best ?: return
        val ang = Mathx.angle(target.x - px, target.y - py)
        val spread = 0.18f
        val shots = multishot
        for (i in 0 until shots) {
            val off = (i - (shots - 1) / 2f) * spread
            val a = ang + off
            val crit = Mathx.chance(critChance)
            val dmg = damage * (if (crit) 2.2f else 1f)
            bullets.add(Bullet(px, py, Mathx.cosf(a) * sc.px(bulletSpd), Mathx.sinf(a) * sc.px(bulletSpd), dmg, pierce, crit))
        }
        engine.sfx.tone(com.cayadev.orbo.engine.Sfx.Wave.SQUARE, 760f, 900f, 0.04f, 0.25f)
    }

    private fun updateBullets(dt: Float) {
        val it = bullets.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.x += b.vx * dt; b.y += b.vy * dt
            if (Mathx.dist(b.x, b.y, sc.cx, sc.cy) > (if (sc.isRound) sc.r else sc.minDim / 2) + sc.px(0.1f)) { it.remove(); continue }
            var hit = false
            for (e in enemies) {
                if (Mathx.dist2(b.x, b.y, e.x, e.y) < (e.r + sc.px(0.018f)) * (e.r + sc.px(0.018f))) {
                    e.hp -= b.dmg; e.hitFx = 0.12f
                    particles.spawn(b.x, b.y, b.vx * 0.1f, b.vy * 0.1f, 0.2f, sc.px(0.01f), if (b.crit) Palette.gold else accent)
                    if (b.pierce > 0) { b.pierce--; } else { hit = true }
                    break
                }
            }
            if (hit) it.remove()
        }
    }

    private fun updateEnemies(dt: Float) {
        val it = enemies.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.hitFx > 0f) e.hitFx -= dt
            val dx = px - e.x; val dy = py - e.y; val d = hypot(dx, dy).coerceAtLeast(0.001f)
            e.x += dx / d * e.spd * dt; e.y += dy / d * e.spd * dt
            if (e.hp <= 0f) {
                kills++; addScore(if (e.kind == 3) 50 else 5)
                gems.add(Gem(e.x, e.y, if (e.kind == 3) 8 else if (e.kind == 2) 3 else 1))
                particles.burst(e.x, e.y, if (e.kind == 3) 28 else 10, enemyColor(e.kind), sc.px(0.5f), sc.px(0.015f), 0.5f)
                if (e.kind == 3) { flash(accent, 0.2f); engine.sfx.explosion() } else engine.sfx.tone(com.cayadev.orbo.engine.Sfx.Wave.NOISE, 300f, 80f, 0.1f, 0.2f)
                it.remove(); continue
            }
            // contact
            if (d < e.r + pr && iFrames <= 0f) {
                hp -= e.dmg; iFrames = 0.7f
                shake(sc.px(0.02f)); flash(Palette.red, 0.3f); engine.sfx.hit(); engine.haptics.heavy()
            }
        }
    }

    private fun updateGems(dt: Float) {
        val mag = sc.px(magnet)
        val it = gems.iterator()
        while (it.hasNext()) {
            val g = it.next()
            val d = Mathx.dist(g.x, g.y, px, py)
            if (d < mag) {
                val nx = (px - g.x); val ny = (py - g.y); val dd = d.coerceAtLeast(0.001f)
                g.x += nx / dd * sc.px(0.9f) * dt; g.y += ny / dd * sc.px(0.9f) * dt
            }
            if (d < pr + sc.px(0.02f)) { collectXp(g.v); it.remove() }
        }
    }

    private fun collectXp(v: Int) {
        xp += v; engine.sfx.tone(com.cayadev.orbo.engine.Sfx.Wave.SINE, 900f, 1200f, 0.05f, 0.25f)
        if (xp >= xpNeed) { xp -= xpNeed; level++; xpNeed = (xpNeed * 1.3f + 2).toInt(); openLevelUp() }
    }

    private fun openLevelUp() {
        levelingUp = true; joyActive = false
        engine.sfx.powerup(); engine.haptics.success()
        choices.clear()
        val pool = buildPool().shuffled()
        for (i in 0 until 3) choices.add(pool[i % pool.size])
    }

    private fun buildPool(): List<Upgrade> = listOf(
        Upgrade("DAMAGE", "+35% bullet damage", Palette.red) { damage *= 1.35f },
        Upgrade("FIRE RATE", "+25% fire speed", Palette.amber) { fireRate *= 1.25f },
        Upgrade("MULTISHOT", "+1 projectile", Palette.cyan) { multishot++ },
        Upgrade("PIERCE", "Bullets pierce +1", Palette.violet) { pierce++ },
        Upgrade("VELOCITY", "+20% bullet speed", Palette.lime) { bulletSpd *= 1.2f },
        Upgrade("THRUSTERS", "+18% move speed", Palette.blue) { moveSpd *= 1.18f },
        Upgrade("MAGNET", "+40% pickup range", Palette.pink) { magnet *= 1.4f },
        Upgrade("VITALITY", "+25 max HP & heal", Palette.mint) { maxHp += 25f; hp = (hp + 35f).coerceAtMost(maxHp) },
        Upgrade("CRIT", "+10% crit chance", Palette.gold) { critChance = (critChance + 0.1f).coerceAtMost(0.7f) }
    )

    private fun pickCard(x: Float, y: Float) {
        for (i in 0 until 3) {
            val r = cardRect(sc, i)
            if (x in r[0]..(r[0] + r[2]) && y in r[1]..(r[1] + r[3])) {
                choices[i].apply(); levelingUp = false
                engine.sfx.coin(); engine.haptics.medium()
                return
            }
        }
    }

    private fun cardRect(s: ScreenInfo, i: Int): FloatArray {
        val w = s.px(0.62f); val h = s.px(0.17f); val gap = s.px(0.03f)
        val totalH = h * 3 + gap * 2
        val x = s.cx - w / 2; val y = s.cy - totalH / 2 + i * (h + gap)
        return floatArrayOf(x, y, w, h)
    }

    private fun die() {
        shake(sc.px(0.035f)); flash(Palette.red, 0.5f); engine.sfx.explosion(); engine.haptics.error()
        particles.burst(px, py, 40, accent, sc.px(1.1f), sc.px(0.022f), 0.9f)
        gameOver()
    }

    private fun enemyColor(kind: Int) = when (kind) { 1 -> Palette.amber; 2 -> Palette.violet; 3 -> Palette.red; else -> Palette.pink }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.vGradientRect(c, 0f, 0f, s.w, s.h, 0xFF0A0820.toInt(), Palette.bg)
        Draw.vignette(c, s, Palette.alpha(Palette.bg, 0f), Palette.bg)
        if (state == GState.READY) return
        Draw.withFaceClip(c, s) {
            // gems
            for (g in gems) { Draw.glow(c, g.x, g.y, s.px(0.025f), Palette.cyan, 0.4f); Draw.fillCircle(c, g.x, g.y, s.px(0.012f), Palette.cyan) }
            // enemies
            for (e in enemies) drawEnemy(c, e)
            // bullets
            for (b in bullets) { Draw.glow(c, b.x, b.y, s.px(0.03f), if (b.crit) Palette.gold else accent, 0.5f); Draw.fillCircle(c, b.x, b.y, s.px(0.014f), if (b.crit) Palette.gold else Palette.text) }
            // player
            drawPlayer(c, s)
            // joystick
            if (joyActive && !levelingUp) {
                Draw.strokeCircle(c, joyOx, joyOy, s.px(0.11f), Palette.alpha(Palette.text, 0.15f), s.dp(2f))
                Draw.fillCircle(c, joyCx.coerceIn(joyOx - s.px(0.11f), joyOx + s.px(0.11f)), joyCy.coerceIn(joyOy - s.px(0.11f), joyOy + s.px(0.11f)), s.px(0.035f), Palette.alpha(accent, 0.4f))
            }
        }
        if (levelingUp) drawLevelUp(c, s)
    }

    private fun drawPlayer(c: Canvas, s: ScreenInfo) {
        val flick = iFrames > 0f && ((iFrames * 20f).toInt() % 2 == 0)
        if (flick) return
        Draw.glow(c, px, py, pr * 2.2f, accent, 0.5f)
        val p = Draw.paint; p.reset(); p.isAntiAlias = true; p.color = accent
        // aim direction toward nearest enemy
        var ang = -1.5708f
        var bd = Float.MAX_VALUE
        for (e in enemies) { val d = Mathx.dist2(px, py, e.x, e.y); if (d < bd) { bd = d; ang = Mathx.angle(e.x - px, e.y - py) } }
        val save = c.save(); c.rotate(Math.toDegrees(ang.toDouble()).toFloat() + 90f, px, py)
        val path = android.graphics.Path()
        path.moveTo(px, py - pr); path.lineTo(px + pr * 0.85f, py + pr * 0.8f); path.lineTo(px, py + pr * 0.4f); path.lineTo(px - pr * 0.85f, py + pr * 0.8f); path.close()
        c.drawPath(path, p)
        Draw.fillCircle(c, px, py - pr * 0.1f, pr * 0.3f, Palette.shade(accent, 0.5f))
        c.restoreToCount(save)
    }

    private fun drawEnemy(c: Canvas, e: Enemy) {
        val col = if (e.hitFx > 0f) Palette.text else enemyColor(e.kind)
        Draw.glow(c, e.x, e.y, e.r * 1.6f, enemyColor(e.kind), 0.25f)
        if (e.kind == 3) {
            Draw.fillCircle(c, e.x, e.y, e.r, col)
            Draw.strokeCircle(c, e.x, e.y, e.r * 1.1f, Palette.alpha(Palette.red, 0.6f), sc.dp(2f))
            // boss hp ring
            Draw.arc(c, e.x, e.y, e.r * 1.3f, -90f, 360f * (e.hp / e.maxHp).coerceIn(0f, 1f), Palette.red, sc.dp(3f))
        } else {
            Draw.roundRectCentered(c, e.x, e.y, e.r * 1.7f, e.r * 1.7f, e.r * 0.35f, col)
            Draw.fillCircle(c, e.x - e.r * 0.3f, e.y - e.r * 0.2f, e.r * 0.18f, Palette.bg)
            Draw.fillCircle(c, e.x + e.r * 0.3f, e.y - e.r * 0.2f, e.r * 0.18f, Palette.bg)
        }
    }

    private fun drawLevelUp(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.alpha(Palette.bg, 0.78f))
        Draw.textCentered(c, "${Loc.t("LEVEL")} $level", s.cx, s.cardTop(), s.px(0.06f), accent, Fonts.display)
        for (i in 0 until 3) {
            val r = cardRect(s, i); val u = choices[i]
            Draw.roundRect(c, r[0], r[1], r[2], r[3], r[3] * 0.22f, Palette.panelHi)
            Draw.roundRectStroke(c, r[0], r[1], r[2], r[3], r[3] * 0.22f, Palette.alpha(u.color, 0.7f), s.dp(2f))
            Draw.fillCircle(c, r[0] + r[3] * 0.5f, r[1] + r[3] * 0.5f, r[3] * 0.22f, u.color)
            Draw.text(c, u.name, r[0] + r[3] * 0.95f, r[1] + r[3] * 0.42f, r[3] * 0.26f, Palette.text, android.graphics.Paint.Align.LEFT, Fonts.bodyBold)
            Draw.text(c, u.desc, r[0] + r[3] * 0.95f, r[1] + r[3] * 0.72f, r[3] * 0.18f, Palette.textDim, android.graphics.Paint.Align.LEFT, Fonts.mono)
        }
    }
    private fun ScreenInfo.cardTop() = cy - px(0.17f) * 1.5f - px(0.03f) - px(0.05f)

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        // HP arc (left) and XP arc (full ring)
        val hpFrac = (hp / maxHp).coerceIn(0f, 1f)
        Draw.arc(c, s.cx, s.cy, s.r * 0.97f, 90f, 170f * hpFrac, Palette.lerp(Palette.red, Palette.mint, hpFrac), s.dp(5f))
        val xpFrac = (xp.toFloat() / xpNeed).coerceIn(0f, 1f)
        Draw.arc(c, s.cx, s.cy, s.r * 0.97f, -90f, 170f * xpFrac, Palette.cyan, s.dp(5f))
        Draw.textCentered(c, "${time.toInt()}s", s.cx, s.h * 0.10f, s.px(0.045f), Palette.text, Fonts.mono)
        Draw.textCentered(c, "${Loc.t("LV")}$level", s.cx, s.h * 0.90f, s.px(0.04f), Palette.cyan, Fonts.mono)
    }

    companion object {
        fun meta() = GameMeta(
            id = "nebula_survivor",
            title = "Nebula Survivor",
            tagline = "Bullet-heaven roguelite",
            category = Category.ARCADE,
            tier = Tier.FLAGSHIP,
            accent = Palette.cyan,
            featured = true,
            icon = IconDrawer { c, cx, cy, size, color ->
                val p = Draw.paint; p.reset(); p.isAntiAlias = true; p.color = color
                val path = android.graphics.Path()
                path.moveTo(cx, cy - size * 0.3f); path.lineTo(cx + size * 0.24f, cy + size * 0.22f); path.lineTo(cx, cy + size * 0.08f); path.lineTo(cx - size * 0.24f, cy + size * 0.22f); path.close()
                c.drawPath(path, p)
                Draw.fillCircle(c, cx + size * 0.28f, cy - size * 0.22f, size * 0.07f, Palette.red)
                Draw.fillCircle(c, cx - size * 0.3f, cy + size * 0.05f, size * 0.06f, Palette.amber)
            },
            create = { NebulaSurvivorGame() }
        )
    }
}
