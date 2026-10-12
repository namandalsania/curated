-- Checks for 20261011_trip_delete_clears_copied_photos.sql.
--
-- Run in the Supabase SQL editor after the migration. One transaction ending
-- in ROLLBACK: it leaves nothing behind, fixture auth users included.
--
-- A failure raises an exception, which aborts the script. Silence past the last
-- NOTICE means a case failed - read the error, not the absence of output.
--
-- The cases fail if the trigger is missing, runs AFTER DELETE (the copies'
-- source_trip_id is already null by then), or isn't SECURITY DEFINER (the
-- author's delete can't reach other people's copies through RLS).

begin;

-- ---------------------------------------------------------------------------
-- Fixtures
--
-- ...3a1 author    - owns trips 3b1 (deleted alone in case 1) and 3b2 (kept)
-- ...3a2 saver     - copied places from all of them into saves and a plan
-- ...3a3 bystander - copied a place from 3b2; nothing of theirs may change
-- ...3a4 doomed    - owns trip 3b4; the account is deleted in case 2
-- ---------------------------------------------------------------------------
insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password,
  email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data
)
select '00000000-0000-0000-0000-000000000000', id, 'authenticated', 'authenticated',
       name || '@trip-delete-test.invalid', '', now(), now(), now(), '{}', '{}'
  from (values ('00000000-0000-0000-0000-0000000003a1'::uuid, 'author'),
               ('00000000-0000-0000-0000-0000000003a2'::uuid, 'saver'),
               ('00000000-0000-0000-0000-0000000003a3'::uuid, 'bystander'),
               ('00000000-0000-0000-0000-0000000003a4'::uuid, 'doomed')) u(id, name);

insert into public.users (id, username, display_name) values
  ('00000000-0000-0000-0000-0000000003a1', 'tdel_author',    'Author Person'),
  ('00000000-0000-0000-0000-0000000003a2', 'tdel_saver',     'Saver Person'),
  ('00000000-0000-0000-0000-0000000003a3', 'tdel_bystander', 'Bystander'),
  ('00000000-0000-0000-0000-0000000003a4', 'tdel_doomed',    'Doomed Person');

insert into public.trips (id, author_id, title, destination, start_date, end_date, status, visibility, completed_at) values
  ('00000000-0000-0000-0000-0000000003b1', '00000000-0000-0000-0000-0000000003a1',
   'Deleted Trip', 'Lisbon, Portugal', current_date - 10, current_date - 9, 'completed', 'public', now()),
  ('00000000-0000-0000-0000-0000000003b2', '00000000-0000-0000-0000-0000000003a1',
   'Kept Trip', 'Porto, Portugal', current_date - 20, current_date - 19, 'completed', 'public', now()),
  ('00000000-0000-0000-0000-0000000003b4', '00000000-0000-0000-0000-0000000003a4',
   'Doomed Trip', 'Rome, Italy', current_date - 30, current_date - 29, 'completed', 'public', now()),
  ('00000000-0000-0000-0000-0000000003b5', '00000000-0000-0000-0000-0000000003a1',
   'Nobody Saved This', 'Faro, Portugal', current_date - 40, current_date - 39, 'completed', 'public', now());

insert into public.days (id, trip_id, day_index, date, published_at, published_via) values
  ('00000000-0000-0000-0000-0000000003c1', '00000000-0000-0000-0000-0000000003b1', 1, current_date - 10, now(), 'import'),
  ('00000000-0000-0000-0000-0000000003c2', '00000000-0000-0000-0000-0000000003b2', 1, current_date - 20, now(), 'import'),
  ('00000000-0000-0000-0000-0000000003c4', '00000000-0000-0000-0000-0000000003b4', 1, current_date - 30, now(), 'import');

insert into public.stops (id, day_id, trip_id, name, category, location, order_in_day) values
  ('00000000-0000-0000-0000-0000000003d1', '00000000-0000-0000-0000-0000000003c1',
   '00000000-0000-0000-0000-0000000003b1', 'Deleted Stop', 'sight', 'SRID=4326;POINT(-9.1393 38.7223)', 0),
  ('00000000-0000-0000-0000-0000000003d2', '00000000-0000-0000-0000-0000000003c2',
   '00000000-0000-0000-0000-0000000003b2', 'Kept Stop', 'food', 'SRID=4326;POINT(-8.6291 41.1579)', 0),
  ('00000000-0000-0000-0000-0000000003d4', '00000000-0000-0000-0000-0000000003c4',
   '00000000-0000-0000-0000-0000000003b4', 'Doomed Stop', 'sight', 'SRID=4326;POINT(12.4964 41.9028)', 0);

