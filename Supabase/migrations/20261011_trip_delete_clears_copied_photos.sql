-- Deleting a trip clears the photo on copies of its places.
--
-- Saving a stop, or adding it to a plan, copies a photo URL that points into
-- the trip's Storage folder (stop-photos/<trip id>/...). Deleting a trip now
-- deletes that folder (the app removes the files, then the row - see
-- TripRepository.deleteTrip), so the copies were left pointing at nothing and
-- showed an empty square. This clears saved_places.photo_url and
-- plan_items.photo_url for copies of the trip being deleted.
--
-- Everything else in the copy stays, as before: the place, and the trip title
-- and author credit (20260919_saved_places_and_plans keeps them so a saved
-- place outlives its trip). Account deletion still clears those too, through
-- private.scrub_copies_of_deleted_user on users; this trigger also runs then,
-- once per trip the account's deletion cascades to.
--
-- BEFORE DELETE, not AFTER: source_trip_id is ON DELETE SET NULL, so once the
-- trip row is gone the copies can no longer be matched to it.
--
-- Its own function and trigger on trips rather than an extension of
-- private.scrub_copies_of_deleted_user: that one fires when a users row is
-- deleted, and a trip is usually deleted on its own.
--
-- SECURITY DEFINER: the trip's author deletes it, but the copies belong to
-- other people, whose rows RLS would hide from the author's UPDATE.
--
-- Safe to re-run.

begin;

create schema if not exists private;

create or replace function private.clear_copied_photos_of_deleted_trip()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  -- Only copies whose owner still exists. When an account is deleted, its
  -- trips go in the same statement as its own saves and plans; those rows are
  -- being deleted anyway, and updating them would fail their user foreign key
  -- check (the users row is already gone). Same rule as the account scrub.
  update public.saved_places sp
     set photo_url = null
   where sp.source_trip_id = old.id
     and sp.photo_url is not null
     and exists (select 1 from public.users u where u.id = sp.user_id);

  update public.plan_items pi
     set photo_url = null
   where pi.source_trip_id = old.id
     and pi.photo_url is not null
     and exists (select 1 from public.plans p
                   join public.users u on u.id = p.user_id
                  where p.id = pi.plan_id);

  return old;
end;
$$;

revoke all on function private.clear_copied_photos_of_deleted_trip() from public;

drop trigger if exists trips_clear_copied_photos on public.trips;
create trigger trips_clear_copied_photos
  before delete on public.trips
  for each row execute function private.clear_copied_photos_of_deleted_trip();

commit;
