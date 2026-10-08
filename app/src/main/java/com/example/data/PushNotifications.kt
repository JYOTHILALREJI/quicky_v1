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
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.theme.QuickyGold
import com.example.ui.theme.QuickyPink
import com.example.ui.theme.QuickyPurple

/**
 * ============================================================================
 *  PUSH NOTIFICATIONS — Quicky (v3.4)
 * ============================================================================
 *
 * Six independent notification channels — one per event type:
 *
 *   matches     — new mutual match       (IMPORTANCE_HIGH, heads-up)
 *   messages    — personal 1-to-1 chat   (IMPORTANCE_HIGH, inline reply)
 *   likes       — someone liked you      (IMPORTANCE_HIGH, heads-up)
 *   super_likes — Super Like received    (IMPORTANCE_HIGH, heads-up)
 *   clubs       — club mentions/invites  (IMPORTANCE_DEFAULT)
 *   promotions  — Quicky offers          (IMPORTANCE_LOW)
 *
 * Truth-or-Dare challenges are delivered as in-app overlays (handled by the
 * ViewModel / UI layer) — they do not post a system notification. The
 * per-channel [show] helpers gate on the caller-supplied preference boolean
 * so the Settings toggle is respected without a round-trip to the server.
 *
 * FCM data payload shape (server → device):
 *
 * ```json
 * {
 *   "type":    "match" | "message" | "like" | "super_like" | "club" | "club_mention" | "promo",
 *   "title":   "...",
 *   "body":    "...",
 *   "chat_id": "...",   // message / match types
 *   "club_id": "...",   // club / club_mention types
 *   "sender_name": "..." // for inline-reply Person label
 * }
 * ```
 */
object PushNotifications {

    private const val TAG = "QuickyPush"

    // ---- Stable channel ids (never rename — users may have customised them) ----
    const val CHANNEL_MESSAGES   = "messages"
    const val CHANNEL_MATCHES    = "matches"
    const val CHANNEL_LIKES      = "likes"
    const val CHANNEL_SUPER_LIKES = "super_likes"
    const val CHANNEL_CLUBS      = "clubs"
    const val CHANNEL_PROMOTIONS = "promotions"

    // ---- Payload `type` discriminators (mirror the server constants) -----------
    const val TYPE_MESSAGE     = "message"
    const val TYPE_MATCH       = "match"
    const val TYPE_LIKE        = "like"
    const val TYPE_SUPER_LIKE  = "super_like"
    const val TYPE_CLUB        = "club"
    const val TYPE_CLUB_MENTION = "club_mention"
    const val TYPE_TRUTH_DARE  = "truth_dare"   // in-app only — never posts a notif
    const val TYPE_PROMO       = "promo"

    // ---- RemoteInput key (inline reply for message notifications) --------------
    const val KEY_REPLY_TEXT = "quicky_reply_text"

    // ---- Local persistence (device-scoped, survives restarts) -----------------
    private const val PREFS       = "quicky_push"
    private const val KEY_FCM_TOKEN  = "fcm_token"
    private const val KEY_PERM_ASKED = "notif_perm_asked"

    // ---------------------------------------------------------------------------
    // Notification channels (Android 8+; no-op below O)
    // ---------------------------------------------------------------------------

    /**
     * Creates / updates all Quicky channels. Idempotent — existing channels are
     * cheaply ignored; importance is locked after first creation, which is why
     * the ids above must stay stable across releases.
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        fun channel(
            id: String,
            name: String,
            desc: String,
            importance: Int,
            vibrate: Boolean = true
        ) = NotificationChannel(id, name, importance).also {
            it.description = desc
            if (vibrate) it.enableVibration(true)
        }

        manager.createNotificationChannels(
            listOf(
                channel(
                    CHANNEL_MESSAGES, "Messages",
                    "Personal chat messages from your matches",
                    NotificationManager.IMPORTANCE_HIGH
                ),
                channel(
                    CHANNEL_MATCHES, "New Matches",
                    "When someone matches back with you",
                    NotificationManager.IMPORTANCE_HIGH
                ),
                channel(
                    CHANNEL_LIKES, "Likes",
                    "When someone likes your profile",
                    NotificationManager.IMPORTANCE_HIGH
                ),
                channel(
                    CHANNEL_SUPER_LIKES, "Super Likes",
                    "When someone Super Likes you",
                    NotificationManager.IMPORTANCE_HIGH
                ),
                channel(
                    CHANNEL_CLUBS, "Clubs & Mentions",
                    "Club invites, activity and @mentions",
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                channel(
                    CHANNEL_PROMOTIONS, "Promotions & Offers",
                    "Quicky Gold deals and feature announcements",
                    NotificationManager.IMPORTANCE_LOW,
                    vibrate = false
                )
            )
        )
    }

    // ---------------------------------------------------------------------------
    // POST_NOTIFICATIONS runtime permission (Android 13+ only)
    // ---------------------------------------------------------------------------

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun shouldRequestPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (canPostNotifications(context)) return false
        return !prefs(context).getBoolean(KEY_PERM_ASKED, false)
    }

    fun markPermissionAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_PERM_ASKED, true).apply()
    }

    // ---------------------------------------------------------------------------
    // FCM registration token persistence
    // ---------------------------------------------------------------------------

    fun saveToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun readToken(context: Context): String? =
        prefs(context).getString(KEY_FCM_TOKEN, null)?.takeIf { it.isNotBlank() }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------------------
    // Deep link builder
    // ---------------------------------------------------------------------------

    /**
     * quicky://notify?target=chat&chat_id=<id>
     * quicky://notify?target=match
     * quicky://notify?target=club&club_id=<id>
     * quicky://notify?target=likes
     * quicky://notify?target=super_like
     * quicky://notify               (plain app open)
     */
    fun buildDeepLink(type: String?, chatId: String?, clubId: String?): Uri {
        val b = Uri.Builder().scheme("quicky").authority("notify")
        when (type) {
            TYPE_MATCH       -> b.appendQueryParameter("target", "match")
            TYPE_LIKE        -> b.appendQueryParameter("target", "likes")
            TYPE_SUPER_LIKE  -> b.appendQueryParameter("target", "super_like")
            TYPE_CLUB, TYPE_CLUB_MENTION -> {
                b.appendQueryParameter("target", "club")
                clubId?.let { b.appendQueryParameter("club_id", it) }
            }
            else -> {
                b.appendQueryParameter("target", "chat")
                chatId?.let { b.appendQueryParameter("chat_id", it) }
            }
        }
        return b.build()
    }

