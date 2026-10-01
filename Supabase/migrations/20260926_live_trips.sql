-- Live trip posting: a trip can be posted a day at a time while it happens.
--
-- Run once in the Supabase SQL editor, after 20260924_data_api_grants.sql.
-- Safe to re-run.
--
-- Three things change:
--   1. trips.status becomes draft -> live -> completed. It stops being an enum,
--      matching how budget_tag / season_tag / category are already done, so the
--      dead 'published' value doesn't survive forever (Postgres can't drop an
--      enum value). Existing published trips become 'completed'.
--   2. days gain published_at / published_via, so a live trip can show some days
--      publicly while the rest stay private to its author.
--   3. The visibility predicate, currently copy-pasted into seven policies, moves
--      into helper functions in a `private` schema. PostgREST only exposes the
--      schemas it's configured with (public), so nothing here is callable as an
--      RPC to probe whether an arbitrary trip or day exists.
--
-- visibility is untouched and stays orthogonal: a 'private' trip is invisible to
-- non-owners at every lifecycle state, exactly as before.

begin;

-- ---------------------------------------------------------------------------
-- 1. A schema for the policy helpers, deliberately outside the Data API.
-- ---------------------------------------------------------------------------
create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to anon, authenticated;

-- ---------------------------------------------------------------------------
-- 2. Drop every policy that reads trips.status.
--
-- ALTER COLUMN TYPE refuses to run while a policy references the column, so
-- these have to go first and come back in section 6. The write policies are
-- dropped too - they don't block the alter, but most are rewritten below to go
-- through the same helpers. trips' own update/delete are the exception: they
-- test author_id directly, for the reason given where they're recreated.
-- ---------------------------------------------------------------------------
drop policy if exists "trips_select_visible_or_own" on trips;
drop policy if exists "trips_insert_own" on trips;
drop policy if exists "trips_update_own" on trips;
drop policy if exists "trips_delete_own" on trips;

drop policy if exists "days_select_via_trip" on days;
drop policy if exists "days_insert_via_trip_owner" on days;
drop policy if exists "days_update_via_trip_owner" on days;
drop policy if exists "days_delete_via_trip_owner" on days;

drop policy if exists "stops_select_via_trip" on stops;
drop policy if exists "stops_insert_via_trip_owner" on stops;
drop policy if exists "stops_update_via_trip_owner" on stops;
drop policy if exists "stops_delete_via_trip_owner" on stops;

drop policy if exists "stop_photos_select_via_trip" on stop_photos;
drop policy if exists "stop_photos_insert_via_trip_owner" on stop_photos;
drop policy if exists "stop_photos_update_via_trip_owner" on stop_photos;
drop policy if exists "stop_photos_delete_via_trip_owner" on stop_photos;

drop policy if exists "stop_comments_select" on stop_comments;
drop policy if exists "stop_comments_insert" on stop_comments;

drop policy if exists "trip_shares_insert" on trip_shares;

-- ---------------------------------------------------------------------------
-- 3. trips.status: enum -> text + check, and the lifecycle timestamp.
--
-- The default has to be dropped before the conversion: 'draft'::trip_status
-- can't be cast automatically. The (status, created_at desc) index is rebuilt
-- by Postgres as part of the type change.
-- ---------------------------------------------------------------------------
do $$
begin
  if exists (
    select 1
    from pg_attribute a
    join pg_class c on c.oid = a.attrelid
    join pg_namespace n on n.oid = c.relnamespace
    join pg_type t on t.oid = a.atttypid
    where n.nspname = 'public' and c.relname = 'trips'
      and a.attname = 'status' and t.typname = 'trip_status'
  ) then
    alter table public.trips alter column status drop default;
    alter table public.trips alter column status type text using status::text;
    alter table public.trips alter column status set default 'draft';
  end if;
end $$;

-- Everything published under the old two-state model is a finished trip.
update public.trips set status = 'completed' where status = 'published';

alter table public.trips drop constraint if exists trips_status_check;
alter table public.trips add constraint trips_status_check
  check (status in ('draft', 'live', 'completed'));

drop type if exists trip_status;

alter table public.trips add column if not exists completed_at timestamptz;

