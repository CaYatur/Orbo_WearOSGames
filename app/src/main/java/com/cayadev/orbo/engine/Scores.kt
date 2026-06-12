package com.cayadev.orbo.engine

import android.content.Context

/** Lightweight high-score + settings store backed by SharedPreferences. */
class Scores(context: Context) {
    private val prefs = context.getSharedPreferences("orbo_scores", Context.MODE_PRIVATE)

    fun best(id: String): Int = prefs.getInt("best_$id", 0)

    /** Records a score; returns true if it's a new best. */
    fun submit(id: String, score: Int): Boolean {
        if (score > best(id)) {
            prefs.edit().putInt("best_$id", score).apply()
            addToken(score / 10 + 1)
            return true
        }
        return false
    }

    fun getInt(key: String, def: Int = 0): Int = prefs.getInt(key, def)
    fun setInt(key: String, v: Int) = prefs.edit().putInt(key, v).apply()
    fun getBool(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)
    fun setBool(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()
    fun getFloat(key: String, def: Float): Float = prefs.getFloat(key, def)
    fun setFloat(key: String, v: Float) = prefs.edit().putFloat(key, v).apply()
    fun getString(key: String, def: String): String = prefs.getString(key, def) ?: def
    fun setString(key: String, v: String) = prefs.edit().putString(key, v).apply()

    // "Orbs" — a soft currency earned by playing, shown on the home screen.
    fun orbs(): Int = prefs.getInt("orbs", 0)
    private fun addToken(n: Int) = prefs.edit().putInt("orbs", orbs() + n).apply()

    fun plays(id: String): Int = prefs.getInt("plays_$id", 0)
    fun recordPlay(id: String) = prefs.edit().putInt("plays_$id", plays(id) + 1).apply()
    fun totalPlays(): Int = prefs.getInt("total_plays", 0).also {
        prefs.edit().putInt("total_plays", it).apply()
    }
    fun bumpTotalPlays() = prefs.edit().putInt("total_plays", prefs.getInt("total_plays", 0) + 1).apply()
}
