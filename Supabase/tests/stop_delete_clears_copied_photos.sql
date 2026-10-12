-- Checks for 20261012_stop_delete_clears_copied_photos.sql (run after
-- 20261011_trip_delete_clears_copied_photos.sql, which case 3 also relies on).
--
-- Run in the Supabase SQL editor after the migration. One transaction ending
-- in ROLLBACK: it leaves nothing behind, fixture auth users included.
--
-- A failure raises an exception, which aborts the script. The last result is
-- one row, "4 of 4 cases passed", for editors that show the result grid but
-- not notices; it only appears if every case passed.
--
-- The cases fail if the trigger is missing, runs AFTER DELETE (the copies'
-- stop_id is already null by then), isn't SECURITY DEFINER (the author's
-- delete can't reach other people's copies through RLS), or loses the
-- owner-exists check (an account delete then fails a foreign key).

begin;

-- ---------------------------------------------------------------------------
-- Fixtures
--
-- ...4a1 author    - owns trip 4b1 (stops 4d1, 4d2) and trip 4b2 (stop 4d3)
-- ...4a2 saver     - copied all of those places into saves and a plan
-- ...4a3 bystander - copied 4d3; nothing of theirs may change
-- ...4a4 doomed    - owns trip 4b4 (stop 4d4); the account is deleted in case 4
-- ---------------------------------------------------------------------------
insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password,
  email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data
)
select '00000000-0000-0000-0000-000000000000', id, 'authenticated', 'authenticated',
       name || '@stop-delete-test.invalid', '', now(), now(), now(), '{}', '{}'
  from (values ('00000000-0000-0000-0000-0000000004a1'::uuid, 'author'),
               ('00000000-0000-0000-0000-0000000004a2'::uuid, 'saver'),
               ('00000000-0000-0000-0000-0000000004a3'::uuid, 'bystander'),
               ('00000000-0000-0000-0000-0000000004a4'::uuid, 'doomed')) u(id, name);

insert into public.users (id, username, display_name) values
  ('00000000-0000-0000-0000-0000000004a1', 'sdel_author',    'Author Person'),
  ('00000000-0000-0000-0000-0000000004a2', 'sdel_saver',     'Saver Person'),
  ('00000000-0000-0000-0000-0000000004a3', 'sdel_bystander', 'Bystander'),
  ('00000000-0000-0000-0000-0000000004a4', 'sdel_doomed',    'Doomed Person');

insert into public.trips (id, author_id, title, destination, start_date, end_date, status, visibility, completed_at) values
  ('00000000-0000-0000-0000-0000000004b1', '00000000-0000-0000-0000-0000000004a1',
   'Lisbon Trip', 'Lisbon, Portugal', current_date - 10, current_date - 9, 'completed', 'public', now()),
  ('00000000-0000-0000-0000-0000000004b2', '00000000-0000-0000-0000-0000000004a1',
   'Porto Trip', 'Porto, Portugal', current_date - 20, current_date - 19, 'completed', 'public', now()),
  ('00000000-0000-0000-0000-0000000004b4', '00000000-0000-0000-0000-0000000004a4',
   'Doomed Trip', 'Rome, Italy', current_date - 30, current_date - 29, 'completed', 'public', now());

insert into public.days (id, trip_id, day_index, date, published_at, published_via) values
  ('00000000-0000-0000-0000-0000000004c1', '00000000-0000-0000-0000-0000000004b1', 1, current_date - 10, now(), 'import'),
  ('00000000-0000-0000-0000-0000000004c2', '00000000-0000-0000-0000-0000000004b2', 1, current_date - 20, now(), 'import'),
  ('00000000-0000-0000-0000-0000000004c4', '00000000-0000-0000-0000-0000000004b4', 1, current_date - 30, now(), 'import');

