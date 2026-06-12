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
import com.cayadev.orbo.engine.Swipe
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.game.ArcadeGame
import com.cayadev.orbo.engine.ScreenInfo

/**
 * Push It — a pocket Sokoban. Swipe to walk; walk into a crate to shove it. Land every crate on a
 * glowing target to clear the room, score and gain time. Tap to undo a move. Rooms are reverse-
 * generated, so every one is guaranteed solvable.
 */
class PushItGame : ArcadeGame(meta()) {

    private val W = 7
    private val H = 7
    private val wall = BooleanArray(W * H)
    private var goals = HashSet<Int>()
    private var boxes = HashSet<Int>()
    private var player = 0
    private val history = ArrayList<Pair<Int, HashSet<Int>>>()
    private var timeLeft = 180f
    private var solvedCount = 0
    private val dr = intArrayOf(-1, 1, 0, 0)
    private val dc = intArrayOf(0, 0, -1, 1)

    override val controlHint = "SWIPE TO PUSH · TAP UNDO"
    override val gameOverTitle = "TIME UP"

    override fun onReset() { timeLeft = 180f; solvedCount = 0; build(3) }

    private fun interior(r: Int, c: Int) = r in 1 until H - 1 && c in 1 until W - 1

    private fun build(nBoxes: Int) {
        for (i in 0 until W * H) wall[i] = false
        for (r in 0 until H) for (c in 0 until W) if (r == 0 || c == 0 || r == H - 1 || c == W - 1) wall[r * W + c] = true
        // choose goal cells, boxes start solved
        val cells = ArrayList<Int>()
        for (r in 1 until H - 1) for (c in 1 until W - 1) cells.add(r * W + c)
        do {
            goals = HashSet(); boxes = HashSet()
            val pool = ArrayList(cells)
            for (i in pool.indices.reversed()) { val j = Mathx.rand(i + 1); val t = pool[i]; pool[i] = pool[j]; pool[j] = t }
            for (k in 0 until nBoxes) { goals.add(pool[k]); boxes.add(pool[k]) }
            player = pool[nBoxes]
            // scramble via reverse pulls
            repeat(80) { pull() }
        } while (boxes == goals)
        history.clear()
    }

    private fun reachable(from: Int, blocked: Set<Int>): BooleanArray {
        val seen = BooleanArray(W * H)
        val q = ArrayDeque<Int>(); q.add(from); seen[from] = true
        while (q.isNotEmpty()) {
            val cur = q.removeFirst(); val r = cur / W; val c = cur % W
            for (d in 0 until 4) {
                val nr = r + dr[d]; val nc = c + dc[d]; val ni = nr * W + nc
                if (nr in 0 until H && nc in 0 until W && !seen[ni] && !wall[ni] && ni !in blocked) { seen[ni] = true; q.add(ni) }
            }
        }
        return seen
    }

    private fun pull() {
        val box = boxes.toList()[Mathx.rand(boxes.size)]
        val d = Mathx.rand(4)
        val r = box / W; val c = box % W
        val p = (r + dr[d]) * W + (c + dc[d])       // where player stands to pull
        val p2 = (r + 2 * dr[d]) * W + (c + 2 * dc[d])
        if (!interior(r + dr[d], c + dc[d]) || !interior(r + 2 * dr[d], c + 2 * dc[d])) return
        if (wall[p] || wall[p2] || p in boxes || p2 in boxes) return
        val reach = reachable(player, boxes)
        if (!reach[p]) return
        boxes.remove(box); boxes.add(p); player = p2
    }

    private fun side(s: ScreenInfo) = if (s.isRound) s.minDim * 0.66f else s.minDim * 0.84f
    private fun left(s: ScreenInfo) = s.cx - side(s) / 2f
    private fun top(s: ScreenInfo) = s.cy - side(s) / 2f
    private fun cell(s: ScreenInfo) = side(s) / W

