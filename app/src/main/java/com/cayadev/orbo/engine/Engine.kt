package com.cayadev.orbo.engine

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas

interface Host {
    fun keepScreenOn(on: Boolean)
}

/**
 * Owns the scene stack, frame timing hand-off, transitions and input routing.
 * One Engine lives for the whole app; [OrboView] feeds it frames and input.
 */
class Engine(val context: Context, val host: Host) {

    var screen: ScreenInfo = ScreenInfo(1f, 1f, 1f, true)
        private set

    val sfx = Sfx()
    val haptics = Haptics(context)
    val scores = Scores(context)

    /** True on Wear OS (a real watch); false on phones/tablets. Used for device-appropriate copy. */
    val isWatch: Boolean =
        runCatching { context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) }.getOrDefault(false)

    private val stack = ArrayList<Scene>()
    val top: Scene? get() = stack.lastOrNull()

    // --- transition state ---
    private var trans = 0            // 0 none, +1 push, -1 pop
    private var transT = 0f
    private val transDur = 0.28f
    private var outgoing: Scene? = null

    val transitioning get() = trans != 0

    init {
        sfx.enabled = scores.getBool("snd", true)
        haptics.enabled = scores.getBool("hap", true)
        Loc.init(context, scores)
    }

    fun attach(scene: Scene) {
        scene.engine = this
        scene.attached()
    }

    fun setRoot(scene: Scene) {
        stack.forEach { it.exit() }
        stack.clear()
        attach(scene)
        scene.resized(screen)
        stack.add(scene)
        scene.enter()
    }

    fun push(scene: Scene) {
        if (transitioning) finishTransition()
        attach(scene)
        scene.resized(screen)
        outgoing = top
        stack.add(scene)
        scene.enter()
        trans = 1; transT = 0f
        sfx.swoosh()
    }

    fun pop() {
        if (stack.size <= 1) return
        if (transitioning) finishTransition()
        val removed = stack.removeAt(stack.size - 1)
        outgoing = removed
        top?.enter()
        trans = -1; transT = 0f
        sfx.swoosh()
    }

    /** Collapse the stack back to the root (main menu) with a single pop animation. */
    fun popToRoot() {
        if (stack.size <= 1) return
        if (transitioning) finishTransition()
        // Drop every scene between root and the current top, then animate the top out.
        while (stack.size > 2) {
            val mid = stack.removeAt(stack.size - 2)
            mid.exit()
        }
        pop()
    }

    private fun finishTransition() {
        outgoing?.let { if (trans == -1) it.exit() }
        outgoing = null; trans = 0; transT = 0f
    }

    fun update(dt: Float) {
        if (transitioning) {
            transT += dt / transDur
            if (transT >= 1f) finishTransition()
        }
        top?.update(dt)
    }

    fun render(c: Canvas) {
        val s = screen
        if (transitioning) {
            val e = Ease.inOutCubic(transT.coerceIn(0f, 1f))
            val dir = if (trans == 1) 1f else -1f
            // incoming = top, outgoing = previous
            val incomingX: Float
            val outgoingX: Float
            if (trans == 1) {              // push: new slides in from right
                incomingX = s.w * (1f - e)
                outgoingX = -s.w * 0.28f * e
            } else {                        // pop: current slides out to right
                incomingX = -s.w * 0.28f * (1f - e)
                outgoingX = s.w * e
            }
            outgoing?.let { drawSceneAt(c, it, outgoingX) }
            top?.let { drawSceneAt(c, it, incomingX) }
            // subtle dim on the lower scene
        } else {
            top?.render(c, s)
        }
    }

    private fun drawSceneAt(c: Canvas, scene: Scene, dx: Float) {
        if (dx == 0f) { scene.render(c, screen); return }
        val save = c.save()
        c.translate(dx, 0f)
        scene.render(c, screen)
        c.restoreToCount(save)
    }

    fun resize(w: Float, h: Float, density: Float, isRound: Boolean) {
        val s = ScreenInfo(w, h, density, isRound)
        screen = s
        stack.forEach { it.resized(s) }
    }

    // --- input (ignored while a transition animates) ---
    fun down(x: Float, y: Float) { if (!transitioning) top?.onDown(x, y) }
    fun move(x: Float, y: Float) { if (!transitioning) top?.onMove(x, y) }
    fun up(x: Float, y: Float) { if (!transitioning) top?.onUp(x, y) }
    fun tap(x: Float, y: Float) { if (!transitioning) top?.onTap(x, y) }
    fun longPress(x: Float, y: Float) { if (!transitioning) top?.onLongPress(x, y) }
    fun swipe(dir: Swipe, vx: Float, vy: Float) { if (!transitioning) top?.onSwipe(dir, vx, vy) }
    fun rotary(deltaPx: Float) { if (!transitioning) top?.onRotary(deltaPx) }

    /** Handles app-owned back navigation. Root consumes back so platform back never exits. */
    fun back(): Boolean {
        if (transitioning) return true
        val t = top ?: return true
        if (t.onBack()) return true
        if (stack.size > 1) { pop(); return true }
        return true
    }

    fun onPause() { sfx.stop() }
    fun onResume() { /* sfx lazily restarts on next play */ }
}
