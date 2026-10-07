-- Assertions for account deletion.
--
-- Run in the Supabase SQL editor after 20261006_account_deletion.sql. The
-- whole script is one transaction ending in ROLLBACK, so it leaves nothing
-- behind - including the fixture auth users and Storage rows it creates.
--
-- It deletes a fixture account the way the delete-account Edge Function's
-- final step does - `delete from auth.users` - and checks that every row
-- touching that account is gone or scrubbed, that the other accounts' rows are
-- exactly as they were, and that doing it again is harmless.
--
-- A failure raises an exception, which aborts the script. Silence past the last
-- NOTICE means a case failed - read the error, not the absence of output.

begin;

-- ---------------------------------------------------------------------------
-- Fixtures
--
-- ...2a1 doomed    - the account that gets deleted; touches everything
-- ...2a2 friend    - interacts with doomed both ways
-- ...2a3 bystander - unrelated; nothing of theirs may change
-- ---------------------------------------------------------------------------
insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password,
  email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data
)
values
  ('00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-0000000002a1',
   'authenticated', 'authenticated', 'doomed@deletion-test.invalid', '', now(), now(), now(), '{}', '{}'),
  ('00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-0000000002a2',
   'authenticated', 'authenticated', 'friend@deletion-test.invalid', '', now(), now(), now(), '{}', '{}'),
  ('00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-0000000002a3',
   'authenticated', 'authenticated', 'bystander@deletion-test.invalid', '', now(), now(), now(), '{}', '{}');

insert into public.users (id, username, display_name, avatar_url) values
  ('00000000-0000-0000-0000-0000000002a1', 'del_doomed',    'Doomed Person', 'https://example.invalid/avatars/2a1-1.jpg'),
  ('00000000-0000-0000-0000-0000000002a2', 'del_friend',    'Friend Person', null),
  ('00000000-0000-0000-0000-0000000002a3', 'del_bystander', 'Bystander',     null);

-- Follows: doomed <-> friend, and bystander -> friend (must survive).
insert into public.follows (follower_id, following_id) values
  ('00000000-0000-0000-0000-0000000002a1', '00000000-0000-0000-0000-0000000002a2'),
  ('00000000-0000-0000-0000-0000000002a2', '00000000-0000-0000-0000-0000000002a1'),
  ('00000000-0000-0000-0000-0000000002a3', '00000000-0000-0000-0000-0000000002a2');

-- Trips: b1 is doomed's, b2 is friend's, b3 is bystander's.
insert into public.trips (id, author_id, title, destination, start_date, end_date, status, visibility, completed_at) values
  ('00000000-0000-0000-0000-0000000002b1', '00000000-0000-0000-0000-0000000002a1',
   'Doomed Trip', 'Lisbon, Portugal', current_date - 10, current_date - 8, 'completed', 'public', now()),
  ('00000000-0000-0000-0000-0000000002b2', '00000000-0000-0000-0000-0000000002a2',
   'Friend Trip', 'Rome, Italy', current_date - 20, current_date - 18, 'completed', 'public', now()),
  ('00000000-0000-0000-0000-0000000002b3', '00000000-0000-0000-0000-0000000002a3',
   'Bystander Trip', 'Oslo, Norway', current_date - 30, current_date - 28, 'completed', 'public', now());

insert into public.days (id, trip_id, day_index, date, published_at, published_via) values
  ('00000000-0000-0000-0000-0000000002c1', '00000000-0000-0000-0000-0000000002b1', 1, current_date - 10, now(), 'import'),
  ('00000000-0000-0000-0000-0000000002c2', '00000000-0000-0000-0000-0000000002b2', 1, current_date - 20, now(), 'import');

insert into public.stops (id, day_id, trip_id, name, category, location, order_in_day) values
  ('00000000-0000-0000-0000-0000000002d1', '00000000-0000-0000-0000-0000000002c1',
   '00000000-0000-0000-0000-0000000002b1', 'Doomed Stop', 'sight', 'SRID=4326;POINT(-9.1393 38.7223)', 0),
  ('00000000-0000-0000-0000-0000000002d2', '00000000-0000-0000-0000-0000000002c2',
   '00000000-0000-0000-0000-0000000002b2', 'Friend Stop', 'food', 'SRID=4326;POINT(12.4964 41.9028)', 0);

