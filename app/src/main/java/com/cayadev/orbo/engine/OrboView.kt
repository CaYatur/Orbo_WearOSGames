package com.cayadev.orbo.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.Choreographer
import android.view.InputDevice
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * The single Canvas-rendered surface for the whole app. Runs a vsync-aligned game loop via
 * Choreographer, translates touch into taps/swipes/drags, and forwards rotary (bezel/crown) input.
 */
@SuppressLint("ViewConstructor")
class OrboView(context: Context, val engine: Engine) : View(context), Choreographer.FrameCallback {

    private var lastNanos = 0L
    private var running = false

    private val density = resources.displayMetrics.density
    private val tapSlop = 10f * density
    private val swipeMin = 22f * density
    private val longPressMs = 450L

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var moved = false
    private var tracker: VelocityTracker? = null
    private var longPressFired = false
    private val longPressRunnable = Runnable {
        if (!moved && !longPressFired) { longPressFired = true; engine.longPress(downX, downY) }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
    }

    fun resume() {
        if (running) return
        running = true
        lastNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); resume(); requestFocus() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); pause() }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (lastNanos == 0L) lastNanos = frameTimeNanos
        var dt = (frameTimeNanos - lastNanos) / 1_000_000_000f
        lastNanos = frameTimeNanos
        if (dt > 0.05f) dt = 0.05f          // clamp huge frame gaps (avoids physics blowups)
        if (dt < 0f) dt = 0f
        try { engine.update(dt) } catch (_: Throwable) {}
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val round = resources.configuration.isScreenRound
        engine.resize(w.toFloat(), h.toFloat(), density, round)
        systemGestureExclusionRects = listOf(Rect(0, 0, w, h))
    }

    override fun onDraw(canvas: Canvas) {
        try { engine.render(canvas) } catch (_: Throwable) {}
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y; lastX = x; lastY = y
                downTime = System.currentTimeMillis()
                moved = false; longPressFired = false
                tracker?.recycle(); tracker = VelocityTracker.obtain()
                tracker?.addMovement(event)
                engine.down(x, y)
                postDelayed(longPressRunnable, longPressMs)
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(event)
                if (!moved && (abs(x - downX) > tapSlop || abs(y - downY) > tapSlop)) {
                    moved = true; removeCallbacks(longPressRunnable)
                }
                lastX = x; lastY = y
                engine.move(x, y)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                engine.up(x, y)
                val dx = x - downX; val dy = y - downY
                tracker?.addMovement(event)
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                val vy = tracker?.yVelocity ?: 0f
                val dist = kotlin.math.hypot(dx, dy)
                if (dist > swipeMin && !longPressFired) {
                    // App-level swipe input only; no edge gesture may dismiss the activity.
                    if (abs(dx) > abs(dy)) {
                        engine.swipe(if (dx > 0) Swipe.RIGHT else Swipe.LEFT, vx, vy)
                    } else {
                        engine.swipe(if (dy > 0) Swipe.DOWN else Swipe.UP, vx, vy)
                    }
                } else if (!moved && !longPressFired &&
                    System.currentTimeMillis() - downTime < 350) {
                    engine.tap(downX, downY)
                }
                tracker?.recycle(); tracker = null
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                engine.up(x, y)
                tracker?.recycle(); tracker = null
            }
        }
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            val scroll = -event.getAxisValue(MotionEvent.AXIS_SCROLL)
            val factor = ViewConfiguration.get(context).scaledVerticalScrollFactor
            engine.rotary(scroll * factor)
            return true
        }
        return super.onGenericMotionEvent(event)
    }
}
