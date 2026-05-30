package com.example.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.sin

enum class MusicTrack {
    OLD_SKOOL,
    THE_LAST_RIDE,
    SIDHU_MOOSEWALA,
    LEGEND,
    MUSTANG
}

sealed class DownloadStatus {
    object NotDownloaded : DownloadStatus()
    class Downloading(val progress: Int) : DownloadStatus()
    object Downloaded : DownloadStatus()
    class Failed(val error: String) : DownloadStatus()
}

object LobbyMusicPlayer {
    private const val TAG = "LobbyMusicPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    var currentTrack: MusicTrack = MusicTrack.OLD_SKOOL

    // State tracks whether streaming of studio MP3 is active right now
    var isStreamingActive = false
        private set

    // New state showing whether current track is played from local offline cache
    private val _isOfflinePlayback = MutableStateFlow(false)
    val isOfflinePlaybackFlow = _isOfflinePlayback.asStateFlow()
    var isOfflinePlayback: Boolean
        get() = _isOfflinePlayback.value
        private set(value) {
            _isOfflinePlayback.value = value
        }

    private var playerJob: Job? = null
    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var synthJob: Job? = null

    // Background downloading context and flows
    private var appContext: Context? = null
    private val downloadScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _downloadStates = MutableStateFlow<Map<MusicTrack, DownloadStatus>>(emptyMap())
    val downloadStates = _downloadStates.asStateFlow()

    private val _videoDownloadState = MutableStateFlow<DownloadStatus>(DownloadStatus.NotDownloaded)
    val videoDownloadState = _videoDownloadState.asStateFlow()

    fun initialize(context: Context) {
        synchronized(this) {
            appContext = context.applicationContext
        }
        checkVideoDownloadState()
        startBackgroundDownloads()
    }

    fun checkVideoDownloadState() {
        val context = appContext ?: return
        val cachedVideo = File(context.filesDir, "video_OLD_SKOOL.mp4")
        if (cachedVideo.exists() && cachedVideo.length() > 2 * 1024 * 1024) {
            _videoDownloadState.value = DownloadStatus.Downloaded
        } else {
            _videoDownloadState.value = DownloadStatus.NotDownloaded
        }
    }

    fun downloadVideo() {
        val context = appContext ?: return
        val urlStr = "https://archive.org/download/y-2mate.com-sidhu-moose-wala-juke-box-same-beef-tochan-dhakka-bambiha-bole-old-skool-dollar-legend/y2mate.com%20-%20OLD%20SKOOL%20%20SIDHU%20MOOSE%20WALA%20%20PREM%20DHILLON%20%20KIDD%20%20LATEST%20PUNJABI%20SONGS%202020.mp4"

        downloadScope.launch {
            if (_videoDownloadState.value is DownloadStatus.Downloaded) return@launch

            val tempFile = File(context.filesDir, "temp_video_OLD_SKOOL.mp4")
            val targetFile = File(context.filesDir, "video_OLD_SKOOL.mp4")

            try {
                _videoDownloadState.value = DownloadStatus.Downloading(0)

                val url = URL(urlStr)
                val connection = withContext(Dispatchers.IO) {
                    url.openConnection() as HttpURLConnection
                }.apply {
                    connectTimeout = 20000
                    readTimeout = 20000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }

                val responseCode = withContext(Dispatchers.IO) { connection.responseCode }
                if (responseCode !in 200..299) {
                    throw Exception("Server returned code $responseCode")
                }

                val fileLength = connection.contentLength
                val input: InputStream = connection.inputStream
                val output = FileOutputStream(tempFile)

                val data = ByteArray(16384)
                var total: Long = 0
                var count: Int
                var lastProgressUpdate = 0L

                while (withContext(Dispatchers.IO) { input.read(data) }.also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        val progress = ((total * 100) / fileLength).toInt()
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 300) {
                            _videoDownloadState.value = DownloadStatus.Downloading(progress)
                            lastProgressUpdate = now
                        }
                    } else {
                        val progress = (total / (1024 * 150)).toInt().coerceAtMost(99)
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 300) {
                            _videoDownloadState.value = DownloadStatus.Downloading(progress)
                            lastProgressUpdate = now
                        }
                    }
                    withContext(Dispatchers.IO) { output.write(data, 0, count) }
                }

                withContext(Dispatchers.IO) {
                    output.flush()
                    output.close()
                    input.close()
                }

