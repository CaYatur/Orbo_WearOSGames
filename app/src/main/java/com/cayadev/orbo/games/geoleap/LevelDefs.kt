package com.cayadev.orbo.games.geoleap

import com.cayadev.orbo.engine.Palette

/**
 * A Geo Leap level. Everything is expressed in fractions of the smaller screen dimension so the
 * layout is pixel-identical on every watch/phone resolution. Gaps are expressed as MULTIPLES of the
 * jump's air-distance ([gapMinF]/[gapMaxF]) so every obstacle is guaranteed to have a safe flat
 * landing zone after it — the level can never generate an impossible jump.
 */
class LevelDef(
    val name: String,
    val speed: Float,          // fraction of min screen dimension travelled per second
    val beats: Int,            // number of obstacle groups
    val gapMinF: Float,        // min flat ground after an obstacle, in units of one jump air-distance
    val gapMaxF: Float,        // max flat ground after an obstacle
    val tripleChance: Int,     // 0..100 weight
    val doubleChance: Int,
    val blockChance: Int,
    val accent: Int,
    val spike: Int,
    val sky1: Int,
    val sky2: Int
)

/** Twelve hand-tuned Geo Leap levels of escalating tempo and density, all provably clearable. */
object LevelDefs {
    val levels = listOf(
        LevelDef("IGNITION",    0.56f, 16, 1.55f, 2.20f,  0,  0, 18, Palette.mint,   Palette.pink,   0xFF0A2A28.toInt(), Palette.bg),
        LevelDef("STEREO",      0.62f, 20, 1.45f, 2.05f,  0,  8, 22, Palette.cyan,   Palette.amber,  0xFF0A2036.toInt(), Palette.bg),
        LevelDef("PULSE",       0.68f, 24, 1.35f, 1.90f,  6, 14, 26, Palette.lime,   Palette.violet, 0xFF12260C.toInt(), Palette.bg),
        LevelDef("CASCADE",     0.74f, 28, 1.28f, 1.78f, 10, 18, 28, Palette.violet, Palette.lime,   0xFF20102E.toInt(), Palette.bg),
        LevelDef("NEON",        0.80f, 32, 1.22f, 1.68f, 14, 20, 28, Palette.amber,  Palette.cyan,   0xFF2A2008.toInt(), Palette.bg),
        LevelDef("OVERDRIVE",   0.86f, 36, 1.18f, 1.60f, 18, 22, 30, Palette.orange, Palette.cyan,   0xFF2E1410.toInt(), Palette.bg),
        LevelDef("VELOCITY",    0.92f, 40, 1.14f, 1.52f, 22, 24, 30, Palette.blue,   Palette.gold,   0xFF0C1A33.toInt(), Palette.bg),
        LevelDef("QUANTUM",     0.98f, 44, 1.12f, 1.46f, 26, 26, 30, Palette.cyan,   Palette.pink,   0xFF08222E.toInt(), Palette.bg),
        LevelDef("ECLIPSE",     1.04f, 48, 1.10f, 1.42f, 30, 26, 30, Palette.pink,   Palette.gold,   0xFF2A0A1E.toInt(), Palette.bg),
        LevelDef("NOVA",        1.09f, 52, 1.08f, 1.38f, 34, 26, 28, Palette.gold,   Palette.violet, 0xFF2E2406.toInt(), Palette.bg),
        LevelDef("SINGULARITY", 1.13f, 56, 1.06f, 1.34f, 38, 26, 26, Palette.violet, Palette.mint,   0xFF1A0A2E.toInt(), Palette.bg),
        LevelDef("INFINITY",    1.17f, 62, 1.05f, 1.30f, 42, 26, 24, Palette.red,    Palette.gold,   0xFF2E0A12.toInt(), Palette.bg)
    )
}
