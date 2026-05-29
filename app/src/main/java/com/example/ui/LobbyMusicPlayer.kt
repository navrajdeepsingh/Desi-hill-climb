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
    SIDHU_MOOSEWALA,
    LEGEND
}

object LobbyMusicPlayer {
    private const val TAG = "LobbyMusicPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    var currentTrack: MusicTrack = MusicTrack.OLD_SKOOL

    // State tracks whether streaming of studio MP3 is active right now
    var isStreamingActive = false
        private set

    private var playerJob: Job? = null
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var synthJob: Job? = null

    fun start() {
        synchronized(this) {
            if (isPlaying) return
            isPlaying = true
            isStreamingActive = false
        }

        val streamUrl = when (currentTrack) {
            MusicTrack.OLD_SKOOL -> "https://archive.org/download/sidhu-moose-wala-all-songs/Old%20Skool.mp3"
            MusicTrack.THE_LAST_RIDE -> "https://s320.djpunjab.is/data/128/51922/299856/The%20Last%20Ride%20-%20Sidhu%20Moose%20Wala.mp3"
            MusicTrack.SIDHU_MOOSEWALA -> "https://archive.org/download/y-2mate.com-sidhu-moose-wala-juke-box-same-beef-tochan-dhakka-bambiha-bole-old-skool-dollar-legend/y2mate.com%20-%20295%20Official%20Audio%20%20Sidhu%20Moose%20Wala%20%20The%20Kidd%20%20Moosetape.mp3"
            MusicTrack.LEGEND -> "https://archive.org/download/y-2mate.com-sidhu-moose-wala-juke-box-same-beef-tochan-dhakka-bambiha-bole-old-skool-dollar-legend/y2mate.com%20-%20LEGEND%20%20SIDHU%20MOOSE%20WALA%20%20The%20Kidd%20%20Gold%20Media%20%20Latest%20Punjabi%20Songs%202020.mp3"
        }

        // Cancel previous loading job first
        synchronized(this) {
            playerJob?.cancel()
            stopSynthOnly()
            
            // Start synth fallback immediately to guarantee audio instantly
            startSynthFallback()

            playerJob = playerScope.launch {
                try {
                    cleanUpMediaPlayer()
                    val mp = withContext(Dispatchers.IO) {
                        val instance = MediaPlayer()
                        try {
                            instance.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                            )
                            instance.setDataSource(streamUrl)
                            instance.isLooping = true
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception configuring MediaPlayer: ${e.message}")
                            instance.release()
                            throw e
                        }
                        instance
                    }

                    if (!isActive) {
                        mp.release()
                        return@launch
                    }

                    mp.setOnPreparedListener { player ->
                        synchronized(this@LobbyMusicPlayer) {
                            if (isPlaying && mediaPlayer == player) {
                                Log.d(TAG, "Live direct Sidhu Moose Wala stream loaded successfully! Playing studio track.")
                                isStreamingActive = true
                                stopSynthOnly()
                                try {
                                    player.start()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed starting Lobby track: ${e.message}")
                                }
                            } else {
                                try {
                                    player.release()
                                } catch (e: Exception) {}
                            }
                        }
                    }

                    mp.setOnErrorListener { player, what, extra ->
                        Log.e(TAG, "MediaPlayer streaming failed: what=$what, extra=$extra.")
                        synchronized(this@LobbyMusicPlayer) {
                            isStreamingActive = false
                            if (mediaPlayer == player) {
                                mediaPlayer = null
                            }
                            try {
                                player.release()
                            } catch (e: Exception) {}
                        }
                        true
                    }

                    synchronized(this@LobbyMusicPlayer) {
                        if (isPlaying) {
                            mediaPlayer = mp
                            try {
                                mp.prepareAsync()
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to prepareAsync: ${e.message}")
                                mp.release()
                                mediaPlayer = null
                                isStreamingActive = false
                            }
                        } else {
                            mp.release()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during media setup: ${e.message}")
                    isStreamingActive = false
                }
            }
        }
    }

    fun setTrackAndRestart(track: MusicTrack) {
        synchronized(this) {
            currentTrack = track
            isPlaying = false
            isStreamingActive = false
            playerJob?.cancel()
            stopSynthOnly()
            cleanUpMediaPlayer()
        }
        start()
    }

    fun stop() {
        synchronized(this) {
            isPlaying = false
            isStreamingActive = false
            playerJob?.cancel()
            stopSynthOnly()
            cleanUpMediaPlayer()
        }
    }

    private fun startSynthFallback() {
        synchronized(this) {
            if (synthJob != null) return
            synthJob = CoroutineScope(Dispatchers.Default).launch {
                var trackLocal: AudioTrack? = null
                try {
                    val sampleRate = 22050
                    val minBufferSize = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    ).coerceAtLeast(4096)
                    
                    val track = AudioTrack.Builder()
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
                        .setBufferSizeInBytes(minBufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()

                    trackLocal = track
                    track.play()

                    // Different custom 8bit melodies for each of local Sidhu tracks
                    val notes = when (currentTrack) {
                        MusicTrack.OLD_SKOOL -> doubleArrayOf(
                            261.63, 293.66, 329.63, 392.00, 392.00, 440.00, 392.00, 329.63,
                            293.66, 261.63, 293.66, 329.63, 261.63, 261.63, 293.66, 329.63
                        )
                        MusicTrack.THE_LAST_RIDE -> doubleArrayOf(
                            220.00, 261.63, 293.66, 329.63, 392.00, 329.63, 293.66, 261.63,
                            220.00, 220.00, 261.63, 293.66, 220.00, 220.00, 261.63, 293.66
                        )
                        MusicTrack.SIDHU_MOOSEWALA -> doubleArrayOf(
                            293.66, 349.23, 440.00, 392.00, 440.00, 392.00, 349.23, 293.66,
                            293.66, 349.23, 440.00, 392.00, 523.25, 440.00, 392.00, 349.23
                        )
                        MusicTrack.LEGEND -> doubleArrayOf(
                            196.00, 220.00, 261.63, 196.00, 220.00, 261.63, 293.66, 261.63,
                            196.00, 220.00, 261.63, 196.00, 329.63, 293.66, 261.63, 220.00
                        )
                    }
                    val noteDurationMs = 280
                    var noteIndex = 0

                    while (isActive && isPlaying && !isStreamingActive) {
                        val freq = notes[noteIndex % notes.size]
                        noteIndex++

                        val numSamples = (sampleRate * (noteDurationMs / 1000.0)).toInt()
                        val buffer = ShortArray(numSamples)
                        var phase = 0.0

                        for (i in 0 until numSamples) {
                            val angle = 2.0 * Math.PI * freq * i / sampleRate + phase
                            val sine = sin(angle)
                            // Clean triangle wave sound with soft volume so it doesn't pierce ears
                            val triangle = if (sine >= 0) sine else -sine
                            val sampleVal = (sine * 0.45 + triangle * 0.15) * 0.12
                            buffer[i] = (sampleVal * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
                        }

                        track.write(buffer, 0, buffer.size)
                        delay(noteDurationMs.toLong() - 15)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lobby synth error: ${e.message}")
                } finally {
                    try {
                        trackLocal?.apply {
                            if (state == AudioTrack.STATE_INITIALIZED) {
                                try {
                                    stop()
                                } catch (t: Throwable) {}
                                release()
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Lobby AudioTrack release error: ${t.message}")
                    }
                }
            }
        }
    }

    private fun stopSynthOnly() {
        synchronized(this) {
            synthJob?.cancel()
            synthJob = null
        }
    }

    private fun cleanUpMediaPlayer() {
        synchronized(this) {
            try {
                mediaPlayer?.let { mp ->
                    mp.setOnPreparedListener(null)
                    mp.setOnErrorListener(null)
                    mp.release()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "MediaPlayer release error: ${t.message}")
            }
            mediaPlayer = null
        }
    }
}
