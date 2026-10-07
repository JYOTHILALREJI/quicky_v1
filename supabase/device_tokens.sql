-- ============================================================================
-- QUICKY — FCM DEVICE TOKENS (v3.3 push notifications)
-- ============================================================================
--
-- Run this ONCE in the Supabase SQL editor. It stores the Firebase Cloud
-- Messaging registration token of each signed-in account so that
-- server-side code (Edge Functions / database webhooks using the Firebase
-- Admin SDK with a service-role key) can send match / message / club
-- push notifications to that device.
--
-- One row per ACCOUNT (user_id is the primary key): if the same account
-- signs in on a second device, the newest token wins. Sign-out leaves
-- the row in place — the token is only ever READ by server-side sends
-- and is harmless without a valid FCM project.
--
-- Client contract (see app PushNotifications.kt / QuickyPushService.kt):
--   * written by: QuickyPushService.onNewToken + every sign-in
--     (SparkViewModel.adoptSession) via
--     SupabaseRepository.upsertDeviceToken(...)
--   * RLS: a user can only insert/update their own row; reads are
--     server-side only (service_role bypasses RLS).
--
-- Expected FCM DATA payload shape for Quicky alerts:
--
--   {
--     "type":    "match" | "message" | "club",
--     "title":   "New match!",
--     "body":    "Anna liked you back",
--     "chat_id": "match_<uuid>",   -- type=message: opens that chat
--     "club_id": "<uuid>"          -- type=club:    opens that club page
--   }
--
-- `title`/`body` fall back to the notification payload when present;
-- `type` picks the Android notification channel (matches / messages /
-- clubs) and the tap deep link (quicky://notify?...).
-- ============================================================================

create table if not exists public.device_tokens (
    user_id    uuid primary key references public.profiles (id) on delete cascade,
    fcm_token  text not null,
    updated_at timestamptz not null default now()
);

-- Keep updated_at fresh automatically (same trigger pattern as profiles).
drop trigger if exists device_tokens_set_updated_at on public.device_tokens;
create trigger device_tokens_set_updated_at
    before update on public.device_tokens
    for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------
-- Row Level Security — a user manages ONLY their own device row.
-- (Server-side sends use the service_role key, which bypasses RLS.)
-- ---------------------------------------------------------------------
alter table public.device_tokens enable row level security;

drop policy if exists "device_tokens own row" on public.device_tokens;
create policy "device_tokens own row"
    on public.device_tokens
    for all
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);