insert into public.stops (id, day_id, trip_id, name, category, location, order_in_day) values
  ('00000000-0000-0000-0000-0000000004d1', '00000000-0000-0000-0000-0000000004c1',
   '00000000-0000-0000-0000-0000000004b1', 'Deleted Stop', 'sight', 'SRID=4326;POINT(-9.1393 38.7223)', 0),
  ('00000000-0000-0000-0000-0000000004d2', '00000000-0000-0000-0000-0000000004c1',
   '00000000-0000-0000-0000-0000000004b1', 'Sibling Stop', 'food', 'SRID=4326;POINT(-9.1427 38.7107)', 1),
  ('00000000-0000-0000-0000-0000000004d3', '00000000-0000-0000-0000-0000000004c2',
   '00000000-0000-0000-0000-0000000004b2', 'Porto Stop', 'food', 'SRID=4326;POINT(-8.6291 41.1579)', 0),
  ('00000000-0000-0000-0000-0000000004d4', '00000000-0000-0000-0000-0000000004c4',
   '00000000-0000-0000-0000-0000000004b4', 'Doomed Stop', 'sight', 'SRID=4326;POINT(12.4964 41.9028)', 0);

-- Saved places, one row per (saver, stop) that matters.
insert into public.saved_places (id, user_id, stop_id, source_trip_id, source_trip_title, source_author_name,
                                 name, category, latitude, longitude, photo_url) values
  ('00000000-0000-0000-0000-0000000004f1', '00000000-0000-0000-0000-0000000004a2',   -- saver, of 4d1
   '00000000-0000-0000-0000-0000000004d1', '00000000-0000-0000-0000-0000000004b1', 'Lisbon Trip', 'Author Person',
   'Deleted Stop', 'sight', 38.7223, -9.1393, 'https://example.invalid/stop-photos/4b1/4d1/0.jpg'),
  ('00000000-0000-0000-0000-0000000004f2', '00000000-0000-0000-0000-0000000004a1',   -- author's own save of 4d1
   '00000000-0000-0000-0000-0000000004d1', '00000000-0000-0000-0000-0000000004b1', 'Lisbon Trip', 'Author Person',
   'Deleted Stop', 'sight', 38.7223, -9.1393, 'https://example.invalid/stop-photos/4b1/4d1/0.jpg'),
  ('00000000-0000-0000-0000-0000000004f3', '00000000-0000-0000-0000-0000000004a2',   -- saver, of 4d2 (sibling)
   '00000000-0000-0000-0000-0000000004d2', '00000000-0000-0000-0000-0000000004b1', 'Lisbon Trip', 'Author Person',
   'Sibling Stop', 'food', 38.7107, -9.1427, 'https://example.invalid/stop-photos/4b1/4d2/0.jpg'),
  ('00000000-0000-0000-0000-0000000004f4', '00000000-0000-0000-0000-0000000004a2',   -- saver, of 4d3 (other trip)
   '00000000-0000-0000-0000-0000000004d3', '00000000-0000-0000-0000-0000000004b2', 'Porto Trip', 'Author Person',
   'Porto Stop', 'food', 41.1579, -8.6291, 'https://example.invalid/stop-photos/4b2/4d3/0.jpg'),
  ('00000000-0000-0000-0000-0000000004f5', '00000000-0000-0000-0000-0000000004a3',   -- bystander, of 4d3
   '00000000-0000-0000-0000-0000000004d3', '00000000-0000-0000-0000-0000000004b2', 'Porto Trip', 'Author Person',
   'Porto Stop', 'food', 41.1579, -8.6291, 'https://example.invalid/stop-photos/4b2/4d3/0.jpg'),
  ('00000000-0000-0000-0000-0000000004f6', '00000000-0000-0000-0000-0000000004a2',   -- saver, of 4d4 (doomed)
   '00000000-0000-0000-0000-0000000004d4', '00000000-0000-0000-0000-0000000004b4', 'Doomed Trip', 'Doomed Person',
   'Doomed Stop', 'sight', 41.9028, 12.4964, 'https://example.invalid/stop-photos/4b4/4d4/0.jpg'),
  ('00000000-0000-0000-0000-0000000004f7', '00000000-0000-0000-0000-0000000004a4',   -- doomed's own save of 4d4
   '00000000-0000-0000-0000-0000000004d4', '00000000-0000-0000-0000-0000000004b4', 'Doomed Trip', 'Doomed Person',
   'Doomed Stop', 'sight', 41.9028, 12.4964, 'https://example.invalid/stop-photos/4b4/4d4/0.jpg');

