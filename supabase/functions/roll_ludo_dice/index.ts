// ============================================================================
// roll_ludo_dice — Quicky v3 (server-authoritative dice + 10s roll timer)
//
// POST /functions/v1/roll_ludo_dice
//   body: { "match_id": "LA7K2QD", "auto": false }
//   JWT:  caller must be the player whose turn it is (or the host driving
//         a bot seat)
//   →     { "dice": 1..6, "phase": "...", "auto": false, "board_state": {...} }
//
// Server-authoritative flow (v3 PRD §4–§7, §30, §31):
//   1. Authenticate the caller (JWT → uid).
//   2. Load the match + state row.
//   3. Verify the match has 4 seated players (MATCH_STARTED, PRD §15).
//   4. Verify the caller is the current player (or host-for-bot).
//   5. Verify the phase is AWAITING_ROLL (idempotent re-requests get the
//      current dice back instead of double-rolling).
//   6. Timer check: a request that arrives AFTER the 10s deadline is
//      treated as the AUTOMATIC roll (PRD §6) — the player is never
//      skipped, the server rolls for them.
//   7. Generate the dice server-side, enforce the three-six rule.
//   8. Legal moves exist → AWAITING_MOVE + 30s move deadline; otherwise the
//      turn passes with a fresh 10s roll deadline (also skipping players
//      who already finished all 4 coins).
//   9. Bump `seq` by exactly 1 — every realtime consumer keys on it
//      (ordering, PRD §69) and the DB UPDATE broadcasts the new state to
//      every connected device via postgres_changes.
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
  auth: { persistSession: false },
});

const ROLL_WINDOW_MS = 10_000;
const MOVE_WINDOW_MS = 30_000;
// Small grace so devices with mild clock skew can still complete their
// manual action right at the buzzer without being flagged "late".
const TIMER_GRACE_MS = 3_000;

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

interface Token { stepCount: number }
interface Player {
  id: string;
  name: string;
  seat: number;
  is_bot: boolean;
  tokens: number[];
  score: number;
  finish_position: number | null;
}
interface Board {
  players: Player[];
  turn_index: number;
  dice_value: number | null;
  phase: string;
  consecutive_sixes: number;
  winner_id: string | null;
  status_text: string;
  seq: number;
  completed_players: number;
  finish_order: string[];
  roll_deadline_at: string | null;
  move_deadline_at: string | null;
}

// ----- Board constants (mirrors com.example.game.LudoEngine) -----
const START_INDEX = [0, 13, 26, 39];
const STAR_CELLS = new Set([8, 21, 34, 47]);
const SAFE_CELLS = new Set([...START_INDEX, ...STAR_CELLS]);
const HOME_ENTRY_STEP = 51;
const FINISH_STEP = 57;

function absoluteIndex(seat: number, stepCount: number): number {
  return (START_INDEX[seat] + stepCount - 1) % 52;
}

/** Opponent tokens (excluding players[seat]) sitting on absCell. */
function opponentsOn(players: Player[], seat: number, absCell: number): number {
  let count = 0;
  players.forEach((p, playerIndex) => {
    if (playerIndex === seat) return;
    p.tokens.forEach((step) => {
      if (step >= 1 && step <= HOME_ENTRY_STEP && absoluteIndex(playerIndex, step) === absCell) {
        count++;
      }
    });
  });
  return count;
}

/** Does the seat hold ANY legal move for the dice? (mirror of LudoEngine) */
function hasAnyLegalMove(board: Board, seat: number, dice: number): boolean {
  if (dice < 1 || dice > 6) return false;
  const tokens = board.players[seat].tokens;
  for (let tokenId = 0; tokenId < tokens.length; tokenId++) {
    const step = tokens[tokenId];
    if (step >= FINISH_STEP) continue;
    const newStep = step === 0 ? 1 : step + dice;
    if (newStep > FINISH_STEP) continue;
    const cells = step === 0
      ? [absoluteIndex(seat, 1)]
      : range(step + 1, newStep)
          .filter((s) => s >= 1 && s <= HOME_ENTRY_STEP)
          .map((s) => absoluteIndex(seat, s));
    if (cells.every((c) => opponentsOn(board.players, seat, c) < 2)) return true;
  }
  return false;
}

function range(from: number, to: number): number[] {
  const out: number[] = [];
  for (let i = from; i <= to; i++) out.push(i);
  return out;
}

