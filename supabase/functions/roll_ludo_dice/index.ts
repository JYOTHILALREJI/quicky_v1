// ============================================================================
// roll_ludo_dice — Quicky v2.1 §3.2.2 (server-authoritative dice)
//
// POST /functions/v1/roll_ludo_dice
//   body: { "match_id": "LA7K2QD" }
//   JWT:  caller must be an authenticated match participant
//   →     { "dice": 1..6 }
//
// The roll is generated server-side and written into ludo_game_state
// before being returned — the client only animates the result.
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
  auth: { persistSession: false },
});

function corsHeaders(): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers":
      "authorization, x-client-info, apikey, content-type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
  };
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders(), "Content-Type": "application/json" },
  });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders() });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  try {
    const authHeader = req.headers.get("Authorization") ?? "";
    const callerJwt = authHeader.replace("Bearer ", "");
    if (!callerJwt) return json({ error: "unauthorized" }, 401);

    // Identify the caller from their JWT.
    const callerId = await admin.auth.getUser(callerJwt)
      .then((r) => r.data.user?.id)
      .catch(() => undefined);
    if (!callerId) return json({ error: "unauthorized" }, 401);

    const { match_id: matchId } = await req.json().catch(() => ({}));
    if (typeof matchId !== "string" || !matchId) {
      return json({ error: "match_id_required" }, 400);
    }

    // Lock + load the state row.
    const stateRes = await admin
      .from("ludo_game_state")
      .select("board_state, turn")
      .eq("match_id", matchId)
      .single();
    const state = stateRes.data;
    if (!state) return json({ error: "match_not_found" }, 404);

    const board = state.board_state;
    const players = board?.players ?? [];
    const turnIndex = board?.turn_index ?? 0;
    const phase = board?.phase ?? "AWAITING_ROLL";
    if (phase === "FINISHED") return json({ error: "match_finished" }, 409);

    // Only the player whose turn it is may roll (hosts may roll for bots).
    const current = players[turnIndex];
    const isHost = players[0]?.id === callerId;
    if (!current) return json({ error: "invalid_state" }, 409);
    if (current.id !== callerId && !(isHost && current.is_bot)) {
      return json({ error: "not_your_turn" }, 403);
    }

    // Server-side random roll.
    const dice = 1 + Math.floor(Math.random() * 6);

    const consecutive = dice === 6 ? (board?.consecutive_sixes ?? 0) + 1 : 0;
    let nextPhase = "AWAITING_MOVE";
    let nextTurnIndex = turnIndex;
    let nextConsecutive = consecutive;
    let statusText = `${current.name} rolled ${dice} — pick a token`;

    // Three consecutive sixes forfeit the turn.
    if (dice === 6 && consecutive >= 3) {
      nextTurnIndex = (turnIndex + 1) % players.length;
      nextConsecutive = 0;
      nextPhase = "AWAITING_ROLL";
      statusText = `${current.name} rolled a third 6 — turn forfeited!`;
    }

    const updated = {
      ...board,
      dice_value: dice,
      phase: nextPhase,
      turn_index: nextTurnIndex,
      consecutive_sixes: nextConsecutive,
      status_text: statusText,
    };

    await admin
      .from("ludo_game_state")
      .update({
        board_state: updated,
        dice_value: dice,
        turn: nextTurnIndex,
        updated_at: new Date().toISOString(),
      })
      .eq("match_id", matchId);

    return json({ dice });
  } catch (e) {
    return json({ error: "internal_error", detail: String(e) }, 500);
  }
});
