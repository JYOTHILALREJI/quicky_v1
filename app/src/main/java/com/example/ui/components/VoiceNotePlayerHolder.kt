package com.example.ui.components

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.io.File

/**
 * ============================================================================
 *  CLUB VOICE-NOTE PLAYER HOLDER (v3.3.4)
 * ============================================================================
 *
 * One shared [MediaPlayer] for the whole club chat screen (Compose can't
 * own Android players directly — the screen remembers ONE holder and every
 * voice bubble talks to it). State is exposed through [listener]; the UI
 * recomposes off those callbacks.
 *
 *   val player = remember { VoiceNotePlayerHolder() }
 *   player.play(context, file) { }   // or setOnStateListener before
 *
 * The caller downloads the note (SupabaseRepository.downloadClubVoiceNote)
 * to a cache file first, then hands that file over.
 */
class VoiceNotePlayerHolder {

    /** Render state for the currently-loaded note (null = nothing loaded). */
    data class PlayerState(
        val messageKey: String,
        val isPlaying: Boolean,
        val positionMs: Int,
        val durationMs: Int
    )

    private var player: MediaPlayer? = null
    private var currentKey: String? = null

    var onStateChange: ((PlayerState?) -> Unit)? = null

    private fun emit(playing: Boolean) {
        val p = player ?: return
        val key = currentKey ?: return
        onStateChange?.invoke(
            PlayerState(
                messageKey = key,
                isPlaying = playing,
                positionMs = p.currentPosition,
                durationMs = p.duration
            )
        )
    }

    /** Toggles playback for a given message key (message id + voice path). */
    fun toggle(context: Context, messageKey: String, audioFile: File?) {
        if (audioFile == null) return
        // Same note → pause/resume.
        if (messageKey == currentKey && player != null) {
            val p = player!!
            if (p.isPlaying) {
                p.pause()
                emit(false)
            } else {
                runCatching { p.start() }
                emit(true)
            }
            return
        }
        // Different note → swap.
        release()
        currentKey = messageKey
        player = MediaPlayer()
        try {
            player?.setDataSource(audioFile.absolutePath)
            player?.setOnCompletionListener {
                it.seekTo(0)
                emit(false)
            }
            player?.prepare()
            player?.start()
            emit(true)
        } catch (e: Exception) {
            Log.w(TAG, "Voice note playback failed: ${e.message}")
            release()
        }
    }

    /** True when this exact note is currently loaded (playing or paused). */
    fun isCurrent(messageKey: String): Boolean = messageKey == currentKey

    fun release() {
        runCatching { player?.release() }
        player = null
        currentKey = null
        onStateChange?.invoke(null)
    }

    private companion object {
        const val TAG = "QuickyVoice"
    }
}
