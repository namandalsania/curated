-- Comments on individual places, and sharing a trip to another user's inbox.
--
-- Run once in the Supabase SQL editor, after 20260919b_plan_collaboration.sql.
-- Safe to re-run.

-- ---------------------------------------------------------------------------
-- 1. Comments, one thread per place (stop). trip_id is denormalized the way
-- stops do it, so counts and policies don't need an extra join.
-- ---------------------------------------------------------------------------
create table if not exists stop_comments (
  id uuid primary key default gen_random_uuid(),
  stop_id uuid not null references stops(id) on delete cascade,
  trip_id uuid not null references trips(id) on delete cascade,
  author_id uuid not null references users(id) on delete cascade,
  body text not null check (length(btrim(body)) between 1 and 1000),
  created_at timestamptz not null default now()
);
create index if not exists stop_comments_stop on stop_comments (stop_id, created_at);
create index if not exists stop_comments_trip on stop_comments (trip_id);

alter table stop_comments enable row level security;

-- Readable by anyone who can see the trip; written as yourself; removable by
-- you or by the trip's author (so creators can moderate their own itinerary).
drop policy if exists "stop_comments_select" on stop_comments;
create policy "stop_comments_select" on stop_comments for select using (
  exists (
    select 1 from trips t where t.id = stop_comments.trip_id
    and ((t.status = 'published' and t.visibility in ('public','unlisted')) or auth.uid() = t.author_id)
  )
);
drop policy if exists "stop_comments_insert" on stop_comments;
create policy "stop_comments_insert" on stop_comments for insert with check (
  auth.uid() = author_id
  -- The stop must really belong to the trip named, and that trip must be visible.
  and exists (select 1 from stops s where s.id = stop_id and s.trip_id = trip_id)
  and exists (
    select 1 from trips t where t.id = trip_id
    and ((t.status = 'published' and t.visibility in ('public','unlisted')) or auth.uid() = t.author_id)
  )
);
drop policy if exists "stop_comments_delete" on stop_comments;
create policy "stop_comments_delete" on stop_comments for delete using (
  auth.uid() = author_id
  or exists (select 1 from trips t where t.id = stop_comments.trip_id and t.author_id = auth.uid())
);

-- ---------------------------------------------------------------------------
-- 2. Sharing a trip to someone's inbox inside the app.
-- ---------------------------------------------------------------------------
create table if not exists trip_shares (
  id uuid primary key default gen_random_uuid(),
  trip_id uuid not null references trips(id) on delete cascade,
  sender_id uuid not null references users(id) on delete cascade,
  recipient_id uuid not null references users(id) on delete cascade,
  note text check (note is null or length(btrim(note)) between 1 and 280),
  created_at timestamptz not null default now(),
  read_at timestamptz,
  check (sender_id <> recipient_id)
);
create index if not exists trip_shares_recipient on trip_shares (recipient_id, created_at desc);

alter table trip_shares enable row level security;

-- Only the two people involved can see a share.
drop policy if exists "trip_shares_select" on trip_shares;
create policy "trip_shares_select" on trip_shares for select
  using (auth.uid() = recipient_id or auth.uid() = sender_id);
-- You send as yourself, and only trips you can see.
drop policy if exists "trip_shares_insert" on trip_shares;
create policy "trip_shares_insert" on trip_shares for insert with check (
  auth.uid() = sender_id
  and exists (
    select 1 from trips t where t.id = trip_id
    and ((t.status = 'published' and t.visibility in ('public','unlisted')) or auth.uid() = t.author_id)
  )
);
-- The recipient marks it read.
drop policy if exists "trip_shares_update" on trip_shares;
create policy "trip_shares_update" on trip_shares for update
  using (auth.uid() = recipient_id) with check (auth.uid() = recipient_id);
-- Either side can remove it from their view.
drop policy if exists "trip_shares_delete" on trip_shares;
create policy "trip_shares_delete" on trip_shares for delete
  using (auth.uid() = recipient_id or auth.uid() = sender_id);

-- ---------------------------------------------------------------------------
-- 3. Notifications for both.
-- ---------------------------------------------------------------------------
alter table notifications add column if not exists stop_id uuid references stops(id) on delete cascade;
alter table notifications drop constraint if exists notifications_type_check;
alter table notifications add constraint notifications_type_check
  check (type in ('follow', 'new_trip', 'plan_invite', 'stop_comment', 'trip_share'));

-- ---------------------------------------------------------------------------
-- 4. Live updates for an open comment thread.
-- ---------------------------------------------------------------------------
do $$
begin
  begin alter publication supabase_realtime add table stop_comments; exception when duplicate_object then null; end;
end $$;

notify pgrst, 'reload schema';
