package com.example.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.sin

enum class MusicTrack {
    OLD_SKOOL,
    THE_LAST_RIDE,
    SIDHU_MOOSEWALA
}

object LobbyMusicPlayer {
    private const val TAG = "LobbyMusicPlayer"
    private var job: Job? = null
    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private val sampleRate = 22050
    private var isPlaying = false
    var currentTrack: MusicTrack = MusicTrack.OLD_SKOOL

    // State tracks whether streaming of studio MP3 is active right now
    var isStreamingActive = false
        private set

    fun start() {
        if (isPlaying) return
        isPlaying = true
        isStreamingActive = false

        val streamUrl = when (currentTrack) {
            MusicTrack.OLD_SKOOL -> "https://archive.org/download/sidhu-moose-wala-all-songs/Old%20Skool.mp3"
            MusicTrack.THE_LAST_RIDE -> "https://archive.org/download/sidhu-moose-wala-all-songs/The%20Last%20Ride.mp3"
            MusicTrack.SIDHU_MOOSEWALA -> "https://docs.google.com/uc?export=download&id=16llwWvDDdCZ2GBwWlirBFMlpUVMpHLKB"
        }

        // Start online MediaPlayer stream.
        // It operates asynchronously so it will not block the Main and UI threads.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                cleanUpMediaPlayer()
                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(streamUrl)
                    isLooping = true
                    
                    setOnPreparedListener { player ->
                        if (isPlaying) {
                            Log.d(TAG, "Live direct Sidhu Moose Wala stream loaded successfully! Playing studio track.")
                            isStreamingActive = true
                            // Stop any running synthesizer fallback if it was activated
                            stopSynthOnly()
                            player.start()
                        } else {
                            player.release()
                        }
                    }
                    
                    setOnErrorListener { player, what, extra ->
                        Log.e(TAG, "MediaPlayer streaming failed: what=$what, extra=$extra. Seamless fallback to retro synth.")
                        isStreamingActive = false
                        player.release()
                        mediaPlayer = null
                        startSynthFallback()
                        true // flag error handled
                    }
                }
                mediaPlayer = mp
                mp.prepareAsync() // load in background gracefully
                
                // Set a safety timeout: if stream does not load in 4.5 seconds, start the retro synth
                // so the user is never left waiting in silence!
                delay(4500)
                if (isPlaying && !isStreamingActive && job == null) {
                    Log.d(TAG, "Streaming buffer taking too long. Pre-activating retro synth fallback.")
                    startSynthFallback()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception starting streaming player: ${e.message}. Using retro synth fallback.")
                isStreamingActive = false
                startSynthFallback()
            }
        }
    }

    private fun startSynthFallback() {
        if (job != null) return // Already playing synthesized fallback
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

                // Sidhu Moosewala - Old Skool melody notes
                val oldSkoolMelody = listOf(
                    Pair(523.25f, 180), Pair(523.25f, 180), Pair(523.25f, 180), Pair(466.16f, 180),
                    Pair(523.25f, 220), Pair(622.25f, 320), Pair(587.33f, 220), Pair(523.25f, 220),
                    Pair(466.16f, 320), Pair(0.0f, 150),
                    Pair(392.00f, 180), Pair(466.16f, 180), Pair(523.25f, 220), Pair(523.25f, 180),
                    Pair(523.25f, 180), Pair(587.33f, 320), Pair(523.25f, 220), Pair(466.16f, 220),
                    Pair(392.00f, 320), Pair(0.0f, 250),
                    Pair(130.81f, 150), Pair(0.0f, 100), Pair(130.81f, 150), Pair(392.00f, 180),
                    Pair(466.16f, 180), Pair(0.0f, 200)
                )

                // Sidhu Moosewala - The Last Ride slowlypaced melody
                val theLastRideMelody = listOf(
                    Pair(261.63f, 320), Pair(311.13f, 320), Pair(392.00f, 400), Pair(349.23f, 320),
                    Pair(311.13f, 320), Pair(293.66f, 400), Pair(261.63f, 450), Pair(0.0f, 300),
                    Pair(311.13f, 320), Pair(349.23f, 320), Pair(392.00f, 450), Pair(466.16f, 320),
                    Pair(392.00f, 320), Pair(349.23f, 400), Pair(311.13f, 450), Pair(0.0f, 300),
                    Pair(110.00f, 300), Pair(0.0f, 200), Pair(130.81f, 300), Pair(0.0f, 300)
                )

                var noteIndex = 0
                var phase = 0.0
                var bassPhase = 0.0

                while (isActive && isPlaying && !isStreamingActive) {
                    val activeMelody = when (currentTrack) {
                        MusicTrack.THE_LAST_RIDE -> theLastRideMelody
                        MusicTrack.OLD_SKOOL -> oldSkoolMelody
                        MusicTrack.SIDHU_MOOSEWALA -> oldSkoolMelody
                    }
                    if (noteIndex >= activeMelody.size) {
                        noteIndex = 0
                    }

                    val note = activeMelody[noteIndex]
                    val freq = note.first
                    val durationMs = note.second
                    val samplesPerNote = (sampleRate * durationMs / 1000)
                    val buffer = ShortArray(samplesPerNote)

                    val bassFreq = if (noteIndex % 8 < 4) 110.00f else 82.41f

                    for (i in 0 until samplesPerNote) {
                        var sampleVal = 0.0

                        // 1. Synthesize Lead Melody
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
                            kotlin.math.exp(-8.0 * progress) * 0.4
                        } else {
                            0.08
                        }
                        sampleVal += bassWave * beatEnvelope

                        // 3. Synthesize punchy retro snare backbeat on off-beats (LCG noise)
                        val isSnareTime = (noteIndex % 4 == 2)
                        if (isSnareTime) {
                            val progress = i.toDouble() / samplesPerNote
                            val snareEnvelope = kotlin.math.exp(-12.0 * progress) * 0.16
                            val noise = (((i * 1103515245) + 12345) and 0xFFFF).toDouble() / 65536.0 * 2.0 - 1.0
                            sampleVal += noise * snareEnvelope
                        }

                        val scaled = (sampleVal * 32767.0).coerceIn(-32768.0, 32767.0)
                        buffer[i] = scaled.toInt().toShort()
                    }

                    if (freq > 0) {
                        phase += 2.0 * Math.PI * freq * samplesPerNote / sampleRate
                        phase %= 2.0 * Math.PI
                    }
                    bassPhase += 2.0 * Math.PI * bassFreq * samplesPerNote / sampleRate
                    bassPhase %= 2.0 * Math.PI

                    audioTrack?.write(buffer, 0, buffer.size)
                    noteIndex = (noteIndex + 1) % activeMelody.size
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopSynthOnly()
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
        isStreamingActive = false
        stopSynthOnly()
        cleanUpMediaPlayer()
    }

    private fun stopSynthOnly() {
        job?.cancel()
        job = null
        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    try {
                        stop()
                    } catch (t: Throwable) {
                        Log.e(TAG, "AudioTrack stop warning: ${t.message}")
                    }
                    release()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "AudioTrack release warning: ${t.message}")
        }
        audioTrack = null
    }

    private fun cleanUpMediaPlayer() {
        try {
            mediaPlayer?.release()
        } catch (t: Throwable) {
            Log.e(TAG, "MediaPlayer release error: ${t.message}")
        }
        mediaPlayer = null
    }
}
