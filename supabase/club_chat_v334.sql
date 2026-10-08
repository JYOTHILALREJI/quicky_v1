-- ============================================================================
-- QUICKY v3.3.4 — CLUB CHAT UPGRADE (run ONCE in the Supabase SQL editor)
-- ============================================================================
--
-- What this adds:
--   1. club_messages.voice_url  + club_messages.mentions (text[])
--      - real recorded voice notes (uploaded to the `voice-notes` bucket)
--      - "@name" mentions with a tailored push notification per user
--   2. notifications.club_id    - lets a notification deep-link into the club
--   3. club_message_reactions   - emoji reactions on any club message
--   4. club_reports             - members report toxic members;
--                                  the owner gets notified
--   5. club_members UPDATE policy (owner only) - suspend / un-suspend a
--      member from messaging
--   6. Trigger guards on club_messages:
--      - only ACTIVE members can post (suspended / non-members rejected)
--      - every "@mention" inserts a tailored notification row for that user
--   7. Trigger on club_reports: notifies the club owner
--   8. Storage policies for club voice notes
--      (`voice-notes/club-voice/<club_id>/...` — authenticated read+write)
--
-- Everything is idempotent — safe to re-run.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. club_messages: voice URL + mentions
-- ----------------------------------------------------------------------------
alter table public.club_messages
    add column if not exists voice_url text;
alter table public.club_messages
    add column if not exists mentions text[] not null default '{}';

-- Fast "load older page" + "poll newer than X" scans (cursor pagination).
create index if not exists club_messages_club_created_idx
    on public.club_messages (club_id, created_at desc);
create index if not exists club_messages_mentions_idx
    on public.club_messages using gin (mentions);

