package com.example.data

import android.util.Log

/**
 * Lightweight product-analytics shim (PRD v3.2 §29).
 *
 * The app currently ships WITHOUT a third-party analytics SDK, but the PRD
 * requires named events around the changed interactions. This object gives
 * every call-site a single, dependency-free funnel:
 *
 *   Analytics.log(Analytics.LUDO_DICE_ROLL_COMPLETED, "roll_result" to 6)
 *
 * Events are written to Logcat under the `QuickyAnalytics` tag, with the
 * event name + sorted properties on one line, e.g.
 *
 *   I/QuickyAnalytics: ludo_dice_roll_completed {player_id=user_me, roll_result=6}
 *
 * When a real SDK (Firebase Analytics / Amplitude / …) is wired in, ONLY this
 * file changes — every call site keeps the same signature. The constant
 * strings below are the canonical PRD §29 event names; timing properties
 * (roll_result, movement_duration, turn_handoff_delay, player_id, game_id)
 * are attached at the call sites that own those values.
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

    /** Logs one structured event; `props` are key/value pairs (any order). */
    fun log(event: String, vararg props: Pair<String, Any?>) {
        if (props.isEmpty()) {
            Log.i(TAG, event)
        } else {
            val body = props.joinToString(", ") { (k, v) -> "$k=$v" }
            Log.i(TAG, "$event {$body}")
        }
    }
}
