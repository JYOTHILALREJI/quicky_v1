package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ============================================================================
 *  QUICKY PUSH SERVICE — FCM entry point (v3.4)
 * ============================================================================
 *
 * Registered in the manifest for `com.google.firebase.MESSAGING_EVENT`.
 * Two responsibilities:
 *
 *  1. [onNewToken] — token refresh → persist + mirror to Supabase device_tokens.
 *
 *  2. [onMessageReceived] — routes the payload to the appropriate typed
 *     [PushNotifications] helper, each of which posts on its own dedicated
 *     channel and respects the per-type preference toggle that SparkViewModel
 *     will surface in future via the device_tokens record.
 *
 * Payload shape (see PushNotifications for full reference):
 *   type        = match | message | like | super_like | club | club_mention
 *                 | truth_dare | promo
 *   title, body = display strings
 *   chat_id / match_id / club_id = routing ids
 *   sender_name  = shown in the MessagingStyle sender Person label
 *
 * Truth-or-Dare (`type=truth_dare`) is an in-app overlay only — no system
 * notification is posted here; the Supabase realtime subscription handles it.
 */
class QuickyPushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushNotifications.saveToken(applicationContext, token)
        Log.i(TAG, "New FCM registration token: $token")
        syncTokenToServer(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type       = data["type"]?.takeIf { it.isNotBlank() }
        val title      = data["title"] ?: message.notification?.title
        val body       = data["body"]  ?: message.notification?.body
        val chatId     = data["chat_id"] ?: data["match_id"]
        val clubId     = data["club_id"]
        val senderName = data["sender_name"]

        when (type) {
            PushNotifications.TYPE_MATCH ->
                PushNotifications.showMatch(this, title, body)

            PushNotifications.TYPE_LIKE ->
                PushNotifications.showLike(this, title, body)

            PushNotifications.TYPE_SUPER_LIKE ->
                PushNotifications.showSuperLike(this, title, body)

            PushNotifications.TYPE_CLUB ->
                PushNotifications.showClub(this, title, body, clubId, isMention = false)

            PushNotifications.TYPE_CLUB_MENTION ->
                PushNotifications.showClub(this, title, body, clubId, isMention = true)

            PushNotifications.TYPE_TRUTH_DARE ->
                PushNotifications.onTruthDareReceived(enabled = true)

            PushNotifications.TYPE_PROMO ->
                PushNotifications.showPromotion(this, title, body)

            // v3.3.8 — view-once Quicky Image snap
            PushNotifications.TYPE_SNAP ->
                PushNotifications.showQuicky(this, title, body, chatId, senderName)

            // Default: personal message (TYPE_MESSAGE or unknown)
            else ->
                PushNotifications.showMessage(this, title, body, chatId, senderName)
        }
    }

    companion object {
        private const val TAG = "QuickyPush"

        /**
         * Mirrors the persisted FCM token to Supabase for the currently
         * signed-in account (fire-and-forget). Called from [onNewToken] and
         * after every sign-in via SparkViewModel.adoptSession.
         */
        fun syncTokenToServer(context: Context) {
            val appCtx = context.applicationContext
            val token = PushNotifications.readToken(appCtx) ?: return
            val session = SupabaseAuth.readSession(appCtx) ?: return
            if (!SupabaseRepository.isConfigured()) return
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    SupabaseRepository.upsertDeviceToken(
                        userId      = session.userId,
                        fcmToken    = token,
                        accessToken = session.accessToken
                    )
                }.onSuccess {
                    Log.i(TAG, "FCM token mirrored to Supabase device_tokens")
                }.onFailure { e ->
                    Log.w(TAG, "device_tokens upsert failed: ${e.message}")
                }
            }
        }
    }
}
