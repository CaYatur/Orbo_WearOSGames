package com.cayadev.orbo.game

import android.graphics.Canvas
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Ease
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Loc
import com.cayadev.orbo.engine.Mathx
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.Particles
import com.cayadev.orbo.engine.Scene
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.engine.Swipe
import com.cayadev.orbo.engine.Ui
import kotlin.math.max

/**
 * Base for score/survival style games. Provides the full chrome — start card, in-game HUD,
 * pause menu, game-over card — plus screen shake, flash, particles and a clean input contract,
 * so each game only implements [onReset]/[onStep]/[onDraw] and its controls. This is what keeps
 * 25+ games consistent and high quality with minimal per-game code.
 */
abstract class ArcadeGame(val meta: GameMeta) : Scene() {

    enum class GState { READY, PLAYING, PAUSED, OVER }
    var state = GState.READY; private set
    var score = 0; protected set
    var best = 0; private set
    protected var newBest = false
    protected var timeAlive = 0f
    protected val particles = Particles()

    /** Short control description shown on the start card, e.g. "TAP TO JUMP". */
    protected open val controlHint: String = "TAP TO PLAY"
    protected open val scoreLabel: String = "SCORE"
    protected open val showScoreHud: Boolean = true
    /** Override to title the game-over differently (e.g. "TIME UP"). */
    protected open val gameOverTitle: String = "GAME OVER"
    protected open val accent: Int get() = meta.accent

    private var overlayT = 0f
    private var shakeMag = 0f
    private var flashColor = 0
    private var flashA = 0f

    /** True while the "exit to menu?" confirmation is showing (game is frozen underneath). */
    private var confirmExit = false
    /** True when the current touch gesture started on the on-screen back button. */
    private var backCaptured = false

    // Games never quit on a stray swipe — exit is deliberate (back button + confirmation).
    override val allowSwipeBack = false

    override fun enter() {
        best = engine.scores.best(meta.id)
        toReady()
    }

    protected fun toReady() { state = GState.READY; overlayT = 0f; score = 0 }

    protected fun startGame() {
        score = 0; newBest = false; timeAlive = 0f; particles.clear()
        onReset()
        state = GState.PLAYING; overlayT = 0f
        engine.sfx.blip(); engine.haptics.light()
    }

    protected fun gameOver() {
        if (state != GState.PLAYING) return
        state = GState.OVER; overlayT = 0f
        newBest = engine.scores.submit(meta.id, score)
        if (newBest) { engine.sfx.success() } else engine.sfx.fail()
        engine.haptics.error()
    }

    protected fun pauseGame() { if (state == GState.PLAYING) { state = GState.PAUSED; overlayT = 0f } }
    protected fun resumeGame() { if (state == GState.PAUSED) { state = GState.PLAYING; overlayT = 0f } }

    protected fun addScore(n: Int = 1) { score += n }
    protected fun shake(mag: Float) { shakeMag = max(shakeMag, mag) }
    protected fun flash(color: Int, a: Float = 0.55f) { flashColor = color; flashA = a }

    // --- subclass hooks ---
    protected abstract fun onReset()
    protected abstract fun onStep(dt: Float)
    protected abstract fun onDraw(c: Canvas, s: ScreenInfo)
    protected open fun onPress(x: Float, y: Float) {}
    protected open fun onDrag(x: Float, y: Float) {}
    protected open fun onRelease(x: Float, y: Float) {}
    protected open fun onFlick(dir: Swipe, vx: Float, vy: Float) {}
    protected open fun onTapWorld(x: Float, y: Float) {}
    protected open fun onLongPressWorld(x: Float, y: Float) {}
    /** Extra HUD drawing on top of the default score. */
    protected open fun onDrawHud(c: Canvas, s: ScreenInfo) {}

    final override fun update(dt: Float) {
        overlayT += dt
        particles.update(dt)
        if (shakeMag > 0.2f) shakeMag = Mathx.approach(shakeMag, 0f, 11f, dt) else shakeMag = 0f
        if (flashA > 0f) flashA = max(0f, flashA - dt * 2.6f)
        if (state == GState.PLAYING) { timeAlive += dt; onStep(dt) }
    }

    final override fun render(c: Canvas, s: ScreenInfo) {
        val saved = c.save()
        if (shakeMag > 0.2f) {
            c.translate(Mathx.rand(-shakeMag, shakeMag), Mathx.rand(-shakeMag, shakeMag))
        }
        onDraw(c, s)
        particles.render(c)
        c.restoreToCount(saved)

        if (flashA > 0f) Draw.background(c, Palette.alpha(flashColor, flashA))

        if (state == GState.PLAYING && showScoreHud) drawScoreHud(c, s)
        onDrawHud(c, s)

        when (state) {
            GState.READY -> drawReady(c, s)
            GState.PAUSED -> drawPaused(c, s)
            GState.OVER -> drawOver(c, s)
            else -> {}
        }

        if (confirmExit) drawConfirmExit(c, s)
        if (cornerBackVisible()) drawBackButton(c, s)
    }