-- Saved places. f1-f4 copy the trip deleted in case 1 or the doomed account's
-- trip; f5 and f6 copy the kept trip and must not change.
insert into public.saved_places (id, user_id, stop_id, source_trip_id, source_trip_title, source_author_name,
                                 name, category, latitude, longitude, photo_url) values
  ('00000000-0000-0000-0000-0000000003f1', '00000000-0000-0000-0000-0000000003a2',   -- saver, of 3b1
   '00000000-0000-0000-0000-0000000003d1', '00000000-0000-0000-0000-0000000003b1', 'Deleted Trip', 'Author Person',
   'Deleted Stop', 'sight', 38.7223, -9.1393, 'https://example.invalid/stop-photos/3b1/3d1/0.jpg'),
  ('00000000-0000-0000-0000-0000000003f2', '00000000-0000-0000-0000-0000000003a1',   -- author's own save of 3b1
   '00000000-0000-0000-0000-0000000003d1', '00000000-0000-0000-0000-0000000003b1', 'Deleted Trip', 'Author Person',
   'Deleted Stop', 'sight', 38.7223, -9.1393, 'https://example.invalid/stop-photos/3b1/3d1/0.jpg'),
  ('00000000-0000-0000-0000-0000000003f3', '00000000-0000-0000-0000-0000000003a2',   -- saver, of 3b4
   '00000000-0000-0000-0000-0000000003d4', '00000000-0000-0000-0000-0000000003b4', 'Doomed Trip', 'Doomed Person',
   'Doomed Stop', 'sight', 41.9028, 12.4964, 'https://example.invalid/stop-photos/3b4/3d4/0.jpg'),
  ('00000000-0000-0000-0000-0000000003f4', '00000000-0000-0000-0000-0000000003a4',   -- doomed's own save of 3b4
   '00000000-0000-0000-0000-0000000003d4', '00000000-0000-0000-0000-0000000003b4', 'Doomed Trip', 'Doomed Person',
   'Doomed Stop', 'sight', 41.9028, 12.4964, 'https://example.invalid/stop-photos/3b4/3d4/0.jpg'),
  ('00000000-0000-0000-0000-0000000003f5', '00000000-0000-0000-0000-0000000003a2',   -- saver, of 3b2 (kept)
   '00000000-0000-0000-0000-0000000003d2', '00000000-0000-0000-0000-0000000003b2', 'Kept Trip', 'Author Person',
   'Kept Stop', 'food', 41.1579, -8.6291, 'https://example.invalid/stop-photos/3b2/3d2/0.jpg'),
  ('00000000-0000-0000-0000-0000000003f6', '00000000-0000-0000-0000-0000000003a3',   -- bystander, of 3b2 (kept)
   '00000000-0000-0000-0000-0000000003d2', '00000000-0000-0000-0000-0000000003b2', 'Kept Trip', 'Author Person',
   'Kept Stop', 'food', 41.1579, -8.6291, 'https://example.invalid/stop-photos/3b2/3d2/0.jpg');

-- Plans: saver's plan holds copies of 3b1, 3b4 and 3b2; doomed's own plan holds
-- a copy of 3b4 (it goes with the account).
insert into public.plans (id, user_id, title) values
  ('00000000-0000-0000-0000-0000000003e1', '00000000-0000-0000-0000-0000000003a2', 'Saver Plan'),
  ('00000000-0000-0000-0000-0000000003e4', '00000000-0000-0000-0000-0000000003a4', 'Doomed Plan');
