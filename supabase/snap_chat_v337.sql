-- ============================================================================
-- QUICKY v3.3.7 — SNAP PHOTOS + CLUB PERSONAL CHATS
-- ----------------------------------------------------------------------------
-- Run ONCE in the Supabase SQL editor on the existing project.
--
-- 1) Personal-chat snaps (Snapchat-style view-once photos):
--      · messages gains message_type / snap_path / snap_viewed
--      · new PRIVATE storage bucket `snap-images`
--      · security-definer RPC mark_snap_viewed(p_message_id): marks the row
--        viewed, nulls its path AND deletes the stored image — the photo is
--        permanently gone from the database at the instant it is viewed.
--
-- 2) Club personal chats (1:1 chats started from a club member list):
--      · profiles.allow_club_dm — receiver-side opt-in toggle (Settings >
--        Privacy); when false, the member 3-dot menu hides "Chat personally"
--      · new table club_dm_conversations — deterministic ids
--        "dm_<smallerUserId>_<largerUserId>" so both devices agree without
--        a server round-trip; messages ride the existing `messages` table.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) messages: snap columns
-- ---------------------------------------------------------------------------
alter table public.messages
    add column if not exists message_type text not null default 'TEXT';

alter table public.messages
    add column if not exists snap_path text;                       -- storage object path while UNVIEWED

alter table public.messages
    add column if not exists snap_viewed boolean not null default false;

create index if not exists messages_unviewed_snaps_idx
    on public.messages (snap_viewed)
    where message_type = 'SNAP' and snap_viewed = false;

-- ---------------------------------------------------------------------------
-- 2) profiles: club-DM opt-in flag (toggled by the receiver in Settings)
-- ---------------------------------------------------------------------------
alter table public.profiles
    add column if not exists allow_club_dm boolean not null default true;

-- ---------------------------------------------------------------------------
-- 3) club_dm_conversations — 1:1 chats started from a club member list
-- ---------------------------------------------------------------------------
create table if not exists public.club_dm_conversations (
    id          text primary key,                                  -- 'dm_<smaller_id>_<larger_id>'
    club_id     text not null,                                     -- the club it was started from
    user_a_id   text not null,                                     -- lexicographically smaller participant
    user_b_id   text not null,                                     -- lexicographically larger participant
    created_at  timestamptz not null default now(),
    unique (user_a_id, user_b_id)
);

create index if not exists club_dm_conversations_a_idx
    on public.club_dm_conversations (user_a_id);
create index if not exists club_dm_conversations_b_idx
    on public.club_dm_conversations (user_b_id);

alter table public.club_dm_conversations enable row level security;

drop policy if exists "club_dm_select" on public.club_dm_conversations;
create policy "club_dm_select" on public.club_dm_conversations
    for select using (auth.role() = 'authenticated');

drop policy if exists "club_dm_insert" on public.club_dm_conversations;
create policy "club_dm_insert" on public.club_dm_conversations
    for insert with check (auth.role() = 'authenticated');

-- ---------------------------------------------------------------------------
-- 4) PRIVATE storage bucket for unviewed snaps
--    (deleted object-side by mark_snap_viewed the moment one is viewed)
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public)
values ('snap-images', 'snap-images', false)
on conflict (id) do nothing;

drop policy if exists "snap_images_insert" on storage.objects;
create policy "snap_images_insert" on storage.objects
    for insert to authenticated
    with check (bucket_id = 'snap-images');

drop policy if exists "snap_images_select" on storage.objects;
create policy "snap_images_select" on storage.objects
    for select to authenticated
    using (bucket_id = 'snap-images');

-- NOTE on deletes: the RPC below (security definer) is the ONLY deleter —
-- receivers never need a client-side delete policy, so no user can destroy
-- someone else's unviewed snap through the storage API directly.

-- ---------------------------------------------------------------------------
-- 5) mark_snap_viewed — atomic "viewed" (row update + image destruction)
-- ---------------------------------------------------------------------------
create or replace function public.mark_snap_viewed(p_message_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_path text;
begin
    -- Fetch the current (unviewed) path.
    select snap_path into v_path
    from public.messages
    where id = p_message_id and message_type = 'SNAP';

    -- Permanently destroy the stored image FIRST — no window exists where
    -- the row is marked but the bytes survive.
    if v_path is not null then
        delete from storage.objects
        where bucket_id = 'snap-images' and name = v_path;
    end if;

    -- Flip the row to viewed; the path reference disappears with it.
    update public.messages
    set snap_viewed = true,
        snap_path = null
    where id = p_message_id;
end $$;

grant execute on function public.mark_snap_viewed(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 6) Optional hygiene: snaps sent before this migration can't exist yet;
--    nothing to clean. Kept as a placeholder for future rollbacks/cleanups.
-- ---------------------------------------------------------------------------
