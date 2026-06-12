package com.cayadev.orbo.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * Local persistence for Sketch drawings. Saves PNGs into the app's private files dir
 * (`filesDir/sketches`) so they can be listed, re-opened, edited and deleted entirely in-app with
 * no storage permissions. The in-app gallery is the source of truth for saved work.
 */
object SketchStore {
    private fun dir(ctx: Context): File = File(ctx.filesDir, "sketches").apply { if (!exists()) mkdirs() }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".png") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun newFile(ctx: Context): File = File(dir(ctx), "sketch_${System.currentTimeMillis()}.png")

    fun save(file: File, bmp: Bitmap): Boolean = try {
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    } catch (_: Throwable) { false }

    fun load(file: File): Bitmap? = try { BitmapFactory.decodeFile(file.absolutePath) } catch (_: Throwable) { null }

    /** Decodes a memory-light thumbnail (downsampled by [sample]). */
    fun loadThumb(file: File, sample: Int): Bitmap? = try {
        val opts = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        BitmapFactory.decodeFile(file.absolutePath, opts)
    } catch (_: Throwable) { null }

    fun delete(file: File): Boolean = try { file.delete() } catch (_: Throwable) { false }
}