-- created_at, not updated_at: the feed orders by created_at today and nothing
-- maintains updated_at (there's no trigger for it), so this is the one value
-- that leaves existing feed order exactly as it was.
update public.trips
set completed_at = created_at
where status = 'completed' and completed_at is null;

-- ---------------------------------------------------------------------------
-- 4. days: per-day publish state.
--
-- published_via says how a day became public:
--   'post_day' - the author posted it while travelling. These are the days that
--                become feed items and can trigger a notification.
--   'end_trip' - swept up when the trip ended. Deliberately excluded from feed
--                items, so ending a trip with three unposted days doesn't dump
--                three items plus the trip card into followers' feeds at once.
--   'import'   - a day of a trip that predates live posting, or one written by
--                the import-a-finished-trip flow.
-- ---------------------------------------------------------------------------
alter table public.days
  add column if not exists published_at  timestamptz,
  add column if not exists published_via text;

alter table public.days drop constraint if exists days_published_via_check;
alter table public.days add constraint days_published_via_check
  check (published_via in ('post_day', 'end_trip', 'import'));

-- The two columns are one fact; neither is meaningful alone.
alter table public.days drop constraint if exists days_published_pair_check;
alter table public.days add constraint days_published_pair_check
  check ((published_at is null) = (published_via is null));

-- Backfill: every day of an already-finished trip is public, and marked
-- 'import' so it can never surface as a day feed item.
--
-- The `published_at is null` guard is what makes this re-runnable. Note that on
-- a much later re-run it would also catch an empty day that End trip left
-- unpublished on purpose; migrations here are run once, so that's theoretical.
update public.days d
set published_at = t.completed_at,
    published_via = 'import'
from public.trips t
where t.id = d.trip_id
  and t.status = 'completed'
  and d.published_at is null;

create index if not exists days_trip_published
  on public.days (trip_id, published_at desc);

-- The Home feed's day items: posted days only, newest first.
create index if not exists days_feed
  on public.days (published_at desc)
  where published_at is not null and published_via = 'post_day';

-- ---------------------------------------------------------------------------
-- 5. Policy helpers.
--
-- security definer so a policy on days or stops can ask about the parent trip
-- without recursing back into the trips policy. search_path is empty and every
-- name is schema-qualified, so nothing here can be redirected by a caller's
-- search_path. auth.uid() is wrapped in a scalar subquery so the planner
-- evaluates it once per statement rather than once per row.
-- ---------------------------------------------------------------------------

-- The one definition of "this trip is visible to me".
--
-- A live trip is only visible to other people once it has posted a day. Until
-- then it exists, but there is nothing of it to see - and leaking the trip row
-- alone would announce "X is in Bangkok" before X chose to say so.
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

create or replace function private.trip_is_owned(p_trip_id uuid) returns boolean
language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from public.trips t
    where t.id = p_trip_id and t.author_id = (select auth.uid())
  );
$$;

create or replace function private.day_is_published(p_day_id uuid) returns boolean
language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from public.days d
    where d.id = p_day_id and d.published_at is not null
  );
$$;

-- A stop is visible when its trip is, and either you own it or it sits on a
-- posted day. A stop with no day_id is owner-only: that's the database-level
-- backstop for the app rule that every stop must be assigned a day before its
-- day can be posted.
create or replace function private.stop_is_visible(p_stop_id uuid) returns boolean
language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from public.stops s
    where s.id = p_stop_id
      and private.trip_is_visible(s.trip_id)
      and (
        private.trip_is_owned(s.trip_id)
        or (s.day_id is not null and private.day_is_published(s.day_id))
      )
  );
$$;

create or replace function private.stop_is_owned(p_stop_id uuid) returns boolean
language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from public.stops s
    where s.id = p_stop_id and private.trip_is_owned(s.trip_id)
  );
$$;

-- Callable only by the roles whose policies need them, and only from inside
-- those policies - `private` isn't a Data API schema, so there's no RPC route.
revoke execute on function private.trip_is_visible(uuid)  from public;
revoke execute on function private.trip_is_owned(uuid)    from public;
revoke execute on function private.day_is_published(uuid) from public;
revoke execute on function private.stop_is_visible(uuid)  from public;
revoke execute on function private.stop_is_owned(uuid)    from public;

