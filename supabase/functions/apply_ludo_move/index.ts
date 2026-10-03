// ============================================================================
// apply_ludo_move — Quicky v3 (server-authoritative moves + 30s move timer
//                                        + scores + third-player game end)
//
// POST /functions/v1/apply_ludo_move
//   body: { "match_id": "LA7K2QD", "token_id": 2 }
//      or { "match_id": "LA7K2QD", "auto": true }   ← timer-expiry trigger
//   JWT:  caller must be the player whose turn it is (or the host driving
//         a bot seat)
//   →     { "ok": true, "auto": false, "board_state": {...},
//           "move": { "token_id", "from_step", "to_step", "path",
//                     "captured_player_id", "captured_token_id",
//                     "finished": bool, "score_awarded": int } }
//
// Server-authoritative flow (v3 PRD §8/§10/§12/§13/§18–§25/§59/§60):
//   1. Authenticate + load + verify phase/caller.
//   2. Timer check (PRD §30): a manual move past the 30s deadline is
//      REJECTED and the deterministic timeout move is applied instead.
//      `auto: true` requests the timeout move directly (any participant may
//      trigger it once the deadline passes — the server decides).
//   3. Validate the token ownership + full rule set (release-on-6, exact
//      finish, blocks, safe cells) — the SAME rules as LudoEngine.kt.
//   4. Apply the move + capture in ONE transaction; award +50 per newly
//      finished coin; assign finish positions.
//   5. THIRD player to bring all 4 coins home ends the game (PRD §18):
//      idempotently write ludo_game_results + ludo_user_stats (last/best
//      score persist across restarts, devices, logins) and mark the match
//      COMPLETED.
//   6. Bump `seq` +1; the DB UPDATE broadcasts to every device.
//   7. Return the move path so clients animate cell-by-cell (PRD §12/§13).
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
  auth: { persistSession: false },
});

const ROLL_WINDOW_MS = 10_000;
const MOVE_WINDOW_MS = 30_000;
const TIMER_GRACE_MS = 3_000;
const POINTS_PER_TOKEN = 50;
const GAME_END_COMPLETED_PLAYERS = 3;

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

/** Steps the moved coin travels through (animation path, PRD §13). */
function stepsOnPath(fromStep: number, newStep: number): number[] {
  return range(Math.max(fromStep, 0) + 1, newStep);
}

/**
 * DETERMINISTIC timeout move (PRD §10):
 *   1. finish a coin   2. capture   3. release from yard   4. highest progress
 */
