package com.cayadev.orbo.tools

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
import com.cayadev.orbo.engine.Scene
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.engine.Swipe
import com.cayadev.orbo.engine.Ui

/** Dice Roller — tap to roll one to five dice; swipe or use the buttons to change how many. Shows the
 * total and a quick tumble animation. A handy companion for board games and quick decisions. */
class DiceRollerTool : Scene() {

    private var count = 2
    private val vals = IntArray(5) { 1 }
    private var rollT = 0f
    private var t = 0f
    private val accent = Palette.amber

    override fun update(dt: Float) {
        t += dt
        if (rollT > 0f) {
            rollT -= dt
            if (rollT > 0.05f) for (i in 0 until count) { if (Mathx.chance(0.5f)) vals[i] = Mathx.randRange(1, 6) }
            else if (rollT <= 0f) { engine.sfx.coin(); engine.haptics.medium() }
        }
    }

    private fun roll() {
        if (rollT > 0f) return
        rollT = 0.6f
        for (i in 0 until count) vals[i] = Mathx.randRange(1, 6)
        engine.sfx.blip(); engine.haptics.light()
    }

    private fun setCount(n: Int) { count = n.coerceIn(1, 5); engine.sfx.click(); engine.haptics.tick() }

    override fun onTap(x: Float, y: Float) {
        if (Ui.hitAppBack(sc, x, y)) { engine.back(); return }
        val s = sc
        if (y > s.h * 0.82f) {
            if (x < s.cx - s.px(0.12f)) { setCount(count - 1); return }
            if (x > s.cx + s.px(0.12f)) { setCount(count + 1); return }
        }
        roll()
    }

    override fun onSwipe(dir: Swipe, vx: Float, vy: Float) {
        if (dir == Swipe.LEFT) setCount(count - 1) else if (dir == Swipe.RIGHT) setCount(count + 1)
    }

    override fun onRotary(deltaPx: Float) { setCount(count + if (deltaPx > 0) 1 else -1) }

    private fun diePos(s: ScreenInfo, i: Int): FloatArray {
        val perRow = if (count <= 3) count else 3
        val rows = (count + perRow - 1) / perRow
        val row = i / perRow; val col = i % perRow
        val rowCount = if (row == rows - 1) count - row * perRow else perRow
        val sz = s.px(if (count <= 2) 0.2f else 0.15f)
        val spacing = sz * 1.25f
        val startX = s.cx - (rowCount - 1) * spacing / 2f
        val startY = s.cy - (rows - 1) * spacing / 2f - s.px(0.03f)
        return floatArrayOf(startX + col * spacing, startY + row * spacing, sz)
    }

    private fun drawDie(c: Canvas, s: ScreenInfo, cx: Float, cy: Float, sz: Float, v: Int) {
        val wob = if (rollT > 0.05f) Mathx.rand(-sz * 0.04f, sz * 0.04f) else 0f
        Draw.glow(c, cx, cy, sz * 0.7f, accent, 0.12f)
        Draw.roundRectCentered(c, cx + wob, cy + wob, sz, sz, sz * 0.2f, Palette.text)
        val pip = sz * 0.09f; val o = sz * 0.25f
        val dots = when (v) {
            1 -> listOf(0f to 0f); 2 -> listOf(-o to -o, o to o); 3 -> listOf(-o to -o, 0f to 0f, o to o)
            4 -> listOf(-o to -o, o to -o, -o to o, o to o); 5 -> listOf(-o to -o, o to -o, 0f to 0f, -o to o, o to o)
            else -> listOf(-o to -o, o to -o, -o to 0f, o to 0f, -o to o, o to o)
        }
        for (d in dots) Draw.fillCircle(c, cx + wob + d.first, cy + wob + d.second, pip, Palette.bg)
    }

    override fun render(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        for (i in 0 until count) { val p = diePos(s, i); drawDie(c, s, p[0], p[1], p[2], vals[i]) }
        // total
        var total = 0; for (i in 0 until count) total += vals[i]
        Draw.textCentered(c, "${Loc.t("TOTAL")} $total", s.cx, s.h * 0.74f, s.px(0.05f), accent, Fonts.display)
        // count controls
        Draw.textCentered(c, "−", s.cx - s.px(0.2f), s.h * 0.88f, s.px(0.08f), Palette.textDim, Fonts.display)
        Draw.textCentered(c, "$count", s.cx, s.h * 0.88f, s.px(0.06f), Palette.text, Fonts.display)
        Draw.textCentered(c, "+", s.cx + s.px(0.2f), s.h * 0.88f, s.px(0.08f), Palette.textDim, Fonts.display)
        Ui.appBackButton(c, s, t, accent)
    }

    companion object {
        fun meta() = GameMeta(
            id = "dice_roller",
            title = "Dice Roller",
            tagline = "Roll the dice",
            category = Category.TOOLS,
            tier = Tier.TOOL,
            accent = Palette.amber,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx, cy, size * 0.7f, size * 0.7f, size * 0.16f, color)
                val o = size * 0.18f
                for (d in listOf(-o to -o, o to -o, 0f to 0f, -o to o, o to o)) Draw.fillCircle(c, cx + d.first, cy + d.second, size * 0.07f, Palette.bg)
            },
            create = { DiceRollerTool() }
        )
    }
}
