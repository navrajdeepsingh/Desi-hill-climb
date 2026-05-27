package com.example.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.sin

enum class MusicTrack {
    OLD_SKOOL,
    THE_LAST_RIDE
}

object LobbyMusicPlayer {
    private var job: Job? = null
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 22050
    private var isPlaying = false
    var currentTrack: MusicTrack = MusicTrack.OLD_SKOOL

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

                // Sidhu Moosewala - Old Skool melody notes hook & beats representation
                val oldSkoolMelody = listOf(
                    // "Old skool jatt di..."
                    Pair(523.25f, 180), // C5
                    Pair(523.25f, 180), // C5
                    Pair(523.25f, 180), // C5
                    Pair(466.16f, 180), // Bb4
                    Pair(523.25f, 220), // C5
                    Pair(622.25f, 320), // Eb5
                    Pair(587.33f, 220), // D5
                    Pair(523.25f, 220), // C5
                    Pair(466.16f, 320), // Bb4
                    Pair(0.0f, 150),    // Rest

                    // "...clip chalda"
                    Pair(392.00f, 180), // G4
                    Pair(466.16f, 180), // Bb4
                    Pair(523.25f, 220), // C5
                    Pair(523.25f, 180), // C5
                    Pair(523.25f, 180), // C5
                    Pair(587.33f, 320), // D5
                    Pair(523.25f, 220), // C5
                    Pair(466.16f, 220), // Bb4
                    Pair(392.00f, 320), // G4
                    Pair(0.0f, 250),    // Rest

                    // Hip-Hop punchy bass drum kicks & beat fillers
                    Pair(130.81f, 150), // C3 bass kick line
                    Pair(0.0f, 100),    // Rest
                    Pair(130.81f, 150), // C3 bass line
                    Pair(392.00f, 180), // G4 pop
                    Pair(466.16f, 180), // Bb4 pop
                    Pair(0.0f, 200)     // Rest
                )

                // Sidhu Moosewala - The Last Ride (monochrome, soulful nostalgic tone chord progression)
                val theLastRideMelody = listOf(
                    // "Ae ni khabaan vich..." main slow nostalgic hook
                    Pair(261.63f, 320), // C4 (Lower, soulful octave)
                    Pair(311.13f, 320), // Eb4
                    Pair(392.00f, 400), // G4
                    Pair(349.23f, 320), // F4
                    Pair(311.13f, 320), // Eb4
                    Pair(293.66f, 400), // D4
                    Pair(261.63f, 450), // C4
                    Pair(0.0f, 300),    // Rest

                    Pair(311.13f, 320), // Eb4
                    Pair(349.23f, 320), // F4
                    Pair(392.00f, 450), // G4
                    Pair(466.16f, 320), // Bb4
                    Pair(392.00f, 320), // G4
                    Pair(349.23f, 400), // F4
                    Pair(311.13f, 450), // Eb4
                    Pair(0.0f, 300),    // Rest

                    // Heavy solid Hip-hop heavy slow drum beats / sub kick
                    Pair(110.00f, 300), // A2/Bb2 deep retro rumble
                    Pair(0.0f, 200),
                    Pair(130.81f, 300), // C3 bass punch
                    Pair(0.0f, 300)
                )

                var noteIndex = 0
                var phase = 0.0
                var bassPhase = 0.0

                while (isActive && isPlaying) {
                    val activeMelody = if (currentTrack == MusicTrack.THE_LAST_RIDE) theLastRideMelody else oldSkoolMelody
                    // Safe guard index boundaries when wrapping around different active tracks
                    if (noteIndex >= activeMelody.size) {
                        noteIndex = 0
                    }

                    val note = activeMelody[noteIndex]
                    val freq = note.first
                    val durationMs = note.second
                    val samplesPerNote = (sampleRate * durationMs / 1000)
                    val buffer = ShortArray(samplesPerNote)

                    // Dynamic underlying hip-hop bass key
                    val bassFreq = if (noteIndex % 8 < 4) 110.00f else 82.41f // Deeper bass pads for soul mood

                    for (i in 0 until samplesPerNote) {
                        var sampleVal = 0.0

                        // 1. Synthesize Lead Melody (using punchy square-decay NES chip style)
                        if (freq > 0) {
                            val angle = 2.0 * Math.PI * freq * i / sampleRate + phase
                            val leadSine = sin(angle)
                            val leadSquare = if (leadSine > 0) 0.3 else -0.3
                            val decay = (samplesPerNote - i).toDouble() / samplesPerNote
                            sampleVal += (leadSquare * 0.4 + leadSine * 0.15) * decay
                        }

                        // 2. Synthesize Hip-Hop Bass Beats
                        val bassAngle = 2.0 * Math.PI * bassFreq * i / sampleRate + bassPhase
                        val bassWave = sin(bassAngle)
                        val isKickTime = (noteIndex % 4 == 0)
                        val beatEnvelope = if (isKickTime) {
                            val progress = i.toDouble() / samplesPerNote
                            kotlin.math.exp(-8.0 * progress) * 0.4 // Quick deep punchy kick
                        } else {
                            0.08 // soft rumble backdrop
                        }

                        sampleVal += bassWave * beatEnvelope

                        // 3. Synthesize punchy retro snare backbeat on off-beats (decaying white noise)
                        val isSnareTime = (noteIndex % 4 == 2)
                        if (isSnareTime) {
                            val progress = i.toDouble() / samplesPerNote
                            val snareEnvelope = kotlin.math.exp(-12.0 * progress) * 0.16
                            // Lightning fast deterministic LCG pseudo-random noise generator
                            val noise = (((i * 1103515245) + 12345) and 0xFFFF).toDouble() / 65536.0 * 2.0 - 1.0
                            sampleVal += noise * snareEnvelope
                        }

                        // Clamp value inside valid short ranges
                        val scaled = (sampleVal * 32767.0).coerceIn(-32768.0, 32767.0)
                        buffer[i] = scaled.toInt().toShort()
                    }

                    // Maintain continuous wave alignment
                    if (freq > 0) {
                        phase += 2.0 * Math.PI * freq * samplesPerNote / sampleRate
                        phase %= 2.0 * Math.PI
                    }
                    bassPhase += 2.0 * Math.PI * bassFreq * samplesPerNote / sampleRate
                    bassPhase %= 2.0 * Math.PI

                    // Play chunk out of speaker
                    audioTrack?.write(buffer, 0, buffer.size)

                    noteIndex = (noteIndex + 1) % activeMelody.size
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cleanUp()
            }
        }
    }

    fun setTrackAndRestart(track: MusicTrack) {
        currentTrack = track
        stop()
        start()
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
