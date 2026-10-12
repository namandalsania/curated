-- Deleting a stop clears the photo on copies of it.
--
-- The app now deletes a stop's photo files (stop-photos/<trip id>/<stop id>/)
-- before its row. Saved places and plan items copied from that stop kept a
-- photo URL into that folder, now pointing at nothing. 20261011 clears them
-- when the whole trip is deleted; this does the same when one stop is.
-- Everything else in the copy stays: the place, the trip title and author
-- credit, and source_trip_id (only stop_id goes null, through its foreign key).
--
-- BEFORE DELETE, not AFTER: stop_id is ON DELETE SET NULL, so once the stop
-- row is gone the copies can no longer be matched to it.
--
-- It also fires when a trip delete or an account delete cascades to the
-- trip's stops. That is harmless: by then trips_clear_copied_photos (20261011)
-- has already cleared every copy whose owner still exists, so this finds
-- nothing left to change, and copies whose owner is being deleted in the same
-- statement are skipped here exactly as there.
--
-- SECURITY DEFINER: the trip's author deletes the stop, but the copies belong
-- to other people, whose rows RLS would hide from the author's UPDATE.
--
-- Safe to re-run.

begin;

create schema if not exists private;

create or replace function private.clear_copied_photos_of_deleted_stop()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  -- Only copies whose owner still exists: when an account is deleted, its own
  -- saves and plans go in the same statement, and updating them would fail
  -- their user foreign key check (the users row is already gone).
  update public.saved_places sp
     set photo_url = null
   where sp.stop_id = old.id
     and sp.photo_url is not null
     and exists (select 1 from public.users u where u.id = sp.user_id);

  update public.plan_items pi
     set photo_url = null
   where pi.stop_id = old.id
     and pi.photo_url is not null
     and exists (select 1 from public.plans p
                   join public.users u on u.id = p.user_id
                  where p.id = pi.plan_id);

  return old;
end;
$$;

revoke all on function private.clear_copied_photos_of_deleted_stop() from public;

drop trigger if exists stops_clear_copied_photos on public.stops;
create trigger stops_clear_copied_photos
  before delete on public.stops
  for each row execute function private.clear_copied_photos_of_deleted_stop();

commit;
