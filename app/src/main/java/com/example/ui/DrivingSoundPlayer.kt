package com.example.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.sin

object DrivingSoundPlayer {
    private const val TAG = "DrivingSoundPlayer"
    
    private var player1: MediaPlayer? = null
    private var player2: MediaPlayer? = null
    private var synthJob: Job? = null
    private val sampleRate = 22050
    
    private var isPlaying = false
    private var isP1Active = false
    private var isP2Active = false
    private var isGasActive = false

    private const val SOUND_URL_1 = "https://docs.google.com/uc?export=download&id=1fJYTRMYgPR-_3XPzoXenDzY5f4_Q5rrd"
    private const val SOUND_URL_2 = "https://docs.google.com/uc?export=download&id=1ydS3sWiOQwnHmWyQVXglLkmOra15w1km"

    private var setupJob: Job? = null
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Start playing the driving sounds. Initiates background streaming prepared listeners,
     * and sets up a parallel synth fallback if streaming buffers take time to load.
     */
    fun start() {
        synchronized(this) {
            if (isPlaying) return
            isPlaying = true
            isP1Active = false
            isP2Active = false
            isGasActive = false

            Log.d(TAG, "Starting dual driving audio stream setup...")

            // Start the synth fallback immediately so there is never silence!
            startSynthFallback()

            setupJob?.cancel()
            setupJob = playerScope.launch {
                try {
                    cleanUpPlayers()

                    val p1 = withContext(Dispatchers.IO) {
                        val instance = MediaPlayer()
                        try {
                            instance.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_GAME)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                            )
                            instance.setDataSource(SOUND_URL_1)
                            instance.isLooping = true
                        } catch (e: Exception) {
                            instance.release()
                            throw e
                        }
                        instance
                    }

                    val p2 = withContext(Dispatchers.IO) {
                        val instance = MediaPlayer()
                        try {
                            instance.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_GAME)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                            )
                            instance.setDataSource(SOUND_URL_2)
                            instance.isLooping = true
                        } catch (e: Exception) {
                            instance.release()
                            throw e
                        }
                        instance
                    }

                    if (!isActive) {
                        p1.release()
                        p2.release()
                        return@launch
                    }

                    p1.setOnPreparedListener { mp ->
                        synchronized(this@DrivingSoundPlayer) {
                            if (isPlaying && player1 == mp) {
                                Log.d(TAG, "Driving Sound 1 (Engine) streaming successfully!")
                                isP1Active = true
                                stopSynthOnly()
                                try {
                                    mp.start()
                                } catch (e: Exception) {}
                                updateVolumeLevels()
                            } else {
                                try { mp.release() } catch (ex: Exception) {}
                            }
                        }
                    }

                    p1.setOnErrorListener { mp, what, extra ->
                        Log.e(TAG, "Driving Sound 1 stream failed (what=$what, extra=$extra). Falling back to synth.")
                        synchronized(this@DrivingSoundPlayer) {
                            isP1Active = false
                            if (player1 == mp) {
                                player1 = null
                            }
                            try { mp.release() } catch (ex: Exception) {}
                            startSynthFallback()
                        }
                        true
                    }

                    p2.setOnPreparedListener { mp ->
                        synchronized(this@DrivingSoundPlayer) {
                            if (isPlaying && player2 == mp) {
                                Log.d(TAG, "Driving Sound 2 (Booster) streaming successfully!")
                                isP2Active = true
                                stopSynthOnly()
                                try {
                                    mp.start()
                                } catch (e: Exception) {}
                                updateVolumeLevels()
                            } else {
                                try { mp.release() } catch (ex: Exception) {}
                            }
                        }
                    }

                    p2.setOnErrorListener { mp, what, extra ->
                        Log.e(TAG, "Driving Sound 2 stream failed (some codes=$what, $extra).")
                        synchronized(this@DrivingSoundPlayer) {
                            isP2Active = false
                            if (player2 == mp) {
                                player2 = null
                            }
                            try { mp.release() } catch (ex: Exception) {}
                        }
                        true
                    }

