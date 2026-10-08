package com.example.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * ============================================================================
 *  CLUB VOICE-NOTE RECORDER (v3.3.4)
 * ============================================================================
 *
 * Real microphone capture for club chat (all members — no premium gate in
 * clubs). Wraps [MediaRecorder] into start/stop/elapsed primitives that a
 * Compose screen can drive:
 *
 *   val recorder = remember { VoiceNoteRecorder(context) }
 *   recorder.start()                  // → records AAC (.m4a) into cacheDir
 *   ...                               // screen shows recorder.elapsedSeconds()
 *   recorder.stop()                   // → RecordedNote(file, bytes, seconds)
 *   recorder.discard()                // → wipes the temp file
 *
 * RECORD_AUDIO runtime permission must be granted before [start] — the
 * caller owns the permission ask (screens already use this pattern for
 * POST_NOTIFICATIONS). Every method is fail-safe: a recorder that cannot
 * start just reports zero seconds and stop() returns null, so the UI can
 * fall back to a toast instead of crashing.
 */
class VoiceNoteRecorder(private val context: Context) {

    /** One finished take — ready to upload. */
    data class RecordedNote(
        val bytes: ByteArray,
        val durationSeconds: Int
    )

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    /** Seconds since [start] — 0 while idle (rounded UP, WhatsApp-style). */
    fun elapsedSeconds(): Int {
        if (startedAtMs == 0L) return 0
        return (((System.currentTimeMillis() - startedAtMs) / 1000L) + 1L).toInt()
    }

    /**
     * Begins recording to a private cache file. Returns false (and cleans
     * up) when the recorder cannot start — mic busy, no permission, etc.
     */
    fun start(): Boolean {
        if (recorder != null) return true // already recording
        return runCatching {
            val dir = context.cacheDir
            val file = File(dir, "quicky_voice_${System.currentTimeMillis()}.m4a")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setAudioChannels(1)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()

            recorder = r
            outputFile = file
            startedAtMs = System.currentTimeMillis()
            true
        }.onFailure { e ->
            Log.w(TAG, "Voice recording failed to start: ${e.message}")
            runCatching { recorder?.release() }
            recorder = null
            outputFile?.delete()
            outputFile = null
            startedAtMs = 0L
        }.getOrDefault(false)
    }

    /**
     * Finishes the take and returns it. Null when nothing was captured (or
     * the file came out empty) — callers show a "try again" toast.
     */
    fun stop(): RecordedNote? {
        val r = recorder ?: return null
        val file = outputFile
        val seconds = elapsedSeconds()
        // stop() throws when less than ~1s was captured — the take is
        // unusable then, but we still must release the recorder below.
        runCatching { r.stop() }
        runCatching { r.release() }
        recorder = null
        startedAtMs = 0L
        outputFile = null
        if (file == null || !file.exists()) return null
        val bytes = runCatching { file.readBytes() }.getOrNull()
        val note = if (bytes != null && bytes.isNotEmpty() && seconds >= 1) {
            RecordedNote(bytes = bytes, durationSeconds = seconds)
        } else {
            file.delete()
            null
        }
        return note
    }

    /** Aborts without producing anything. */
    fun discard() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        startedAtMs = 0L
        outputFile?.delete()
        outputFile = null
    }

    private companion object {
        const val TAG = "QuickyVoice"
    }
}
