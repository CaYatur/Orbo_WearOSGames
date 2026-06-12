package com.cayadev.orbo.data

import android.graphics.Canvas
import com.cayadev.orbo.engine.Palette
import com.cayadev.orbo.engine.Scene

/** Top-level groupings shown on the home screen. */
enum class Category(val title: String, val accent: Int, val glyph: String) {
    FEATURED("Featured", Palette.gold, "★"),
    ARCADE("Arcade", Palette.mint, "▶"),
    ACTION("Action", Palette.orange, "✸"),
    REFLEX("Reflex", Palette.cyan, "⚡"),
    PUZZLE("Puzzle", Palette.violet, "◆"),
    BRAIN("Brain", Palette.amber, "✦"),
    STRATEGY("Strategy", Palette.lime, "♜"),
    CLASSIC("Classic", Palette.pink, "♛"),
    TOOLS("Tools", Palette.blue, "✎");
}

enum class Tier { MINI, BIG, FLAGSHIP, TOOL }

/** Drawn vector icon for a game tile (asset-free, crisp at any size). */
fun interface IconDrawer {
    fun draw(c: Canvas, cx: Float, cy: Float, size: Float, color: Int)
}

class GameMeta(
    val id: String,
    val title: String,
    val tagline: String,
    val category: Category,
    val tier: Tier,
    val accent: Int,
    val icon: IconDrawer,
    val create: () -> Scene,
    /** Also surfaced in the FEATURED showcase row (in addition to its primary category). */
    val featured: Boolean = false
)