    // ---------------------------------------------------------------------------
    // Granular show helpers — one per notification type
    // ---------------------------------------------------------------------------

    /** Posts a New Match notification. Respects [enabled] preference toggle. */
    fun showMatch(context: Context, title: String?, body: String?, enabled: Boolean = true) {
        if (!enabled) return
        ensureChannels(context)
        if (!canPostNotifications(context)) return
        val contentIntent = tapIntent(context, TYPE_MATCH, null, null)
        val notif = NotificationCompat.Builder(context, CHANNEL_MATCHES)
            .setSmallIcon(R.drawable.ic_stat_quicky)
            .setColor(QuickyPink.toArgb())
            .setContentTitle(title ?: "It's a Match! 💖")
            .setContentText(body ?: "You matched with someone new!")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body ?: "You matched with someone new!"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(contentIntent)
            .build()
        postSafely(context, "match|", notif)
    }

    /**
     * Posts a personal Message notification with an inline Reply action so the
     * user can respond without opening the app (WhatsApp-style).
     */
    fun showMessage(
        context: Context,
        title: String?,
        body: String?,
        chatId: String?,
        senderName: String?,
        enabled: Boolean = true
    ) {
        if (!enabled) return
        ensureChannels(context)
        if (!canPostNotifications(context)) return
        val contentIntent = tapIntent(context, TYPE_MESSAGE, chatId, null)

        // RemoteInput: inline-reply action on Android 7+
        val replyIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.ACTION_REPLY"
            data = buildDeepLink(TYPE_MESSAGE, chatId, null)
        }
        val replyPendingIntent = PendingIntent.getActivity(
            context,
            ("reply|$chatId").hashCode(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel("Reply to ${senderName ?: "message"}…")
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_quicky,
            "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        // MessagingStyle for a richer, WhatsApp-like heads-up bubble
        val person = Person.Builder()
            .setName(senderName ?: "Someone")
            .setImportant(true)
            .build()
        val msgStyle = NotificationCompat.MessagingStyle(
            Person.Builder().setName("Me").build()
        ).addMessage(body ?: "", System.currentTimeMillis(), person)

        val notif = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_quicky)
            .setColor(QuickyPurple.toArgb())
            .setStyle(msgStyle)
            .setContentTitle(title ?: senderName ?: "New Message")
            .setContentText(body ?: "You have a new message")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(contentIntent)
            .addAction(replyAction)
            .build()
        postSafely(context, "msg|${chatId ?: ""}", notif)
    }

    /** Posts a Like notification. Respects [enabled] preference toggle. */
    fun showLike(context: Context, title: String?, body: String?, enabled: Boolean = true) {
        if (!enabled) return
        ensureChannels(context)
        if (!canPostNotifications(context)) return
        val contentIntent = tapIntent(context, TYPE_LIKE, null, null)
        val notif = NotificationCompat.Builder(context, CHANNEL_LIKES)
            .setSmallIcon(R.drawable.ic_stat_quicky)
            .setColor(QuickyPink.toArgb())
            .setContentTitle(title ?: "Someone likes you! ❤️")
            .setContentText(body ?: "Open Quicky to see who liked you")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(contentIntent)
            .build()
        postSafely(context, "like|", notif)
    }