/** Next seat clockwise, skipping players who already finished all 4 coins. */
function nextActiveSeat(board: Board, from: number): number {
  const n = board.players.length;
  for (let hop = 1; hop <= n; hop++) {
    const candidate = (from + hop) % n;
    const done = board.players[candidate].tokens.every((s) => s >= FINISH_STEP);
    if (!done) return candidate;
  }
  return from;
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

    const body = await req.json().catch(() => ({}));
    const matchId: string = body.match_id ?? "";
    const autoFlag: boolean = body.auto === true;
    if (typeof matchId !== "string" || !matchId) {
      return json({ error: "match_id_required" }, 400);
    }

    // Load the state row (service role bypasses RLS — validation happens here).
    const stateRes = await admin
      .from("ludo_game_state")
      .select("board_state, roll_deadline_at, move_deadline_at")
      .eq("match_id", matchId)
      .single();
    const state = stateRes.data;
    if (!state) return json({ error: "match_not_found" }, 404);

    const board: Board = state.board_state;
    const players: Player[] = board?.players ?? [];
    const turnIndex = board?.turn_index ?? 0;
    const phase = board?.phase ?? "AWAITING_ROLL";

    if (phase === "FINISHED") return json({ error: "match_finished" }, 409);

    // MATCH_STARTED gate (PRD §15): all four seats must be filled.
    if (players.length < 4) return json({ error: "match_not_started" }, 409);

    // Idempotency: a duplicated AWAITING_MOVE request returns the standing
    // dice instead of rolling again (PRD §31 — concurrent-request protection).
    if (phase === "AWAITING_MOVE") {
      return json({
        dice: board.dice_value,
        phase,
        auto: false,
        stale: true,
        board_state: board,
      });
    }

    const current = players[turnIndex];
    const isHost = players[0]?.id === callerId;
    if (!current) return json({ error: "invalid_state" }, 409);
    // Only the player whose turn it is may roll (hosts may roll for bots).
    if (current.id !== callerId && !(isHost && current.is_bot)) {
      return json({ error: "not_your_turn" }, 403);
    }

    // Timer verification (PRD §30): requests past the deadline (plus a small
    // grace) still roll — they are the AUTOMATIC roll for that turn.
    const now = Date.now();
    const rollDeadline = board.roll_deadline_at ? Date.parse(board.roll_deadline_at) : null;
    const expired = rollDeadline !== null && now > rollDeadline + TIMER_GRACE_MS;
    const isAutoRoll = autoFlag || expired;

    // Server-side random roll.
    const dice = 1 + Math.floor(Math.random() * 6);

    const consecutive = dice === 6 ? (board?.consecutive_sixes ?? 0) + 1 : 0;
    let nextPhase = "AWAITING_MOVE";
    let nextTurnIndex = turnIndex;
    let nextConsecutive = consecutive;
    let statusText = `${current.name} rolled ${dice} — pick a token`;
    let rollDeadlineIso: string | null = null;
    let moveDeadlineIso: string | null = null;

    // Three consecutive sixes forfeit the turn (the third six is not played).
    if (dice === 6 && consecutive >= 3) {
      nextTurnIndex = nextActiveSeat(board, turnIndex);
      nextConsecutive = 0;
      nextPhase = "AWAITING_ROLL";
      rollDeadlineIso = new Date(now + ROLL_WINDOW_MS).toISOString();
      statusText = `${current.name} rolled a third 6 — turn forfeited!`;
    } else if (hasAnyLegalMove({ ...board, dice_value: dice } as Board, turnIndex, dice)) {
      moveDeadlineIso = new Date(now + MOVE_WINDOW_MS).toISOString();
      if (dice === 6) statusText += " (extra roll after)";
    } else {
      // No legal move — the dice is shown, then the turn passes (PRD §17).
      nextTurnIndex = nextActiveSeat(board, turnIndex);
      nextConsecutive = dice === 6 ? consecutive : 0;
      nextPhase = "AWAITING_ROLL";
      rollDeadlineIso = new Date(now + ROLL_WINDOW_MS).toISOString();
      statusText = `${current.name} rolled ${dice} — no legal moves, turn passes.`;
    }

    const updated: Board = {
      ...board,
      dice_value: dice,
      phase: nextPhase,
      turn_index: nextTurnIndex,
      consecutive_sixes: nextConsecutive,
      status_text: statusText,
      seq: (board?.seq ?? 0) + 1,
      roll_deadline_at: rollDeadlineIso,
      move_deadline_at: moveDeadlineIso,
    };

    // Conditional atomic transition (PRD §31): only this caller's turn +
    // a still-pending dice (null column) may be written, which makes a
    // double-tap race lose harmlessly. NOTE: `phase` lives inside
    // board_state — the table has no phase column, so the dice_value
    // nullability is the conditional proxy for AWAITING_ROLL.
    const writeRes = await admin
      .from("ludo_game_state")
      .update({
        board_state: updated,
        dice_value: nextPhase === "AWAITING_MOVE" ? dice : null,
        turn: nextTurnIndex,
        roll_deadline_at: rollDeadlineIso,
        move_deadline_at: moveDeadlineIso,
        updated_at: new Date().toISOString(),
      })
      .eq("match_id", matchId)
      .eq("turn", turnIndex)
      .is("dice_value", null)
      .select();

    if (!writeRes.data || writeRes.data.length === 0) {
      // Lost the race — return the winner's authoritative state.
      const retry = await admin
        .from("ludo_game_state")
        .select("board_state")
        .eq("match_id", matchId)
        .single();
      const fresh: Board = retry.data?.board_state ?? board;
      return json({
        dice: fresh.dice_value ?? dice,
        phase: fresh.phase,
        auto: false,
        stale: true,
        board_state: fresh,
      });
    }

    return json({ dice, phase: nextPhase, auto: isAutoRoll, board_state: updated });
  } catch (e) {
    return json({ error: "internal_error", detail: String(e) }, 500);
  }
});
