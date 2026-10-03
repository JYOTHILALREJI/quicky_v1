-- ============================================================================
-- QUICKY — SUPABASE DATABASE SCHEMA
-- ============================================================================
-- Run this whole file in: Supabase Dashboard → SQL Editor → New query → Run
--
-- Table names match the constants in app/src/main/java/com/example/data/
-- SupabaseConfig.kt so the app's PostgREST calls resolve out of the box:
--
--   profiles, user_interests, likes, matches, messages, clubs,
--   club_members, club_messages, notifications, games, game_prompts
--
-- The app's SupabaseRepository writes/reads exactly these columns:
--   games:          id, name, description, is_free, tag, players_count
--   game_prompts:   id, category, type, text, difficulty
--   messages:       conversation_id, sender_id, text, ...
--   club_messages:  club_id, sender_id, sender_name, message_type, text, ...
--
-- Row Level Security (RLS) is enabled on every table because the mobile
-- client only holds the ANON key (never ship the service_role key).
-- ============================================================================


-- ============================================================================
-- 1. PROFILES
-- ============================================================================
create table if not exists public.profiles (
    id                       uuid primary key references auth.users (id) on delete cascade,
    name                     text not null default '',
    age                      int  not null default 21 check (age between 18 and 99),
    bio                      text not null default '',
    city                     text not null default '',
    distance_km              int  not null default 0,
    relationship_intent      text not null default 'Long-term partner',
    occupation               text not null default '',
    industry                 text not null default '',
    education                text not null default '',
    education_level          text not null default 'Bachelor''s Degree',
    height                   text not null default '172 cm',
    gender                   text not null default 'Female',
    interested_in            text not null default 'Everyone',
    languages                text[] not null default array['English'],
    is_verified              boolean not null default false,
    is_online                boolean not null default false,
    character_badge          text not null default 'The Explorer',
    character_description    text not null default '',
    show_character_badge     boolean not null default true,
    photo_urls               text[] not null default '{}',        -- Storage public URLs (max 3)
    interests                text[] not null default '{}',        -- system + custom interests (chips)
    lifestyle                jsonb  not null default '{}'::jsonb,
    field_visibility         jsonb  not null default '{}'::jsonb, -- {"height": "MATCHES_ONLY", ...}
    prompts                  jsonb  not null default '[]'::jsonb, -- [{question, answer}]
    compatibility_score      int  not null default 85,
    profile_completion_score int  not null default 85,
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now()
);

-- Keep updated_at fresh automatically
create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end $$;

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at
    before update on public.profiles
    for each row execute function public.set_updated_at();

-- ----------------------------------------------------------------
-- AUTH & ONBOARDING (Auth PRD) — extended profiles columns.
-- Idempotent ALTERs so this file is safe to re-run on an existing
-- database (the create-table above only fires on first setup).
-- ----------------------------------------------------------------
alter table public.profiles add column if not exists onboarding_completed boolean       not null default false;
alter table public.profiles add column if not exists onboarding_step       int            not null default 1;
alter table public.profiles add column if not exists date_of_birth        date;                      -- NEVER exposed publicly
alter table public.profiles add column if not exists custom_gender        text           not null default '';
alter table public.profiles add column if not exists looking_for         text[]         not null default '{}';
alter table public.profiles add column if not exists hobbies              text[]         not null default '{}';
alter table public.profiles add column if not exists height_cm            int;
alter table public.profiles add column if not exists weight_kg           real;                      -- optional, private by default
alter table public.profiles add column if not exists qualification        text           not null default '';
alter table public.profiles add column if not exists occupation           text           not null default '';
alter table public.profiles add column if not exists latitude             double precision;
alter table public.profiles add column if not exists longitude            double precision;

-- v2 language discovery filter: GIN index so the `p.languages && :filter`
-- array-overlap check in get_discovery_profiles stays fast at scale.
create index if not exists idx_profiles_languages
    on public.profiles using gin (languages);

-- Auto-create the profiles row the moment a Supabase Auth account is
-- registered (email+password or OAuth). security definer bypasses RLS
-- for the system-trigger insert.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (id, name, onboarding_completed, onboarding_step)
    values (
        new.id,
        split_part(coalesce(new.email, 'user'), '@', 1),
        false,
        1
    )
    on conflict (id) do nothing;
    return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();


-- ============================================================================
-- 2. USER INTERESTS  (powers matching + discovery filters)
--    Normalized one-row-per-interest copy of profiles.interests so shared-
--    interest matching is a cheap indexed join.
-- ============================================================================
create table if not exists public.user_interests (
    user_id    uuid not null references public.profiles (id) on delete cascade,
    interest   text not null,
    source     text not null default 'SYSTEM' check (source in ('SYSTEM', 'CUSTOM')),
    created_at timestamptz not null default now(),
    primary key (user_id, interest)
);

-- B-tree index: shared-interest matching and filter joins run on exact
-- text equality, which B-tree handles natively (GIN requires an operator
-- class for plain text and is meant for arrays/full-text/trigrams).
create index if not exists user_interests_interest_idx
    on public.user_interests (interest);

-- Optional: fuzzy search on interests (LIKE '%phot%') — uncomment to enable.
-- create extension if not exists pg_trgm;
-- create index if not exists user_interests_interest_trgm_idx
--     on public.user_interests using gin (interest gin_trgm_ops);


-- ============================================================================
-- 3. LIKES / PASSES  (discovery actions — a match is a mutual LIKE)
-- ============================================================================
create table if not exists public.likes (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null references public.profiles (id) on delete cascade,
    target_user_id uuid not null references public.profiles (id) on delete cascade,
    action          text not null check (action in ('LIKE', 'PASS', 'SUPER_LIKE')),
    created_at      timestamptz not null default now(),
    unique (user_id, target_user_id),
    check (user_id <> target_user_id)
);

create index if not exists likes_target_idx on public.likes (target_user_id, action);


-- ============================================================================
-- 4. MATCHES
-- ============================================================================
create table if not exists public.matches (
    id              uuid primary key default gen_random_uuid(),
    user_a_id       uuid not null references public.profiles (id) on delete cascade,
    user_b_id       uuid not null references public.profiles (id) on delete cascade,
    matched_at      timestamptz not null default now(),
    last_message    text,
    has_active_game boolean not null default false,
    is_new          boolean not null default true,
    check (user_a_id < user_b_id),
    unique (user_a_id, user_b_id)
);