                    synchronized(this@DrivingSoundPlayer) {
                        if (isPlaying) {
                            player1 = p1
                            player2 = p2
                            try {
                                p1.prepareAsync()
                                p2.prepareAsync()
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed calling prepareAsync: ${e.message}")
                                p1.release()
                                p2.release()
                                player1 = null
                                player2 = null
                            }
                        } else {
                            p1.release()
                            p2.release()
                        }
                    }

                    // Wait 4 seconds for streams to begin; fallback instantly if they take too long
                    delay(4000)
                    synchronized(this@DrivingSoundPlayer) {
                        if (isPlaying && (!isP1Active && !isP2Active) && synthJob == null) {
                            Log.d(TAG, "Streaming buffer taking too long (network lag). Triggering synth engine audio.")
                            startSynthFallback()
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Exception during stream launch: ${e.message}")
                    startSynthFallback()
                }
            }
        }
    }

    /**
     * Alters the balance and pitch feel dynamically based on acceleration state.
     */
    fun setGasState(gasPressed: Boolean) {
        synchronized(this) {
            if (isGasActive == gasPressed) return
            isGasActive = gasPressed
        }
        updateVolumeLevels()
    }

    private fun updateVolumeLevels() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isGasActive) {
                    // Maximum acceleration rumble
                    player1?.setVolume(0.95f, 0.95f)
                    player2?.setVolume(0.95f, 0.95f)
                } else {
                    // Soft idle/cruising hum
                    player1?.setVolume(0.55f, 0.55f)
                    player2?.setVolume(0.18f, 0.18f)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun pause() {
        try {
            player1?.apply { if (isPlaying) pause() }
            player2?.apply { if (isPlaying) pause() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resume() {
        try {
            if (isPlaying) {
                player1?.apply { start() }
                player2?.apply { start() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        synchronized(this) {
            isPlaying = false
            setupJob?.cancel()
            stopSynthOnly()
            cleanUpPlayers()
        }
    }

    private fun startSynthFallback() {
        if (synthJob != null) return
        synthJob = CoroutineScope(Dispatchers.Default).launch {
            var trackLocal: AudioTrack? = null
            try {
                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)
                
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
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

                trackLocal = track
                track.play()

                var phase = 0.0
                val buffer = ShortArray(1024)

                while (isActive && isPlaying && (!isP1Active && !isP2Active)) {
                    // Dynamic frequency engine hum: pitch increases on gas pressed
                    val targetFreq = if (isGasActive) 175.0 else 75.0
                    
                    for (i in buffer.indices) {
                        val angle = 2.0 * Math.PI * targetFreq * i / sampleRate + phase
                        val sine = sin(angle)
                        
                        // Add organic dirt and noise (distorted sawtooth shape) to feel like actual piston strokes!
                        val sawtooth = (i % 80).toDouble() / 80.0 - 0.5
                        val sampleVal = (sine * 0.55 + sawtooth * 0.25) * 0.35
                        
                        buffer[i] = (sampleVal * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
                    }
                    
                    phase += 2.0 * Math.PI * targetFreq * buffer.size / sampleRate
                    phase %= 2.0 * Math.PI

                    track.write(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
                    Log.e(TAG, "AudioTrack release warning: ${t.message}")
                }
            }
        }
    }

    private fun stopSynthOnly() {
        synthJob?.cancel()
        synthJob = null
    }

    private fun cleanUpPlayers() {
        synchronized(this) {
            try {
                player1?.let { mp ->
                    mp.setOnPreparedListener(null)
                    mp.setOnErrorListener(null)
                    mp.release()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Player1 release warning: ${t.message}")
            }
            player1 = null

            try {
                player2?.let { mp ->
                    mp.setOnPreparedListener(null)
                    mp.setOnErrorListener(null)
                    mp.release()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Player2 release warning: ${t.message}")
            }
            player2 = null
        }
    }
}
