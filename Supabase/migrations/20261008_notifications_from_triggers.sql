-- Notifications are created by the database, not the app.
--
-- Until now the app inserted notifications itself, under a policy that only
-- checked actor_id = auth.uid(): any signed-in user could create a
-- notification of any type, for anyone, about anything. This migration:
--
--   1. Adds the types 'like' and 'new_day', and notifications.day_id.
--   2. CLEANUP (review before running): removes duplicate rows that would stop
--      the new unique indexes from being created, keeping the newest of each.
--   3. Adds partial unique indexes so each notification exists once:
--        follow       (recipient, actor)
--        like         (recipient, actor, trip)
--        trip_share   (recipient, actor, trip)
--        new_trip     (recipient, trip)
--        new_day      (recipient, day)
--        plan_invite  (recipient, plan)
--      stop_comment has none: every comment is its own notification.
--   4. One share per (sender, recipient, trip), with the same cleanup.
--   5. SECURITY DEFINER triggers in `private` create them:
--        follows insert          -> 'follow' to the person followed
--        likes insert            -> 'like' to the trip's author
--        stop_comments insert    -> 'stop_comment' to the trip's author
--        trip_shares insert      -> 'trip_share' to the recipient
--        trips becomes completed + public -> 'new_trip' to the author's followers
--        a day posted live (post_day) on a public trip -> 'new_day' to followers
--        plan_members insert (pending invite) -> 'plan_invite' to the invitee
--      Never to yourself, never between a blocked pair (either direction), and
--      a repeat (unfollow/refollow, unlike/like, re-sending) is ignored via the
--      unique indexes and ON CONFLICT DO NOTHING.
--   6. Drops the client insert policy and the insert grant. The app's
--      notify*() calls must be removed in the same release: once this is
--      applied they're refused (the app ignores those failures, so an older
--      build keeps working - the triggers make the notifications instead).
--
-- Safe to re-run.

begin;

-- ---------------------------------------------------------------------------
-- 1. Types and the day a 'new_day' is about
-- ---------------------------------------------------------------------------
alter table public.notifications
  add column if not exists day_id uuid references public.days(id) on delete cascade;

alter table public.notifications drop constraint if exists notifications_type_check;
alter table public.notifications add constraint notifications_type_check
  check (type in ('follow', 'new_trip', 'new_day', 'like', 'stop_comment', 'trip_share', 'plan_invite'));

-- ---------------------------------------------------------------------------
-- 2. CLEANUP - REVIEW BEFORE RUNNING.
--
-- Deletes duplicate notifications, keeping the newest row of each group (ties
-- broken by id). On 2026-10-07 this matched 3 rows, all type 'follow': the
-- seed was run twice with different notification ids, so three follow pairs
-- (one of them Diego -> Maya) each have a 2026-09-17 and a 2026-09-28 row.
-- No other type had duplicates. Read state of the deleted copies is lost;
-- they were identical apart from created_at.
--
-- To preview without deleting, run the SELECT inside the CTE on its own.
-- ---------------------------------------------------------------------------
with ranked as (
  select id,
         row_number() over (
           partition by type, recipient_id,
             case type
               when 'follow'      then actor_id::text
               when 'like'        then actor_id::text || ':' || trip_id::text
               when 'trip_share'  then actor_id::text || ':' || trip_id::text
               when 'new_trip'    then trip_id::text
               when 'new_day'     then day_id::text
               when 'plan_invite' then plan_id::text
             end
           order by created_at desc, id desc
         ) as rn
    from public.notifications
   where type in ('follow', 'like', 'trip_share', 'new_trip', 'new_day', 'plan_invite')
)
delete from public.notifications n
 using ranked r
 where n.id = r.id and r.rn > 1;

-- ---------------------------------------------------------------------------
-- 3. One of each
-- ---------------------------------------------------------------------------
create unique index if not exists notifications_one_follow
  on public.notifications (recipient_id, actor_id) where type = 'follow';
create unique index if not exists notifications_one_like
  on public.notifications (recipient_id, actor_id, trip_id) where type = 'like';
create unique index if not exists notifications_one_trip_share
  on public.notifications (recipient_id, actor_id, trip_id) where type = 'trip_share';
create unique index if not exists notifications_one_new_trip
  on public.notifications (recipient_id, trip_id) where type = 'new_trip';
create unique index if not exists notifications_one_new_day
  on public.notifications (recipient_id, day_id) where type = 'new_day';
create unique index if not exists notifications_one_plan_invite
  on public.notifications (recipient_id, plan_id) where type = 'plan_invite';

-- ---------------------------------------------------------------------------
-- 4. One share per (sender, recipient, trip)
--
-- CLEANUP - REVIEW BEFORE RUNNING: keeps the newest of any repeated share.
-- On 2026-10-07 the table was empty, so this matched nothing.
-- ---------------------------------------------------------------------------
with ranked as (
  select id, row_number() over (
           partition by sender_id, recipient_id, trip_id order by created_at desc, id desc) as rn
    from public.trip_shares
)
delete from public.trip_shares s using ranked r where s.id = r.id and r.rn > 1;

alter table public.trip_shares drop constraint if exists trip_shares_one_per_trip;
alter table public.trip_shares
  add constraint trip_shares_one_per_trip unique (sender_id, recipient_id, trip_id);

