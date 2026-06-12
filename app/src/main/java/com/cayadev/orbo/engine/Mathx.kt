package com.cayadev.orbo.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Misc math + tweening helpers used across the engine and games. */
object Mathx {
    const val TAU = (PI * 2.0).toFloat()
    val rng = Random(System.nanoTime())

    fun clamp(v: Float, lo: Float, hi: Float) = if (v < lo) lo else if (v > hi) hi else v
    fun clamp(v: Int, lo: Int, hi: Int) = if (v < lo) lo else if (v > hi) hi else v

    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    fun norm(v: Float, a: Float, b: Float) = if (b == a) 0f else (v - a) / (b - a)

    /** Frame-rate independent smoothing toward target. `speed` ~ 1..20. */
    fun approach(cur: Float, target: Float, speed: Float, dt: Float): Float {
        val t = 1f - Math.exp((-speed * dt).toDouble()).toFloat()
        return cur + (target - cur) * t
    }

    fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    fun dist2(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        return dx * dx + dy * dy
    }

    fun rand(a: Float, b: Float) = a + rng.nextFloat() * (b - a)
    fun rand(n: Int) = rng.nextInt(n)
    /** Inclusive integer in [a, b]. */
    fun randRange(a: Int, b: Int) = a + rng.nextInt(b - a + 1)
    fun randSign() = if (rng.nextBoolean()) 1f else -1f
    fun chance(p: Float) = rng.nextFloat() < p

    fun angle(dx: Float, dy: Float) = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
    fun cosf(a: Float) = cos(a.toDouble()).toFloat()
    fun sinf(a: Float) = sin(a.toDouble()).toFloat()
}

/** Common easing functions (input/return in 0..1). */
object Ease {
    fun outCubic(t: Float): Float { val u = 1f - t; return 1f - u * u * u }
    fun inCubic(t: Float) = t * t * t
    fun inOutCubic(t: Float): Float =
        if (t < 0.5f) 4f * t * t * t else 1f - Math.pow((-2f * t + 2f).toDouble(), 3.0).toFloat() / 2f
    fun outBack(t: Float): Float {
        val c1 = 1.70158f; val c3 = c1 + 1f; val u = t - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }
    fun outElastic(t: Float): Float {
        if (t == 0f || t == 1f) return t
        val c4 = Mathx.TAU / 3f
        return Math.pow(2.0, (-10f * t).toDouble()).toFloat() * Mathx.sinf((t * 10f - 0.75f) * c4) + 1f
    }
    fun outBounce(t: Float): Float {
        val n1 = 7.5625f; val d1 = 2.75f
        var x = t
        return when {
            x < 1f / d1 -> n1 * x * x
            x < 2f / d1 -> { x -= 1.5f / d1; n1 * x * x + 0.75f }
            x < 2.5f / d1 -> { x -= 2.25f / d1; n1 * x * x + 0.9375f }
            else -> { x -= 2.625f / d1; n1 * x * x + 0.984375f }
        }
    }
    /** Symmetric 0->1->0 pulse. */
    fun pulse(t: Float) = Mathx.sinf(t * Mathx.TAU * 0.5f)
}
