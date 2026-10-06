-- Reporting and blocking, for the closed beta.
--
-- Run once in the Supabase SQL editor, after 20260930_lock_spatial_ref_sys.sql.
-- Safe to re-run. Tests: Supabase/tests/reports_blocks_rls.sql.
--
--   1. reports - anyone signed in can file one; nobody can read them back
--      through the API. Reports are reviewed in the dashboard.
--   2. blocks  - yours to add, list and remove.
--   3. private.is_blocked_pair(a, b), and blocking applied in BOTH directions
--      to trip visibility (and so to days, stops, photos and comments, which
--      all go through it), comments, follows, notifications and shares.
--
-- Every policy here only adds conditions to what was there; none is loosened.
-- The app also filters blocked accounts client-side, but that is a
-- convenience - these policies are the enforcement.

begin;

create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to anon, authenticated;

-- ---------------------------------------------------------------------------
-- 1. reports
--
-- reason and status are text + check rather than enum types, as with
-- trips.status since 20260926: adding a value later is one constraint swap,
-- not an ALTER TYPE.
--
-- target_id carries no foreign key - it points at a trip, stop, comment or
-- profile depending on target_type - so a report outlives what it reports.
-- ---------------------------------------------------------------------------
create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null default auth.uid() references public.users(id) on delete cascade,
  target_type text not null check (target_type in ('trip', 'stop', 'comment', 'profile')),
  target_id uuid not null,
  reason text not null check (reason in ('spam', 'inappropriate', 'harassment', 'misleading', 'other')),
  note text check (note is null or length(btrim(note)) between 1 and 500),
  created_at timestamptz not null default now(),
  status text not null default 'open' check (status in ('open', 'reviewed', 'actioned')),
  -- One report per person per thing. Reporting it again is a no-op for the
  -- app, which treats the duplicate-key error as "already reported".
  constraint reports_one_per_target unique (reporter_id, target_type, target_id)
);
create index if not exists reports_open on public.reports (created_at desc) where status = 'open';
create index if not exists reports_target on public.reports (target_type, target_id);

alter table public.reports enable row level security;

-- Insert only, as yourself, and only as a new report. No select/update/delete
-- policies exist, so those are refused for every client role.
drop policy if exists "reports_insert_own" on public.reports;
create policy "reports_insert_own" on public.reports for insert to authenticated
  with check (reporter_id = (select auth.uid()) and status = 'open');

-- Grants follow 20260924, minus everything but insert: anon gets nothing.
revoke all on public.reports from anon, authenticated;
grant insert on public.reports to authenticated;
grant all on public.reports to service_role;

-- ---------------------------------------------------------------------------
-- 2. blocks
-- ---------------------------------------------------------------------------
create table if not exists public.blocks (
  blocker_id uuid not null default auth.uid() references public.users(id) on delete cascade,
  blocked_id uuid not null references public.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  constraint blocks_not_self check (blocker_id <> blocked_id)
);
-- The primary key serves "whom have I blocked"; this serves the reverse
-- lookup is_blocked_pair() makes on every row it checks.
create index if not exists blocks_blocked on public.blocks (blocked_id, blocker_id);

alter table public.blocks enable row level security;

-- Your own blocks only. Nobody can see who has blocked them.
drop policy if exists "blocks_select_own" on public.blocks;
create policy "blocks_select_own" on public.blocks for select to authenticated
  using (blocker_id = (select auth.uid()));
drop policy if exists "blocks_insert_own" on public.blocks;
create policy "blocks_insert_own" on public.blocks for insert to authenticated
  with check (blocker_id = (select auth.uid()));
drop policy if exists "blocks_delete_own" on public.blocks;
create policy "blocks_delete_own" on public.blocks for delete to authenticated
  using (blocker_id = (select auth.uid()));

revoke all on public.blocks from anon, authenticated;
grant select, insert, delete on public.blocks to authenticated;
grant all on public.blocks to service_role;

-- ---------------------------------------------------------------------------
-- 3. The helper.
--
-- Same shape as private.trip_is_visible(): security definer so a policy can
-- see block rows the caller's own blocks policy would hide (the other
-- direction), empty search_path, schema-qualified names, not callable as an
-- RPC because `private` isn't a Data API schema.
--
-- Null on either side means no pair: an anonymous caller (auth.uid() is null)
-- or a notification with no actor is never "blocked".
-- ---------------------------------------------------------------------------
create or replace function private.is_blocked_pair(p_a uuid, p_b uuid) returns boolean
language sql stable security definer set search_path = ''
as $$
  select p_a is not null and p_b is not null and exists (
    select 1 from public.blocks b
    where (b.blocker_id = p_a and b.blocked_id = p_b)
       or (b.blocker_id = p_b and b.blocked_id = p_a)
  );