-- ---------------------------------------------------------------------------
-- 5. Triggers
-- ---------------------------------------------------------------------------

-- The one place notifications are written. Skips nobody-to-tell, yourself,
-- and blocked pairs; a duplicate hits a unique index and is dropped.
create or replace function private.notify(
  p_recipient uuid, p_actor uuid, p_type text,
  p_trip uuid default null, p_stop uuid default null, p_plan uuid default null, p_day uuid default null
) returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if p_recipient is null or p_actor is null or p_recipient = p_actor then
    return;
  end if;
  if private.is_blocked_pair(p_actor, p_recipient) then
    return;
  end if;
  insert into public.notifications (recipient_id, actor_id, type, trip_id, stop_id, plan_id, day_id)
  values (p_recipient, p_actor, p_type, p_trip, p_stop, p_plan, p_day)
  on conflict do nothing;
end;
$$;

-- Everyone following p_author, for a trip or day they posted.
create or replace function private.notify_followers(
  p_author uuid, p_type text, p_trip uuid, p_day uuid default null
) returns void
language plpgsql
security definer
set search_path = ''
as $$
declare f record;
begin
  for f in select follower_id from public.follows where following_id = p_author loop
    perform private.notify(f.follower_id, p_author, p_type, p_trip, null, null, p_day);
  end loop;
end;
$$;

create or replace function private.on_follow() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  perform private.notify(new.following_id, new.follower_id, 'follow');
  return null;
end;
$$;

create or replace function private.on_like() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  perform private.notify(
    (select t.author_id from public.trips t where t.id = new.trip_id),
    new.user_id, 'like', new.trip_id);
  return null;
end;
$$;

create or replace function private.on_stop_comment() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  perform private.notify(
    (select t.author_id from public.trips t where t.id = new.trip_id),
    new.author_id, 'stop_comment', new.trip_id, new.stop_id);
  return null;
end;
$$;

create or replace function private.on_trip_share() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  perform private.notify(new.recipient_id, new.sender_id, 'trip_share', new.trip_id);
  return null;
end;
$$;

-- A trip that becomes finished and public - published from a draft, a live
-- trip ended, or a finished trip made public. Each follower hears once per
-- trip (the unique index), however often it's toggled.
create or replace function private.on_trip_published() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if new.status = 'completed' and new.visibility = 'public'
     and (tg_op = 'INSERT' or old.status is distinct from 'completed' or old.visibility is distinct from 'public') then
    perform private.notify_followers(new.author_id, 'new_trip', new.id);
  end if;
  return null;
end;
$$;

-- A day posted while travelling, on a public trip. Days published by ending
-- the trip or by import are covered by 'new_trip' instead.
create or replace function private.on_day_posted() returns trigger
language plpgsql security definer set search_path = ''
as $$
declare t record;
begin
  if new.published_at is null or new.published_via is distinct from 'post_day' then
    return null;
  end if;
  if tg_op = 'UPDATE' and old.published_at is not null then
    return null;
  end if;
  select author_id, visibility into t from public.trips where id = new.trip_id;
  if t.visibility = 'public' then
    perform private.notify_followers(t.author_id, 'new_day', new.trip_id, new.id);
  end if;
  return null;
end;
$$;

create or replace function private.on_plan_invite() returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if new.status = 'pending' then
    perform private.notify(new.user_id, new.invited_by, 'plan_invite', null, null, new.plan_id);
  end if;
  return null;
end;
$$;

revoke all on function private.notify(uuid, uuid, text, uuid, uuid, uuid, uuid) from public;
revoke all on function private.notify_followers(uuid, text, uuid, uuid) from public;
revoke all on function private.on_follow() from public;
revoke all on function private.on_like() from public;
revoke all on function private.on_stop_comment() from public;
revoke all on function private.on_trip_share() from public;
revoke all on function private.on_trip_published() from public;
revoke all on function private.on_day_posted() from public;
revoke all on function private.on_plan_invite() from public;

drop trigger if exists follows_notify on public.follows;
create trigger follows_notify after insert on public.follows
  for each row execute function private.on_follow();

drop trigger if exists likes_notify on public.likes;
create trigger likes_notify after insert on public.likes
  for each row execute function private.on_like();

drop trigger if exists stop_comments_notify on public.stop_comments;
create trigger stop_comments_notify after insert on public.stop_comments
  for each row execute function private.on_stop_comment();

drop trigger if exists trip_shares_notify on public.trip_shares;
create trigger trip_shares_notify after insert on public.trip_shares
  for each row execute function private.on_trip_share();

drop trigger if exists trips_notify_published on public.trips;
create trigger trips_notify_published after insert or update of status, visibility on public.trips
  for each row execute function private.on_trip_published();

drop trigger if exists days_notify_posted on public.days;
create trigger days_notify_posted after insert or update of published_at on public.days
  for each row execute function private.on_day_posted();

drop trigger if exists plan_members_notify on public.plan_members;
create trigger plan_members_notify after insert on public.plan_members
  for each row execute function private.on_plan_invite();

-- ---------------------------------------------------------------------------
-- 6. Clients can no longer create notifications
-- ---------------------------------------------------------------------------
drop policy if exists "notifications_insert_as_actor" on public.notifications;
revoke insert on public.notifications from anon, authenticated;

notify pgrst, 'reload schema';

commit;
