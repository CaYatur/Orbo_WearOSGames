package com.cayadev.orbo.games

import android.graphics.Canvas
import com.cayadev.orbo.data.Category
import com.cayadev.orbo.data.GameMeta
import com.cayadev.orbo.data.IconDrawer
import com.cayadev.orbo.data.Tier
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Loc
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.game.ArcadeGame
import com.cayadev.orbo.engine.ScreenInfo

/**
 * Slide Out — free the red block. Drag blocks along their length to clear a path so the red one can
 * slide off the right edge on row 3. Solve the jam to score and gain time. A pocket take on the
 * classic gridlock puzzle, with a handful of hand-tuned, always-solvable boards.
 */
class SlideOutGame : ArcadeGame(meta()) {

    private val GRID = 6
    private val exitRow = 2
    private class Car(var r: Int, var c: Int, val len: Int, val horiz: Boolean, val target: Boolean)
    private var cars = ArrayList<Car>()
    private var level = 0
    private var timeLeft = 150f
    private var solvedCount = 0

    private var sel = -1
    private var startR = 0; private var startC = 0
    private var pressX = 0f; private var pressY = 0f

    override val controlHint = "DRAG BLOCKS TO FREE RED"
    override val gameOverTitle = "TIME UP"

    override fun onReset() { timeLeft = 150f; solvedCount = 0; level = 0; load(0) }

    private fun load(idx: Int) {
        cars = makeLevel(idx % 4)
        sel = -1
    }

    private fun makeLevel(i: Int): ArrayList<Car> {
        val l = ArrayList<Car>()
        when (i) {
            0 -> { l.add(Car(2, 0, 2, true, true)); l.add(Car(1, 3, 3, false, false)) }
            1 -> { l.add(Car(2, 0, 2, true, true)); l.add(Car(2, 2, 2, false, false)); l.add(Car(2, 4, 2, false, false)) }
            2 -> { l.add(Car(2, 1, 2, true, true)); l.add(Car(2, 4, 3, false, false)); l.add(Car(0, 0, 2, true, false)) }
            else -> { l.add(Car(2, 0, 2, true, true)); l.add(Car(1, 2, 2, false, false)); l.add(Car(0, 3, 3, false, false)); l.add(Car(2, 5, 2, false, false)) }
        }
        return l
    }

    private fun occupies(car: Car, r: Int, c: Int): Boolean {
        return if (car.horiz) r == car.r && c in car.c until car.c + car.len
        else c == car.c && r in car.r until car.r + car.len
    }

    private fun cellFree(r: Int, c: Int, exclude: Int): Boolean {
        if (r < 0 || c < 0 || r >= GRID || c >= GRID) return false
        for (i in cars.indices) if (i != exclude && occupies(cars[i], r, c)) return false
        return true
    }

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.66f else s.minDim * 0.84f
    private fun left(s: ScreenInfo) = s.cx - side(s) / 2f
    private fun top(s: ScreenInfo) = s.cy - side(s) / 2f
    private fun cell(s: ScreenInfo) = side(s) / GRID

    override fun onStep(dt: Float) {
        timeLeft -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver() }
    }

    override fun onPress(x: Float, y: Float) {
        val s = sc; val cs = cell(s)
        val c = ((x - left(s)) / cs).toInt(); val r = ((y - top(s)) / cs).toInt()
        sel = -1
        for (i in cars.indices) if (occupies(cars[i], r, c)) { sel = i; startR = cars[i].r; startC = cars[i].c; pressX = x; pressY = y; engine.sfx.blip(); break }
    }

    override fun onDrag(x: Float, y: Float) {
        if (sel < 0) return
        val s = sc; val cs = cell(s); val car = cars[sel]
        if (car.horiz) {
            val want = Math.round((x - pressX) / cs)
            car.c = clampMove(startC, want, true)
        } else {
            val want = Math.round((y - pressY) / cs)
            car.r = clampMove(startR, want, false)
        }
    }

    private fun clampMove(start: Int, want: Int, horiz: Boolean): Int {
        val car = cars[sel]
        var pos = start
        val step = if (want > 0) 1 else -1
        var n = kotlin.math.abs(want)
        while (n > 0) {
            val next = pos + step
            // the newly entered leading cell
            val lead = if (step > 0) next + car.len - 1 else next
            val ok = if (horiz) cellFree(car.r, lead, sel) && next >= 0 && next + car.len - 1 < GRID
                     else cellFree(lead, car.c, sel) && next >= 0 && next + car.len - 1 < GRID
            if (!ok) break
            pos = next; n--
        }
        return pos
    }

    override fun onRelease(x: Float, y: Float) {
        if (sel >= 0) { engine.sfx.click(); engine.haptics.tick() }
        sel = -1
        val t = cars.firstOrNull { it.target } ?: return
        if (t.horiz && t.r == exitRow && t.c + t.len - 1 >= GRID - 1) onSolved()
    }

    private fun onSolved() {
        solvedCount++
        addScore(150); timeLeft += 25f
        engine.sfx.success(); engine.haptics.success(); flash(accent, 0.25f)
        level++; load(level)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val cs = cell(s); val l = left(s); val t = top(s)
        Draw.roundRect(c, l - s.dp(3f), t - s.dp(3f), cs * GRID + s.dp(6f), cs * GRID + s.dp(6f), s.dp(6f), Palette.alpha(Palette.panel, 0.6f))
        // grid
        for (r in 0 until GRID) for (col in 0 until GRID)
            Draw.roundRect(c, l + col * cs + s.dp(1.5f), t + r * cs + s.dp(1.5f), cs - s.dp(3f), cs - s.dp(3f), cs * 0.08f, Palette.alpha(Palette.panelHi, 0.5f))
        // exit arrow
        val ey = t + exitRow * cs + cs / 2
        Draw.line(c, l + GRID * cs + s.dp(2f), ey, l + GRID * cs + s.px(0.05f), ey, Palette.alpha(Palette.red, 0.7f), s.dp(3f))
        // cars
        for (car in cars) {
            val x = l + car.c * cs; val y = t + car.r * cs
            val w = if (car.horiz) car.len * cs else cs
            val hh = if (car.horiz) cs else car.len * cs
            val col = if (car.target) Palette.red else Palette.blue
            Draw.roundRect(c, x + cs * 0.1f, y + cs * 0.1f, w - cs * 0.2f, hh - cs * 0.2f, cs * 0.2f, col)
            Draw.roundRect(c, x + cs * 0.1f, y + cs * 0.1f, w - cs * 0.2f, (hh - cs * 0.2f) * 0.4f, cs * 0.2f, Palette.alpha(Palette.text, 0.15f))
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        Ui.edgeProgress(c, s, timeLeft / 150f, Palette.lerp(Palette.red, accent, timeLeft / 150f))
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.07f, s.px(0.045f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "slide_out",
            title = "Slide Out",
            tagline = "Free the red block",
            category = Category.PUZZLE,
            tier = Tier.MINI,
            accent = Palette.red,
            icon = IconDrawer { c, cx, cy, size, _ ->
                Draw.roundRectStroke(c, cx - size * 0.42f, cy - size * 0.42f, size * 0.84f, size * 0.84f, size * 0.08f, Palette.alpha(Palette.blue, 0.4f), size * 0.04f)
                Draw.roundRectCentered(c, cx - size * 0.05f, cy, size * 0.5f, size * 0.2f, size * 0.06f, Palette.red)
                Draw.roundRectCentered(c, cx - size * 0.3f, cy - size * 0.25f, size * 0.18f, size * 0.4f, size * 0.05f, Palette.blue)
            },
            create = { SlideOutGame() }
        )
    }
}
