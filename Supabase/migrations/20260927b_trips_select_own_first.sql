-- Let an author read back a trip in the same statement that creates it.
--
-- Run once in the Supabase SQL editor, after 20260926_live_trips.sql. Safe to
-- re-run.
--
-- 20260926 made the trips read policy `private.trip_is_visible(trips.id)`. That
-- helper looks the trip up by id, and as a STABLE function it can't see a row
-- inserted by the statement that's running - so `insert ... returning` (what
-- supabase-js / supabase-kt do by default with select()) fails its own read-back
-- and Postgres reports it as "new row violates row-level security policy".
-- Creating any trip from the app hit this.
--
-- The fix is the one plans_select already uses (20260919b): test author_id on the
-- row directly first. It widens nothing - the helper already let authors read
-- their own trips - it just answers that case without a lookup.
--
-- The app now avoids returning on trip inserts anyway; this makes the policy
-- correct for every other client too.

begin;

drop policy if exists "trips_select_visible_or_own" on public.trips;
create policy "trips_select_visible_or_own" on public.trips for select
  using (
    (select auth.uid()) = author_id
    or private.trip_is_visible(id)
  );

notify pgrst, 'reload schema';

commit;
