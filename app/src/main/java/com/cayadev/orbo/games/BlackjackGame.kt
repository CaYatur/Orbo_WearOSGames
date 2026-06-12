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
import com.cayadev.orbo.engine.Ui
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.game.ArcadeGame

/**
 * Blackjack — beat the dealer to 21 without busting. Hit to draw, stand to let the dealer play. Wins
 * pay points, a natural blackjack pays extra; busts and losses cost a life. Three lives. Classic
 * casino card play on your wrist.
 */
class BlackjackGame : ArcadeGame(meta()) {

    private enum class Phase { PLAYER, RESULT }
    private var phase = Phase.PLAYER
    private val player = ArrayList<Int>()
    private val dealer = ArrayList<Int>()
    private var hideHole = true
    private var lives = 3
    private var resultMsg = ""
    private var resultWin = 0
    private var endTimer = 0f

    override val controlHint = "HIT OR STAND · BEAT THE DEALER"

    override fun onReset() { lives = 3; deal() }

    private fun draw(): Int = Mathx.randRange(1, 13)

    private fun deal() {
        player.clear(); dealer.clear()
        player.add(draw()); player.add(draw())
        dealer.add(draw()); dealer.add(draw())
        hideHole = true; phase = Phase.PLAYER; resultMsg = ""
        if (value(player) == 21) stand()    // natural -> resolve immediately
    }

    private fun cardVal(r: Int) = if (r > 10) 10 else r
    private fun value(hand: List<Int>): Int {
        var sum = 0; var aces = 0
        for (r in hand) { if (r == 1) { aces++; sum += 11 } else sum += cardVal(r) }
        while (sum > 21 && aces > 0) { sum -= 10; aces-- }
        return sum
    }

    private fun hit() {
        if (phase != Phase.PLAYER) return
        player.add(draw()); engine.sfx.click(); engine.haptics.light()
        if (value(player) > 21) resolve()
    }

    private fun stand() {
        if (phase != Phase.PLAYER) return
        hideHole = false
        while (value(dealer) < 17) dealer.add(draw())
        resolve()
    }

    private fun resolve() {
        hideHole = false
        val pv = value(player); val dv = value(dealer)
        val natural = player.size == 2 && pv == 21
        resultWin = when {
            pv > 21 -> 2
            dv > 21 -> 1
            pv > dv -> 1
            pv < dv -> 2
            else -> 0
        }
        when (resultWin) {
            1 -> { addScore(if (natural) 100 else 50); resultMsg = if (natural) "BLACKJACK!" else "YOU WIN"; engine.sfx.success(); engine.haptics.success(); flash(accent, 0.2f) }
            2 -> { lives--; resultMsg = if (pv > 21) "BUST" else "DEALER WINS"; engine.sfx.fail(); engine.haptics.error(); flash(Palette.red, 0.2f) }
            else -> { resultMsg = "PUSH"; engine.sfx.blip() }
        }
        phase = Phase.RESULT; endTimer = 1.8f
    }

    override fun onStep(dt: Float) {
        if (phase == Phase.RESULT) { endTimer -= dt; if (endTimer <= 0f) { if (resultWin == 2 && lives <= 0) gameOver() else deal() } }
    }

    private fun btn(s: ScreenInfo, left: Boolean): FloatArray {
        val w = s.px(0.32f); val h = s.px(0.12f)
        val cx = if (left) s.cx - s.px(0.19f) else s.cx + s.px(0.19f)
        return floatArrayOf(cx, s.h * 0.84f, w, h)
    }

    override fun onTapWorld(x: Float, y: Float) {
        if (phase != Phase.PLAYER) return
        val l = btn(sc, true); val r = btn(sc, false)
        if (kotlin.math.abs(x - l[0]) < l[2] / 2 && kotlin.math.abs(y - l[1]) < l[3] / 2) hit()
        else if (kotlin.math.abs(x - r[0]) < r[2] / 2 && kotlin.math.abs(y - r[1]) < r[3] / 2) stand()
    }

    private fun rankStr(r: Int) = when (r) { 1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> "$r" }

