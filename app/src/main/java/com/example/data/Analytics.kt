package com.example.data

import android.os.Bundle
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics

/**
 * Lightweight product-analytics shim (PRD v3.2 §29).
 *
 * Every call-site uses one funnel:
 *
 *   Analytics.log(Analytics.LUDO_DICE_ROLL_COMPLETED, "roll_result" to 6)
 *
 * Events are written to Logcat under the `QuickyAnalytics` tag, with the
 * event name + sorted properties on one line, e.g.
 *
 *   I/QuickyAnalytics: ludo_dice_roll_completed {player_id=user_me, roll_result=6}
 *
 * v3.3: with google-services.json + firebase-analytics now wired in,
 * every event is ALSO forwarded to Firebase Analytics (same event name,
 * stringified properties — Firebase Analytics has no numeric params).
 * The Firebase side degrades to a no-op whenever Firebase isn't
 * available (unit tests, missing config), so the Logcat funnel above
 * stays the source of truth for local debugging.
 *
 * The constant strings below are the canonical PRD §29 event names;
 * timing properties (roll_result, movement_duration, turn_handoff_delay,
 * player_id, game_id) are attached at the call sites that own those
 * values.
 */
object Analytics {

    private const val TAG = "QuickyAnalytics"

    // ---- Matches (PRD §29) ----
    const val MATCH_CHAT_CLICKED = "match_chat_clicked"
    const val MATCH_TRUTH_OR_DARE_CLICKED = "match_truth_or_dare_clicked"

    // ---- Games (PRD §29) ----
    const val TRUTH_OR_DARE_OPENED = "truth_or_dare_opened"
    const val TRUTH_OR_DARE_PROMPT_SELECTED = "truth_or_dare_prompt_selected"
    const val CUSTOM_QUESTION_SELECTED = "custom_question_selected"
    const val PREMIUM_GAMES_OPENED = "premium_games_opened"

    // ---- Ludo (PRD §29) ----
    const val LUDO_DICE_ROLL_STARTED = "ludo_dice_roll_started"
    const val LUDO_DICE_ROLL_COMPLETED = "ludo_dice_roll_completed"
    const val LUDO_COIN_MOVE_STARTED = "ludo_coin_move_started"
    const val LUDO_COIN_MOVE_COMPLETED = "ludo_coin_move_completed"
    const val LUDO_TURN_HANDOFF = "ludo_turn_handoff"

    // ---- Safety (Settings > Privacy > Blocked Users) ----
    const val SAFETY_USER_BLOCKED = "safety_user_blocked"
    const val SAFETY_USER_UNBLOCKED = "safety_user_unblocked"

    // ---- Push notifications (v3.3) ----
    const val PUSH_NOTIFICATION_SHOWN = "push_notification_shown"
    const val PUSH_NOTIFICATION_TAPPED = "push_notification_tapped"
    const val PUSH_PERMISSION_RESULT = "push_permission_result"

    /**
     * Firebase Analytics forwarder — null when Firebase isn't available
     * (unit tests / missing google-services config); never crashes the
     * caller either way.
     */
    private val firebase: FirebaseAnalytics? by lazy {
        runCatching { Firebase.analytics }.getOrNull()
    }

    /** Logs one structured event; `props` are key/value pairs (any order). */
    fun log(event: String, vararg props: Pair<String, Any?>) {
        if (props.isEmpty()) {
            Log.i(TAG, event)
        } else {
            val body = props.joinToString(", ") { (k, v) -> "$k=$v" }
            Log.i(TAG, "$event {$body}")
        }
        firebase?.let { fa ->
            runCatching {
                val params = Bundle().apply {
                    props.forEach { (k, v) -> putString(k, v?.toString()) }
                }
                fa.logEvent(event, params)
            }
        }
    }
}