                if (tempFile.exists() && tempFile.length() > 2 * 1024 * 1024) {
                    if (targetFile.exists()) targetFile.delete()
                    val renameSuccess = tempFile.renameTo(targetFile)
                    if (renameSuccess) {
                        Log.d(TAG, "Successfully downloaded offline video file to ${targetFile.absolutePath}")
                        _videoDownloadState.value = DownloadStatus.Downloaded
                    } else {
                        throw Exception("Failed to rename temp file")
                    }
                } else {
                    throw Exception("Video file download incomplete or too small")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed downloading video: ${e.message}")
                _videoDownloadState.value = DownloadStatus.Failed(e.message ?: "Unknown error")
                try {
                    if (tempFile.exists()) tempFile.delete()
                } catch (t: Throwable) {}
            }
        }
    }

    private fun updateDownloadState(track: MusicTrack, status: DownloadStatus) {
        val current = _downloadStates.value.toMutableMap()
        current[track] = status
        _downloadStates.value = current
    }

    fun startBackgroundDownloads() {
        val context = appContext ?: return
        downloadScope.launch {
            // All tracks are pre-packaged in the application's assets so they are always Downloaded!
            val currentStates = mutableMapOf<MusicTrack, DownloadStatus>()
            MusicTrack.entries.forEach { track ->
                currentStates[track] = DownloadStatus.Downloaded
            }
            _downloadStates.value = currentStates
        }
    }

    private suspend fun downloadTrackSuspend(track: MusicTrack) {
        val context = appContext ?: return
        if (_downloadStates.value[track] is DownloadStatus.Downloaded) return

        val urlStr = when (track) {
            MusicTrack.OLD_SKOOL -> "https://archive.org/download/sidhu-moose-wala-all-songs/Old%20Skool.mp3"
            MusicTrack.THE_LAST_RIDE -> "https://s320.djpunjab.is/data/128/51922/299856/The%20Last%20Ride%20-%20Sidhu%20Moose%20Wala.mp3"
            MusicTrack.SIDHU_MOOSEWALA -> "https://archive.org/download/y-2mate.com-sidhu-moose-wala-juke-box-same-beef-tochan-dhakka-bambiha-bole-old-skool-dollar-legend/y2mate.com%20-%20295%20Official%20Audio%20%20Sidhu%20Moose%20Wala%20%20The%20Kidd%20%20Moosetape.mp3"
            MusicTrack.LEGEND -> "https://archive.org/download/y-2mate.com-sidhu-moose-wala-juke-box-same-beef-tochan-dhakka-bambiha-bole-old-skool-dollar-legend/y2mate.com%20-%20LEGEND%20%20SIDHU%20MOOSE%20WALA%20%20The%20Kidd%20%20Gold%20Media%20%20Latest%20Punjabi%20Songs%202020.mp3"
            MusicTrack.MUSTANG -> "https://p320.djpunjab.is/data/48/40359/284497/Mustang%20-%20Sidhu%20Moose%20Wala.mp3"
        }

        val tempFile = File(context.filesDir, "temp_${track.name}.mp3")
        val targetFile = File(context.filesDir, "track_${track.name}.mp3")

        try {
            updateDownloadState(track, DownloadStatus.Downloading(0))

            val url = URL(urlStr)
            val connection = withContext(Dispatchers.IO) {
                url.openConnection() as HttpURLConnection
            }.apply {
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            val responseCode = withContext(Dispatchers.IO) { connection.responseCode }
            if (responseCode !in 200..299) {
                throw Exception("Server returned code $responseCode")
            }

            val fileLength = connection.contentLength
            val input: InputStream = connection.inputStream
            val output = FileOutputStream(tempFile)

            val data = ByteArray(8192)
            var total: Long = 0
            var count: Int
            var lastProgressUpdate = 0L

            while (withContext(Dispatchers.IO) { input.read(data) }.also { count = it } != -1) {
                total += count
                if (fileLength > 0) {
                    val progress = ((total * 100) / fileLength).toInt()
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 300) {
                        updateDownloadState(track, DownloadStatus.Downloading(progress))
                        lastProgressUpdate = now
                    }
                } else {
                    val progress = (total / (1024 * 50)).toInt().coerceAtMost(99)
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 300) {
                        updateDownloadState(track, DownloadStatus.Downloading(progress))
                        lastProgressUpdate = now
                    }
                }
                withContext(Dispatchers.IO) { output.write(data, 0, count) }
            }

            withContext(Dispatchers.IO) {
                output.flush()
                output.close()
                input.close()
            }

            if (tempFile.exists() && tempFile.length() > 500 * 1024) {
                if (targetFile.exists()) targetFile.delete()
                val renameSuccess = tempFile.renameTo(targetFile)
                if (renameSuccess) {
                    Log.d(TAG, "Successfully downloaded offline track ${track.name} to ${targetFile.absolutePath}")
                    updateDownloadState(track, DownloadStatus.Downloaded)
                } else {
                    throw Exception("Failed to rename temp file")
                }
            } else {
                throw Exception("File is too small/invalid")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading track ${track.name}: ${e.message}")
            updateDownloadState(track, DownloadStatus.Failed(e.message ?: "Unknown error"))
            try {
                if (tempFile.exists()) tempFile.delete()
            } catch (t: Throwable) {}
        }
    }

    fun downloadTrack(track: MusicTrack) {
        val context = appContext ?: return
        downloadScope.launch {
            downloadTrackSuspend(track)
        }
    }

    fun start() {
        synchronized(this) {
            if (isPlaying) return
            isPlaying = true
            isStreamingActive = false
            isOfflinePlayback = false
        }

        val streamUrl = when (currentTrack) {
            MusicTrack.OLD_SKOOL -> "https://archive.org/download/sidhu-moose-wala-all-songs/Old%20Skool.mp3"
            MusicTrack.THE_LAST_RIDE -> "https://s320.djpunjab.is/data/128/51922/299856/The%20Last%20Ride%20-%20Sidhu%20Moose%20Wala.mp3"
            MusicTrack.SIDHU_MOOSEWALA -> "https://archive.org/download/y-2mate.com-sidhu-moose-wala-juke-box-same-beef-tochan-dhakka-bambiha-bole-old-skool-dollar-legend/y2mate.com%20-%20295%20Official%20Audio%20%20Sidhu%20Moose%20Wala%20%20The%20Kidd%20%20Moosetape.mp3"
            MusicTrack.LEGEND -> "https://archive.org/download/y-2mate.com-sidhu-moose-wala-juke-box-same-beef-tochan-dhakka-bambiha-bole-old-skool-dollar-legend/y2mate.com%20-%20LEGEND%20%20SIDHU%20MOOSE%20WALA%20%20The%20Kidd%20%20Gold%20Media%20%20Latest%20Punjabi%20Songs%202020.mp3"
            MusicTrack.MUSTANG -> "https://p320.djpunjab.is/data/48/40359/284497/Mustang%20-%20Sidhu%20Moose%20Wala.mp3"
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
                            var loadedFromAsset = false
                            val assetManager = appContext?.assets
                            if (assetManager != null) {
                                try {
                                    val afd = assetManager.openFd("track_${currentTrack.name}.mp3")
                                    instance.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                    afd.close()
                                    isOfflinePlayback = true
                                    loadedFromAsset = true
                                    Log.d(TAG, "Successfully loaded track_${currentTrack.name}.mp3 from packaged assets!")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed loading track_${currentTrack.name}.mp3 from assets: ${e.message}")
                                }
                            }

                            if (!loadedFromAsset) {
                                val cachedFile = appContext?.let { context ->
                                    File(context.filesDir, "track_${currentTrack.name}.mp3")
                                }
                                if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 500 * 1024) {
                                    Log.d(TAG, "Playing from offline cache: ${cachedFile.absolutePath}")
                                    instance.setDataSource(cachedFile.absolutePath)
                                    isOfflinePlayback = true
                                } else {
                                    Log.d(TAG, "No cache: streaming from remote URL: $streamUrl")
                                    instance.setDataSource(streamUrl)
                                    isOfflinePlayback = false
                                }
                            }
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
                                Log.d(TAG, "Track loaded successfully! Playing audio.")
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
                        Log.e(TAG, "MediaPlayer playback failed: what=$what, extra=$extra. Fallback to synthesizer.")
                        synchronized(this@LobbyMusicPlayer) {
                            isStreamingActive = false
                            isOfflinePlayback = false
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
                                isOfflinePlayback = false
                            }
                        } else {
                            mp.release()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during media setup: ${e.message}")
                    isStreamingActive = false
                    isOfflinePlayback = false
                }
            }
        }
    }

    fun setTrackAndRestart(track: MusicTrack) {
        synchronized(this) {
            currentTrack = track
            isPlaying = false
            isStreamingActive = false
            isOfflinePlayback = false
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
                        MusicTrack.MUSTANG -> doubleArrayOf(
                            329.63, 329.63, 392.00, 329.63, 440.00, 392.00, 329.63, 293.66,
                            329.63, 329.63, 392.00, 329.63, 440.00, 440.00, 523.25, 440.00
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