insert into public.plans (id, user_id, title) values
  ('00000000-0000-0000-0000-0000000004e1', '00000000-0000-0000-0000-0000000004a2', 'Saver Plan'),
  ('00000000-0000-0000-0000-0000000004e4', '00000000-0000-0000-0000-0000000004a4', 'Doomed Plan');
insert into public.plan_items (id, plan_id, day_number, position, stop_id, source_trip_id, source_trip_title,
                               source_author_name, name, category, latitude, longitude, photo_url, added_by) values
  ('00000000-0000-0000-0000-000000000491', '00000000-0000-0000-0000-0000000004e1', 1, 0,   -- of 4d1
   '00000000-0000-0000-0000-0000000004d1', '00000000-0000-0000-0000-0000000004b1', 'Lisbon Trip', 'Author Person',
   'Deleted Stop', 'sight', 38.7223, -9.1393, 'https://example.invalid/stop-photos/4b1/4d1/0.jpg',
   '00000000-0000-0000-0000-0000000004a2'),
  ('00000000-0000-0000-0000-000000000492', '00000000-0000-0000-0000-0000000004e1', 1, 1,   -- of 4d2 (sibling)
   '00000000-0000-0000-0000-0000000004d2', '00000000-0000-0000-0000-0000000004b1', 'Lisbon Trip', 'Author Person',
   'Sibling Stop', 'food', 38.7107, -9.1427, 'https://example.invalid/stop-photos/4b1/4d2/0.jpg',
   '00000000-0000-0000-0000-0000000004a2'),
  ('00000000-0000-0000-0000-000000000493', '00000000-0000-0000-0000-0000000004e1', 1, 2,   -- of 4d3 (other trip)
   '00000000-0000-0000-0000-0000000004d3', '00000000-0000-0000-0000-0000000004b2', 'Porto Trip', 'Author Person',
   'Porto Stop', 'food', 41.1579, -8.6291, 'https://example.invalid/stop-photos/4b2/4d3/0.jpg',
   '00000000-0000-0000-0000-0000000004a2'),
  ('00000000-0000-0000-0000-000000000494', '00000000-0000-0000-0000-0000000004e1', 1, 3,   -- of 4d4 (doomed)
   '00000000-0000-0000-0000-0000000004d4', '00000000-0000-0000-0000-0000000004b4', 'Doomed Trip', 'Doomed Person',
   'Doomed Stop', 'sight', 41.9028, 12.4964, 'https://example.invalid/stop-photos/4b4/4d4/0.jpg',
   '00000000-0000-0000-0000-0000000004a2'),
  ('00000000-0000-0000-0000-000000000496', '00000000-0000-0000-0000-0000000004e4', 1, 0,   -- doomed's own plan, of 4d4
   '00000000-0000-0000-0000-0000000004d4', '00000000-0000-0000-0000-0000000004b4', 'Doomed Trip', 'Doomed Person',
   'Doomed Stop', 'sight', 41.9028, 12.4964, 'https://example.invalid/stop-photos/4b4/4d4/0.jpg',
   '00000000-0000-0000-0000-0000000004a4');

-- Each case adds its number here once it has passed; the summary counts them.
create temp table cases_passed (n int primary key);

-- Fingerprints of copies that no case should touch until case 3 deletes 4b1:
-- copies of the sibling stop 4d2 (until then), and of the other trip's 4d3.
create temp table untouched_before as
select 'sibling' t, md5(string_agg(x::text, '|' order by x::text)) h from (
  select sp::text from public.saved_places sp where sp.stop_id = '00000000-0000-0000-0000-0000000004d2'
  union all select pi::text from public.plan_items pi where pi.stop_id = '00000000-0000-0000-0000-0000000004d2') x(x)
union all
select 'other_trip', md5(string_agg(x::text, '|' order by x::text)) from (
  select sp::text from public.saved_places sp where sp.stop_id = '00000000-0000-0000-0000-0000000004d3'
  union all select pi::text from public.plan_items pi where pi.stop_id = '00000000-0000-0000-0000-0000000004d3') x(x);

