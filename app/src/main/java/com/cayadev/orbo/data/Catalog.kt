package com.cayadev.orbo.data

/**
 * Central registry of every game and tool. Entries are added here as each title is built,
 * so the home screen and category lists stay in sync automatically.
 */
object Catalog {
    private val all = ArrayList<GameMeta>()

    fun register(meta: GameMeta) {
        all.removeAll { it.id == meta.id }
        all.add(meta)
    }

    fun games(): List<GameMeta> = all
    fun byId(id: String): GameMeta? = all.firstOrNull { it.id == id }
    fun inCategory(cat: Category): List<GameMeta> =
        if (cat == Category.FEATURED) all.filter { it.featured } else all.filter { it.category == cat }
    fun count(cat: Category): Int = inCategory(cat).size

    /** Categories that currently contain at least one entry, in enum order. */
    fun activeCategories(): List<Category> = Category.values().filter { count(it) > 0 }

    fun ensureLoaded() {
        if (!loaded) { loaded = true; GameRegistry.registerAll() }
    }
    private var loaded = false
}