    override fun onStep(dt: Float) {
        timeLeft -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver() }
    }

    override fun onFlick(dir: Swipe, vx: Float, vy: Float) {
        val d = when (dir) { Swipe.UP -> 0; Swipe.DOWN -> 1; Swipe.LEFT -> 2; Swipe.RIGHT -> 3 }
        move(d)
    }

    private fun move(d: Int) {
        val r = player / W; val c = player % W
        val nr = r + dr[d]; val nc = c + dc[d]; val ni = nr * W + nc
        if (wall[ni]) return
        val snapshot = player to HashSet(boxes)
        if (ni in boxes) {
            val br = nr + dr[d]; val bc = nc + dc[d]; val bi = br * W + bc
            if (wall[bi] || bi in boxes) return
            boxes.remove(ni); boxes.add(bi)
            player = ni
            engine.sfx.click(); engine.haptics.light()
        } else {
            player = ni; engine.sfx.click(); engine.haptics.tick()
        }
        history.add(snapshot)
        if (history.size > 60) history.removeAt(0)
        if (boxes == goals) onSolved()
    }

    override fun onTapWorld(x: Float, y: Float) {
        if (history.isEmpty()) return
        val last = history.removeAt(history.size - 1)
        player = last.first; boxes = last.second
        engine.sfx.blip(); engine.haptics.tick()
    }

    private fun onSolved() {
        solvedCount++
        addScore(150); timeLeft += 25f
        engine.sfx.success(); engine.haptics.success(); flash(accent, 0.25f)
        build((3 + solvedCount / 2).coerceAtMost(4))
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val cs = cell(s); val l = left(s); val t = top(s)
        for (r in 0 until H) for (col in 0 until W) {
            val x = l + col * cs; val y = t + r * cs; val i = r * W + col
            if (wall[i]) Draw.roundRect(c, x + s.dp(1f), y + s.dp(1f), cs - s.dp(2f), cs - s.dp(2f), cs * 0.12f, Palette.panelHi)
            else Draw.roundRect(c, x + s.dp(2f), y + s.dp(2f), cs - s.dp(4f), cs - s.dp(4f), cs * 0.1f, Palette.alpha(Palette.panel, 0.6f))
            if (i in goals) Draw.strokeCircle(c, x + cs / 2, y + cs / 2, cs * 0.22f, Palette.alpha(accent, 0.8f), s.dp(2f))
        }
        for (b in boxes) {
            val x = l + (b % W) * cs; val y = t + (b / W) * cs
            val onGoal = b in goals
            Draw.roundRect(c, x + cs * 0.14f, y + cs * 0.14f, cs * 0.72f, cs * 0.72f, cs * 0.12f, if (onGoal) accent else Palette.amber)
            Draw.roundRectStroke(c, x + cs * 0.14f, y + cs * 0.14f, cs * 0.72f, cs * 0.72f, cs * 0.12f, Palette.alpha(Palette.text, 0.25f), s.dp(1.5f))
        }
        // player
        val px = l + (player % W) * cs + cs / 2; val py = t + (player / W) * cs + cs / 2
        Draw.glow(c, px, py, cs * 0.4f, Palette.cyan, 0.3f)
        Draw.fillCircle(c, px, py, cs * 0.28f, Palette.cyan)
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        Ui.edgeProgress(c, s, timeLeft / 180f, Palette.lerp(Palette.red, accent, timeLeft / 180f))
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.07f, s.px(0.045f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "push_it",
            title = "Push It",
            tagline = "Crates on targets",
            category = Category.PUZZLE,
            tier = Tier.MINI,
            accent = Palette.mint,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectStroke(c, cx - size * 0.42f, cy - size * 0.42f, size * 0.84f, size * 0.84f, size * 0.08f, Palette.alpha(color, 0.4f), size * 0.05f)
                Draw.roundRectCentered(c, cx - size * 0.18f, cy + size * 0.15f, size * 0.3f, size * 0.3f, size * 0.06f, Palette.amber)
                Draw.strokeCircle(c, cx + size * 0.2f, cy - size * 0.18f, size * 0.14f, color, size * 0.05f)
            },
            create = { PushItGame() }
        )
    }
}