$$;

revoke execute on function private.is_blocked_pair(uuid, uuid) from public;
grant execute on function private.is_blocked_pair(uuid, uuid) to anon, authenticated;

-- ---------------------------------------------------------------------------
-- 4. Trip visibility.
--
-- 20260926's definition with one condition added to the non-owner branch.
-- Days, stops, stop photos, comments and trip shares all ask this function,
-- so they all follow. The owner branch is untouched: you always see your own.
-- ---------------------------------------------------------------------------
create or replace function private.trip_is_visible(p_trip_id uuid) returns boolean
language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from public.trips t
    where t.id = p_trip_id
      and (
        t.author_id = (select auth.uid())
        or (
          t.visibility in ('public', 'unlisted')
          and not private.is_blocked_pair((select auth.uid()), t.author_id)
          and (
            t.status = 'completed'
            or (
              t.status = 'live'
              and exists (
                select 1 from public.days d
                where d.trip_id = t.id and d.published_at is not null
              )
            )
          )
        )
      )
  );
$$;

-- ---------------------------------------------------------------------------
-- 5. Comments: also hidden when their author and the reader are blocked -
-- including a blocked person's comment on your own trip. The insert policy
-- needs nothing new: you can't comment on a trip you can't see.
-- ---------------------------------------------------------------------------
drop policy if exists "stop_comments_select" on public.stop_comments;
create policy "stop_comments_select" on public.stop_comments for select
  using (
    private.stop_is_visible(stop_comments.stop_id)
    and not private.is_blocked_pair((select auth.uid()), stop_comments.author_id)
  );

-- ---------------------------------------------------------------------------
-- 6. Follows: a block removes them both ways, and neither side can follow
-- the other while it stands. Unblocking doesn't restore them.
-- ---------------------------------------------------------------------------
drop policy if exists "follows_insert_self" on public.follows;
create policy "follows_insert_self" on public.follows for insert
  with check (
    auth.uid() = follower_id
    and not private.is_blocked_pair(follower_id, following_id)
  );

-- A trigger rather than app code, so it holds however the block is made.
-- security definer because follows_delete_self only lets the follower remove
-- their own edge, and one of these two belongs to the other person.
create or replace function private.remove_follows_on_block() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  delete from public.follows f
  where (f.follower_id = new.blocker_id and f.following_id = new.blocked_id)
     or (f.follower_id = new.blocked_id and f.following_id = new.blocker_id);
  return new;
end;
$$;

revoke execute on function private.remove_follows_on_block() from public;

drop trigger if exists blocks_remove_follows on public.blocks;
create trigger blocks_remove_follows
  after insert on public.blocks
  for each row execute function private.remove_follows_on_block();

-- ---------------------------------------------------------------------------
-- 7. Notifications: none created between a blocked pair, and any that already
-- exist stop showing (they come back if the block is lifted).
-- ---------------------------------------------------------------------------
drop policy if exists "notifications_select_own" on public.notifications;
create policy "notifications_select_own" on public.notifications for select
  using (
    auth.uid() = recipient_id
    and not private.is_blocked_pair(recipient_id, actor_id)
  );

drop policy if exists "notifications_insert_as_actor" on public.notifications;
create policy "notifications_insert_as_actor" on public.notifications for insert
  with check (
    auth.uid() = actor_id
    and not private.is_blocked_pair(actor_id, recipient_id)
  );

-- ---------------------------------------------------------------------------
-- 8. Trip shares (the in-app inbox) - the same rule as notifications, since a
-- share is a message to a person: none sent between a blocked pair, and
-- existing ones hidden from both sides.
-- ---------------------------------------------------------------------------
drop policy if exists "trip_shares_select" on public.trip_shares;
create policy "trip_shares_select" on public.trip_shares for select
  using (
    (auth.uid() = recipient_id or auth.uid() = sender_id)
    and not private.is_blocked_pair(sender_id, recipient_id)
  );

drop policy if exists "trip_shares_insert" on public.trip_shares;
create policy "trip_shares_insert" on public.trip_shares for insert with check (
  auth.uid() = sender_id
  and private.trip_is_visible(trip_id)
  and not private.is_blocked_pair(sender_id, recipient_id)
);

notify pgrst, 'reload schema';

commit;
