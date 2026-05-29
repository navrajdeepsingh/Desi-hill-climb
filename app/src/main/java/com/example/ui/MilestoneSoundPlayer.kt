package com.example.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.sin

object MilestoneSoundPlayer {
    private const val TAG = "MilestoneSoundPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var isPreparing = false
    private var isReady = false

    private var playerJob: Job? = null
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Direct Google Drive direct stream URL
    private const val STREAM_URL = "https://docs.google.com/uc?export=download&id=1RVM-I1y2YN5wM_3htda--kUB4VOJtmj1"

    /**
     * Pre-buffers the MediaPlayer so that when the 1000m mark is hit,
     * the sound plays immediately with zero lag.
     */
    fun preBuffer() {
        synchronized(this) {
            if (isPreparing || isReady) return
            isPreparing = true
            isReady = false
        }

        synchronized(this) {
            playerJob?.cancel()
            playerJob = playerScope.launch {
                try {
                    cleanUp()
                    val mp = withContext(Dispatchers.IO) {
                        val instance = MediaPlayer()
                        try {
                            instance.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            instance.setDataSource(STREAM_URL)
                        } catch (e: Exception) {
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
                        synchronized(this@MilestoneSoundPlayer) {
                            if (mediaPlayer == player) {
                                Log.d(TAG, "Milestone GDrive sound pre-buffered and ready to play!")
                                isReady = true
                                isPreparing = false
                            } else {
                                try { player.release() } catch (e: Exception) {}
                            }
                        }
                    }

                    mp.setOnErrorListener { player, what, extra ->
                        Log.e(TAG, "Milestone sound MediaPlayer preparation failed: what=$what, extra=$extra. Synthesizer fallback activated.")
                        synchronized(this@MilestoneSoundPlayer) {
                            isReady = false
                            isPreparing = false
                            if (mediaPlayer == player) {
                                mediaPlayer = null
                            }
                            try { player.release() } catch (e: Exception) {}
                        }
                        true
                    }

                    synchronized(this@MilestoneSoundPlayer) {
                        if (isPreparing) {
                            mediaPlayer = mp
                            try {
                                mp.prepareAsync()
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed preparing GDrive milestone stream: ${e.message}")
                                mp.release()
                                mediaPlayer = null
                                isReady = false
                                isPreparing = false
                            }
                        } else {
                            mp.release()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception preparing custom milestone player: ${e.message}")
                    synchronized(MilestoneSoundPlayer) {
                        isReady = false
                        isPreparing = false
                    }
                }
            }
        }
    }

    /**
     * Plays the pre-buffered Google Drive stream. If the stream is not ready,
     * plays a delightful triumphant local synthesizer chime using AudioTrack.
     */
    fun play() {
        CoroutineScope(Dispatchers.IO).launch {
            if (isReady && mediaPlayer != null) {
                try {
                    Log.d(TAG, "Playing direct GDrive milestone sound!")
                    mediaPlayer?.start()
                    
                    // Once played, we mark as not ready and pre-buffer a fresh instance for the NEXT 1000m milestone!
                    isReady = false
                    delay(5000) // Wait for playback to complete before rebuilding
                    preBuffer()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed playing media stream, playing chime instead: ${e.message}")
                    playSynthesizedChime()
                    isReady = false
                    preBuffer()
                }
            } else {
                Log.w(TAG, "Stream not ready/offline. Triggering glorious synthesizer victory chime fallback.")
                playSynthesizedChime()
                // Trigger pre-buffer in case we are just starting or recovered connection
                preBuffer()
            }
        }
    }

    /**
     * Synthesizes a beautiful and bright triplet celebratory chime in real-time.
     */
    private fun playSynthesizedChime() {
        CoroutineScope(Dispatchers.Default).launch {
            var track: AudioTrack? = null
            try {
                val sampleRate = 22050
                // Triplet upward major triad nodes: C5 (523Hz), E5 (659Hz), G5 (784Hz), C6 (1046Hz) for celebratory feel
                val notes = listOf(
                    Pair(523.25f, 150),
                    Pair(659.25f, 150),
                    Pair(783.99f, 150),
                    Pair(1046.50f, 350)
                )

                // Sum total duration and compile into short buffer
                val totalDurationMs = notes.sumOf { it.second }
                val numSamples = sampleRate * totalDurationMs / 1000
                val buffer = ShortArray(numSamples)

                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize.coerceAtLeast(numSamples * 2))
                    .setTransferMode(AudioTrack.MODE_STATIC) // Static mode is extremely low-overhead for short sounds
                    .build()

                var currentSampleOffset = 0
                for (note in notes) {
                    val freq = note.first
                    val durationMs = note.second
                    val noteSamples = sampleRate * durationMs / 1000

                    for (i in 0 until noteSamples) {
                        if (currentSampleOffset + i >= numSamples) break
                        
                        // Pure sine-wave with ADSR volume envelope (linear decay)
                        val angle = 2.0 * Math.PI * freq * i / sampleRate
                        val sine = sin(angle)
                        val decay = (noteSamples - i).toDouble() / noteSamples
                        
                        val sampleVal = sine * 0.45 * decay
                        val scaled = (sampleVal * 32767.0).coerceIn(-32768.0, 32767.0)
                        buffer[currentSampleOffset + i] = scaled.toInt().toShort()
                    }
                    currentSampleOffset += noteSamples
                }

                // Load and play short static clip instantly
                track.write(buffer, 0, buffer.size)
                track.play()
                
                // Let the audio run completely
                delay(totalDurationMs.toLong() + 100)
            } catch (e: Exception) {
                Log.e(TAG, "Error compiling static beep chime: ${e.message}")
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Stop and cleanup players
     */
    fun stop() {
        synchronized(this) {
            isReady = false
            isPreparing = false
            playerJob?.cancel()
            cleanUp()
        }
    }

    private fun cleanUp() {
        synchronized(this) {
            try {
                mediaPlayer?.let { mp ->
                    mp.setOnPreparedListener(null)
                    mp.setOnErrorListener(null)
                    mp.release()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Milestone MediaPlayer release error: ${t.message}")
            }
            mediaPlayer = null
        }
    }
}