    // ---- on-screen back / exit button (shared geometry, always inside the face) ----
    private fun cornerBackVisible(): Boolean =
        !confirmExit && (state == GState.READY || state == GState.PLAYING || state == GState.OVER)

    private fun inBackBtn(x: Float, y: Float): Boolean = Ui.hitAppBack(sc, x, y)

    private fun drawBackButton(c: Canvas, s: ScreenInfo) = Ui.appBackButton(c, s, overlayT, accent)

    /** What the corner button does, depending on state. */
    private fun cornerBackAction() {
        engine.sfx.click(); engine.haptics.light()
        when (state) {
            GState.PLAYING -> pauseGame()
            GState.READY, GState.OVER -> engine.pop()   // nothing in progress → back to the list
            else -> {}
        }
    }

    private fun askExit() {
        if (state == GState.PLAYING) pauseGame()   // freeze the simulation behind the prompt
        confirmExit = true; overlayT = 0f
        engine.sfx.click()
    }

    private fun cancelExit() {
        confirmExit = false; overlayT = 0f
        engine.sfx.click()
    }

    private fun doExit() {
        engine.sfx.swoosh(); engine.haptics.medium()
        engine.popToRoot()                          // land on the main menu
    }

    protected open fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        val y = if (s.isRound) s.h * 0.13f else s.h * 0.08f
        Draw.textCentered(c, "$score", s.cx, y, s.px(0.11f), Palette.text, Fonts.display)
    }

    // ---- overlays ----
    private fun scrim(c: Canvas, s: ScreenInfo, a: Float) {
        Draw.background(c, Palette.alpha(Palette.bg, a))
    }

    private fun drawReady(c: Canvas, s: ScreenInfo) {
        val t = (overlayT / 0.35f).coerceIn(0f, 1f)
        scrim(c, s, 0.66f * Ease.outCubic(t))
        val iy = s.h * 0.30f
        val ir = s.px(0.13f)
        Ui.iconDisc(c, s.cx, iy, ir, accent)
        meta.icon.draw(c, s.cx, iy, ir * 1.3f, accent)
        Draw.textCenteredFit(c, meta.title, s.cx, s.h * 0.50f, s.px(0.095f), s.px(0.86f), Palette.text, Fonts.display, t)
        if (best > 0) {
            Draw.textCentered(c, "${Loc.t("BEST")}  $best", s.cx, s.h * 0.585f, s.px(0.042f), accent, Fonts.mono, t, 0.1f)
        }
        val pulse = 0.5f + 0.5f * Ease.pulse(overlayT * 1.4f)
        Ui.pill(c, s.cx, s.h * 0.71f, s.px(0.52f), s.px(0.135f), Loc.t("TAP TO PLAY"), accent, true, 0.8f + 0.2f * pulse)
        Draw.textCenteredFit(c, Loc.t(controlHint), s.cx, s.h * 0.83f, s.px(0.034f), s.px(0.78f), Palette.textDim, Fonts.mono, t, 0.12f)
    }

    private fun drawPaused(c: Canvas, s: ScreenInfo) {
        if (confirmExit) { scrim(c, s, 0.72f); return }   // confirm overlay draws on top
        val t = (overlayT / 0.25f).coerceIn(0f, 1f)
        scrim(c, s, 0.72f * t)
        Draw.textCenteredFit(c, Loc.t("PAUSED"), s.cx, s.h * 0.255f, s.px(0.085f), s.px(0.82f), Palette.text, Fonts.display, t)
        Ui.pill(c, s.cx, s.h * 0.45f, s.px(0.52f), s.px(0.135f), Loc.t("RESUME"), accent, true)
        Ui.pill(c, s.cx, s.h * 0.615f, s.px(0.52f), s.px(0.135f), Loc.t("RESTART"), Palette.textDim, false)
        Ui.pill(c, s.cx, s.h * 0.78f, s.px(0.52f), s.px(0.135f), Loc.t("EXIT"), Palette.red, false)
    }

    private fun drawOver(c: Canvas, s: ScreenInfo) {
        val t = (overlayT / 0.4f).coerceIn(0f, 1f)
        scrim(c, s, 0.74f * Ease.outCubic(t))
        Draw.textCenteredFit(c, Loc.t(gameOverTitle), s.cx, s.h * 0.25f, s.px(0.07f), s.px(0.86f), Palette.textDim, Fonts.display, t, 0.06f)
        val scl = Ease.outBack(t)
        Draw.textCentered(c, "$score", s.cx, s.h * 0.43f, s.px(0.20f) * scl, accent, Fonts.display, t)
        if (newBest) {
            val gl = 0.5f + 0.5f * Ease.pulse(overlayT * 2f)
            Draw.textCentered(c, Loc.t("★ NEW BEST ★"), s.cx, s.h * 0.565f, s.px(0.046f), Palette.gold, Fonts.bodyBold, t, 0.08f * gl + 0.06f)
        } else {
            Draw.textCentered(c, "${Loc.t("BEST")}  $best", s.cx, s.h * 0.565f, s.px(0.044f), Palette.textDim, Fonts.mono, t, 0.1f)
        }
        val pulse = 0.6f + 0.4f * Ease.pulse(overlayT * 1.5f)
        Ui.pill(c, s.cx, s.h * 0.72f, s.px(0.5f), s.px(0.135f), Loc.t("RETRY"), accent, true, 0.8f + 0.2f * pulse)
    }

    private fun drawConfirmExit(c: Canvas, s: ScreenInfo) {
        val t = (overlayT / 0.22f).coerceIn(0f, 1f)
        scrim(c, s, 0.82f * t)
        Draw.textCenteredFit(c, Loc.t("Exit game?"), s.cx, s.h * 0.30f, s.px(0.082f), s.px(0.86f), Palette.text, Fonts.display, t)
        Draw.textCenteredFit(c, Loc.t("Progress will be lost"), s.cx, s.h * 0.405f, s.px(0.036f), s.px(0.82f), Palette.textDim, Fonts.mono, t, 0.06f)
        Ui.pill(c, s.cx, s.h * 0.57f, s.px(0.52f), s.px(0.135f), Loc.t("EXIT TO MENU"), Palette.red, true, t)
        Ui.pill(c, s.cx, s.h * 0.74f, s.px(0.52f), s.px(0.135f), Loc.t("RESUME"), accent, false)
    }

    // ---- input ----
    private fun inPill(s: ScreenInfo, x: Float, y: Float, cyFrac: Float, wFrac: Float = 0.5f, hFrac: Float = 0.135f): Boolean {
        val cy = s.h * cyFrac; val w = s.px(wFrac); val h = s.px(hFrac)
        return x in (s.cx - w / 2)..(s.cx + w / 2) && y in (cy - h / 2)..(cy + h / 2)
    }

    final override fun onTap(x: Float, y: Float) {
        if (backCaptured) { backCaptured = false; cornerBackAction(); return }
        if (confirmExit) {
            when {
                inPill(sc, x, y, 0.57f, 0.52f) -> doExit()
                inPill(sc, x, y, 0.74f, 0.52f) -> cancelExit()
            }
            return
        }
        when (state) {
            GState.READY -> startGame()
            GState.OVER -> startGame()
            GState.PAUSED -> {
                when {
                    inPill(sc, x, y, 0.45f, 0.52f) -> { engine.sfx.click(); resumeGame() }
                    inPill(sc, x, y, 0.615f, 0.52f) -> { engine.sfx.click(); startGame() }
                    inPill(sc, x, y, 0.78f, 0.52f) -> askExit()
                }
            }
            GState.PLAYING -> onTapWorld(x, y)
        }
    }

    final override fun onLongPress(x: Float, y: Float) {
        if (state == GState.PLAYING && !backCaptured) onLongPressWorld(x, y)
    }
    final override fun onDown(x: Float, y: Float) {
        backCaptured = false
        if (cornerBackVisible() && inBackBtn(x, y)) { backCaptured = true; return }
        if (state == GState.PLAYING) onPress(x, y)
    }
    final override fun onMove(x: Float, y: Float) {
        if (backCaptured) return
        if (state == GState.PLAYING) onDrag(x, y)
    }
    final override fun onUp(x: Float, y: Float) {
        if (backCaptured) return
        if (state == GState.PLAYING) onRelease(x, y)
    }
    final override fun onSwipe(dir: Swipe, vx: Float, vy: Float) {
        if (backCaptured) { backCaptured = false; return }
        if (state == GState.PLAYING) onFlick(dir, vx, vy)
    }

    override fun onBack(): Boolean = when (state) {
        GState.PLAYING -> { askExit(); true }                 // physical back → "are you sure?"
        GState.PAUSED -> { if (confirmExit) cancelExit() else askExit(); true }
        else -> false                                          // READY / OVER → pop to the list
    }
}