insert into public.plan_items (id, plan_id, day_number, position, stop_id, source_trip_id, source_trip_title,
                               source_author_name, name, category, latitude, longitude, photo_url, added_by) values
  ('00000000-0000-0000-0000-000000000391', '00000000-0000-0000-0000-0000000003e1', 1, 0,
   '00000000-0000-0000-0000-0000000003d1', '00000000-0000-0000-0000-0000000003b1', 'Deleted Trip', 'Author Person',
   'Deleted Stop', 'sight', 38.7223, -9.1393, 'https://example.invalid/stop-photos/3b1/3d1/0.jpg',
   '00000000-0000-0000-0000-0000000003a2'),
  ('00000000-0000-0000-0000-000000000394', '00000000-0000-0000-0000-0000000003e1', 1, 1,
   '00000000-0000-0000-0000-0000000003d4', '00000000-0000-0000-0000-0000000003b4', 'Doomed Trip', 'Doomed Person',
   'Doomed Stop', 'sight', 41.9028, 12.4964, 'https://example.invalid/stop-photos/3b4/3d4/0.jpg',
   '00000000-0000-0000-0000-0000000003a2'),
  ('00000000-0000-0000-0000-000000000395', '00000000-0000-0000-0000-0000000003e1', 1, 2,
   '00000000-0000-0000-0000-0000000003d2', '00000000-0000-0000-0000-0000000003b2', 'Kept Trip', 'Author Person',
   'Kept Stop', 'food', 41.1579, -8.6291, 'https://example.invalid/stop-photos/3b2/3d2/0.jpg',
   '00000000-0000-0000-0000-0000000003a2'),
  ('00000000-0000-0000-0000-000000000396', '00000000-0000-0000-0000-0000000003e4', 1, 0,
   '00000000-0000-0000-0000-0000000003d4', '00000000-0000-0000-0000-0000000003b4', 'Doomed Trip', 'Doomed Person',
   'Doomed Stop', 'sight', 41.9028, 12.4964, 'https://example.invalid/stop-photos/3b4/3d4/0.jpg',
   '00000000-0000-0000-0000-0000000003a4');

-- Fingerprint of the copies of the kept trip, compared after every case.
create temp table kept_copies_before as
select 'saved_places' t, md5(string_agg(x::text, '|' order by x.id)) h
  from public.saved_places x where x.source_trip_id = '00000000-0000-0000-0000-0000000003b2'
union all
select 'plan_items', md5(string_agg(x::text, '|' order by x.id))
  from public.plan_items x where x.source_trip_id = '00000000-0000-0000-0000-0000000003b2';

-- ---------------------------------------------------------------------------
-- Case 1: the author deletes trip 3b1 from the app - as the authenticated
-- role, through RLS, the way PostgREST runs it.
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"00000000-0000-0000-0000-0000000003a1","role":"authenticated"}', true);

do $$
declare n int;
begin
  delete from public.trips where id = '00000000-0000-0000-0000-0000000003b1';
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL case 1: the author''s delete removed % trip rows, expected 1', n; end if;
end $$;

reset role;
select set_config('request.jwt.claims', '', true);

do $$
declare sp public.saved_places; pi public.plan_items;
begin
  -- The saver's copy: photo gone, everything else kept.
  select * into sp from public.saved_places where id = '00000000-0000-0000-0000-0000000003f1';
  if sp.id is null then raise exception 'FAIL case 1: the saver''s saved place was deleted'; end if;
  if sp.photo_url is not null then
    raise exception 'FAIL case 1: the saver''s saved place still has the deleted trip''s photo: %', sp.photo_url;
  end if;
  if sp.name <> 'Deleted Stop' or sp.source_trip_title is distinct from 'Deleted Trip'
     or sp.source_author_name is distinct from 'Author Person' or sp.latitude is null then
    raise exception 'FAIL case 1: the saver''s saved place lost more than its photo';
  end if;
  if sp.source_trip_id is not null or sp.stop_id is not null then
    raise exception 'FAIL case 1: the saved place still links to the deleted trip or stop';
  end if;

  -- The author's own save of their own trip goes the same way.
  if (select photo_url from public.saved_places where id = '00000000-0000-0000-0000-0000000003f2') is not null then
    raise exception 'FAIL case 1: the author''s own saved copy still has the photo';
  end if;

  -- The saver's plan item.
  select * into pi from public.plan_items where id = '00000000-0000-0000-0000-000000000391';
  if pi.id is null then raise exception 'FAIL case 1: the plan item was deleted'; end if;
  if pi.photo_url is not null then
    raise exception 'FAIL case 1: the plan item still has the deleted trip''s photo: %', pi.photo_url;
  end if;
  if pi.name <> 'Deleted Stop' or pi.source_trip_title is distinct from 'Deleted Trip'
     or pi.added_by is distinct from '00000000-0000-0000-0000-0000000003a2' then
    raise exception 'FAIL case 1: the plan item lost more than its photo';
  end if;

  -- Copies of other trips keep their photos.
  if (select photo_url from public.saved_places where id = '00000000-0000-0000-0000-0000000003f3') is null
     or (select photo_url from public.plan_items where id = '00000000-0000-0000-0000-000000000394') is null then
    raise exception 'FAIL case 1: a copy of a different trip lost its photo';
  end if;
  raise notice 'PASS 1: deleting a trip as its author clears the photo on every copy of it (others'' saves and plans, and the author''s own), and nothing else';
