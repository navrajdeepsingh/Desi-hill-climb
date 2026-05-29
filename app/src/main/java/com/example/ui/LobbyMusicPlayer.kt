package com.example.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*

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
            cleanUpMediaPlayer()
        }
        start()
    }

    fun stop() {
        synchronized(this) {
            isPlaying = false
            isStreamingActive = false
            playerJob?.cancel()
            cleanUpMediaPlayer()
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
