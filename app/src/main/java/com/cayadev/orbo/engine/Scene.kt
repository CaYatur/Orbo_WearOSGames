package com.cayadev.orbo.engine

import android.graphics.Canvas

enum class Swipe { LEFT, RIGHT, UP, DOWN }

/**
 * A full-screen unit of the app: the home menu, a game, a tool, an overlay.
 * The [Engine] keeps a stack of scenes and drives the active one each frame.
 */
abstract class Scene {
    lateinit var engine: Engine
    val sc: ScreenInfo get() = engine.screen

    open fun attached() {}
    open fun enter() {}
    open fun exit() {}
    open fun resized(s: ScreenInfo) {}

    open fun update(dt: Float) {}
    abstract fun render(c: Canvas, s: ScreenInfo)

    // --- input (single primary pointer) ---
    open fun onDown(x: Float, y: Float) {}
    open fun onMove(x: Float, y: Float) {}
    open fun onUp(x: Float, y: Float) {}
    open fun onTap(x: Float, y: Float) {}
    open fun onLongPress(x: Float, y: Float) {}
    open fun onSwipe(dir: Swipe, vx: Float, vy: Float) {}
    /** Rotary bezel / crown scroll, in pixels (positive = scroll down/forward). */
    open fun onRotary(deltaPx: Float) {}

    /** Return true to consume the back action; false lets the engine pop the stack. */
    open fun onBack(): Boolean = false

    /** Legacy gesture policy flag; platform swipe-back is disabled at the view/activity layer. */
    open val allowSwipeBack: Boolean = false
}
