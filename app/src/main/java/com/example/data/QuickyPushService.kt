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
 *  QUICKY PUSH SERVICE — FCM entry point (v3.3)
 * ============================================================================
 *
 * Registered in the manifest for `com.google.firebase.MESSAGING_EVENT`.
 * Two responsibilities only:
 *
 *  1. [onNewToken] — the FCM registration token changed (first launch,
 *     token rotation). It is persisted locally (survives restarts) and
 *     mirrored to the Supabase `device_tokens` table for the signed-in
 *     account, so server-side Edge Functions / triggers can send
 *     match / message / club alerts to THIS device. The token is also
 *     re-mirrored on every sign-in (SparkViewModel.adoptSession) —
 *     covering the account-switch case and the "token arrived before
 *     the user signed in" case.
 *
 *  2. [onMessageReceived] — an FCM message arrived while the app is in
 *     the foreground (data messages ALWAYS land here; `notification`
 *     payloads only while foregrounded). It is rendered by
 *     [PushNotifications.show] on the channel matching its `type`
 *     (matches / messages / clubs) with a quicky://notify deep link.
 *
 * While the app is backgrounded and the payload is a `notification`
 * message, FCM renders it itself using the
 * `default_notification_channel_id` manifest meta-data ("messages").
 */
class QuickyPushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushNotifications.saveToken(applicationContext, token)
        // Easier to grab for server-side testing (curl / FCM console).
        Log.i(TAG, "New FCM registration token: $token")
        syncTokenToServer(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"]?.takeIf { it.isNotBlank() }
        // Prefer the data payload; fall back to the notification payload
        // (FCM console test sends / foreground notification messages).
        val title = data["title"] ?: message.notification?.title
        val body = data["body"] ?: message.notification?.body
        val chatId = data["chat_id"] ?: data["match_id"]
        val clubId = data["club_id"]

        PushNotifications.show(this, type, title, body, chatId, clubId)
    }

    companion object {
        private const val TAG = "QuickyPush"

        /**
         * Mirrors the persisted FCM token to Supabase for the currently
         * signed-in account (fire-and-forget — offline just logs a warning;
         * the next sign-in retries). Safe to call from anywhere: the
         * service (token refresh), the ViewModel (post sign-in), or tests.
         */
        fun syncTokenToServer(context: Context) {
            val appCtx = context.applicationContext
            val token = PushNotifications.readToken(appCtx) ?: return
            // Not signed in yet — adoptSession re-pushes after sign-in.
            val session = SupabaseAuth.readSession(appCtx) ?: return
            if (!SupabaseRepository.isConfigured()) return
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    SupabaseRepository.upsertDeviceToken(
                        userId = session.userId,
                        fcmToken = token,
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