create index if not exists matches_user_a_idx on public.matches (user_a_id);
create index if not exists matches_user_b_idx on public.matches (user_b_id);


-- ============================================================================
-- 5. MESSAGES  (personal 1:1 chat)
-- ============================================================================
create table if not exists public.messages (
    id              uuid primary key default gen_random_uuid(),
    conversation_id text not null,          -- canonical match/conversation id
    sender_id       text not null,
    text            text not null default '',
    is_read         boolean not null default false,
    reply_to_text   text,
    reply_to_sender text,
    created_at      timestamptz not null default now()
);

create index if not exists messages_conversation_idx
    on public.messages (conversation_id, created_at desc);


-- ============================================================================
-- 6. CLUBS  (max 15 members, one club per user)
-- ============================================================================
create table if not exists public.clubs (
    id          text primary key,
    owner_id    text not null,
    name        text not null,
    description text not null default '',
    logo_emoji  text not null default '🎮',
    max_members int  not null default 15 check (max_members <= 15),
    status      text not null default 'ACTIVE' check (status in ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    category    text not null default 'Casual Gaming',
    created_at  timestamptz not null default now()
);


-- ============================================================================
-- 7. CLUB MEMBERS  (enforces the 15-member cap in the database)
-- ============================================================================
create table if not exists public.club_members (
    club_id         text not null references public.clubs (id) on delete cascade,
    user_id         text not null,
    user_name       text not null default '',
    character_badge text not null default 'The Explorer',
    is_verified     boolean not null default true,
    role            text not null default 'MEMBER' check (role in ('OWNER', 'MEMBER')),
    status          text not null default 'ACTIVE',
    joined_at       timestamptz not null default now(),
    primary key (club_id, user_id)
);

create index if not exists club_members_user_idx on public.club_members (user_id);

-- Database-level guarantee: nobody can push a club past 15 members
create or replace function public.enforce_club_member_cap()
returns trigger language plpgsql as $$
begin
    if (select count(*) from public.club_members where club_id = new.club_id) >= 15 then
        raise exception 'Club is full (max 15 members)';
    end if;
    return new;
end $$;

drop trigger if exists club_members_cap on public.club_members;
create trigger club_members_cap
    before insert on public.club_members
    for each row execute function public.enforce_club_member_cap();


-- ============================================================================
-- 8. CLUB MESSAGES  (group chat)
-- ============================================================================
create table if not exists public.club_messages (
    id                     uuid primary key default gen_random_uuid(),
    club_id                text not null references public.clubs (id) on delete cascade,
    sender_id              text not null,
    sender_name            text not null default '',
    message_type           text not null default 'TEXT' check (message_type in ('TEXT', 'STICKER', 'VOICE', 'SYSTEM')),
    text                   text not null default '',
    sticker_emoji          text,
    voice_duration_seconds int,
    reply_to_text          text,
    reply_to_sender        text,
    created_at             timestamptz not null default now()
);

create index if not exists club_messages_club_idx
    on public.club_messages (club_id, created_at desc);


-- ============================================================================
-- 9. NOTIFICATIONS
-- ============================================================================
create table if not exists public.notifications (
    id         uuid primary key default gen_random_uuid(),
    user_id    text not null,
    title      text not null,
    message    text not null default '',
    type       text not null default 'SYSTEM' check (type in ('MATCH', 'MESSAGE', 'GAME', 'LIKE', 'SYSTEM')),
    is_read    boolean not null default false,
    created_at timestamptz not null default now()
);

create index if not exists notifications_user_idx on public.notifications (user_id, created_at desc);


-- ============================================================================
-- 10. GAMES CATALOG  (content served to the Games Hub screen)
-- ============================================================================
create table if not exists public.games (
    id            text primary key,
    name          text not null,
    description   text not null default '',
    is_free       boolean not null default false,
    tag           text not null default 'PREMIUM',
    players_count text not null default '2 Players'
);


-- ============================================================================
-- 11. GAME PROMPTS  (Truth or Dare content)
-- ============================================================================
create table if not exists public.game_prompts (
    id         text primary key,
    category   text not null default 'Flirty',   -- Flirty | Funny | Deep | First Date
    type       text not null default 'TRUTH',    -- TRUTH | DARE
    text       text not null,
    difficulty text not null default 'Medium'    -- Mild | Medium | Deep | Spicy
);


-- ============================================================================
-- 12. INTEREST CATALOG  (admin-manageable system interests)
--     Serves the onboarding chips + Personal Information sheet.
--     Add rows here to extend the catalog without an app release.
-- ============================================================================
create table if not exists public.interests (
    id   int  primary key generated by default as identity,
    name text not null unique
);


-- ============================================================================
-- 13. HOBBY CATALOG  (admin-manageable system hobbies)
-- ============================================================================
create table if not exists public.hobbies (
    id   int  primary key generated by default as identity,
    name text not null unique
);


-- ============================================================================
-- SEED DATA (bundled app catalog — safe to extend with your own rows)
-- ============================================================================
insert into public.games (id, name, description, is_free, tag, players_count) values
    ('game_truth_or_dare', 'Truth or Dare', 'The ultimate classic playful dating icebreaker. Pick Truth for intimate revelations or Dare for spontaneous fun.', true,  'FREE',    '2 Players'),
    ('game_would_you_rather', 'Would You Rather', 'Hilarious and intriguing moral dilemma questions that reveal true priorities and red/green flags.', false, 'PREMIUM', '2 Players'),
    ('game_this_or_that', 'This or That', 'Rapid-fire preference showdowns (Night owl vs Early bird, Beach vs Mountains, Books vs Movies).', false, 'PREMIUM', '2 Players'),
    ('game_two_truths_and_lie', 'Two Truths & A Lie', 'Spot the bluff! Three outrageous claims, but only two actually happened. Can you guess the lie?', false, 'PREMIUM', '2 Players'),
    ('game_rapid_questions', 'Rapid Questions', 'Timed lightning round: answer 5 personal questions in 60 seconds with zero hesitation.', false, 'PREMIUM', '2 Players'),
    ('game_conversation_cards', 'Conversation Cards', 'Thought-provoking curated decks for deeper emotional connection and core values discovery.', false, 'PREMIUM', '2 Players'),
    ('game_couples_challenge', 'Couples Challenge', 'Interactive compatibility challenges designed to see how well you sync under playful pressure.', false, 'PREMIUM', '2 Players'),
    ('game_deep_questions', 'Deep Questions', 'Skip the surface: explore dreams, existential theories, and transformative life lessons.', false, 'PREMIUM', '2 Players')
on conflict (id) do nothing;

insert into public.game_prompts (id, category, type, text, difficulty) values
    ('tod_1',  'Flirty',     'TRUTH', 'What''s the most romantic thing someone has ever done for you?', 'Mild'),
    ('tod_2',  'Flirty',     'TRUTH', 'What is an instant green flag that makes your heart skip a beat?', 'Mild'),
    ('tod_3',  'Flirty',     'TRUTH', 'If our first date was tonight, what would be the ideal vibe?', 'Medium'),
    ('tod_4',  'Flirty',     'DARE',  'Send a voice note giving your best sincere compliment in 5 seconds.', 'Spicy'),
    ('tod_5',  'Flirty',     'DARE',  'Send an emoji combo that secretly describes our chemistry so far.', 'Mild'),
    ('tod_6',  'Funny',      'TRUTH', 'What''s the most embarrassing fashion phase you went through as a teenager?', 'Mild'),
    ('tod_7',  'Funny',      'TRUTH', 'What is the weirdest habit you have when you''re completely alone?', 'Medium'),
    ('tod_8',  'Funny',      'DARE',  'Type out the last 3 items in your recent search history without context.', 'Medium'),
    ('tod_9',  'Deep',       'TRUTH', 'What is a life lesson you had to learn the hard way that you''re grateful for?', 'Medium'),
    ('tod_10', 'Deep',       'TRUTH', 'What is a passion or dream you haven''t shared with most people?', 'Deep'),
    ('tod_11', 'First Date', 'TRUTH', 'What''s your golden rule for what makes a great first date?', 'Mild'),
    ('tod_12', 'First Date', 'DARE',  'Choose our theoretical first date cocktail/drink order right now.', 'Mild')
on conflict (id) do nothing;


-- Interest catalog seed (mirrors AppContent.interestCatalog — 36 entries)
insert into public.interests (name) values
    ('Music'), ('Movies'), ('Travel'), ('Fitness'), ('Cooking'), ('Gaming'),
    ('Photography'), ('Art'), ('Dancing'), ('Reading'), ('Hiking'), ('Coffee'),
    ('Pets'), ('Sports'), ('Fashion'), ('Technology'), ('Yoga'), ('Cycling'),
    ('Foodie'), ('Volunteering'), ('Comedy'), ('Theatre'), ('Writing'), ('Singing'),
    ('Swimming'), ('Running'), ('Camping'), ('Astrology'), ('Board Games'),
    ('Anime'), ('Podcasts'), ('Wine & Dine'), ('Motorcycles'), ('Gardening'),
    ('Startups'), ('DIY & Crafts')
on conflict (name) do nothing;

-- Hobby catalog seed (mirrors AppContent.hobbyCatalog — 10 entries)
insert into public.hobbies (name) values
    ('Photography'), ('Playing Cricket'), ('Painting'), ('Hiking'), ('Cooking'),
    ('Playing Musical Instruments'), ('Reading'), ('Football'), ('Dancing'), ('Gaming')
on conflict (name) do nothing;


-- ============================================================================
-- ROW LEVEL SECURITY
--   The mobile app authenticates with Supabase Auth and only ever holds
--   the ANON key, so every table needs explicit policies.
-- ============================================================================
alter table public.profiles       enable row level security;
alter table public.user_interests enable row level security;
alter table public.likes          enable row level security;
alter table public.matches        enable row level security;
alter table public.messages       enable row level security;
alter table public.clubs          enable row level security;
alter table public.club_members   enable row level security;
alter table public.club_messages  enable row level security;
alter table public.notifications  enable row level security;
alter table public.games          enable row level security;
alter table public.game_prompts   enable row level security;
alter table public.interests      enable row level security;
alter table public.hobbies        enable row level security;

-- profiles: everyone logged-in can browse candidates; you only edit yours
drop policy if exists "profiles_select" on public.profiles;
create policy "profiles_select" on public.profiles      for select using (auth.role() = 'authenticated');
drop policy if exists "profiles_insert" on public.profiles;
create policy "profiles_insert" on public.profiles      for insert with check (auth.uid() = id);
drop policy if exists "profiles_update" on public.profiles;
create policy "profiles_update" on public.profiles      for update using (auth.uid() = id);
drop policy if exists "profiles_delete" on public.profiles;
create policy "profiles_delete" on public.profiles      for delete using (auth.uid() = id);

-- user_interests (matching / filters): public read, self-manage
drop policy if exists "interests_select" on public.user_interests;
create policy "interests_select" on public.user_interests for select using (auth.role() = 'authenticated');
drop policy if exists "interests_insert" on public.user_interests;
create policy "interests_insert" on public.user_interests for insert with check (auth.uid() = user_id);
drop policy if exists "interests_delete" on public.user_interests;
create policy "interests_delete" on public.user_interests for delete using (auth.uid() = user_id);

-- likes: you see who YOU liked; others stay private (see_who_liked_you is a premium feature)
drop policy if exists "likes_select" on public.likes;
create policy "likes_select"  on public.likes  for select using (auth.uid() = user_id or (auth.uid() = target_user_id and action = 'LIKE'));
drop policy if exists "likes_insert" on public.likes;
create policy "likes_insert" on public.likes  for insert with check (auth.uid() = user_id);
drop policy if exists "likes_delete" on public.likes;
create policy "likes_delete" on public.likes  for delete using (auth.uid() = user_id);

-- matches: only the two participants
drop policy if exists "matches_select" on public.matches;
create policy "matches_select" on public.matches for select using (auth.uid() = user_a_id or auth.uid() = user_b_id);
drop policy if exists "matches_update" on public.matches;
create policy "matches_update" on public.matches for update using (auth.uid() = user_a_id or auth.uid() = user_b_id);
drop policy if exists "matches_delete" on public.matches;
create policy "matches_delete" on public.matches for delete using (auth.uid() = user_a_id or auth.uid() = user_b_id);

-- messages: only conversation participants read/write
-- (tighten with a participant lookup once the conversations table lands)
drop policy if exists "messages_select" on public.messages;
create policy "messages_select" on public.messages for select using (auth.role() = 'authenticated');
drop policy if exists "messages_insert" on public.messages;
create policy "messages_insert" on public.messages for insert with check (auth.role() = 'authenticated');

-- clubs: readable by all authenticated (needed for discovery)
drop policy if exists "clubs_select" on public.clubs;
create policy "clubs_select" on public.clubs for select using (auth.role() = 'authenticated');
drop policy if exists "clubs_insert" on public.clubs;
create policy "clubs_insert" on public.clubs for insert with check (auth.role() = 'authenticated');
drop policy if exists "clubs_update" on public.clubs;
create policy "clubs_update" on public.clubs for update using (auth.role() = 'authenticated');
drop policy if exists "clubs_delete" on public.clubs;
create policy "clubs_delete" on public.clubs for delete using (auth.role() = 'authenticated');

-- club_members: public read (member lists), members/owners manage rows
drop policy if exists "club_members_select" on public.club_members;
create policy "club_members_select" on public.club_members for select using (auth.role() = 'authenticated');
drop policy if exists "club_members_insert" on public.club_members;
create policy "club_members_insert" on public.club_members for insert with check (auth.role() = 'authenticated');
drop policy if exists "club_members_delete" on public.club_members;
create policy "club_members_delete" on public.club_members for delete using (auth.role() = 'authenticated');

-- club_messages: club chat is open to authenticated users
-- (tighten with a club-membership EXISTS check when auth users are wired)
drop policy if exists "club_messages_select" on public.club_messages;
create policy "club_messages_select" on public.club_messages for select using (auth.role() = 'authenticated');
drop policy if exists "club_messages_insert" on public.club_messages;
create policy "club_messages_insert" on public.club_messages for insert with check (auth.role() = 'authenticated');

-- notifications: strictly private per user
drop policy if exists "notifications_select" on public.notifications;
create policy "notifications_select" on public.notifications for select using (auth.uid()::text = user_id);
drop policy if exists "notifications_insert" on public.notifications;
create policy "notifications_insert" on public.notifications for insert with check (auth.uid()::text = user_id);
drop policy if exists "notifications_update" on public.notifications;
create policy "notifications_update" on public.notifications for update using (auth.uid()::text = user_id);
drop policy if exists "notifications_delete" on public.notifications;
create policy "notifications_delete" on public.notifications for delete using (auth.uid()::text = user_id);

-- games & prompts: read-only content catalog for everyone
drop policy if exists "games_select"        on public.games;
create policy "games_select"        on public.games       for select using (auth.role() = 'authenticated');
drop policy if exists "game_prompts_select" on public.game_prompts;
create policy "game_prompts_select" on public.game_prompts for select using (auth.role() = 'authenticated');

-- interests & hobbies catalogs: public read — plain catalog names (the same
-- list ships inside the app bundle), readable before sign-in so the
-- onboarding chips always load. Manage rows from the dashboard only.
drop policy if exists "interests_catalog_select" on public.interests;
create policy "interests_catalog_select" on public.interests for select using (true);

drop policy if exists "hobbies_catalog_select" on public.hobbies;
create policy "hobbies_catalog_select" on public.hobbies for select using (true);


-- ============================================================================
-- STORAGE BUCKETS  (names match SupabaseConfig.kt)
-- ============================================================================
insert into storage.buckets (id, name, public)
values ('profile-photos', 'profile-photos', true)
on conflict (id) do nothing;

insert into storage.buckets (id, name, public)
values ('voice-notes', 'voice-notes', false)
on conflict (id) do nothing;

insert into storage.buckets (id, name, public)
values ('stickers', 'stickers', true)
on conflict (id) do nothing;

-- Public read for the public buckets; authenticated users can upload
drop policy if exists "profile_photos_public_read" on storage.objects;
create policy "profile_photos_public_read" on storage.objects
    for select using (bucket_id = 'profile-photos');

drop policy if exists "profile_photos_authenticated_upload" on storage.objects;
create policy "profile_photos_authenticated_upload" on storage.objects
    for insert with check (bucket_id = 'profile-photos' and auth.role() = 'authenticated');

drop policy if exists "stickers_public_read" on storage.objects;
create policy "stickers_public_read" on storage.objects
    for select using (bucket_id = 'stickers');

-- voice notes stay private to their owner path (<user_id>/...)
drop policy if exists "voice_notes_owner_all" on storage.objects;
create policy "voice_notes_owner_all" on storage.objects
    for all using (
        bucket_id = 'voice-notes'
        and (storage.foldername(name))[1] = auth.uid()::text
    );


-- ============================================================================
-- QUICKY v2 — "PREMIUM CASSY" EDITION (PRD §6, §9.3)
--   • get_discovery_profiles  server-side ranked discovery (PRD §6.1)
--   • rate_limits + check_rate_limit  sliding-window abuse prevention (§6.2)
--   • record_swipe             atomic LIKE/PASS + mutual-match creation
--   • reports / subscriptions / boosts / message_reactions /
--     game_sessions / club_events  new v2 tables (§9.3)
--   • input validation CHECKs + duplicate-message trigger (§6.3, §6.5)
-- Everything below is idempotent — safe to re-run.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- v2.1  INPUT VALIDATION (PRD §6.3)
-- ----------------------------------------------------------------------------
do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'profiles_bio_length'
  ) then
    alter table public.profiles
      add constraint profiles_bio_length check (char_length(bio) <= 500);
  end if;

  if not exists (
    select 1 from pg_constraint where conname = 'profiles_latitude_range'
  ) then
    alter table public.profiles
      add constraint profiles_latitude_range check (latitude between -90 and 90);
  end if;

  if not exists (
    select 1 from pg_constraint where conname = 'profiles_longitude_range'
  ) then
    alter table public.profiles
      add constraint profiles_longitude_range check (longitude between -180 and 180);
  end if;

  if not exists (
    select 1 from pg_constraint where conname = 'messages_text_length'
  ) then
    alter table public.messages
      add constraint messages_text_length check (char_length(text) <= 2000);
  end if;
