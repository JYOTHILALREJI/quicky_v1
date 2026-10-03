package com.example.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.math.min

/**
 * ============================================================================
 * LUDO REALTIME — Quicky v3 (PRD §14/§15/§54)
 *
 * Supabase Realtime subscription for an active Ludo match, implemented with
 * the Phoenix websocket wire protocol on top of OkHttp (already a project
 * dependency — no extra SDK needed).
 *
 *   Player A → Edge Function → DB transaction
 *               ↓ (postgres_changes UPDATE)
 *           Players A/B/C/D  ← this class
 *
 * One socket carries both channels for the active match:
 *   - realtime:public:ludo_game_state     (UPDATE → authoritative board)
 *   - realtime:public:ludo_chat_messages  (INSERT → room chat)
 *
 * Polling remains ONLY as a recovery fallback in the ViewModel — realtime
 * is the primary synchronization path during normal play.
 *
 * Ordering / recovery (PRD §69/§70): the board_state JSON carries a
 * monotonic `seq`; consumers must ignore events with seq <= last applied
 * and re-fetch the authoritative state when they detect a gap.
 * ============================================================================
 */
object LudoRealtime {

    /** Callbacks arrive on OkHttp worker threads — dispatch to your own
     *  thread/scope before touching UI state. */
    interface Listener {
        /** Authoritative ludo_game_state row (the full record incl. board_state). */
        fun onGameState(row: JSONObject)

        /** Freshly inserted ludo_chat_messages row. */
        fun onChatMessage(row: JSONObject)

        /** Socket lifecycle: connected / reconnected / lost. */
        fun onConnection(online: Boolean)
    }

    private val socketClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // websockets stream forever
            .pingInterval(20, TimeUnit.SECONDS)     // transport-level keepalive
            .build()
    }

    private val scheduler: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "ludo-realtime-heartbeat").apply { isDaemon = true }
        }
    }

    /** Opaque handle — [close] tears the subscription down. */
    class Subscription internal constructor(private val impl: RealtimeSocket) {
        fun close() = impl.shutdown()
    }

    fun subscribe(
        matchCode: String,
        accessToken: String?,
        listener: Listener
    ): Subscription {
        val socket = RealtimeSocket(matchCode, accessToken, listener)
        socket.connect()
        return Subscription(socket)
    }

    // ------------------------------------------------------------------
    // Socket implementation
    // ------------------------------------------------------------------

    private class RealtimeSocket(
        private val matchCode: String,
        private var accessToken: String?,
        private val listener: Listener
    ) {
        private var webSocket: WebSocket? = null
        private val heartbeat = java.util.concurrent.atomic.AtomicBoolean(false)
        private val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        private val refCounter = java.util.concurrent.atomic.AtomicLong(0)
        private var reconnectAttempt = 0

        private val stateTopic = "realtime:public:${SupabaseConfig.TABLE_LUDO_GAME_STATE}"
        private val chatTopic = "realtime:public:${SupabaseConfig.TABLE_LUDO_CHAT_MESSAGES}"

        fun connect() {
            if (closed.get()) return
            val url = buildString {
                append(SupabaseConfig.SUPABASE_URL.replace("https://", "wss://").replace("http://", "ws://"))
                append("/realtime/v1/websocket?apikey=")
                append(SupabaseConfig.SUPABASE_ANON_KEY)
                append("&vsn=1.0.0")
            }
            val request = Request.Builder().url(url).build()
            webSocket = socketClient.newWebSocket(request, SocketListener())
        }

        fun shutdown() {
            if (!closed.compareAndSet(false, true)) return
            heartbeat.set(false)
            runCatching { webSocket?.close(1000, "client closed") }
            webSocket = null
            listener.onConnection(false)
        }

        private fun nextRef(): String = refCounter.incrementAndGet().toString()

        private inner class SocketListener : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (closed.get()) return
                reconnectAttempt = 0
                // 1) JWT for RLS-protected channels.
                accessToken?.let { token ->
                    send(
                        JSONObject()
                            .put("topic", "phoenix")
                            .put("event", "access_token")
                            .put("payload", JSONObject().put("access_token", token))
                            .put("ref", nextRef())
                    )
                }
                // 2) Authoritative game-state channel (UPDATE only).
                send(
                    JSONObject()
                        .put("topic", stateTopic)
                        .put("event", "phx_join")
                        .put(
                            "payload",
                            JSONObject().put(
                                "config",
                                JSONObject().put(
                                    "postgres_changes",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("event", "UPDATE")
                                            .put("schema", "public")
                                            .put("table", SupabaseConfig.TABLE_LUDO_GAME_STATE)
                                            .put("filter", "match_id=eq.$matchCode")
                                    )
                                )
                            )
                        )
                        .put("ref", nextRef())
                )
                // 3) Room-chat channel (INSERT only) — realtime chat, no polling.
                send(
                    JSONObject()
                        .put("topic", chatTopic)
                        .put("event", "phx_join")
                        .put(
                            "payload",
                            JSONObject().put(
                                "config",
                                JSONObject().put(
                                    "postgres_changes",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("event", "INSERT")
                                            .put("schema", "public")
                                            .put("table", SupabaseConfig.TABLE_LUDO_CHAT_MESSAGES)
                                            .put("filter", "match_id=eq.$matchCode")
                                    )
                                )
                            )
                        )
                        .put("ref", nextRef())
                )
                // 4) Phoenix heartbeat every 25s keeps the socket alive.
                //    The task reads the CURRENT [webSocket] field so it keeps
                //    working across automatic reconnects.
                if (heartbeat.compareAndSet(false, true)) {
                    scheduler.scheduleWithFixedDelay(
                        { runCatching { sendHeartbeat() } },
                        25, 25, MILLISECONDS
                    )
                }
                listener.onConnection(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (closed.get()) return
                val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (msg.optString("event")) {
                    "postgres_changes" -> handlePostgresChanges(msg.optJSONObject("payload"))
                    // phx_reply / presence / system messages are informational.
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (closed.get()) return
                listener.onConnection(false)
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (closed.get()) return
                listener.onConnection(false)
                scheduleReconnect()
            }
        }

        private fun handlePostgresChanges(payload: JSONObject?) {
            val data = payload?.optJSONObject("data") ?: return
            val table = data.optString("table")
            val record = data.optJSONObject("record") ?: return
            when (table) {
                SupabaseConfig.TABLE_LUDO_GAME_STATE -> listener.onGameState(record)
                SupabaseConfig.TABLE_LUDO_CHAT_MESSAGES -> listener.onChatMessage(record)
            }
        }

        /** Sends a Phoenix heartbeat over the CURRENT socket (survives reconnects). */
        private fun sendHeartbeat() {
            if (closed.get()) return
            send(
                JSONObject()
                    .put("topic", "phoenix")
                    .put("event", "heartbeat")
                    .put("payload", JSONObject())
                    .put("ref", nextRef())
            )
        }

        private fun send(message: JSONObject) {
            webSocket?.send(message.toString())
        }

        private fun scheduleReconnect() {
            if (closed.get()) return
            val delayMs = min(1000L shl reconnectAttempt.coerceAtMost(4), 15_000L)
            reconnectAttempt += 1
            scheduler.schedule({
                if (!closed.get()) connect()
            }, delayMs, MILLISECONDS)
        }
    }
}