grant execute on function private.trip_is_visible(uuid)  to anon, authenticated;
grant execute on function private.trip_is_owned(uuid)    to anon, authenticated;
grant execute on function private.day_is_published(uuid) to anon, authenticated;
grant execute on function private.stop_is_visible(uuid)  to anon, authenticated;
grant execute on function private.stop_is_owned(uuid)    to anon, authenticated;

-- ---------------------------------------------------------------------------
-- 6. Policies, rebuilt on the helpers.
-- ---------------------------------------------------------------------------

-- trips: live and completed trips are readable per visibility; drafts stay
-- with their author.
create policy "trips_select_visible_or_own" on trips for select
  using (private.trip_is_visible(trips.id));
create policy "trips_insert_own" on trips for insert
  with check (auth.uid() = author_id);
-- Straight at the column, not through the helper: the helper reads the row as
-- it currently stands, so a USING-only update policy would happily let an owner
-- reassign author_id to somebody else. WITH CHECK is what closes that.
create policy "trips_update_own" on trips for update
  using (auth.uid() = author_id)
  with check (auth.uid() = author_id);
create policy "trips_delete_own" on trips for delete
  using (auth.uid() = author_id);

-- days: the trip must be visible, and the day must be posted unless you own it.
--
-- On the update policies below, the same expression appears in USING and WITH
-- CHECK on purpose: USING reads the row as it stands, WITH CHECK the row as it
-- would be, so both together mean "yours before, and still yours after".
create policy "days_select_via_trip" on days for select using (
  private.trip_is_visible(days.trip_id)
  and (days.published_at is not null or private.trip_is_owned(days.trip_id))
);
create policy "days_insert_via_trip_owner" on days for insert
  with check (private.trip_is_owned(trip_id));
create policy "days_update_via_trip_owner" on days for update
  using (private.trip_is_owned(days.trip_id))
  with check (private.trip_is_owned(days.trip_id));
create policy "days_delete_via_trip_owner" on days for delete
  using (private.trip_is_owned(days.trip_id));

-- stops: as days, reaching through day_id. An unassigned stop is owner-only.
create policy "stops_select_via_trip" on stops for select using (
  private.trip_is_visible(stops.trip_id)
  and (
    private.trip_is_owned(stops.trip_id)
    or (stops.day_id is not null and private.day_is_published(stops.day_id))
  )
);
create policy "stops_insert_via_trip_owner" on stops for insert
  with check (private.trip_is_owned(trip_id));
create policy "stops_update_via_trip_owner" on stops for update
  using (private.trip_is_owned(stops.trip_id))
  with check (private.trip_is_owned(stops.trip_id));
create policy "stops_delete_via_trip_owner" on stops for delete
  using (private.trip_is_owned(stops.trip_id));

-- stop_photos: whatever the stop allows.
create policy "stop_photos_select_via_trip" on stop_photos for select
  using (private.stop_is_visible(stop_photos.stop_id));
create policy "stop_photos_insert_via_trip_owner" on stop_photos for insert
  with check (private.stop_is_owned(stop_id));
create policy "stop_photos_update_via_trip_owner" on stop_photos for update
  using (private.stop_is_owned(stop_photos.stop_id))
  with check (private.stop_is_owned(stop_photos.stop_id));
create policy "stop_photos_delete_via_trip_owner" on stop_photos for delete
  using (private.stop_is_owned(stop_photos.stop_id));

-- stop_comments: a comment is readable exactly when its place is. This is
-- stricter than before, which only checked the trip - a comment on a stop of an
-- unposted day now stays hidden along with the stop.
create policy "stop_comments_select" on stop_comments for select
  using (private.stop_is_visible(stop_comments.stop_id));
create policy "stop_comments_insert" on stop_comments for insert with check (
  auth.uid() = author_id
  -- The stop must really belong to the trip named, and be one you can see.
  -- stop_comments.trip_id, not a bare trip_id: unqualified, it would bind to
  -- stops.trip_id inside the subquery and the check would be a tautology.
  and exists (
    select 1 from stops s
    where s.id = stop_comments.stop_id and s.trip_id = stop_comments.trip_id
  )
  and private.stop_is_visible(stop_comments.stop_id)
);

-- trip_shares: you send as yourself, and only trips you can see.
create policy "trip_shares_insert" on trip_shares for insert with check (
  auth.uid() = sender_id
  and private.trip_is_visible(trip_id)
);

notify pgrst, 'reload schema';

commit;