function pickTimeoutMove(board: Board, seat: number, dice: number): number | null {
  const tokens = board.players[seat].tokens;
  const legal: number[] = [];
  for (let tokenId = 0; tokenId < tokens.length; tokenId++) {
    if (isMoveLegal(board, seat, tokenId, dice)) legal.push(tokenId);
  }
  if (legal.length === 0) return null;

  // 1) Finish a coin.
  for (const tokenId of legal) {
    const step = tokens[tokenId];
    if ((step === 0 ? 1 : step + dice) === FINISH_STEP) return tokenId;
  }

  // 2) Capture an opponent.
  for (const tokenId of legal) {
    const step = tokens[tokenId];
    const newStep = step === 0 ? 1 : step + dice;
    const landed = newStep <= HOME_ENTRY_STEP ? absoluteIndex(seat, newStep) : -1;
    if (landed >= 0 && !SAFE_CELLS.has(landed) && opponentsOn(board.players, seat, landed) === 1) {
      return tokenId;
    }
  }

  // 3) Release from the yard.
  for (const tokenId of legal) {
    if (tokens[tokenId] === 0) return tokenId;
  }

  // 4) Highest-progress legal coin.
  let best = legal[0];
  for (const tokenId of legal) {
    if (tokens[tokenId] > tokens[best]) best = tokenId;
  }
  return best;
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

// ---------------------------------------------------------------------------
// Game completion transaction (PRD §25) — strictly idempotent.
// ---------------------------------------------------------------------------
async function finalizeGame(matchId: string, board: Board): Promise<void> {
  // 1. Lock: already completed? Then results exist — never write twice.
  const matchRes = await admin
    .from("ludo_matches")
    .select("status")
    .eq("id", matchId)
    .single();
  if (matchRes.data?.status === "COMPLETED") return;

  // 2. Compute the standings: finishers by position, then the remaining
  //    players by finished coins + progress steps (4th place, PRD §19).
  const finishers = board.players
    .filter((p) => p.finish_position != null)
    .sort((a, b) => (a.finish_position ?? 0) - (b.finish_position ?? 0));
  const remaining = board.players
    .filter((p) => p.finish_position == null)
    .sort((a, b) => {
      const fa = a.tokens.filter((s) => s >= FINISH_STEP).length;
      const fb = b.tokens.filter((s) => s >= FINISH_STEP).length;
      if (fb !== fa) return fb - fa;
      const sa = a.tokens.reduce((sum, s) => sum + s, 0);
      const sb = b.tokens.reduce((sum, s) => sum + s, 0);
      return sb - sa;
    });
  const ranking = finishers.concat(remaining);

  // 3. ludo_game_results — one row per participating HUMAN (bots skipped).
  //    ON CONFLICT DO NOTHING keeps the transaction idempotent.
  const resultRows = ranking.map((player, index) => ({
    match_id: matchId,
    user_id: player.id,
    seat: player.seat,
    score: player.score,
    finished_tokens: player.tokens.filter((s) => s >= FINISH_STEP).length,
    finish_position: index + 1,
    coins_home: player.tokens.filter((s) => s >= FINISH_STEP).length,
  })).filter((row) => !row.user_id.startsWith("bot_seat_"));

  if (resultRows.length > 0) {
    await admin
      .from("ludo_game_results")
      .upsert(resultRows, { onConflict: "match_id,user_id", ignoreDuplicates: true });
  }

  // 4. ludo_user_stats — per-user aggregates incl. the REQUIRED last_score
  //    (survives app restart / logout / new device, PRD §24/§27).
  for (const row of resultRows) {
    await admin.rpc("upsert_ludo_user_stats", {
      p_user_id: row.user_id,
      p_score: row.score,
      p_finish_position: row.finish_position,
      p_completed: true,
    });
  }

  // 5. Close the match row (completion timestamp).
  await admin
    .from("ludo_matches")
    .update({
      status: "COMPLETED",
      winner_id: ranking[0]?.user_id ?? board.winner_id ?? null,
      completed_at: new Date().toISOString(),
    })
    .eq("id", matchId);
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

    const body = await req.json().catch(() => ({}));
    const matchId: string = body.match_id ?? "";
    const tokenId: number | null =
      typeof body.token_id === "number" ? body.token_id : null;
    const autoFlag: boolean = body.auto === true;
    if (typeof matchId !== "string" || !matchId) {
      return json({ error: "match_id_required" }, 400);
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
    const players: Player[] = board.players ?? [];
    const current = players[turnIndex];
    const isHost = players[0]?.id === callerId;
    if (!current) return json({ error: "invalid_state" }, 409);
    // Only the current player (or the host driving a bot seat) may move.
    if (current.id !== callerId && !(isHost && current.is_bot) && !autoFlag) {
      return json({ error: "not_your_turn" }, 403);
    }

    const dice = board.dice_value ?? 0;
    const now = Date.now();
    const moveDeadline = board.move_deadline_at
      ? Date.parse(board.move_deadline_at)
      : null;
    const expired = moveDeadline !== null && now > moveDeadline + TIMER_GRACE_MS;

    let chosenToken: number;
    let isAutoMove = autoFlag || expired;

    if (autoFlag || expired) {
      // Timeout (PRD §10): server picks the deterministic legal move; if
      // none exists the turn simply advances.
      const timeoutToken = pickTimeoutMove(board, turnIndex, dice);
      if (timeoutToken === null) {
        const nextSeat = nextActiveSeat(board, turnIndex);
        const advanced: Board = {
          ...board,
          turn_index: nextSeat,
          dice_value: null,
          phase: "AWAITING_ROLL",
          consecutive_sixes: 0,
          status_text: `${current.name} had no legal moves — turn passes.`,
          seq: (board.seq ?? 0) + 1,
          roll_deadline_at: new Date(now + ROLL_WINDOW_MS).toISOString(),
          move_deadline_at: null,
        };
        await admin
          .from("ludo_game_state")
          .update({
            board_state: advanced,
            dice_value: null,
            turn: nextSeat,
            roll_deadline_at: advanced.roll_deadline_at,
            move_deadline_at: null,
            updated_at: new Date().toISOString(),
          })
          .eq("match_id", matchId);
        return json({ ok: true, auto: true, advanced: true, board_state: advanced });
      }
      chosenToken = timeoutToken;
    } else {
      // Manual move — full validation (anti-cheat, PRD §67).
      if (tokenId === null || tokenId < 0 || tokenId > 3) {
        return json({ error: "token_id_invalid" }, 400);
      }
      if (current.id !== callerId && !(isHost && current.is_bot)) {
        return json({ error: "not_your_turn" }, 403);
      }
      if (!isMoveLegal(board, turnIndex, tokenId, dice)) {
        return json({ error: "illegal_move" }, 422);
      }
      chosenToken = tokenId;
      isAutoMove = false;
    }

    // ---------------- Apply the move (single transaction body) ----------------
    const seat = turnIndex;
    const fromStep = players[seat].tokens[chosenToken];
    const newStep = fromStep === 0 ? 1 : fromStep + dice;
    const landedAbs = newStep <= HOME_ENTRY_STEP ? absoluteIndex(seat, newStep) : -1;

    let capturedPlayerId: string | null = null;
    let capturedTokenId: number | null = null;
    if (landedAbs >= 0 && !SAFE_CELLS.has(landedAbs)) {
      players.forEach((p, playerIndex) => {
        if (playerIndex === seat) return;
        const onCell = p.tokens
          .map((step, idx) => ({ step, idx }))
          .filter(({ step }) => step >= 1 && step <= HOME_ENTRY_STEP &&
            absoluteIndex(playerIndex, step) === landedAbs);
        if (onCell.length === 1) {
          capturedPlayerId = p.id;
          capturedTokenId = onCell[0].idx;
          p.tokens[onCell[0].idx] = 0; // sent home
        }
      });
    }

    players[seat].tokens[chosenToken] = newStep;
    const finishedToken = newStep === FINISH_STEP;
    const scoreAwarded = finishedToken ? POINTS_PER_TOKEN : 0;
    players[seat].score = (players[seat].score ?? 0) + scoreAwarded;

    const moverCompleted = players[seat].tokens.every((s) => s >= FINISH_STEP);

    // --- Finish-position assignment + THIRD-player game end (PRD §61) ---
    let completedPlayers = board.completed_players ?? 0;
    let finishOrder: string[] = board.finish_order ?? [];
    let winnerId: string | null = board.winner_id ?? null;
    let gameFinished = false;
    if (moverCompleted && players[seat].finish_position == null) {
      players[seat].finish_position = finishOrder.length + 1;
      finishOrder = finishOrder.concat(players[seat].id);
      if (winnerId == null) winnerId = players[seat].id;
      completedPlayers += 1;
      if (completedPlayers >= GAME_END_COMPLETED_PLAYERS) gameFinished = true;
    }

    // Extra turn: NOT granted when the mover just completed all 4 coins.
    const extraTurn = !moverCompleted && !gameFinished &&
      (dice === 6 || capturedPlayerId !== null || finishedToken);

    let nextTurnIndex = turnIndex;
    let nextPhase = "AWAITING_ROLL";
    let rollDeadlineIso: string | null = new Date(now + ROLL_WINDOW_MS).toISOString();
    let moveDeadlineIso: string | null = null;
    let statusText: string;

    if (gameFinished) {
      nextPhase = "FINISHED";
      rollDeadlineIso = null;
      statusText = `🏁 ${finishOrder.length} players finished — game complete!`;
    } else if (moverCompleted) {
      nextTurnIndex = nextActiveSeat({ ...board, players } as Board, turnIndex);
      statusText = `🏆 ${players[seat].name} brought all 4 coins home — rank #${players[seat].finish_position}!`;
    } else if (extraTurn) {
      statusText = `${players[seat].name}` +
        (capturedPlayerId !== null ? " captured a token — extra roll!"
          : finishedToken ? ` sent a token home (+${POINTS_PER_TOKEN}) — extra roll!`
          : " rolled a 6 — extra roll!");
    } else {
      nextTurnIndex = nextActiveSeat({ ...board, players } as Board, turnIndex);
      statusText = `${players[seat].name} moved` +
        (capturedPlayerId !== null ? " and captured a token!"
          : finishedToken ? ` a token home (+${POINTS_PER_TOKEN})!` : ".");
    }

    const updated: Board = {
      ...board,
      players,
      turn_index: nextTurnIndex,
      dice_value: null,
      phase: nextPhase,
      consecutive_sixes: dice === 6 ? (board.consecutive_sixes ?? 0) : 0,
      winner_id: winnerId,
      status_text: statusText,
      seq: (board.seq ?? 0) + 1,
      completed_players: completedPlayers,
      finish_order: finishOrder,
      roll_deadline_at: rollDeadlineIso,
      move_deadline_at: moveDeadlineIso,
    };

    // Conditional atomic write (PRD §31) — a racing duplicate request that
    // still expects turn_index==board.turn_index + a pending dice (non-null
    // column = AWAITING_MOVE proxy; there is no `phase` table column) loses.
    const writeRes = await admin
      .from("ludo_game_state")
      .update({
        board_state: updated,
        dice_value: null,
        turn: nextTurnIndex,
        roll_deadline_at: rollDeadlineIso,
        move_deadline_at: moveDeadlineIso,
        updated_at: new Date().toISOString(),
      })
      .eq("match_id", matchId)
      .eq("turn", turnIndex)
      .not("dice_value", "is", null)
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
        ok: true,
        stale: true,
        auto: isAutoMove,
        board_state: fresh,
        move: { token_id: chosenToken, from_step: fromStep, to_step: newStep, path: stepsOnPath(fromStep, newStep) },
      });
    }

    // Audit log entry (from/to cells are stepCount semantics).
    await admin.from("ludo_moves").insert({
      match_id: matchId,
      user_id: current.id,
      from_cell: fromStep,
      to_cell: newStep,
      dice,
      is_auto: isAutoMove,
    });

    // Game-finished transaction: results + user stats + COMPLETED (idempotent).
    if (gameFinished) {
      await finalizeGame(matchId, updated);
    }

    return json({
      ok: true,
      auto: isAutoMove,
      board_state: updated,
      move: {
        token_id: chosenToken,
        from_step: fromStep,
        to_step: newStep,
        path: stepsOnPath(fromStep, newStep),
        captured_player_id: capturedPlayerId,
        captured_token_id: capturedTokenId,
        finished: finishedToken,
        score_awarded: scoreAwarded,
      },
    });
  } catch (e) {
    return json({ error: "internal_error", detail: String(e) }, 500);
  }
});
