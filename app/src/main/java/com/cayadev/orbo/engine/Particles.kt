package com.cayadev.orbo.engine

import android.graphics.Canvas

/**
 * Allocation-free particle system (parallel arrays) for juice: bursts, sparks, trails, confetti.
 * A single instance is reused per scene; capacity caps memory and keeps the watch smooth.
 */
class Particles(private val cap: Int = 220) {
    private val x = FloatArray(cap)
    private val y = FloatArray(cap)
    private val vx = FloatArray(cap)
    private val vy = FloatArray(cap)
    private val life = FloatArray(cap)
    private val maxLife = FloatArray(cap)
    private val size = FloatArray(cap)
    private val grav = FloatArray(cap)
    private val drag = FloatArray(cap)
    private val color = IntArray(cap)
    private val glow = BooleanArray(cap)
    private var head = 0
    var count = 0; private set

    fun clear() { count = 0; head = 0 }

    fun spawn(
        px: Float, py: Float, pvx: Float, pvy: Float, ttl: Float, psize: Float,
        pcolor: Int, gravity: Float = 0f, pdrag: Float = 0f, isGlow: Boolean = true
    ) {
        val i = head
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        life[i] = ttl; maxLife[i] = ttl; size[i] = psize
        color[i] = pcolor; grav[i] = gravity; drag[i] = pdrag; glow[i] = isGlow
        head = (head + 1) % cap
        if (count < cap) count++
    }

    /** Radial burst of particles. */
    fun burst(px: Float, py: Float, n: Int, pcolor: Int, speed: Float, psize: Float,
              ttl: Float = 0.6f, gravity: Float = 0f, spread: Float = 1f) {
        repeat(n) {
            val a = Mathx.rand(0f, Mathx.TAU)
            val sp = speed * Mathx.rand(0.35f, 1f) * spread
            spawn(px, py, Mathx.cosf(a) * sp, Mathx.sinf(a) * sp,
                ttl * Mathx.rand(0.7f, 1.1f), psize * Mathx.rand(0.6f, 1.2f),
                pcolor, gravity, 1.2f)
        }
    }

    fun trail(px: Float, py: Float, pcolor: Int, psize: Float, ttl: Float = 0.35f) {
        spawn(px + Mathx.rand(-2f, 2f), py + Mathx.rand(-2f, 2f),
            Mathx.rand(-12f, 12f), Mathx.rand(-12f, 12f), ttl, psize, pcolor, 0f, 2f)
    }

    fun update(dt: Float) {
        var i = 0
        while (i < cap) {
            if (life[i] > 0f) {
                life[i] -= dt
                if (life[i] <= 0f) { life[i] = 0f } else {
                    vy[i] += grav[i] * dt
                    val d = 1f - drag[i] * dt
                    vx[i] *= d; vy[i] *= d
                    x[i] += vx[i] * dt; y[i] += vy[i] * dt
                }
            }
            i++
        }
    }

    fun render(c: Canvas) {
        var i = 0
        while (i < cap) {
            val l = life[i]
            if (l > 0f) {
                val t = (l / maxLife[i]).coerceIn(0f, 1f)
                val sz = size[i] * (0.4f + 0.6f * t)
                if (glow[i]) Draw.glow(c, x[i], y[i], sz * 2.2f, color[i], 0.7f * t)
                Draw.fillCircle(c, x[i], y[i], sz, Palette.alpha(color[i], t))
            }
            i++
        }
    }
}