-- ----------------------------------------------------------------------------
-- 2. notifications: club deep-link column
--    (the app's notification router opens the club chat from quicky://notify)
-- ----------------------------------------------------------------------------
alter table public.notifications
    add column if not exists club_id text;

create index if not exists notifications_user_unread_idx
    on public.notifications (user_id, created_at desc) where not is_read;

-- ----------------------------------------------------------------------------
-- 3. club_message_reactions
-- ----------------------------------------------------------------------------
create table if not exists public.club_message_reactions (
    id         uuid primary key default gen_random_uuid(),
    message_id uuid not null references public.club_messages (id) on delete cascade,
    club_id    text not null references public.clubs (id) on delete cascade,
    user_id    text not null,
    user_name  text not null default '',
    emoji      text not null check (char_length(emoji) between 1 and 16),
    created_at timestamptz not null default now(),
    unique (message_id, user_id, emoji)
);

create index if not exists club_reactions_message_idx
    on public.club_message_reactions (message_id);

alter table public.club_message_reactions enable row level security;

drop policy if exists "club_reactions_select" on public.club_message_reactions;
create policy "club_reactions_select" on public.club_message_reactions
    for select using (auth.role() = 'authenticated');

drop policy if exists "club_reactions_insert" on public.club_message_reactions;
create policy "club_reactions_insert" on public.club_message_reactions
    for insert with check (auth.role() = 'authenticated');

-- You can only remove YOUR OWN reaction (owners remove the member instead).
drop policy if exists "club_reactions_delete" on public.club_message_reactions;
create policy "club_reactions_delete" on public.club_message_reactions
    for delete using (auth.uid()::text = user_id);

-- ----------------------------------------------------------------------------
-- 4. club_reports (member moderation)
-- ----------------------------------------------------------------------------
create table if not exists public.club_reports (
    id                 uuid primary key default gen_random_uuid(),
    club_id            text not null references public.clubs (id) on delete cascade,
    reporter_id        text not null,
    reporter_name      text not null default '',
    reported_user_id   text not null,
    reported_user_name text not null default '',
    reason             text not null default 'TOXIC_LANGUAGE'
        check (reason in ('TOXIC_LANGUAGE', 'HARASSMENT', 'SPAM', 'OTHER')),
    details            text not null default '',
    status             text not null default 'OPEN'
        check (status in ('OPEN', 'REVIEWED', 'DISMISSED')),
    created_at         timestamptz not null default now(),
    -- one OPEN report per (club, reporter, reported) — repeat reports just
    -- re-flag the same member instead of spamming the owner
    unique (club_id, reporter_id, reported_user_id)
);

create index if not exists club_reports_club_idx
    on public.club_reports (club_id, created_at desc);

alter table public.club_reports enable row level security;

drop policy if exists "club_reports_select" on public.club_reports;
create policy "club_reports_select" on public.club_reports
    for select using (auth.role() = 'authenticated');

drop policy if exists "club_reports_insert" on public.club_reports;
create policy "club_reports_insert" on public.club_reports
    for insert with check (auth.role() = 'authenticated');

-- Only the club owner reviews/dismisses reports.
drop policy if exists "club_reports_owner_update" on public.club_reports;
create policy "club_reports_owner_update" on public.club_reports
    for update using (
        auth.role() = 'authenticated'
        and exists (
            select 1 from public.clubs c
            where c.id = club_id and c.owner_id = auth.uid()::text
        )
    );

-- ----------------------------------------------------------------------------
-- 5. club_members: owner-only UPDATE policy (suspend / un-suspend)
--    (status values: ACTIVE | SUSPENDED — the insert gate rejects posts
--     from SUSPENDED members, see §6)
-- ----------------------------------------------------------------------------
alter table public.club_members
    drop constraint if exists club_members_status_values;
alter table public.club_members
    add constraint club_members_status_values
    check (status in ('ACTIVE', 'SUSPENDED'));

drop policy if exists "club_members_owner_update" on public.club_members;
create policy "club_members_owner_update" on public.club_members
    for update using (
        auth.role() = 'authenticated'
        and exists (
            select 1 from public.clubs c
            where c.id = club_id and c.owner_id = auth.uid()::text
        )
    )
    with check (
        auth.role() = 'authenticated'
        and exists (
            select 1 from public.clubs c
            where c.id = club_id and c.owner_id = auth.uid()::text
        )
    );

-- ----------------------------------------------------------------------------
-- 6a. club_messages INSERT gate: sender must be an ACTIVE member.
--     SYSTEM messages (member-removed notices etc.) bypass the gate.
-- ----------------------------------------------------------------------------
create or replace function public.enforce_club_message_membership()
returns trigger language plpgsql security definer as $$
declare
    m record;
begin
    if new.message_type = 'SYSTEM' then
        return new;
    end if;
    select status into m
      from public.club_members
     where club_id = new.club_id and user_id = new.sender_id;
    if not found then
        raise exception 'NOT_A_MEMBER: only members of this club can post here.';
    end if;
    if m.status = 'SUSPENDED' then
        raise exception 'MEMBER_SUSPENDED: the club owner suspended you from messaging in this club.';
    end if;
    return new;
end $$;

drop trigger if exists club_messages_membership_gate on public.club_messages;
create trigger club_messages_membership_gate
    before insert on public.club_messages
    for each row execute function public.enforce_club_message_membership();

-- ----------------------------------------------------------------------------
-- 6b. club_messages mention notifications (tailored push per mentioned user)
-- ----------------------------------------------------------------------------
create or replace function public.notify_club_mentions()
returns trigger language plpgsql security definer as $$
declare
    mentioned_id text;
    club_name    text;
begin
    if coalesce(array_length(new.mentions, 1), 0) = 0 then
        return new;
    end if;
    select name into club_name from public.clubs where id = new.club_id;
    foreach mentioned_id in array new.mentions loop
        if mentioned_id <> new.sender_id then
            insert into public.notifications (user_id, title, message, type, club_id)
            values (
                mentioned_id,
                'You were mentioned in ' || coalesce(club_name, 'a club'),
                new.sender_name || ': ' || left(coalesce(nullif(new.text, ''), new.message_type), 120),
                'MESSAGE',
                new.club_id
            );
        end if;
    end loop;
    return new;
end $$;

drop trigger if exists club_messages_mention_notify on public.club_messages;
create trigger club_messages_mention_notify
    after insert on public.club_messages
    for each row execute function public.notify_club_mentions();

-- ----------------------------------------------------------------------------
-- 7. club_reports: notify the club owner on a new OPEN report
-- ----------------------------------------------------------------------------
create or replace function public.notify_club_report()
returns trigger language plpgsql security definer as $$
declare
    owner_id text;
    club_name text;
begin
    select owner_id, name into owner_id, club_name
      from public.clubs where id = new.club_id;
    if owner_id is not null and owner_id <> new.reporter_id then
        insert into public.notifications (user_id, title, message, type, club_id)
        values (
            owner_id,
            'Member reported in ' || coalesce(club_name, 'your club'),
            new.reporter_name || ' reported ' || new.reported_user_name || ' (' || new.reason || ')',
            'SYSTEM',
            new.club_id
        );
    end if;
    return new;
end $$;

drop trigger if exists club_reports_owner_notify on public.club_reports;
create trigger club_reports_owner_notify
    after insert on public.club_reports
    for each row execute function public.notify_club_report();

-- ----------------------------------------------------------------------------
-- 8. Storage: club voice notes (private bucket, authenticated club access)
--    Path convention: voice-notes/club-voice/<club_id>/<sender>_<ts>.m4a
-- ----------------------------------------------------------------------------
drop policy if exists "club_voice_authenticated_read" on storage.objects;
create policy "club_voice_authenticated_read" on storage.objects
    for select using (
        bucket_id = 'voice-notes'
        and (storage.foldername(name))[1] = 'club-voice'
        and auth.role() = 'authenticated'
    );

drop policy if exists "club_voice_authenticated_upload" on storage.objects;
create policy "club_voice_authenticated_upload" on storage.objects
    for insert with check (
        bucket_id = 'voice-notes'
        and (storage.foldername(name))[1] = 'club-voice'
        and auth.role() = 'authenticated'
    );