create temp view untouched_now as
select 'sibling' t, md5(string_agg(x::text, '|' order by x::text)) h from (
  select sp::text from public.saved_places sp where sp.stop_id = '00000000-0000-0000-0000-0000000004d2'
  union all select pi::text from public.plan_items pi where pi.stop_id = '00000000-0000-0000-0000-0000000004d2') x(x)
union all
select 'other_trip', md5(string_agg(x::text, '|' order by x::text)) from (
  select sp::text from public.saved_places sp where sp.stop_id = '00000000-0000-0000-0000-0000000004d3'
  union all select pi::text from public.plan_items pi where pi.stop_id = '00000000-0000-0000-0000-0000000004d3') x(x);

-- ---------------------------------------------------------------------------
-- Case 1: the author deletes stop 4d1 from the app - as the authenticated
-- role, through RLS, the way PostgREST runs it.
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"00000000-0000-0000-0000-0000000004a1","role":"authenticated"}', true);

do $$
declare n int;
begin
  delete from public.stops where id = '00000000-0000-0000-0000-0000000004d1';
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL case 1: the author''s delete removed % stop rows, expected 1', n; end if;
end $$;

reset role;
select set_config('request.jwt.claims', '', true);

do $$
declare sp public.saved_places; pi public.plan_items;
begin
  select * into sp from public.saved_places where id = '00000000-0000-0000-0000-0000000004f1';
  if sp.id is null then raise exception 'FAIL case 1: the saver''s saved place was deleted'; end if;
  if sp.photo_url is not null then
    raise exception 'FAIL case 1: the saver''s saved place still has the deleted stop''s photo: %', sp.photo_url;
  end if;
  if sp.name <> 'Deleted Stop' or sp.source_trip_title is distinct from 'Lisbon Trip'
     or sp.source_author_name is distinct from 'Author Person' or sp.latitude is null then
    raise exception 'FAIL case 1: the saver''s saved place lost more than its photo';
  end if;
  if sp.stop_id is not null or sp.source_trip_id is distinct from '00000000-0000-0000-0000-0000000004b1' then
    raise exception 'FAIL case 1: expected stop_id null and the trip link kept, got % / %', sp.stop_id, sp.source_trip_id;
  end if;

  if (select photo_url from public.saved_places where id = '00000000-0000-0000-0000-0000000004f2') is not null then
    raise exception 'FAIL case 1: the author''s own saved copy still has the photo';
  end if;

  select * into pi from public.plan_items where id = '00000000-0000-0000-0000-000000000491';
  if pi.id is null then raise exception 'FAIL case 1: the plan item was deleted'; end if;
  if pi.photo_url is not null then
    raise exception 'FAIL case 1: the plan item still has the deleted stop''s photo: %', pi.photo_url;
  end if;
  if pi.name <> 'Deleted Stop' or pi.source_trip_title is distinct from 'Lisbon Trip'
     or pi.added_by is distinct from '00000000-0000-0000-0000-0000000004a2' then
    raise exception 'FAIL case 1: the plan item lost more than its photo';
  end if;
  insert into pg_temp.cases_passed values (1);
  raise notice 'PASS 1: deleting a stop as the trip''s author clears the photo on every copy of it (others'' saves and plans, and the author''s own), and nothing else';
end $$;

-- ---------------------------------------------------------------------------
-- Case 2: copies of other stops - the same trip's sibling, another trip's -
-- are byte-for-byte unchanged.
-- ---------------------------------------------------------------------------
do $$
declare changed text;
begin
  select string_agg(b.t, ', ') into changed
    from untouched_before b join untouched_now a on a.t = b.t
   where a.h is distinct from b.h;
  if changed is not null then
    raise exception 'FAIL case 2: copies of a stop that wasn''t deleted changed: %', changed;
  end if;
  insert into pg_temp.cases_passed values (2);
  raise notice 'PASS 2: copies of other stops (same trip and another trip) are unchanged';
end $$;

-- ---------------------------------------------------------------------------
-- Case 3: deleting the rest of trip 4b1 still works. Its delete cascades to
-- the remaining stop 4d2, so both triggers run; the sibling's copies lose
-- their photo, and the other trip's copies stay as they were.
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims',
  '{"sub":"00000000-0000-0000-0000-0000000004a1","role":"authenticated"}', true);