    /** Posts a Super Like notification. Respects [enabled] preference toggle. */
    fun showSuperLike(context: Context, title: String?, body: String?, enabled: Boolean = true) {
        if (!enabled) return
        ensureChannels(context)
        if (!canPostNotifications(context)) return
        val contentIntent = tapIntent(context, TYPE_SUPER_LIKE, null, null)
        val notif = NotificationCompat.Builder(context, CHANNEL_SUPER_LIKES)
            .setSmallIcon(R.drawable.ic_stat_quicky)
            .setColor(QuickyGold.toArgb())
            .setContentTitle(title ?: "You got a Super Like! ⭐")
            .setContentText(body ?: "Someone Super Liked you — open Quicky to see who")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(contentIntent)
            .build()
        postSafely(context, "super_like|", notif)
    }

    /** Posts a Club notification. Respects [enabled] preference toggle. */
    fun showClub(
        context: Context,
        title: String?,
        body: String?,
        clubId: String?,
        isMention: Boolean = false,
        mentionsEnabled: Boolean = true,
        clubsEnabled: Boolean = true
    ) {
        // Club-mention filtering: if it's a mention, also check the mentions toggle
        if (isMention && !mentionsEnabled) return
        if (!clubsEnabled) return
        ensureChannels(context)
        if (!canPostNotifications(context)) return
        val type = if (isMention) TYPE_CLUB_MENTION else TYPE_CLUB
        val contentIntent = tapIntent(context, type, null, clubId)
        val notif = NotificationCompat.Builder(context, CHANNEL_CLUBS)
            .setSmallIcon(R.drawable.ic_stat_quicky)
            .setColor(QuickyPurple.toArgb())
            .setContentTitle(title ?: if (isMention) "Someone mentioned you 📣" else "Club update")
            .setContentText(body ?: "Check your clubs for the latest activity")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_LIGHTS)
            .setContentIntent(contentIntent)
            .build()
        postSafely(context, "club|${clubId ?: ""}", notif)
    }

    /** Posts a Promotions notification. Respects [enabled] preference toggle. */
    fun showPromotion(context: Context, title: String?, body: String?, enabled: Boolean = false) {
        if (!enabled) return
        ensureChannels(context)
        if (!canPostNotifications(context)) return
        val contentIntent = tapIntent(context, TYPE_PROMO, null, null)
        val notif = NotificationCompat.Builder(context, CHANNEL_PROMOTIONS)
            .setSmallIcon(R.drawable.ic_stat_quicky)
            .setColor(QuickyGold.toArgb())
            .setContentTitle(title ?: "Quicky Gold offer")
            .setContentText(body ?: "Open Quicky to see today's deal")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROMO)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        postSafely(context, "promo|", notif)
    }

    /**
     * Truth-or-Dare is delivered as an in-app overlay only (no system notification).
     * This stub is intentionally a no-op — the ViewModel drives the in-app overlay
     * via the real-time subscription that arrives on the `truth_dare` type.
     */
    fun onTruthDareReceived(enabled: Boolean) {
        // Handled entirely in SparkViewModel via Supabase realtime.
        // The `truthOrDare` NotificationPreferences field is read by the ViewModel
        // to decide whether to show the in-app overlay.
        Log.d(TAG, "Truth-or-Dare in-app overlay dispatched (prefs.enabled=$enabled)")
    }

    // ---------------------------------------------------------------------------
    // Legacy compatibility — routes old single-type payloads to the right channel
    // ---------------------------------------------------------------------------

    /**
     * Compatibility entry-point for payloads that only specify `type` without
     * a dedicated helper being called. Delegates to the appropriate typed helper
     * with all preferences defaulting to enabled.
     */
    fun show(
        context: Context,
        type: String?,
        title: String?,
        body: String?,
        chatId: String? = null,
        clubId: String? = null,
        senderName: String? = null
    ) {
        when (type) {
            TYPE_MATCH       -> showMatch(context, title, body)
            TYPE_LIKE        -> showLike(context, title, body)
            TYPE_SUPER_LIKE  -> showSuperLike(context, title, body)
            TYPE_CLUB        -> showClub(context, title, body, clubId, isMention = false)
            TYPE_CLUB_MENTION -> showClub(context, title, body, clubId, isMention = true)
            TYPE_TRUTH_DARE  -> { /* in-app only — no system notification */ }
            TYPE_PROMO       -> showPromotion(context, title, body)
            else             -> showMessage(context, title, body, chatId, senderName)
        }
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private fun tapIntent(
        context: Context,
        type: String?,
        chatId: String?,
        clubId: String?
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = buildDeepLink(type, chatId, clubId)
        }
        return PendingIntent.getActivity(
            context,
            (type ?: "push").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun postSafely(context: Context, slotKey: String, notification: android.app.Notification) {
        runCatching {
            NotificationManagerCompat.from(context).notify(slotKey, slotKey.hashCode(), notification)
            Analytics.log(Analytics.PUSH_NOTIFICATION_SHOWN, "slot" to slotKey)
        }.onFailure { Log.w(TAG, "Failed to post notification '$slotKey': ${it.message}") }
    }
}
