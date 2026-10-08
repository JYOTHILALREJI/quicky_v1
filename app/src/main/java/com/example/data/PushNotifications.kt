package com.example.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.toArgb
import com.example.MainActivity
import com.example.R
import com.example.ui.theme.QuickyPink

/**
 * ============================================================================
 *  PUSH NOTIFICATIONS — Quicky (v3.3)
 * ============================================================================
 *
 * One home for everything notification-related on the client:
 *
 *  - The three Quicky notification channels (see [ensureChannels]):
 *      `matches`  — new matches & likes      (heads-up, high importance)
 *      `messages` — chat messages            (heads-up, high importance)
 *      `clubs`    — club invites / activity   (default importance)
 *  - The [POST_NOTIFICATIONS][Manifest.permission.POST_NOTIFICATIONS]
 *    runtime-permission helpers for Android 13+ (on Android 8–12
 *    notifications are enabled by default and only channels matter —
 *    there is no runtime permission to request below API 33).
 *  - FCM registration-token persistence (the token is ALSO mirrored to
 *    the Supabase `device_tokens` table so server-side Edge Functions
 *    can address this device — see [QuickyPushService]).
 *  - [show]: turns an incoming FCM message into a posted system
 *    notification whose tap deep-links back into the right screen via
 *    `quicky://notify?...` (routed by SparkViewModel).
 *
 * Expected FCM **data** payload shape (sent by the server with the
 * Firebase Admin SDK — plain FCM `notification` payloads are also
 * handled while the app is in the foreground):
 *
 * ```json
 * {
 *   "type": "match" | "message" | "club",
 *   "title": "New match!",
 *   "body":  "Anna matched with you too",
 *   "chat_id": "match_<uuid>",   // for type=message
 *   "club_id": "<uuid>"         // for type=club
 * }
 * ```
 */
object PushNotifications {

    private const val TAG = "QuickyPush"

    // ---- Channel ids (stable across releases — never rename) ----------
    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_MATCHES = "matches"
    const val CHANNEL_CLUBS = "clubs"

    // ---- Payload `type` discriminators ---------------------------------
    const val TYPE_MESSAGE = "message"
    const val TYPE_MATCH = "match"
    const val TYPE_CLUB = "club"

    // ---- Local persistence (device-scoped, survives restarts) ---------
    private const val PREFS = "quicky_push"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_PERM_ASKED = "notif_perm_asked"

    // ------------------------------------------------------------------
    // Notification channels (Android 8+; no-op below)
    // ------------------------------------------------------------------

    /**
     * Creates/updates the three Quicky channels. Idempotent — creating an
     * existing channel is a cheap no-op (only name/description updates
     * apply; importance is immutable after first creation, which is why
     * the ids above must stay stable).
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val messages = NotificationChannel(
            CHANNEL_MESSAGES, "New messages", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Chat messages from your matches"
            enableVibration(true)
        }
        val matches = NotificationChannel(
            CHANNEL_MATCHES, "New matches & likes", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "When someone likes you back"
            enableVibration(true)
        }
        val clubs = NotificationChannel(
            CHANNEL_CLUBS, "Club activity", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Invites and updates for clubs you follow"
        }
        manager.createNotificationChannel(messages)
        manager.createNotificationChannel(matches)
        manager.createNotificationChannel(clubs)
    }

    // ------------------------------------------------------------------
    // POST_NOTIFICATIONS runtime permission (Android 13+ only)
    // ------------------------------------------------------------------

    /**
     * True when the app may post notifications right now: pre-Android 13
     * grants notifications by default; Android 13+ needs the explicit
     * runtime permission.
     */
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * True when the one-time permission prompt should be shown: Android
     * 13+, permission still not granted, and the app has never asked
     * before (per-install flag — the user's "don't ask again" choice in
     * the system dialog is respected).
     */
    fun shouldRequestPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (canPostNotifications(context)) return false
        return !prefs(context).getBoolean(KEY_PERM_ASKED, false)
    }

    /** Records that the prompt was shown (called right after launching it). */
    fun markPermissionAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_PERM_ASKED, true).apply()
    }

    // ------------------------------------------------------------------
    // FCM registration token persistence
    // ------------------------------------------------------------------

    fun saveToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun readToken(context: Context): String? =
        prefs(context).getString(KEY_FCM_TOKEN, null)?.takeIf { it.isNotBlank() }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------
    // Deep link
    // ------------------------------------------------------------------

    /**
     * Builds the in-app deep link a notification tap should follow.
     * Parsed by `SparkViewModel.handleNotificationDeepLink`:
     *
     *   quicky://notify?target=chat&chat_id=<match id>
     *   quicky://notify?target=match
     *   quicky://notify?target=club&club_id=<club id>
     *   quicky://notify                (no target — plain app open)
     */
    fun buildDeepLink(type: String?, chatId: String?, clubId: String?): Uri {
        val builder = Uri.Builder().scheme("quicky").authority("notify")
        when (type) {
            TYPE_MATCH -> builder.appendQueryParameter("target", "match")
            TYPE_CLUB -> {
                builder.appendQueryParameter("target", "club")
                clubId?.let { builder.appendQueryParameter("club_id", it) }
            }
            else -> {
                builder.appendQueryParameter("target", "chat")
                chatId?.let { builder.appendQueryParameter("chat_id", it) }
            }
        }
        return builder.build()
    }

    // ------------------------------------------------------------------
    // Showing a notification
    // ------------------------------------------------------------------

    /**
     * Posts one push notification on the channel matching [type]. No-ops
     * (silently) when notifications are not permitted, so callers never
     * need their own guard. Each `type + chatId/clubId` pair gets its own
     * notification slot — two different chats never overwrite each other.
     */
    fun show(
        context: Context,
        type: String?,
        title: String?,
        body: String?,
        chatId: String? = null,
        clubId: String? = null
    ) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return

        val channel = when (type) {
            TYPE_MATCH -> CHANNEL_MATCHES
            TYPE_CLUB -> CHANNEL_CLUBS
            else -> CHANNEL_MESSAGES
        }
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: "Quicky"
        val resolvedBody = body?.takeIf { it.isNotBlank() } ?: "You have a new update"

        // Explicit intent (component + data URI) — the quicky:// scheme
        // intent-filter in the manifest only covers auth-callback, so
        // explicit targeting is what reliably reaches the singleTask
        // MainActivity both cold (onCreate) and warm (onNewIntent).
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = buildDeepLink(type, chatId, clubId)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            (type ?: "push").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_quicky)
            // Brand tint for the small icon / accent (rosewood).
            .setColor(QuickyPink.toArgb())
            .setContentTitle(resolvedTitle)
            .setContentText(resolvedBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(resolvedBody))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            // Heads-up + peeking for people-to-people alerts, quieter for clubs.
            .setPriority(
                if (type == TYPE_CLUB) NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_HIGH
            )
            .setCategory(
                if (type == TYPE_CLUB) NotificationCompat.CATEGORY_SOCIAL
                else NotificationCompat.CATEGORY_MESSAGE
            )
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val slotKey = (type ?: TYPE_MESSAGE) + "|" + (chatId ?: clubId ?: "")
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(slotKey, slotKey.hashCode(), notification)
            Analytics.log(
                Analytics.PUSH_NOTIFICATION_SHOWN,
                "type" to (type ?: TYPE_MESSAGE)
            )
        }.onFailure { Log.w(TAG, "Failed to post notification: ${it.message}") }
    }
}