insert into public.stop_photos (id, stop_id, storage_path, order_index) values
  ('00000000-0000-0000-0000-0000000002e1', '00000000-0000-0000-0000-0000000002d1',
   '00000000-0000-0000-0000-0000000002b1/00000000-0000-0000-0000-0000000002d1/0.jpg', 0);

-- Comments: friend on doomed's stop, doomed on friend's stop.
insert into public.stop_comments (id, stop_id, trip_id, author_id, body) values
  ('00000000-0000-0000-0000-0000000002f1', '00000000-0000-0000-0000-0000000002d1',
   '00000000-0000-0000-0000-0000000002b1', '00000000-0000-0000-0000-0000000002a2', 'Friend on doomed'),
  ('00000000-0000-0000-0000-0000000002f2', '00000000-0000-0000-0000-0000000002d2',
   '00000000-0000-0000-0000-0000000002b2', '00000000-0000-0000-0000-0000000002a1', 'Doomed on friend');

-- Likes and bookmarks both ways, plus bystander's like of friend's trip.
insert into public.likes (user_id, trip_id) values
  ('00000000-0000-0000-0000-0000000002a1', '00000000-0000-0000-0000-0000000002b2'),
  ('00000000-0000-0000-0000-0000000002a2', '00000000-0000-0000-0000-0000000002b1'),
  ('00000000-0000-0000-0000-0000000002a3', '00000000-0000-0000-0000-0000000002b2');
insert into public.bookmarks (user_id, trip_id) values
  ('00000000-0000-0000-0000-0000000002a1', '00000000-0000-0000-0000-0000000002b2'),
  ('00000000-0000-0000-0000-0000000002a2', '00000000-0000-0000-0000-0000000002b1');

-- Notifications: doomed acted (the old SET NULL case), doomed received, and
-- one between the others that must survive.
insert into public.notifications (id, recipient_id, actor_id, type, trip_id) values
  ('00000000-0000-0000-0000-000000000291', '00000000-0000-0000-0000-0000000002a2',
   '00000000-0000-0000-0000-0000000002a1', 'follow', null),
  ('00000000-0000-0000-0000-000000000292', '00000000-0000-0000-0000-0000000002a1',
   '00000000-0000-0000-0000-0000000002a2', 'follow', null),
  ('00000000-0000-0000-0000-000000000293', '00000000-0000-0000-0000-0000000002a2',
   '00000000-0000-0000-0000-0000000002a3', 'follow', null);

-- Shares: doomed sent friend's trip to bystander; friend sent its own trip to doomed;
-- bystander sent friend's trip to friend (survives).
insert into public.trip_shares (id, trip_id, sender_id, recipient_id) values
  ('00000000-0000-0000-0000-000000000294', '00000000-0000-0000-0000-0000000002b2',
   '00000000-0000-0000-0000-0000000002a1', '00000000-0000-0000-0000-0000000002a3'),
  ('00000000-0000-0000-0000-000000000295', '00000000-0000-0000-0000-0000000002b2',
   '00000000-0000-0000-0000-0000000002a2', '00000000-0000-0000-0000-0000000002a1'),
  ('00000000-0000-0000-0000-000000000296', '00000000-0000-0000-0000-0000000002b2',
   '00000000-0000-0000-0000-0000000002a3', '00000000-0000-0000-0000-0000000002a2');

