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
