package com.example.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.sin

object LobbyMusicPlayer {
    private var job: Job? = null
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 22050
    private var isPlaying = false

    fun start() {
        if (isPlaying) return
        isPlaying = true
        job = CoroutineScope(Dispatchers.Default).launch {
            try {
                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()

                // A nostalgic 8-bit chip-tune melody loop
                // Key of A minor or C Major:
                val notes = listOf(
                    440.0f, 523.25f, 659.25f, 783.99f, // Am7 (A - C - E - G)
                    493.88f, 587.33f, 739.99f, 880.0f, // Bm7 (B - D - F# - A)
                    523.25f, 659.25f, 783.99f, 987.77f, // Cmaj7 (C - E - G - B)
                    392.00f, 493.88f, 587.33f, 783.99f  // G7 (G - B - D - G)
                )

                var noteIndex = 0
                val noteDurationMs = 210 // fast 8-bit pace
                val samplesPerNote = (sampleRate * noteDurationMs / 1000)
                var phase = 0.0

                while (isActive && isPlaying) {
                    val freq = notes[noteIndex]
                    val buffer = ShortArray(samplesPerNote)

                    // Generate a nice retro sound wave
                    for (i in 0 until samplesPerNote) {
                        val angle = 2.0 * Math.PI * freq * i / sampleRate + phase
                        val sineVal = sin(angle)
                        val squareVal = if (sineVal > 0) 0.4 else -0.4
                        // Combine 60% square wave (classic nes chip tone) and 40% sine wave for a rich sound:
                        val value = (squareVal * 0.4 + sineVal * 0.2) * 32767.0
                        buffer[i] = value.toInt().toShort()
                    }
                    
                    // Maintain phase continuity across notes
                    phase += 2.0 * Math.PI * freq * samplesPerNote / sampleRate
                    phase %= 2.0 * Math.PI

                    // Write synchronously to the audio track stream
                    audioTrack?.write(buffer, 0, buffer.size)

                    noteIndex = (noteIndex + 1) % notes.size
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cleanUp()
            }
        }
    }

    fun stop() {
        isPlaying = false
        job?.cancel()
        cleanUp()
    }

    private fun cleanUp() {
        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                    release()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null
        job = null
    }
}