end
$$;

-- Strip HTML tags from profiles.bio before it lands (text sanitization)
create or replace function public.sanitize_profile_bio()
returns trigger
language plpgsql
as $$
begin
  new.bio := regexp_replace(new.bio, '<[^>]*>', '', 'g');
  new.name := regexp_replace(new.name, '<[^>]*>', '', 'g');
  return new;
end;
$$;

drop trigger if exists profiles_sanitize_bio on public.profiles;
create trigger profiles_sanitize_bio
  before insert or update of bio, name on public.profiles
  for each row execute function public.sanitize_profile_bio();

-- Reject identical messages sent within 60 seconds (PRD §6.5)
create or replace function public.reject_duplicate_message()
returns trigger
language plpgsql
as $$
begin
  if new.text <> '' and exists (
    select 1 from public.messages m
    where m.conversation_id = new.conversation_id
      and m.sender_id = new.sender_id
      and m.text = new.text
      and m.created_at > now() - interval '60 seconds'
  ) then
    raise exception 'DUPLICATE_MESSAGE: identical text within 60 seconds';
  end if;
  return new;
end;
$$;

drop trigger if exists messages_dedup on public.messages;
create trigger messages_dedup
  before insert on public.messages
  for each row execute function public.reject_duplicate_message();


-- ----------------------------------------------------------------------------
-- v2.2  RATE LIMITING (PRD §6.2)
--   Sliding-window counter kept in PostgreSQL; the security-definer
--   helper below is what RPCs call before accepting a write.
-- ----------------------------------------------------------------------------
create table if not exists public.rate_limits (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references public.profiles (id) on delete cascade,
  action        text not null,
  window_start  timestamptz not null default now(),
  request_count int not null default 0,
  unique (user_id, action, window_start)
);

