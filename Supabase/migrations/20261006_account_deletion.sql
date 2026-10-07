-- Account deletion: make every reference to a user disappear with the account.
--
-- The delete-account Edge Function removes the caller's Storage objects, then
-- deletes the auth user. auth.users -> public.users is ON DELETE CASCADE, and
-- this migration makes sure everything hanging off public.users follows:
--
--   1. notifications.actor_id becomes ON DELETE CASCADE. It was SET NULL, which
--      would have left "Someone started following you" rows behind.
--   2. Copies of the user's places in other people's saved places and plans
--      keep the place (name, category, location - the saver's own list) but
--      lose what came from the deleted user: author name, trip title, photo.
--   3. Reports filed ABOUT a deleted user, trip, stop or comment stay, with
--      target_deleted_at recording that the target is gone. Reports filed BY
--      the user are deleted (reporter_id already cascades).
--   4. public.account_storage_objects(uuid) lists a user's Storage objects for
--      the Edge Function. Service role only.
--
-- Everything else that references users was already ON DELETE CASCADE (or SET
-- NULL where the row belongs to someone else - see the audit in
-- supabase/functions/delete-account/README.md).
--
-- Safe to re-run.

begin;

-- ---------------------------------------------------------------------------
-- 1. Notifications a deleted user caused go with them
-- ---------------------------------------------------------------------------
alter table public.notifications drop constraint if exists notifications_actor_id_fkey;
alter table public.notifications
  add constraint notifications_actor_id_fkey
  foreign key (actor_id) references public.users(id) on delete cascade;

-- CLEANUP - REVIEW BEFORE RUNNING.
-- Rows left by the old SET NULL: a notification with no actor. Every
-- notification the app creates has an actor, so a null one can only come from
-- an account deleted under the old rule. On 2026-10-06 there were none, so
-- this is expected to delete 0 rows; it's here so a re-run tidies any that
-- appear before the constraint above is in place.
delete from public.notifications where actor_id is null;

-- ---------------------------------------------------------------------------
-- 2. Scrub what other people copied from the deleted user's trips
-- ---------------------------------------------------------------------------
-- Saving a stop, or adding it to a plan, copies the author's display name, the
-- trip title and a photo URL. The place itself stays in the saver's list; the
-- deleted user's name, title and photo (whose file is deleted from Storage
-- anyway) don't. Runs BEFORE the users row goes, while trips still link the
-- copies to their author - the cascade then sets source_trip_id to null.
create or replace function private.scrub_copies_of_deleted_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.saved_places sp
     set source_author_name = null, source_trip_title = null, photo_url = null
   where sp.user_id <> old.id
     and sp.source_trip_id in (select t.id from public.trips t where t.author_id = old.id);

  update public.plan_items pi
     set source_author_name = null, source_trip_title = null, photo_url = null
   where pi.source_trip_id in (select t.id from public.trips t where t.author_id = old.id)
     and not exists (select 1 from public.plans p where p.id = pi.plan_id and p.user_id = old.id);

  return old;
end;
$$;

revoke all on function private.scrub_copies_of_deleted_user() from public;

drop trigger if exists users_scrub_copies on public.users;
create trigger users_scrub_copies
  before delete on public.users
  for each row execute function private.scrub_copies_of_deleted_user();

-- ---------------------------------------------------------------------------
-- 3. Reports about deleted things stay, marked as such
-- ---------------------------------------------------------------------------
alter table public.reports add column if not exists target_deleted_at timestamptz;

-- The triggers below look reports up by target; the unique constraint leads
-- with reporter_id, so it can't serve that.
create index if not exists reports_target on public.reports (target_type, target_id);

create or replace function private.mark_reports_target_deleted()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.reports r
     set target_deleted_at = now()
   where r.target_type = tg_argv[0]
     and r.target_id = old.id
     and r.target_deleted_at is null;
  return null;
end;
$$;

revoke all on function private.mark_reports_target_deleted() from public;

-- Fires for each row a cascade removes too, so deleting an account marks
-- reports on its profile, trips, stops and comments in the same transaction.
drop trigger if exists users_mark_reports on public.users;
create trigger users_mark_reports
  after delete on public.users
  for each row execute function private.mark_reports_target_deleted('profile');

drop trigger if exists trips_mark_reports on public.trips;
create trigger trips_mark_reports
  after delete on public.trips
  for each row execute function private.mark_reports_target_deleted('trip');

drop trigger if exists stops_mark_reports on public.stops;
create trigger stops_mark_reports
  after delete on public.stops
  for each row execute function private.mark_reports_target_deleted('stop');

drop trigger if exists stop_comments_mark_reports on public.stop_comments;
create trigger stop_comments_mark_reports
  after delete on public.stop_comments
  for each row execute function private.mark_reports_target_deleted('comment');

-- ---------------------------------------------------------------------------
-- 4. A user's Storage objects, for the delete-account function
-- ---------------------------------------------------------------------------
-- Three ways an object belongs to someone:
--   - they uploaded it (owner; hosted Supabase also has owner_id as text -
--     read through to_jsonb so this works whichever columns exist);
--   - an avatar named "<user id>-<timestamp>.jpg";
--   - a stop photo under "<trip id>/" of one of their trips.
-- Owner catches photos of trips deleted earlier (deleting a trip leaves its
-- files); the path rules catch anything uploaded without an owner.
-- In public so the service role can call it over PostgREST; nobody else can.
create or replace function public.account_storage_objects(p_user uuid)
returns table (bucket_id text, name text)
language sql
stable
security definer
set search_path = ''
as $$
  select o.bucket_id, o.name
    from storage.objects o
   where o.bucket_id in ('stop-photos', 'avatars')
     and (
       o.owner = p_user
       or to_jsonb(o) ->> 'owner_id' = p_user::text
       or (o.bucket_id = 'avatars' and o.name like p_user::text || '-%')
       or (o.bucket_id = 'stop-photos'
           and split_part(o.name, '/', 1) in (select t.id::text from public.trips t where t.author_id = p_user))
     );
$$;

revoke all on function public.account_storage_objects(uuid) from public, anon, authenticated;
grant execute on function public.account_storage_objects(uuid) to service_role;

notify pgrst, 'reload schema';

commit;
