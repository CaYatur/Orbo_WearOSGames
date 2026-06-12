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
 * Anagram — unscramble the word. Tap the jumbled letters to spell it out; tap a placed letter to send
 * it back. Solve words to score and gain time. The word list follows the app language (English or
 * Turkish), so it works in both.
 */
class AnagramGame : ArcadeGame(meta()) {

    private val en = listOf("TIME","GAME","PLAY","CODE","BLUE","FIRE","MOON","STAR","TREE","BIRD","FISH","RAIN","SNOW","WIND","LEAF","GOLD","RING","BOOK","DOOR","KING","LION","ROSE","SHIP","WAVE","CAKE","CLOUD","RIVER","STONE","LIGHT","MUSIC","OCEAN","TIGER","HONEY","PLANT")
    private val tr = listOf("ZAMAN","OYUN","MAVİ","ATEŞ","YILDIZ","AĞAÇ","BALIK","YAĞMUR","RÜZGAR","YAPRAK","ALTIN","YÜZÜK","KİTAP","KAPI","KRAL","ASLAN","GEMİ","DALGA","PASTA","DENİZ","BULUT","NEHİR","MÜZİK","KAPLAN","ÇİÇEK","KEDİ","ELMA","ARABA","GÜNEŞ","DEFTER")

    private var word = ""
    private lateinit var bank: CharArray
    private lateinit var used: BooleanArray
    private val answer = ArrayList<Int>()       // indices into bank
    private var timeLeft = 120f
    private var solvedFx = 0f

    override val controlHint = "TAP LETTERS TO SPELL THE WORD"
    override val gameOverTitle = "TIME UP"

    override fun onReset() { timeLeft = 120f; newWord() }

    private fun newWord() {
        val list = if (Loc.lang == Loc.Lang.TR) tr else en
        word = list[Mathx.rand(list.size)]
        val chars = word.toCharArray()
        do {
            bank = chars.copyOf()
            for (i in bank.indices.reversed()) { val j = Mathx.rand(i + 1); val t = bank[i]; bank[i] = bank[j]; bank[j] = t }
        } while (String(bank) == word && word.length > 1)
        used = BooleanArray(bank.size)
        answer.clear()
    }

    private fun regionW(s: ScreenInfo) = if (s.isRound) s.minDim * 0.7f else s.minDim * 0.88f
    private fun tileW(s: ScreenInfo) = (regionW(s) / word.length).coerceAtMost(s.px(0.14f))
    private fun bankY(s: ScreenInfo) = s.h * 0.66f
    private fun ansY(s: ScreenInfo) = s.h * 0.42f
    private fun startX(s: ScreenInfo) = s.cx - (tileW(s) * word.length) / 2f + tileW(s) / 2f

    override fun onStep(dt: Float) {
        if (solvedFx > 0f) solvedFx -= dt
        timeLeft -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; gameOver() }
    }

    override fun onTapWorld(x: Float, y: Float) {
        val s = sc; val tw = tileW(s)
        // answer row -> remove
        if (kotlin.math.abs(y - ansY(s)) < tw * 0.6f) {
            for (k in answer.indices) {
                val ax = startX(s) + k * tw
                if (kotlin.math.abs(x - ax) < tw * 0.5f) { used[answer[k]] = false; answer.removeAt(k); engine.sfx.click(); engine.haptics.tick(); return }
            }
        }
        // bank row -> add
        if (kotlin.math.abs(y - bankY(s)) < tw * 0.6f) {
            for (i in bank.indices) {
                if (used[i]) continue
                val bx = startX(s) + i * tw
                if (kotlin.math.abs(x - bx) < tw * 0.5f) {
                    used[i] = true; answer.add(i); engine.sfx.click(); engine.haptics.tick()
                    checkSolved(); return
                }
            }
        }
    }

    private fun checkSolved() {
        if (answer.size != word.length) return
        val sb = StringBuilder(); for (i in answer) sb.append(bank[i])
        if (sb.toString() == word) {
            addScore(50 + word.length * 10); timeLeft += 12f; solvedFx = 0.6f
            engine.sfx.success(); engine.haptics.success(); flash(accent, 0.2f)
            newWord()
        }
    }

    override fun onDraw(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        if (state == GState.READY) return
        val tw = tileW(s)
        // answer slots
        for (k in 0 until word.length) {
            val ax = startX(s) + k * tw
            Draw.roundRectCentered(c, ax, ansY(s), tw * 0.86f, tw * 0.86f, tw * 0.14f, Palette.alpha(Palette.panel, 0.7f))
            if (k < answer.size) {
                Draw.roundRectCentered(c, ax, ansY(s), tw * 0.86f, tw * 0.86f, tw * 0.14f, Palette.alpha(accent, 0.85f))
                Draw.textCentered(c, "${bank[answer[k]]}", ax, ansY(s), tw * 0.5f, Palette.bg, Fonts.display)
            }
        }
        // bank tiles
        for (i in bank.indices) {
            val bx = startX(s) + i * tw
            if (used[i]) {
                Draw.roundRectCentered(c, bx, bankY(s), tw * 0.86f, tw * 0.86f, tw * 0.14f, Palette.alpha(Palette.panel, 0.4f))
            } else {
                Draw.roundRectCentered(c, bx, bankY(s), tw * 0.86f, tw * 0.86f, tw * 0.14f, Palette.panelHi)
                Draw.roundRectStroke(c, bx - tw * 0.43f, bankY(s) - tw * 0.43f, tw * 0.86f, tw * 0.86f, tw * 0.14f, Palette.alpha(accent, 0.4f), s.dp(1.4f))
                Draw.textCentered(c, "${bank[i]}", bx, bankY(s), tw * 0.5f, Palette.text, Fonts.display)
            }
        }
    }

    override fun onDrawHud(c: Canvas, s: ScreenInfo) {
        if (state != GState.PLAYING) return
        Ui.edgeProgress(c, s, timeLeft / 120f, Palette.lerp(Palette.red, accent, timeLeft / 120f))
    }

    override fun drawScoreHud(c: Canvas, s: ScreenInfo) {
        Draw.textCentered(c, "$score", s.cx, s.h * 0.18f, s.px(0.06f), Palette.text, Fonts.display)
    }

    companion object {
        fun meta() = GameMeta(
            id = "anagram",
            title = "Anagram",
            tagline = "Unscramble the word",
            category = Category.BRAIN,
            tier = Tier.MINI,
            accent = Palette.pink,
            icon = IconDrawer { c, cx, cy, size, color ->
                val ch = charArrayOf('W', 'O', 'R', 'D')
                for (i in 0 until 4) {
                    Draw.roundRectCentered(c, cx + (i - 1.5f) * size * 0.26f, cy, size * 0.24f, size * 0.24f, size * 0.05f, Palette.alpha(color, 0.8f))
                    Draw.textCentered(c, "${ch[i]}", cx + (i - 1.5f) * size * 0.26f, cy, size * 0.16f, Palette.bg, Fonts.display)
                }
            },
            create = { AnagramGame() }
        )
    }
}
