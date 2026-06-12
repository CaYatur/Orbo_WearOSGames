package com.cayadev.orbo.home

import android.graphics.Canvas
import android.graphics.Paint
import com.cayadev.orbo.BuildConfig
import com.cayadev.orbo.engine.Draw
import com.cayadev.orbo.engine.Fonts
import com.cayadev.orbo.engine.Loc
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.Scene
import com.cayadev.orbo.engine.ScreenInfo
import com.cayadev.orbo.engine.Ui

/** Settings & About — sound/haptics toggles, language selector, plus CaYaDev credits. */
class SettingsScene : Scene() {
    private var t = 0f
    private var sound = true
    private var haptics = true

    override fun enter() {
        sound = engine.scores.getBool("snd", true)
        haptics = engine.scores.getBool("hap", true)
    }

    override fun update(dt: Float) { t += dt }

    private fun rowRect(s: ScreenInfo, i: Int): FloatArray {
        val w = s.px(0.66f); val h = s.px(0.125f)
        val y = s.h * 0.285f + i * (h + s.px(0.03f))
        return floatArrayOf(s.cx - w / 2, y, w, h)
    }

    private fun hit(r: FloatArray, x: Float, y: Float) =
        x in r[0]..(r[0] + r[2]) && y in r[1]..(r[1] + r[3])

    override fun onTap(x: Float, y: Float) {
        if (Ui.hitAppBack(sc, x, y)) { engine.back(); return }
        if (hit(rowRect(sc, 0), x, y)) {
            sound = !sound; engine.scores.setBool("snd", sound); engine.sfx.enabled = sound
            if (sound) engine.sfx.blip(); engine.haptics.light(); return
        }
        if (hit(rowRect(sc, 1), x, y)) {
            haptics = !haptics; engine.scores.setBool("hap", haptics); engine.haptics.enabled = haptics
            engine.haptics.medium(); engine.sfx.click(); return
        }
        if (hit(rowRect(sc, 2), x, y)) {
            Loc.toggle(); engine.sfx.click(); engine.haptics.light(); return
        }
    }

    override fun render(c: Canvas, s: ScreenInfo) {
        Draw.background(c, Palette.bg)
        Draw.vignette(c, s, Palette.bgSoft, Palette.bg)
        Ui.screenTitle(c, s, Loc.t("Settings"), Palette.mint)

        drawToggle(c, s, 0, Loc.t("SOUND"), sound, Palette.mint)
        drawToggle(c, s, 1, Loc.t("HAPTICS"), haptics, Palette.cyan)
        drawLanguage(c, s, 2, Palette.violet)

        // about
        Draw.textCentered(c, "ORBO  v${BuildConfig.VERSION_NAME}", s.cx, s.h * 0.80f, s.px(0.038f), Palette.textDim, Fonts.mono, letterSpacing = 0.1f)
        Draw.textCentered(c, "made by CaYaDev", s.cx, s.h * 0.865f, s.px(0.042f), Palette.text, Fonts.bodyBold)
        Ui.appBackButton(c, s, t, Palette.mint)
    }

    private fun drawToggle(c: Canvas, s: ScreenInfo, i: Int, label: String, on: Boolean, accent: Int) {
        val r = rowRect(s, i)
        Draw.roundRect(c, r[0], r[1], r[2], r[3], r[3] * 0.4f, Palette.panel)
        Draw.roundRectStroke(c, r[0], r[1], r[2], r[3], r[3] * 0.4f, Palette.alpha(accent, if (on) 0.6f else 0.2f), s.dp(1.5f))
        Draw.text(c, label, r[0] + r[3] * 0.5f, r[1] + r[3] * 0.62f, r[3] * 0.34f, Palette.text, Paint.Align.LEFT, Fonts.bodyBold)
        // switch
        val sw = r[3] * 1.5f; val sx = r[0] + r[2] - sw - r[3] * 0.3f; val sy = r[1] + r[3] * 0.5f
        Draw.roundRect(c, sx, sy - r[3] * 0.22f, sw, r[3] * 0.44f, r[3] * 0.22f, if (on) Palette.alpha(accent, 0.5f) else Palette.track)
        val knobX = if (on) sx + sw - r[3] * 0.22f else sx + r[3] * 0.22f
        Draw.fillCircle(c, knobX, sy, r[3] * 0.18f, if (on) accent else Palette.textDim)
    }

    private fun drawLanguage(c: Canvas, s: ScreenInfo, i: Int, accent: Int) {
        val r = rowRect(s, i)
        Draw.roundRect(c, r[0], r[1], r[2], r[3], r[3] * 0.4f, Palette.panel)
        Draw.roundRectStroke(c, r[0], r[1], r[2], r[3], r[3] * 0.4f, Palette.alpha(accent, 0.6f), s.dp(1.5f))
        Draw.text(c, Loc.t("LANGUAGE"), r[0] + r[3] * 0.5f, r[1] + r[3] * 0.62f, r[3] * 0.34f, Palette.text, Paint.Align.LEFT, Fonts.bodyBold)
        // two-state pill: EN | TR, current highlighted
        val pw = r[3] * 1.0f; val gap = r[3] * 0.12f
        val cy = r[1] + r[3] * 0.5f
        val trX = r[0] + r[2] - r[3] * 0.35f - pw / 2
        val enX = trX - pw - gap
        val cur = Loc.lang
        drawLangChip(c, s, enX, cy, pw, r[3] * 0.7f, "EN", cur == Loc.Lang.EN, accent)
        drawLangChip(c, s, trX, cy, pw, r[3] * 0.7f, "TR", cur == Loc.Lang.TR, accent)
    }

    private fun drawLangChip(c: Canvas, s: ScreenInfo, cx: Float, cy: Float, w: Float, h: Float, label: String, on: Boolean, accent: Int) {
        if (on) Draw.roundRectCentered(c, cx, cy, w, h, h * 0.45f, Palette.alpha(accent, 0.85f))
        else Draw.roundRectStroke(c, cx - w / 2, cy - h / 2, w, h, h * 0.45f, Palette.alpha(Palette.textDim, 0.5f), s.dp(1.2f))
        Draw.textCentered(c, label, cx, cy, h * 0.5f, if (on) Palette.bg else Palette.textDim, Fonts.bodyBold)
    }
}