create index if not exists rate_limits_user_idx
  on public.rate_limits (user_id, action, window_start desc);

-- Counts & records one request; returns false when the quota is exhausted.
create or replace function public.check_rate_limit(
  p_user_id        uuid,
  p_action         text,
  p_max_requests   int,
  p_window_seconds int default 3600
)
returns boolean
language plpgsql
security definer
as $$
declare
  v_count int;
begin
  if p_user_id is null or p_action is null or p_max_requests is null then
    return false;
  end if;

  -- Purge stale windows for this user + action.
  delete from public.rate_limits
  where user_id = p_user_id
    and action = p_action
    and window_start < now() - make_interval(secs => p_window_seconds);

  select coalesce(sum(request_count), 0) into v_count
  from public.rate_limits
  where user_id = p_user_id
    and action = p_action
    and window_start > now() - make_interval(secs => p_window_seconds);

  if v_count >= p_max_requests then
    return false;
  end if;

  insert into public.rate_limits (user_id, action, window_start, request_count)
  values (p_user_id, p_action, date_trunc('hour', now()), 1)
  on conflict (user_id, action, window_start)
  do update set request_count = public.rate_limits.request_count + 1;

  return true;
end;
$$;


-- ----------------------------------------------------------------------------
-- v2.3  SERVER-SIDE DISCOVERY FILTERING (PRD §6.1)
--   Ranked candidate profiles: compatibility score (shared interests +
--   intent match + verification + online bonus), Haversine distance,
--   already-swiped exclusion — all enforced IN the database so the client
--   can neither bypass filters nor over-fetch.
-- ----------------------------------------------------------------------------
create or replace function public.get_discovery_profiles(
  p_user_id           uuid,
  p_min_age           int  default 18,
  p_max_age           int  default 99,
  p_max_distance_km   int  default 500,
  p_gender            text default null,
  p_intent            text default null,
  p_verified_only     boolean default false,
  p_occupation        text default null,
  p_shared_interests  text[] default '{}',
  p_languages         text[] default '{}',
  p_limit             int  default 20,
  p_offset            int  default 0
)
returns table (
  profile_id         uuid,
  name                text,
  age                 int,
  bio                 text,
  city                text,
  distance_km        real,
  photo_urls          text[],
  interests          text[],
  languages          text[],
  compatibility_score int,
  is_verified         boolean,
  relationship_intent text,
  last_active         timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
begin
  -- The caller may only ever query their OWN discovery deck.
  if p_user_id is distinct from auth.uid() then
    raise exception 'FORBIDDEN: p_user_id must match the authenticated user';
  end if;

  return query
  select
    ranked.profile_id,
    ranked.name,
    ranked.age,
    ranked.bio,
    ranked.city,
    ranked.dist_km as distance_km,
    ranked.photo_urls,
    ranked.interests,
    ranked.languages,
    ranked.compat as compatibility_score,
    ranked.is_verified,
    ranked.relationship_intent,
    ranked.last_active
  from (
    select
      p.id as profile_id,
      p.name,
      p.age,
      p.bio,
      p.city,
      case
        when up.latitude is not null and p.latitude is not null then
          (6371 * acos(
            least(1.0, greatest(-1.0,
              cos(radians(up.latitude)) * cos(radians(p.latitude)) *
              cos(radians(p.longitude) - radians(up.longitude)) +
              sin(radians(up.latitude)) * sin(radians(p.latitude))
            ))
          ))::real
        else p.distance_km::real
      end as dist_km,
      p.photo_urls,
      p.interests,
      p.languages,
      (
        (select count(*) from unnest(p.interests) i
          where i = any(up.interests)) * 10
        + case when p.relationship_intent = up.relationship_intent then 20 else 0 end
        + case when p.is_verified then 15 else 0 end
        + case when p.is_online then 10 else 0 end
        + case when p.languages && up.languages then 15 else 0 end
      )::int as compat,
      p.is_verified,
      p.relationship_intent,
      p.updated_at as last_active
    from public.profiles p
    cross join public.profiles up
    where up.id = p_user_id
      and p.id <> p_user_id
      and p.onboarding_completed = true
      and p.age between p_min_age and p_max_age
      and not exists (
        select 1 from public.likes l
        where l.user_id = p_user_id and l.target_user_id = p.id
      )
      and (p_gender is null or p.gender = p_gender)
      and (p_intent is null or p.relationship_intent = p_intent)
      and (p_verified_only = false or p.is_verified)
      and (p_occupation is null or p.occupation ilike '%' || p_occupation || '%')
      and (cardinality(p_shared_interests) = 0 or p.interests && p_shared_interests)
      and (cardinality(p_languages) = 0 or p.languages && p_languages)
  ) ranked
  where (p_max_distance_km is null or ranked.dist_km <= p_max_distance_km)
  order by ranked.compat desc, ranked.dist_km asc
  limit p_limit offset p_offset;
end;
$$;


-- ----------------------------------------------------------------------------
-- v2.4  ATOMIC SWIPE RECORDING + MUTUAL MATCH (PRD §6.1 / §6.2)
--   Likes and passes are written server-side; a mutual LIKE/SUPER_LIKE
--   pair creates the match row in the same transaction. Swipe quota:
--   200/hour, 1000/day (PRD: 50/hour, 200/day with headroom for pass
--   actions — tighten to taste).
-- ----------------------------------------------------------------------------
create or replace function public.record_swipe(
  p_user_id        uuid,
  p_target_user_id uuid,
  p_action         text
)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_hour_ok boolean;
  v_day_ok  boolean;
  v_mutual  boolean;
  v_a       uuid;
  v_b       uuid;
begin
  -- You may only record swipes for yourself.
  if p_user_id is distinct from auth.uid() then
    return 'FORBIDDEN';
  end if;

  if p_action not in ('LIKE', 'PASS', 'SUPER_LIKE') then
    return 'INVALID_ACTION';
  end if;
  if p_user_id is null or p_target_user_id is null or p_user_id = p_target_user_id then
    return 'INVALID_TARGET';
  end if;

  select public.check_rate_limit(p_user_id, 'swipe_hour', 200, 3600) into v_hour_ok;
  if not v_hour_ok then
    return 'RATE_LIMITED_HOURLY';
  end if;
  select public.check_rate_limit(p_user_id, 'swipe_day', 1000, 86400) into v_day_ok;
  if not v_day_ok then
    return 'RATE_LIMITED_DAILY';
  end if;

  insert into public.likes (user_id, target_user_id, action)
  values (p_user_id, p_target_user_id, p_action)
  on conflict (user_id, target_user_id)
  do update set action = excluded.action, created_at = now();

  if p_action in ('LIKE', 'SUPER_LIKE') then
    select exists (
      select 1 from public.likes l
      where l.user_id = p_target_user_id
        and l.target_user_id = p_user_id
        and l.action in ('LIKE', 'SUPER_LIKE')
    ) into v_mutual;

    if v_mutual then
      v_a := least(p_user_id, p_target_user_id);
      v_b := greatest(p_user_id, p_target_user_id);
      insert into public.matches (user_a_id, user_b_id)
      values (v_a, v_b)
      on conflict (user_a_id, user_b_id) do nothing;
      return 'MATCH';
    end if;
  end if;

  return 'OK';
end;
$$;

-- RPC access: authenticated users only (never anon).
revoke all on function public.get_discovery_profiles(uuid, int, int, int, text, text, boolean, text, text[], int, int) from public, anon;
grant execute on function public.get_discovery_profiles(uuid, int, int, int, text, text, boolean, text, text[], int, int) to authenticated;

revoke all on function public.record_swipe(uuid, uuid, text) from public, anon;
grant execute on function public.record_swipe(uuid, uuid, text) to authenticated;


-- ----------------------------------------------------------------------------
-- v2.5  NEW v2 TABLES (PRD §9.3)
-- ----------------------------------------------------------------------------

-- Reports & safety
create table if not exists public.reports (
  id          uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.profiles (id) on delete cascade,
  reported_id uuid not null references public.profiles (id) on delete cascade,
  reason      text not null check (reason in ('SPAM', 'INAPPROPRIATE', 'FAKE_PROFILE', 'HARASSMENT', 'UNDERAGE', 'OTHER')),
  details     text,
  status      text not null default 'PENDING' check (status in ('PENDING', 'REVIEWED', 'ACTIONED', 'DISMISSED')),
  created_at  timestamptz not null default now(),
  check (reporter_id <> reported_id)
);

create index if not exists reports_reported_idx on public.reports (reported_id, status);

-- Subscription tiers (Quicky+ / Gold — RevenueCat webhook writes here)
create table if not exists public.subscriptions (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references public.profiles (id) on delete cascade,
  tier          text not null check (tier in ('FREE', 'PLUS', 'GOLD')),
  started_at    timestamptz not null default now(),
  expires_at    timestamptz,
  revenuecat_id text,
  is_active     boolean not null default true
);

create index if not exists subscriptions_user_idx on public.subscriptions (user_id) where is_active;

-- 30-minute visibility boosts
create table if not exists public.boosts (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references public.profiles (id) on delete cascade,
  started_at timestamptz not null default now(),
  expires_at timestamptz not null,
  is_active  boolean not null default true
);

create index if not exists boosts_user_idx on public.boosts (user_id) where is_active;

-- Emoji reactions on chat messages
create table if not exists public.message_reactions (
  id         uuid primary key default gen_random_uuid(),
  message_id uuid not null references public.messages (id) on delete cascade,
  user_id    uuid not null references public.profiles (id) on delete cascade,
  emoji      text not null,
  created_at timestamptz not null default now(),
  unique (message_id, user_id, emoji)
);

-- Turn-based game sessions (solo / couple / group)
create table if not exists public.game_sessions (
  id         uuid primary key default gen_random_uuid(),
  host_id    uuid not null references public.profiles (id),
  game_id    text not null references public.games (id),
  mode       text not null check (mode in ('SOLO', 'COUPLE', 'GROUP')),
  status     text not null default 'ACTIVE' check (status in ('ACTIVE', 'PAUSED', 'COMPLETED')),
  created_at timestamptz not null default now()
);

create index if not exists game_sessions_host_idx on public.game_sessions (host_id, status);

-- Club events & RSVPs
create table if not exists public.club_events (
  id          uuid primary key default gen_random_uuid(),
  club_id     text not null references public.clubs (id) on delete cascade,
  title       text not null,
  description text,
  location    text,
  starts_at   timestamptz not null,
  created_by  uuid not null references public.profiles (id),
  created_at  timestamptz not null default now()
);

create index if not exists club_events_club_idx on public.club_events (club_id, starts_at);


-- ----------------------------------------------------------------------------
-- v2.6  ROW LEVEL SECURITY FOR THE NEW TABLES
-- ----------------------------------------------------------------------------
alter table public.rate_limits        enable row level security;
alter table public.reports            enable row level security;
alter table public.subscriptions      enable row level security;
alter table public.boosts             enable row level security;
alter table public.message_reactions  enable row level security;
alter table public.game_sessions      enable row level security;
alter table public.club_events        enable row level security;

-- rate_limits: only the (security-definer) RPCs touch this; a user may
-- read their own counters for transparency. No client writes.
drop policy if exists "rate_limits_select_own" on public.rate_limits;
create policy "rate_limits_select_own" on public.rate_limits
  for select using (auth.uid() = user_id);

-- reports: you can file one and review your own; moderation reads all.
drop policy if exists "reports_select_own" on public.reports;
create policy "reports_select_own" on public.reports
  for select using (auth.uid() = reporter_id);
drop policy if exists "reports_insert_own" on public.reports;
create policy "reports_insert_own" on public.reports
  for insert with check (auth.uid() = reporter_id);

-- subscriptions & boosts: strictly private to the owner (system writes
-- via service role / security definer functions).
drop policy if exists "subscriptions_select_own" on public.subscriptions;
create policy "subscriptions_select_own" on public.subscriptions
  for select using (auth.uid() = user_id);

drop policy if exists "boosts_select_own" on public.boosts;
create policy "boosts_select_own" on public.boosts
  for select using (auth.uid() = user_id);

-- message reactions: participants can read; you react as yourself only.
drop policy if exists "message_reactions_select" on public.message_reactions;
create policy "message_reactions_select" on public.message_reactions
  for select using (auth.role() = 'authenticated');
drop policy if exists "message_reactions_insert_own" on public.message_reactions;
create policy "message_reactions_insert_own" on public.message_reactions
  for insert with check (auth.uid() = user_id);
drop policy if exists "message_reactions_delete_own" on public.message_reactions;
create policy "message_reactions_delete_own" on public.message_reactions
  for delete using (auth.uid() = user_id);

-- game sessions: readable to authenticated users; hosts create their own.
drop policy if exists "game_sessions_select" on public.game_sessions;
create policy "game_sessions_select" on public.game_sessions
  for select using (auth.role() = 'authenticated');
drop policy if exists "game_sessions_insert_host" on public.game_sessions;
create policy "game_sessions_insert_host" on public.game_sessions
  for insert with check (auth.uid() = host_id);
drop policy if exists "game_sessions_update_host" on public.game_sessions;
create policy "game_sessions_update_host" on public.game_sessions
  for update using (auth.uid() = host_id);

-- club events: members browse; club members create.
drop policy if exists "club_events_select" on public.club_events;
create policy "club_events_select" on public.club_events
  for select using (auth.role() = 'authenticated');
drop policy if exists "club_events_insert_member" on public.club_events;
create policy "club_events_insert_member" on public.club_events
  for insert with check (
    auth.uid() = created_by
    and exists (
      select 1 from public.club_members cm
      where cm.club_id = club_id and cm.user_id = auth.uid()::text
    )
  );


-- ============================================================================
-- QUICKY v2.1 — POLISH, LUDO ARENA, MONETIZATION & CLUB LIFECYCLE
--   • Ludo Arena 4-player online tables + server-authoritative helpers
--   • Club deletion (owner only) + member notifications
--   • premium_gate() — centralized premium check (QA override)
-- Everything below is idempotent — safe to re-run.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- v2.1 §3.8  CLUB DELETION — OWNER ONLY
-- ----------------------------------------------------------------------------

-- Only the owner may delete a club (RLS). The old policy allowed any
-- authenticated user — that hole is closed here.
drop policy if exists "clubs_delete" on public.clubs;
create policy "clubs_delete" on public.clubs
  for delete using (owner_id = auth.uid()::text);

-- FK cascades: members / messages / events disappear with the club.
-- (club_members + club_messages already cascade on delete; re-asserted
-- for safety. club_events references clubs too — same treatment.)
do $$
begin
  if exists (select 1 from information_schema.tables
             where table_schema = 'public' and table_name = 'club_events') then
    alter table public.club_events
      drop constraint if exists club_events_club_id_fkey;
    alter table public.club_events
      add constraint club_events_club_id_fkey
        foreign key (club_id) references public.clubs (id) on delete cascade;
  end if;
end
$$;

-- Notifications type gains CLUB_DELETED.
do $$
begin
  -- Rebuild the CHECK so the new type is allowed (and future-proof LUDO too).
  alter table public.notifications drop constraint if exists notifications_type_check;
  alter table public.notifications
    add constraint notifications_type_check
    check (type in ('MATCH', 'MESSAGE', 'GAME', 'LIKE', 'SYSTEM',
                    'CLUB_DELETED', 'LUDO'));
exception
  when duplicate_object then null; -- constraint name collision — next run fixes it
end
$$;

-- Member notification on club deletion.
-- IMPORTANT: this fires BEFORE the row delete so club_members rows are
-- still readable — an AFTER DELETE trigger would run after the FK cascade
-- has already wiped the member list (classic Postgres gotcha).
create or replace function public.notify_club_deletion()
returns trigger
language plpgsql
security definer
as $$
begin
  insert into public.notifications (user_id, type, title, message)
  select m.user_id,
         'CLUB_DELETED',
         'Club deleted',
         'The club "' || old.name || '" was deleted by the owner.'
  from public.club_members m
  where m.club_id = old.id;

  return old;
end;
$$;

drop trigger if exists trg_club_deletion on public.clubs;
create trigger trg_club_deletion
  before delete on public.clubs
  for each row execute function public.notify_club_deletion();

-- ----------------------------------------------------------------------------
-- v2.1 §3.7  PREMIUM GATE — centralized server-side check
-- ----------------------------------------------------------------------------
-- premium_gate() returns whether the caller has Quicky Gold.
--
-- QA OVERRIDE (v2.1): to validate every premium feature end-to-end before
-- v2.2 re-enables monetization, flip the constant below to true IN A
-- QA-SPECIFIC Supabase project only — NEVER in production:
--
--   create or replace function public.premium_gate_qa() ...
--
-- The default stays the REAL check so production behavior is unchanged.
create or replace function public.premium_gate(p_user_id text)
returns boolean
language sql
stable
security definer
as $$
  select coalesce(
    (select (s->>'is_premium')::boolean
     from public.subscriptions s
     where s.user_id = p_user_id
       and s.status = 'ACTIVE'
     order by s.created_at desc
     limit 1),
    false
  );
$$;

-- ----------------------------------------------------------------------------
-- v2.1 §3.2.4  LUDO ARENA — ONLINE 4-PLAYER TABLES
-- ----------------------------------------------------------------------------

create table if not exists public.ludo_matches (
    id          text primary key,          -- short shareable code, e.g. "LA7K2QD"
    host_id     text not null,
    status      text not null default 'IN_PROGRESS'
                check (status in ('WAITING', 'IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    winner_id   text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create table if not exists public.ludo_players (
    match_id  text not null references public.ludo_matches (id) on delete cascade,
    user_id   text not null,              -- auth uid, or "bot_seat_N" for bots
    name      text not null default '',
    seat      int  not null check (seat between 0 and 3), -- 0=RED..3=BLUE
    is_bot    boolean not null default false,
    joined_at timestamptz not null default now(),
    primary key (match_id, user_id),
    unique (match_id, seat)
);

create index if not exists ludo_players_user_idx on public.ludo_players (user_id);

create table if not exists public.ludo_game_state (
    match_id   text primary key references public.ludo_matches (id) on delete cascade,
    turn       int  not null default 0,
    dice_value int,
    board_state jsonb not null,
    updated_at timestamptz not null default now()
);

-- Full audit log of every accepted move (who moved what, with which dice).
create table if not exists public.ludo_moves (
    id         uuid primary key default gen_random_uuid(),
    match_id   text not null references public.ludo_matches (id) on delete cascade,
    user_id    text not null,
    from_cell  int  not null default 0,
    to_cell    int  not null default 0,
    dice       int  not null,
    created_at timestamptz not null default now()
);

create index if not exists ludo_moves_match_idx
  on public.ludo_moves (match_id, created_at desc);

-- Room chat for a match — human messages only (system logs are an
-- anti-requirement in v2.1 §3.2.3).
create table if not exists public.ludo_chat_messages (
    id                     uuid primary key default gen_random_uuid(),
    match_id               text not null references public.ludo_matches (id) on delete cascade,
    sender_id              text not null,
    sender_name            text not null default '',
    text                   text not null default '',
    sticker_emoji          text,
    voice_duration_seconds int,
    created_at             timestamptz not null default now()
);

create index if not exists ludo_chat_match_idx
  on public.ludo_chat_messages (match_id, created_at asc);

-- ----- Ludo RLS -----
alter table public.ludo_matches        enable row level security;
alter table public.ludo_players       enable row level security;
alter table public.ludo_game_state     enable row level security;
alter table public.ludo_moves          enable row level security;
alter table public.ludo_chat_messages  enable row level security;

drop policy if exists "ludo_matches_select" on public.ludo_matches;
create policy "ludo_matches_select" on public.ludo_matches
  for select using (auth.role() = 'authenticated');
drop policy if exists "ludo_matches_insert" on public.ludo_matches;
create policy "ludo_matches_insert" on public.ludo_matches
  for insert with check (auth.role() = 'authenticated');
drop policy if exists "ludo_matches_delete" on public.ludo_matches;
create policy "ludo_matches_delete" on public.ludo_matches
  for delete using (host_id = auth.uid()::text);

drop policy if exists "ludo_players_select" on public.ludo_players;
create policy "ludo_players_select" on public.ludo_players
  for select using (auth.role() = 'authenticated');
drop policy if exists "ludo_players_insert" on public.ludo_players;
create policy "ludo_players_insert" on public.ludo_players
  for insert with check (auth.role() = 'authenticated');

drop policy if exists "ludo_state_select" on public.ludo_game_state;
create policy "ludo_state_select" on public.ludo_game_state
  for select using (auth.role() = 'authenticated');
drop policy if exists "ludo_state_insert" on public.ludo_game_state;
create policy "ludo_state_insert" on public.ludo_game_state
  for insert with check (auth.role() = 'authenticated');
-- Participants (humans + host-owned bots) may push state updates. This is
-- the fallback write path when the Edge Functions are not deployed; the
-- `apply_ludo_move` function remains the authoritative route.
drop policy if exists "ludo_state_update" on public.ludo_game_state;
create policy "ludo_state_update" on public.ludo_game_state
  for update using (
    auth.role() = 'authenticated' and exists (
      select 1 from public.ludo_players p
      where p.match_id = ludo_game_state.match_id
        and (p.user_id = auth.uid()::text or p.user_id like 'bot_seat_%')
    )
  );

drop policy if exists "ludo_moves_insert" on public.ludo_moves;
create policy "ludo_moves_insert" on public.ludo_moves
  for insert with check (auth.role() = 'authenticated');
drop policy if exists "ludo_moves_select" on public.ludo_moves;
create policy "ludo_moves_select" on public.ludo_moves
  for select using (auth.role() = 'authenticated');

drop policy if exists "ludo_chat_select" on public.ludo_chat_messages;
create policy "ludo_chat_select" on public.ludo_chat_messages
  for select using (auth.role() = 'authenticated');
drop policy if exists "ludo_chat_insert" on public.ludo_chat_messages;
create policy "ludo_chat_insert" on public.ludo_chat_messages
  for insert with check (auth.role() = 'authenticated');

-- ----------------------------------------------------------------------------
-- v2.1 §3.2.2  ROLL_LUDO_DICE RPC (server-side random)
-- ----------------------------------------------------------------------------
-- Used directly when Edge Functions are preferred over SQL. The Deno Edge
-- Function (supabase/functions/roll_ludo_dice) wraps the same logic with
-- JWT verification; this SQL variant is the always-available fallback:
--   select public.roll_ludo_dice('<match_id>');
-- It stores the roll into ludo_game_state and returns it.
create or replace function public.roll_ludo_dice(p_match_id text)
returns int
language plpgsql
security definer
as $$
declare
  v_dice int;
  v_state jsonb;
begin
  select board_state into v_state
    from public.ludo_game_state where match_id = p_match_id
    for update;

  if v_state is null then
    raise exception 'LUDO_MATCH_NOT_FOUND';
  end if;

  v_dice := 1 + floor(random() * 6)::int;

  v_state := v_state
    || jsonb_build_object(
         'dice_value', v_dice,
         'phase', 'AWAITING_MOVE',
         'updated_at', to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
       );

  update public.ludo_game_state
     set board_state = v_state,
         dice_value = v_dice,
         turn = coalesce((v_state->>'turn_index')::int, 0),
         updated_at = now()
   where match_id = p_match_id;

  return v_dice;
end;
$$;
