-- ============================================================================
-- QUICKY v3.3.8 — QUICKY IMAGE PUSH NOTIFICATIONS
-- ----------------------------------------------------------------------------
-- Run ONCE in the Supabase SQL editor (or as a migration).
-- This migration documents the setup requirements for the send-push Edge
-- Function and adds a grant so authenticated users can call it.
--
-- WHAT THIS DOES
-- --------------
-- 1) Documents the required environment variables for the `send-push`
--    Edge Function (deploy it from supabase/functions/send-push/index.ts).
--
-- 2) (Optional) Creates a Postgres DB webhook that fires `send-push` whenever
--    a new SNAP message row is inserted — as an alternative to calling the
--    function from the mobile app. See section 3 below.
--
-- REQUIRED SETUP BEFORE RUNNING
-- ------------------------------
-- A) Firebase service account
--    Firebase Console → Project Settings → Service accounts → Generate new key
--    Save the JSON file — you will need the fields below.
--
-- B) Set Supabase secrets (run in your shell):
--
--    supabase secrets set \
--      FIREBASE_PROJECT_ID="quicky-your-project-id" \
--      FIREBASE_CLIENT_EMAIL="firebase-adminsdk-xxxxx@quicky.iam.gserviceaccount.com" \
--      FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nMIIEvAIBADANBgkqhkiG...\n-----END PRIVATE KEY-----\n"
--
--    Note: The SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY secrets are already
--    injected automatically by the Supabase runtime.
--
-- C) Deploy the Edge Function:
--
--    supabase functions deploy send-push --no-verify-jwt
--
--    --no-verify-jwt allows the DB webhook (section 3) to call the function
--    without a user JWT; the function itself still validates input.
--
-- ============================================================================


-- ============================================================================
-- 1) VERIFY device_tokens table exists (from device_tokens.sql v3.3)
-- ============================================================================
-- The table must exist before the Edge Function can query it.
-- If it's missing, run supabase/device_tokens.sql first.
do $$
begin
    if not exists (
        select 1 from information_schema.tables
        where table_schema = 'public' and table_name = 'device_tokens'
    ) then
        raise exception 'device_tokens table is missing — run supabase/device_tokens.sql first';
    end if;
end $$;


-- ============================================================================
-- 2) FCM notification payload constants (documentation only)
-- ============================================================================
-- The following `type` values are handled by QuickyPushService.onMessageReceived
-- and routed to the appropriate PushNotifications helper:
--
--   quicky      → PushNotifications.showQuicky()   ← NEW in v3.3.8
--   message     → PushNotifications.showMessage()
--   match       → PushNotifications.showMatch()
--   like        → PushNotifications.showLike()
--   super_like  → PushNotifications.showSuperLike()
--   club        → PushNotifications.showClub()
--   club_mention→ PushNotifications.showClub(isMention=true)
--   promo       → PushNotifications.showPromotion()
--   truth_dare  → in-app overlay only (no system notification)


-- ============================================================================
-- 3) (OPTIONAL) Database webhook — fires send-push on every new SNAP message
-- ============================================================================
-- This is the server-side alternative to the mobile app calling send-push
-- directly. Uncomment this block if you prefer the trigger approach.
--
-- Note: Supabase HTTP webhooks require the pg_net extension. Enable it in the
--       Dashboard → Extensions → pg_net, then uncomment:
--
-- create extension if not exists pg_net with schema extensions;
--
-- create or replace function public.notify_snap_receiver()
-- returns trigger
-- language plpgsql
-- security definer
-- set search_path = public
-- as $$
-- declare
--     v_receiver_id  text;
--     v_sender_name  text;
--     v_function_url text;
-- begin
--     -- Only fire for SNAP messages
--     if NEW.message_type != 'SNAP' then
--         return NEW;
--     end if;
--
--     -- Derive receiver from conversation_id ('dm_<a>_<b>' or 'match_<uuid>')
--     -- For match_ conversations: look up the other participant in the matches table
--     select
--         case when m.user_id_1 = NEW.sender_id then m.user_id_2 else m.user_id_1 end
--     into v_receiver_id
--     from matches m
--     where m.id = NEW.conversation_id;
--
--     -- Sender's display name from profiles
--     select name into v_sender_name from profiles where id = NEW.sender_id;
--
--     -- Invoke the Edge Function (fire-and-forget)
--     v_function_url := current_setting('app.supabase_url') || '/functions/v1/send-push';
--     perform net.http_post(
--         url     := v_function_url,
--         headers := jsonb_build_object(
--                      'Content-Type', 'application/json',
--                      'Authorization', 'Bearer ' || current_setting('app.service_role_key')
--                    ),
--         body    := jsonb_build_object(
--                      'recipient_user_id', v_receiver_id,
--                      'type',              'quicky',
--                      'title',             '📸 ' || v_sender_name || ' sent a Quicky!',
--                      'body',              'Tap to open before it disappears!',
--                      'chat_id',           NEW.conversation_id,
--                      'sender_name',       v_sender_name
--                    )
--     );
--     return NEW;
-- end $$;
--
-- drop trigger if exists snap_push_on_insert on public.messages;
-- create trigger snap_push_on_insert
--     after insert on public.messages
--     for each row execute function public.notify_snap_receiver();
-- ============================================================================


-- ============================================================================
-- 4) Grant authenticated users permission to invoke send-push via supabase.functions
-- ============================================================================
-- (No SQL action needed — Supabase Edge Functions are callable by any valid
--  JWT bearer token; the grant is managed through the project's anon/service
--  role key policies at the API gateway level.)

-- ============================================================================
-- DONE. Verify by sending a test Quicky snap in the app and confirming the
-- receiver gets a "📸 ... sent a Quicky!" heads-up notification on their device.
-- ============================================================================