    private fun drawCard(c: Canvas, s: ScreenInfo, cx: Float, cy: Float, rank: Int, hidden: Boolean) {
        val w = s.px(0.13f); val h = s.px(0.19f)
        Draw.roundRectCentered(c, cx, cy, w, h, w * 0.16f, if (hidden) Palette.panelHi else Palette.text)
        Draw.roundRectStroke(c, cx - w / 2, cy - h / 2, w, h, w * 0.16f, Palette.alpha(Palette.line, 0.8f), s.dp(1.5f))
        if (hidden) { Draw.roundRectCentered(c, cx, cy, w * 0.7f, h * 0.78f, w * 0.1f, Palette.alpha(accent, 0.3f)); return }
        val red = rank % 2 == 0
        Draw.textCentered(c, rankStr(rank), cx, cy - h * 0.12f, w * 0.5f, if (red) Palette.red else Palette.bg, Fonts.display)
        Draw.textCentered(c, if (red) "♥" else "♠", cx, cy + h * 0.22f, w * 0.4f, if (red) Palette.red else Palette.bg, Fonts.bodyBold)
    }

    private fun drawHand(c: Canvas, s: ScreenInfo, hand: List<Int>, cy: Float, hideFirst: Boolean) {
        val spacing = s.px(0.1f)
        val startX = s.cx - (hand.size - 1) * spacing / 2f
        for (i in hand.indices) drawCard(c, s, startX + i * spacing, cy, hand[i], hideFirst && i == 1)
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        // dealer
        Draw.textCentered(c, Loc.t("DEALER"), s.cx, s.h * 0.13f, s.px(0.035f), Palette.textDim, Fonts.mono, letterSpacing = 0.12f)
        drawHand(c, s, dealer, s.h * 0.27f, hideHole)
        if (!hideHole) Draw.textCentered(c, "${value(dealer)}", s.cx + s.px(0.22f), s.h * 0.27f, s.px(0.05f), Palette.text, Fonts.display)
        // player
        drawHand(c, s, player, s.h * 0.6f, false)
        Draw.textCentered(c, "${value(player)}", s.cx + s.px(0.22f), s.h * 0.6f, s.px(0.05f), accent, Fonts.display)
        // buttons
        if (phase == Phase.PLAYER) {
            val l = btn(s, true); val r = btn(s, false)
            Ui.pill(c, l[0], l[1], l[2], l[3], Loc.t("HIT"), accent, true)
            Ui.pill(c, r[0], r[1], r[2], r[3], Loc.t("STAND"), Palette.textDim, false)
        } else {
            Draw.textCentered(c, Loc.t(resultMsg), s.cx, s.h * 0.84f, s.px(0.06f),
                if (resultWin == 1) accent else if (resultWin == 2) Palette.red else Palette.textDim, Fonts.display)
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        val gap = s.px(0.045f); val startX = s.cx - s.px(0.2f)
        for (i in 0 until 3) {
            if (i < lives) Draw.fillCircle(c, startX + i * gap, s.h * 0.13f, s.px(0.013f), Palette.red)
            else Draw.strokeCircle(c, startX + i * gap, s.h * 0.13f, s.px(0.011f), Palette.alpha(Palette.red, 0.3f), s.dp(1.2f))
        }
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx + s.px(0.2f), s.h * 0.13f, s.px(0.04f), accent, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "blackjack",
            title = "Blackjack",
            tagline = "Beat the dealer",
            category = Category.CLASSIC,
            tier = Tier.MINI,
            accent = Palette.mint,
            icon = IconDrawer { c, cx, cy, size, color ->
                Draw.roundRectCentered(c, cx - size * 0.12f, cy, size * 0.4f, size * 0.56f, size * 0.08f, Palette.text)
                Draw.roundRectCentered(c, cx + size * 0.14f, cy, size * 0.4f, size * 0.56f, size * 0.08f, color)
                Draw.textCentered(c, "A", cx - size * 0.12f, cy, size * 0.3f, Palette.bg, Fonts.display)
                Draw.textCentered(c, "K", cx + size * 0.14f, cy, size * 0.3f, Palette.bg, Fonts.display)
            },
            create = { BlackjackGame() }
        )
    }
}