-- Saved places: doomed saved friend's stop; friend saved doomed's stop (a copy
-- carrying doomed's name, trip title and photo).
insert into public.saved_places (id, user_id, stop_id, source_trip_id, source_trip_title, source_author_name,
                                 name, category, latitude, longitude, photo_url) values
  ('00000000-0000-0000-0000-000000000297', '00000000-0000-0000-0000-0000000002a1',
   '00000000-0000-0000-0000-0000000002d2', '00000000-0000-0000-0000-0000000002b2', 'Friend Trip', 'Friend Person',
   'Friend Stop', 'food', 41.9028, 12.4964, 'https://example.invalid/friend.jpg'),
  ('00000000-0000-0000-0000-000000000298', '00000000-0000-0000-0000-0000000002a2',
   '00000000-0000-0000-0000-0000000002d1', '00000000-0000-0000-0000-0000000002b1', 'Doomed Trip', 'Doomed Person',
   'Doomed Stop', 'sight', 38.7223, -9.1393, 'https://example.invalid/doomed.jpg');

-- Plans: doomed's own plan (friend is a member); friend's plan where doomed is
-- a member and added a copy of doomed's stop.
insert into public.plans (id, user_id, title) values
  ('00000000-0000-0000-0000-00000000029a', '00000000-0000-0000-0000-0000000002a1', 'Doomed Plan'),
  ('00000000-0000-0000-0000-00000000029b', '00000000-0000-0000-0000-0000000002a2', 'Friend Plan');
insert into public.plan_members (plan_id, user_id, role, status, invited_by) values
  ('00000000-0000-0000-0000-00000000029a', '00000000-0000-0000-0000-0000000002a2', 'editor', 'accepted', '00000000-0000-0000-0000-0000000002a1'),
  ('00000000-0000-0000-0000-00000000029b', '00000000-0000-0000-0000-0000000002a1', 'editor', 'accepted', '00000000-0000-0000-0000-0000000002a2');
insert into public.plan_items (id, plan_id, day_number, position, stop_id, source_trip_id, source_trip_title,
                               source_author_name, name, category, latitude, longitude, photo_url, added_by) values
  ('00000000-0000-0000-0000-00000000029c', '00000000-0000-0000-0000-00000000029b', 1, 0,
   '00000000-0000-0000-0000-0000000002d1', '00000000-0000-0000-0000-0000000002b1', 'Doomed Trip', 'Doomed Person',
   'Doomed Stop', 'sight', 38.7223, -9.1393, 'https://example.invalid/doomed.jpg', '00000000-0000-0000-0000-0000000002a1'),
  ('00000000-0000-0000-0000-00000000029d', '00000000-0000-0000-0000-00000000029a', 1, 0,
   null, null, null, null, 'Doomed custom place', 'other', null, null, null, '00000000-0000-0000-0000-0000000002a1');

-- Reports: doomed reported friend's trip (goes with doomed). Friend reported
-- doomed's profile, trip, stop and comment (stay, marked deleted). Bystander
-- reported friend's trip (untouched).
insert into public.reports (id, reporter_id, target_type, target_id, reason) values
  ('00000000-0000-0000-0000-0000000002e0', '00000000-0000-0000-0000-0000000002a1', 'trip',    '00000000-0000-0000-0000-0000000002b2', 'spam'),
  ('00000000-0000-0000-0000-0000000002e2', '00000000-0000-0000-0000-0000000002a2', 'profile', '00000000-0000-0000-0000-0000000002a1', 'harassment'),
  ('00000000-0000-0000-0000-0000000002e3', '00000000-0000-0000-0000-0000000002a2', 'trip',    '00000000-0000-0000-0000-0000000002b1', 'misleading'),
  ('00000000-0000-0000-0000-0000000002e4', '00000000-0000-0000-0000-0000000002a2', 'stop',    '00000000-0000-0000-0000-0000000002d1', 'inappropriate'),
  ('00000000-0000-0000-0000-0000000002e5', '00000000-0000-0000-0000-0000000002a2', 'comment', '00000000-0000-0000-0000-0000000002f2', 'spam'),
  ('00000000-0000-0000-0000-0000000002e6', '00000000-0000-0000-0000-0000000002a3', 'trip',    '00000000-0000-0000-0000-0000000002b2', 'other');

-- Blocks both ways between doomed and bystander.
insert into public.blocks (blocker_id, blocked_id) values
  ('00000000-0000-0000-0000-0000000002a1', '00000000-0000-0000-0000-0000000002a3'),
  ('00000000-0000-0000-0000-0000000002a3', '00000000-0000-0000-0000-0000000002a1');

-- Storage. Doomed: current avatar, an old avatar with no owner (prefix only), a
-- photo of b1, and a photo left behind by a trip deleted earlier (owner only).
-- Friend: an ownerless photo under friend's trip, and an avatar - neither may
-- be listed for doomed.
insert into storage.objects (bucket_id, name, owner) values
  ('avatars',     '00000000-0000-0000-0000-0000000002a1-1700000000000.jpg', '00000000-0000-0000-0000-0000000002a1'),
  ('avatars',     '00000000-0000-0000-0000-0000000002a1-1600000000000.jpg', null),
  ('stop-photos', '00000000-0000-0000-0000-0000000002b1/00000000-0000-0000-0000-0000000002d1/0.jpg', '00000000-0000-0000-0000-0000000002a1'),
  ('stop-photos', '00000000-0000-0000-0000-0000000009ff/00000000-0000-0000-0000-0000000009fe/0.jpg', '00000000-0000-0000-0000-0000000002a1'),
  ('stop-photos', '00000000-0000-0000-0000-0000000002b2/00000000-0000-0000-0000-0000000002d2/0.jpg', null),
  ('avatars',     '00000000-0000-0000-0000-0000000002a2-1700000000000.jpg', '00000000-0000-0000-0000-0000000002a2');

-- ---------------------------------------------------------------------------
-- Case 1: the Storage helper finds exactly doomed's objects, for service_role only
-- ---------------------------------------------------------------------------
do $$
declare n int; names text;
begin
  select count(*), string_agg(o.bucket_id || ':' || o.name, ' ' order by o.name) into n, names
    from public.account_storage_objects('00000000-0000-0000-0000-0000000002a1') o;
  if n <> 4 then
    raise exception 'FAIL case 1: expected 4 objects for doomed, got % (%)', n, names;
  end if;
  if names like '%0002a2-%' or names like '%0002b2/%' then
    raise exception 'FAIL case 1: listed someone else''s object: %', names;
  end if;
  raise notice 'PASS 1a: storage helper lists doomed''s 4 objects (owner, avatar prefix, trip folder, orphan) and nothing else';
end $$;

do $$
declare r text;
begin
  foreach r in array array['anon', 'authenticated'] loop
    if has_function_privilege(r, 'public.account_storage_objects(uuid)', 'execute') then
      raise exception 'FAIL case 1: % can execute account_storage_objects', r;
    end if;
  end loop;
  if not has_function_privilege('service_role', 'public.account_storage_objects(uuid)', 'execute') then
    raise exception 'FAIL case 1: service_role cannot execute account_storage_objects';
  end if;
  raise notice 'PASS 1b: only service_role can call the storage helper';
end $$;

-- ---------------------------------------------------------------------------
-- Snapshot of every row that doesn't involve doomed, to compare afterwards.
-- Rows of doomed's trip are excluded too (they go with the trip).
-- ---------------------------------------------------------------------------
create temp view others_now as
          select 'follows' t, md5(coalesce(string_agg(x::text, '|' order by x::text), '')) h from public.follows x
           where '00000000-0000-0000-0000-0000000002a1' not in (x.follower_id, x.following_id)
union all select 'trips', md5(coalesce(string_agg(x::text, '|' order by x::text), '')) from public.trips x
           where x.author_id <> '00000000-0000-0000-0000-0000000002a1'
union all select 'stops', md5(coalesce(string_agg(x::text, '|' order by x::text), '')) from public.stops x
           where x.trip_id <> '00000000-0000-0000-0000-0000000002b1'
union all select 'likes', md5(coalesce(string_agg(x::text, '|' order by x::text), '')) from public.likes x
           where x.user_id <> '00000000-0000-0000-0000-0000000002a1' and x.trip_id <> '00000000-0000-0000-0000-0000000002b1'
union all select 'notifications', md5(coalesce(string_agg(x::text, '|' order by x::text), '')) from public.notifications x
           where x.recipient_id <> '00000000-0000-0000-0000-0000000002a1'
             and x.actor_id is distinct from '00000000-0000-0000-0000-0000000002a1'
union all select 'trip_shares', md5(coalesce(string_agg(x::text, '|' order by x::text), '')) from public.trip_shares x
           where '00000000-0000-0000-0000-0000000002a1' not in (x.sender_id, x.recipient_id)
union all select 'stop_comments', md5(coalesce(string_agg(x::text, '|' order by x::text), '')) from public.stop_comments x
           where x.author_id <> '00000000-0000-0000-0000-0000000002a1' and x.trip_id <> '00000000-0000-0000-0000-0000000002b1'
union all select 'reports_by_bystander', md5(coalesce(string_agg(x::text, '|' order by x::text), '')) from public.reports x
           where x.reporter_id = '00000000-0000-0000-0000-0000000002a3'
union all select 'storage_of_friend', md5(coalesce(string_agg(x.name, '|' order by x.name), '')) from storage.objects x
           where x.name like '%0002a2-%' or x.name like '%0002b2/%';

create temp table others_before as select * from others_now;

-- ---------------------------------------------------------------------------
-- Case 2a: the order matters. While doomed still owns files, the account can't
-- be deleted (storage.objects.owner references auth.users on projects that
-- still have that constraint) - so the function removes files first.
-- ---------------------------------------------------------------------------
do $$
begin
  if exists (select 1 from pg_constraint where conname = 'objects_owner_fkey'
             and conrelid = 'storage.objects'::regclass) then
    begin
      delete from auth.users where id = '00000000-0000-0000-0000-0000000002a1';
      raise exception 'FAIL case 2a: the account was deleted while it still owned files';
    exception when foreign_key_violation then
      raise notice 'PASS 2a: deleting the account before its files is refused (objects_owner_fkey) - files go first';
    end;
  else
    raise notice 'SKIP 2a: this project has no objects_owner_fkey; file-first order still applies (see README)';
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- The deletion, in the Edge Function's order: (1) remove doomed's files - here
-- with SQL, in the function through the Storage API, using the same helper -
-- then (2) delete the auth user.
-- ---------------------------------------------------------------------------
delete from storage.objects o
 using public.account_storage_objects('00000000-0000-0000-0000-0000000002a1') f
 where o.bucket_id = f.bucket_id and o.name = f.name;

delete from auth.users where id = '00000000-0000-0000-0000-0000000002a1';

-- ---------------------------------------------------------------------------
-- Case 2: nothing references doomed any more, and nothing blocked the delete
-- ---------------------------------------------------------------------------
do $$
declare
  d constant uuid := '00000000-0000-0000-0000-0000000002a1';
  b1 constant uuid := '00000000-0000-0000-0000-0000000002b1';
  leftovers text;
begin
  select string_agg(t || '=' || n, ', ') into leftovers from (
              select 'users' t, count(*) n from public.users where id = d
    union all select 'follows', count(*) from public.follows where d in (follower_id, following_id)
    union all select 'trips', count(*) from public.trips where author_id = d
    union all select 'days', count(*) from public.days where trip_id = b1
    union all select 'stops', count(*) from public.stops where trip_id = b1
    union all select 'stop_photos', count(*) from public.stop_photos where stop_id = '00000000-0000-0000-0000-0000000002d1'
    union all select 'stop_comments', count(*) from public.stop_comments where author_id = d or trip_id = b1
    union all select 'likes', count(*) from public.likes where user_id = d or trip_id = b1
    union all select 'bookmarks', count(*) from public.bookmarks where user_id = d or trip_id = b1
    union all select 'notifications', count(*) from public.notifications where d in (recipient_id, actor_id)
    union all select 'trip_shares', count(*) from public.trip_shares where d in (sender_id, recipient_id)
    union all select 'saved_places', count(*) from public.saved_places where user_id = d
    union all select 'plans', count(*) from public.plans where user_id = d
    union all select 'plan_members', count(*) from public.plan_members where d in (user_id, invited_by)
    union all select 'plan_items', count(*) from public.plan_items
               where added_by = d or plan_id = '00000000-0000-0000-0000-00000000029a'
    union all select 'reports_filed', count(*) from public.reports where reporter_id = d
    union all select 'blocks', count(*) from public.blocks where d in (blocker_id, blocked_id)
  ) c where n > 0;
  if leftovers is not null then
    raise exception 'FAIL case 2: rows still reference the deleted account: %', leftovers;
  end if;
  raise notice 'PASS 2: no row in any table references the deleted account';
end $$;

-- ---------------------------------------------------------------------------
-- Case 3: notifications doomed caused are gone (they used to be SET NULL)
-- ---------------------------------------------------------------------------
do $$
begin
  if exists (select 1 from public.notifications where id = '00000000-0000-0000-0000-000000000291') then
    raise exception 'FAIL case 3: the follow notification doomed caused is still there';
  end if;
  if exists (select 1 from public.notifications where actor_id is null) then
    raise exception 'FAIL case 3: a notification with no actor was left behind';
  end if;
  raise notice 'PASS 3: notifications the deleted account caused are removed, not left actor-less';
end $$;

-- ---------------------------------------------------------------------------
-- Case 4: friend's copies keep the place but lose doomed's name, title, photo
-- ---------------------------------------------------------------------------
do $$
declare sp public.saved_places; pi public.plan_items;
begin
  select * into sp from public.saved_places where id = '00000000-0000-0000-0000-000000000298';
  if sp.id is null then raise exception 'FAIL case 4: friend''s saved place was deleted'; end if;
  if sp.source_author_name is not null or sp.source_trip_title is not null or sp.photo_url is not null then
    raise exception 'FAIL case 4: friend''s saved place still carries doomed''s name/title/photo: % / % / %',
      sp.source_author_name, sp.source_trip_title, sp.photo_url;
  end if;
  if sp.name <> 'Doomed Stop' or sp.latitude is null then
    raise exception 'FAIL case 4: the place itself was lost from friend''s saves';
  end if;

  select * into pi from public.plan_items where id = '00000000-0000-0000-0000-00000000029c';
  if pi.id is null then raise exception 'FAIL case 4: the item in friend''s plan was deleted'; end if;
  if pi.source_author_name is not null or pi.source_trip_title is not null
     or pi.photo_url is not null or pi.added_by is not null then
    raise exception 'FAIL case 4: the item in friend''s plan still carries doomed''s data';
  end if;
  raise notice 'PASS 4: copies in friend''s saves and plan keep the place, lose doomed''s name, trip title and photo';
end $$;

-- ---------------------------------------------------------------------------
-- Case 5: reports about doomed's things stay, marked; doomed's own reports go
-- ---------------------------------------------------------------------------
do $$
declare n int;
begin
  select count(*) into n from public.reports
   where id in ('00000000-0000-0000-0000-0000000002e2', '00000000-0000-0000-0000-0000000002e3',
                '00000000-0000-0000-0000-0000000002e4', '00000000-0000-0000-0000-0000000002e5')
     and target_deleted_at is not null;
  if n <> 4 then
    raise exception 'FAIL case 5: expected 4 reports about doomed''s profile/trip/stop/comment kept and marked, got %', n;
  end if;
  if exists (select 1 from public.reports where id = '00000000-0000-0000-0000-0000000002e0') then
    raise exception 'FAIL case 5: the report doomed filed survived';
  end if;
  if (select target_deleted_at from public.reports where id = '00000000-0000-0000-0000-0000000002e6') is not null then
    raise exception 'FAIL case 5: bystander''s report about a trip that still exists was marked deleted';
  end if;
  raise notice 'PASS 5: reports about the deleted account''s profile, trip, stop and comment stay, marked deleted';
end $$;

-- ---------------------------------------------------------------------------
-- Case 6: nobody else's rows changed
-- ---------------------------------------------------------------------------
do $$
declare changed text;
begin
  select string_agg(b.t, ', ') into changed
    from others_before b join others_now a on a.t = b.t
   where a.h is distinct from b.h;
  if changed is not null then
    raise exception 'FAIL case 6: rows not involving the deleted account changed in: %', changed;
  end if;
  raise notice 'PASS 6: every row not involving the deleted account is unchanged (9 tables compared)';
end $$;

-- ---------------------------------------------------------------------------
-- Case 7: running the deletion again is harmless, and a retry still finds files
-- ---------------------------------------------------------------------------
do $$
declare n int;
begin
  delete from auth.users where id = '00000000-0000-0000-0000-0000000002a1';
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL case 7: second delete removed % rows', n; end if;

  select count(*) into n from public.account_storage_objects('00000000-0000-0000-0000-0000000002a1');
  if n <> 0 then raise exception 'FAIL case 7: % of the deleted account''s files are left', n; end if;
  raise notice 'PASS 7: deleting again is a no-op, and none of the deleted account''s files are left';
end $$;

rollback;
