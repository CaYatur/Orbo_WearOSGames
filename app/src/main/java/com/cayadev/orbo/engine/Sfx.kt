package com.cayadev.orbo.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.sin
import kotlin.math.PI

/**
 * Tiny asset-free sound engine: synthesizes and mixes short procedural tones on a background
 * thread and streams them through a single AudioTrack. Supports overlapping voices, so games
 * can fire blips/explosions/coins/jumps freely. Everything is wrapped defensively — if audio
 * can't initialize on a given device, the app simply runs silently.
 */
class Sfx {
    enum class Wave { SINE, SQUARE, SAW, TRI, NOISE }

    var enabled = true

    private val sampleRate = 44100
    private val frames = 512                       // mix block size
    private val voices = ArrayList<Voice>()
    private val lock = Object()
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private var running = false
    private val rngState = intArrayOf(0x2545F491)

    private class Voice(
        var wave: Wave, var f0: Float, var f1: Float, var durSec: Float,
        var vol: Float, var attack: Float, var release: Float, var vibrato: Float
    ) {
        var t = 0f
        var phase = 0.0
    }

    fun start() {
        if (track != null) return
        try {
            val min = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = maxOf(min, frames * 2 * 4)
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            running = true
            thread = Thread({ mixLoop() }, "orbo-sfx").apply { isDaemon = true; start() }
        } catch (_: Throwable) { track = null }
    }

    fun stop() {
        running = false
        synchronized(lock) { lock.notifyAll() }
        try { thread?.join(200) } catch (_: Throwable) {}
        try { track?.stop(); track?.release() } catch (_: Throwable) {}
        track = null; thread = null
        synchronized(lock) { voices.clear() }
    }

    private fun noise(): Float {
        var x = rngState[0]
        x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
        rngState[0] = x
        return (x.toFloat() / Int.MAX_VALUE)
    }

    private fun mixLoop() {
        val buf = ShortArray(frames)
        val t = track ?: return
        var playing = false
        while (running) {
            val hasVoices = synchronized(lock) { voices.isNotEmpty() }
            if (!hasVoices) {
                if (playing) { try { t.pause() } catch (_: Throwable) {}; playing = false }
                synchronized(lock) { if (voices.isEmpty() && running) try { lock.wait(1000) } catch (_: Throwable) {} }
                continue
            }
            if (!playing) { try { t.play() } catch (_: Throwable) {}; playing = true }
            // mix one block
            for (i in 0 until frames) buf[i] = 0
            synchronized(lock) {
                val it = voices.iterator()
                while (it.hasNext()) {
                    val v = it.next()
                    if (v.t >= v.durSec) { it.remove(); continue }
                    var i = 0
                    while (i < frames && v.t < v.durSec) {
                        val prog = v.t / v.durSec
                        val freq = v.f0 + (v.f1 - v.f0) * prog
                        val vib = if (v.vibrato > 0f) 1f + 0.012f * v.vibrato * sin(v.t * 2.0 * PI * 7.0).toFloat() else 1f
                        v.phase += (freq * vib) * 2.0 * PI / sampleRate
                        if (v.phase > 2 * PI) v.phase -= 2 * PI
                        val s = when (v.wave) {
                            Wave.SINE -> sin(v.phase).toFloat()
                            Wave.SQUARE -> if (sin(v.phase) >= 0) 1f else -1f
                            Wave.SAW -> ((v.phase / PI).toFloat() - 1f)
                            Wave.TRI -> (2.0 / PI * kotlin.math.asin(sin(v.phase))).toFloat()
                            Wave.NOISE -> noise()
                        }
                        // envelope
                        val env = when {
                            v.t < v.attack -> v.t / v.attack
                            v.t > v.durSec - v.release -> (v.durSec - v.t) / v.release
                            else -> 1f
                        }.coerceIn(0f, 1f)
                        val cur = buf[i] + (s * env * v.vol * 8000f).toInt()
                        buf[i] = cur.coerceIn(-30000, 30000).toShort()
                        v.t += 1f / sampleRate
                        i++
                    }
                }
            }
            try { t.write(buf, 0, frames) } catch (_: Throwable) {}
        }
    }

    private fun add(v: Voice) {
        if (!enabled) return
        if (track == null) start()
        synchronized(lock) {
            if (voices.size < 12) voices.add(v)
            lock.notifyAll()
        }
    }

    fun tone(wave: Wave, f0: Float, f1: Float = f0, dur: Float = 0.12f, vol: Float = 0.6f,
             attack: Float = 0.005f, release: Float = 0.04f, vibrato: Float = 0f) =
        add(Voice(wave, f0, f1, dur, vol, attack, release.coerceAtMost(dur), vibrato))

    // --- Named, reusable game sounds ---
    fun click() = tone(Wave.SQUARE, 880f, 760f, 0.04f, 0.4f, 0.002f, 0.03f)
    fun blip() = tone(Wave.SINE, 660f, 990f, 0.08f, 0.5f)
    fun coin() { tone(Wave.SQUARE, 988f, 988f, 0.05f, 0.45f); tone(Wave.SQUARE, 1319f, 1319f, 0.11f, 0.45f, 0.05f) }
    fun jump() = tone(Wave.SQUARE, 420f, 760f, 0.13f, 0.5f, 0.003f, 0.05f)
    fun hit() = tone(Wave.SAW, 220f, 90f, 0.16f, 0.55f, 0.003f, 0.08f)
    fun explosion() { tone(Wave.NOISE, 400f, 60f, 0.34f, 0.7f, 0.004f, 0.2f); tone(Wave.SAW, 160f, 40f, 0.3f, 0.5f, 0.004f, 0.2f) }
    fun powerup() { tone(Wave.SQUARE, 523f, 1047f, 0.18f, 0.45f, 0.004f, 0.06f, 1f) }
    fun success() { tone(Wave.SINE, 784f, 784f, 0.1f, 0.5f); tone(Wave.SINE, 1047f, 1047f, 0.16f, 0.5f, 0.004f, 0.08f) ; tone(Wave.SINE, 1319f, 1319f, 0.2f, 0.5f, 0.004f, 0.1f) }
    fun fail() { tone(Wave.SAW, 330f, 110f, 0.4f, 0.5f, 0.004f, 0.2f) }
    fun swoosh() = tone(Wave.NOISE, 1200f, 300f, 0.12f, 0.25f, 0.01f, 0.08f)
    fun beep(freq: Float, dur: Float = 0.1f, vol: Float = 0.5f) = tone(Wave.SINE, freq, freq, dur, vol, 0.005f, 0.03f)
}