do $$
declare n int;
begin
  delete from public.trips where id = '00000000-0000-0000-0000-0000000004b1';
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL case 3: the author''s trip delete removed % rows, expected 1', n; end if;
end $$;

reset role;
select set_config('request.jwt.claims', '', true);

do $$
begin
  if exists (select 1 from public.stops where trip_id = '00000000-0000-0000-0000-0000000004b1') then
    raise exception 'FAIL case 3: the deleted trip''s stops are still there';
  end if;
  if (select photo_url from public.saved_places where id = '00000000-0000-0000-0000-0000000004f3') is not null
     or (select photo_url from public.plan_items where id = '00000000-0000-0000-0000-000000000492') is not null then
    raise exception 'FAIL case 3: copies of the deleted trip''s other stop still have its photo';
  end if;
  if (select name from public.saved_places where id = '00000000-0000-0000-0000-0000000004f3') is distinct from 'Sibling Stop' then
    raise exception 'FAIL case 3: the copy of the deleted trip''s other stop was lost';
  end if;
  if exists (select 1 from untouched_before b join untouched_now a on a.t = b.t
              where b.t = 'other_trip' and a.h is distinct from b.h) then
    raise exception 'FAIL case 3: copies of another trip changed';
  end if;
  insert into pg_temp.cases_passed values (3);
  raise notice 'PASS 3: a trip delete still works with both triggers - its stops go, their copies lose the photo, other trips'' copies are unchanged';
end $$;

-- ---------------------------------------------------------------------------
-- Case 4: deleting doomed's account cascades to trip 4b4 and stop 4d4. The
-- saver's copies lose photo, title and name; doomed's own save and plan go;
-- nothing errors.
-- ---------------------------------------------------------------------------
do $$
declare sp public.saved_places; pi public.plan_items; n int;
begin
  delete from auth.users where id = '00000000-0000-0000-0000-0000000004a4';
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL case 4: the account delete removed % rows, expected 1', n; end if;

  if exists (select 1 from public.stops where id = '00000000-0000-0000-0000-0000000004d4') then
    raise exception 'FAIL case 4: the deleted account''s stop is still there';
  end if;

  select * into sp from public.saved_places where id = '00000000-0000-0000-0000-0000000004f6';
  if sp.id is null then raise exception 'FAIL case 4: the saver''s copy was deleted with the account'; end if;
  if sp.photo_url is not null or sp.source_trip_title is not null or sp.source_author_name is not null then
    raise exception 'FAIL case 4: the saver''s copy still carries the deleted account''s photo/title/name';
  end if;

  select * into pi from public.plan_items where id = '00000000-0000-0000-0000-000000000494';
  if pi.id is null or pi.photo_url is not null or pi.source_trip_title is not null then
    raise exception 'FAIL case 4: the saver''s plan item was lost or still carries the deleted account''s data';
  end if;

  select count(*) into n from (
    select 1 from public.saved_places where user_id = '00000000-0000-0000-0000-0000000004a4'
    union all select 1 from public.plans where user_id = '00000000-0000-0000-0000-0000000004a4'
    union all select 1 from public.plan_items where plan_id = '00000000-0000-0000-0000-0000000004e4'
  ) x;
  if n <> 0 then raise exception 'FAIL case 4: % of the deleted account''s own saves/plans/items are left', n; end if;
  insert into pg_temp.cases_passed values (4);
  raise notice 'PASS 4: account deletion still cascades through its trip and stops - other people''s copies lose photo, title and name, its own saves and plan go';
end $$;

-- ---------------------------------------------------------------------------
-- Summary: an error unless all four cases recorded a pass, then one row.
-- ---------------------------------------------------------------------------
do $$
declare n int;
begin
  select count(*) into n from pg_temp.cases_passed;
  if n <> 4 then raise exception 'FAIL summary: only % of 4 cases passed', n; end if;
end $$;

select format('%s of 4 cases passed', count(*)) as result from pg_temp.cases_passed;

rollback;
