// ============================================================================
// apply_ludo_move — Quicky v2.1 §3.2.4 (server-authoritative move validation)
//
// POST /functions/v1/apply_ludo_move
//   body: { "match_id": "LA7K2QD", "token_id": 2 }
//   JWT:  caller must be the player whose turn it is (or the host driving bots)
//   →     { "ok": true, "board_state": {...} }
//
// Validates the move against the REAL Ludo rules — the same ones as the
// Kotlin `LudoEngine` — before mutating ludo_game_state:
//   1. Tokens leave the yard only on a 6.
//   2. Extra turn on 6 / capture / finishing a token; 3 consecutive 6s
//      forfeit the turn (enforced at roll time by roll_ludo_dice).
//   3. Capture: landing on a single opponent token on a non-safe cell
//      sends it home. Two+ opponent tokens form a BLOCK — cannot pass.
//   4. Safe cells: the four starts + four star cells — no capture there.
//   5. Home column + final cell need an EXACT roll (no overshoot).
//   6. Win: first player to bring all four tokens home.
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
  auth: { persistSession: false },
});

// ----- Board constants (mirrors com.example.game.LudoEngine) -----
const START_INDEX = [0, 13, 26, 39];
const START_CELLS = new Set(START_INDEX);
const STAR_CELLS = new Set([8, 21, 34, 47]);
const SAFE_CELLS = new Set([...START_CELLS, ...STAR_CELLS]);
const HOME_ENTRY_STEP = 51;
const FINISH_STEP = 57;

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
interface Player { id: string; name: string; seat: number; is_bot: boolean; tokens: number[] }
interface Board {
  players: Player[];
  turn_index: number;
  dice_value: number | null;
  phase: string;
  consecutive_sixes: number;
  winner_id: string | null;
  status_text: string;
}

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

function isMoveLegal(board: Board, seat: number, tokenId: number, dice: number): boolean {
  if (dice < 1 || dice > 6) return false;
  const step = board.players[seat].tokens[tokenId];
  if (step === undefined || step >= FINISH_STEP) return false;

  const newStep = step === 0 ? 1 : step + dice;
  if (newStep > FINISH_STEP) return false; // exact roll to finish only

  const cells = step === 0
    ? [absoluteIndex(seat, 1)]
    : range(step + 1, newStep)
        .filter((s) => s >= 1 && s <= HOME_ENTRY_STEP)
        .map((s) => absoluteIndex(seat, s));

  return cells.every((c) => opponentsOn(board.players, seat, c) < 2);
}

function range(from: number, to: number): number[] {
  const out: number[] = [];
  for (let i = from; i <= to; i++) out.push(i);
  return out;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders() });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  try {
    const callerJwt = (req.headers.get("Authorization") ?? "").replace("Bearer ", "");
    if (!callerJwt) return json({ error: "unauthorized" }, 401);

    const callerId = await admin.auth.getUser(callerJwt)
      .then((r) => r.data.user?.id)
      .catch(() => undefined);
    if (!callerId) return json({ error: "unauthorized" }, 401);

    const { match_id: matchId, token_id: tokenId } = await req.json().catch(() => ({}));
    if (typeof matchId !== "string" || !matchId) return json({ error: "match_id_required" }, 400);
    if (typeof tokenId !== "number" || tokenId < 0 || tokenId > 3) {
      return json({ error: "token_id_invalid" }, 400);
    }

    const stateRes = await admin
      .from("ludo_game_state")
      .select("board_state")
      .eq("match_id", matchId)
      .single();
    const board: Board | null = stateRes.data?.board_state ?? null;
    if (!board) return json({ error: "match_not_found" }, 404);
    if (board.phase === "FINISHED") return json({ error: "match_finished" }, 409);
    if (board.phase !== "AWAITING_MOVE") return json({ error: "roll_first" }, 409);

    const turnIndex = board.turn_index ?? 0;
    const players = board.players ?? [];
    const current = players[turnIndex];
    const isHost = players[0]?.id === callerId;
    if (!current) return json({ error: "invalid_state" }, 409);
    // Only the current player (or the host driving a bot seat) may move.
    if (current.id !== callerId && !(isHost && current.is_bot)) {
      return json({ error: "not_your_turn" }, 403);
    }

    const dice = board.dice_value ?? 0;
    if (!isMoveLegal(board, turnIndex, tokenId, dice)) {
      return json({ error: "illegal_move" }, 422);
    }

    // --- Apply the move ---
    const seat = turnIndex;
    const fromStep = players[seat].tokens[tokenId];
    const newStep = fromStep === 0 ? 1 : fromStep + dice;
    const landedAbs = newStep <= HOME_ENTRY_STEP ? absoluteIndex(seat, newStep) : -1;

    let capturedOpponent = false;
    if (landedAbs >= 0 && !SAFE_CELLS.has(landedAbs)) {
      players.forEach((p, playerIndex) => {
        if (playerIndex === seat) return;
        const onCell = p.tokens
          .map((step, idx) => ({ step, idx }))
          .filter(({ step }) => step >= 1 && step <= HOME_ENTRY_STEP &&
            absoluteIndex(playerIndex, step) === landedAbs);
        if (onCell.length === 1) {
          capturedOpponent = true;
          p.tokens[onCell[0].idx] = 0; // sent home
        }
      });
    }

    players[seat].tokens[tokenId] = newStep;
    const finishedToken = newStep === FINISH_STEP;
    const moverWon = players[seat].tokens.every((s) => s >= FINISH_STEP);

    let nextTurnIndex = turnIndex;
    let nextPhase = "AWAITING_ROLL";
    let winnerId: string | null = null;
    let statusText: string;

    if (moverWon) {
      winnerId = players[seat].id;
      nextPhase = "FINISHED";
      statusText = `${players[seat].name} brought all 4 tokens home — victory!`;
    } else if (dice === 6 || capturedOpponent || finishedToken) {
      statusText = `${players[seat].name}` +
        (capturedOpponent ? " captured a token — extra roll!"
          : finishedToken ? " sent a token home — extra roll!"
          : " rolled a 6 — extra roll!");
    } else {
      nextTurnIndex = (turnIndex + 1) % players.length;
      statusText = `${players[seat].name} moved${capturedOpponent ? " and captured a token!" : "."}`;
    }

    const updated: Board = {
      ...board,
      players,
      turn_index: nextTurnIndex,
      dice_value: null,
      phase: nextPhase,
      consecutive_sixes: dice === 6 ? (board.consecutive_sixes ?? 0) : 0,
      winner_id: winnerId ?? board.winner_id ?? null,
      status_text: statusText,
    };

    await admin
      .from("ludo_game_state")
      .update({
        board_state: updated,
        dice_value: null,
        turn: nextTurnIndex,
        updated_at: new Date().toISOString(),
      })
      .eq("match_id", matchId);

    // Audit log entry (from/to cells are stepCount semantics).
    await admin.from("ludo_moves").insert({
      match_id: matchId,
      user_id: current.id,
      from_cell: fromStep,
      to_cell: newStep,
      dice,
    });

    // Close the match row when someone wins.
    if (winnerId) {
      await admin.from("ludo_matches")
        .update({ status: "COMPLETED", winner_id: winnerId })
        .eq("id", matchId);
    }

    return json({ ok: true, board_state: updated });
  } catch (e) {
    return json({ error: "internal_error", detail: String(e) }, 500);
  }
});
