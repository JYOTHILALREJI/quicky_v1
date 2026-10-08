-- ============================================================================
-- QUICKY v3.3.5 — CLUB CHAT FIX-UP MIGRATION (run once in the SQL editor)
-- ============================================================================
-- Fixes for the three bugs reported against v3.3.4:
--
--   1. "null" text rendered inside club chat bubbles — the Android client's
--      JSON parser turned SQL NULL columns into the LITERAL string "null"
--      (fixed app-side in v3.3.5; this script scrubs any rows that were
--      written with the literal string during the v3.3.4 window).
--   2. Voice notes failing with "Voice note unavailable" — the storage
--      upload used the upsert flag, which some Supabase storage versions
--      reject without an UPDATE policy on storage.objects even for fresh
--      objects. This adds the missing UPDATE policy for the club-voice
--      prefix.
--   3. (App-side only) The per-member moderation dropdown now anchors to
--      the member row inside the members sheet — no DB change needed.
--
-- Safe to run multiple times (idempotent).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Storage: club-voice UPDATE policy (upsert safety for voice uploads)
--    Uploads PUT /storage/v1/object/voice-notes/club-voice/<club>/… with
--    x-upsert: true; the UPDATE grant keeps that path RLS-clean.
-- ----------------------------------------------------------------------------
drop policy if exists "club_voice_authenticated_update" on storage.objects;
create policy "club_voice_authenticated_update" on storage.objects
    for update using (
        bucket_id = 'voice-notes'
        and (storage.foldername(name))[1] = 'club-voice'
        and auth.role() = 'authenticated'
    );

-- ----------------------------------------------------------------------------
-- 2. Data scrub: literal 'null' strings written by the v3.3.4 client bug.
--    (The v3.3.5 parser also guards these on read — this keeps the data
--    itself clean for any other consumer.)
-- ----------------------------------------------------------------------------
update public.club_messages set reply_to_text   = null  where reply_to_text   = 'null';
update public.club_messages set reply_to_sender = null  where reply_to_sender = 'null';
update public.club_messages set sticker_emoji   = null  where sticker_emoji   = 'null';
update public.club_messages set voice_url       = null  where voice_url       = 'null';
update public.club_messages set sender_name     = 'Member' where sender_name  = 'null';

-- NOTE: club_messages.text is intentionally NOT scrubbed — a user may
-- legitimately send the word "null"; the v3.3.5 read-side guard already
-- neutralizes JSON-null texts without touching real content.