end $$;

-- ---------------------------------------------------------------------------
-- Case 2: deleting doomed's account cascades to trip 3b4. The saver's copies
-- lose the photo (and, through the account scrub, the name and title); doomed's
-- own save and plan go with the account; nothing errors.
-- ---------------------------------------------------------------------------
do $$
declare sp public.saved_places; pi public.plan_items; n int;
begin
  delete from auth.users where id = '00000000-0000-0000-0000-0000000003a4';
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL case 2: the account delete removed % rows, expected 1', n; end if;

  if exists (select 1 from public.trips where id = '00000000-0000-0000-0000-0000000003b4') then
    raise exception 'FAIL case 2: the deleted account''s trip is still there';
  end if;

  select * into sp from public.saved_places where id = '00000000-0000-0000-0000-0000000003f3';
  if sp.id is null then raise exception 'FAIL case 2: the saver''s copy was deleted with the account'; end if;
  if sp.photo_url is not null or sp.source_trip_title is not null or sp.source_author_name is not null then
    raise exception 'FAIL case 2: the saver''s copy still carries the deleted account''s photo/title/name: % / % / %',
      sp.photo_url, sp.source_trip_title, sp.source_author_name;
  end if;

  select * into pi from public.plan_items where id = '00000000-0000-0000-0000-000000000394';
  if pi.id is null or pi.photo_url is not null or pi.source_trip_title is not null then
    raise exception 'FAIL case 2: the saver''s plan item was lost or still carries the deleted account''s data';
  end if;

  select count(*) into n from (
    select 1 from public.saved_places where user_id = '00000000-0000-0000-0000-0000000003a4'
    union all select 1 from public.plans where user_id = '00000000-0000-0000-0000-0000000003a4'
    union all select 1 from public.plan_items where plan_id = '00000000-0000-0000-0000-0000000003e4'
  ) x;
  if n <> 0 then raise exception 'FAIL case 2: % of the deleted account''s own saves/plans/items are left', n; end if;
  raise notice 'PASS 2: account deletion still cascades - its trip goes, other people''s copies lose photo, title and name, its own saves and plan go';
end $$;

-- ---------------------------------------------------------------------------
-- Case 3: a trip nobody copied deletes cleanly.
-- ---------------------------------------------------------------------------
do $$
declare n int;
begin
  delete from public.trips where id = '00000000-0000-0000-0000-0000000003b5';
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL case 3: expected to delete 1 trip, deleted %', n; end if;
  raise notice 'PASS 3: a trip with no copies deletes cleanly';
end $$;

-- ---------------------------------------------------------------------------
-- Case 4: copies of the kept trip are byte-for-byte unchanged.
-- ---------------------------------------------------------------------------
do $$
declare changed text;
begin
  select string_agg(b.t, ', ') into changed
    from kept_copies_before b
    join (
      select 'saved_places' t, md5(string_agg(x::text, '|' order by x.id)) h
        from public.saved_places x where x.source_trip_id = '00000000-0000-0000-0000-0000000003b2'
      union all
      select 'plan_items', md5(string_agg(x::text, '|' order by x.id))
        from public.plan_items x where x.source_trip_id = '00000000-0000-0000-0000-0000000003b2'
    ) a on a.t = b.t
   where a.h is distinct from b.h;
  if changed is not null then
    raise exception 'FAIL case 4: copies of a trip that wasn''t deleted changed in: %', changed;
  end if;
  raise notice 'PASS 4: copies of trips that weren''t deleted are unchanged (saves and plan items)';
end $$;

rollback;
