-- RLS assertions for live trip posting.
--
-- Run in the Supabase SQL editor after 20260926_live_trips.sql. The whole script
-- is one transaction ending in ROLLBACK, so it leaves nothing behind - including
-- the fixture auth users it creates.
--
-- Every case goes through the policies (set role + a JWT claim), not by calling
-- the private.* helpers directly: the helpers being right is not the same thing
-- as the policies using them correctly.
--
-- A failure raises an exception, which aborts the script. Silence past the last
-- NOTICE means a case failed - read the error, not the absence of output.

begin;

-- ---------------------------------------------------------------------------
-- Fixtures
--
-- ...a1 owner     - author of everything below
-- ...a2 follower  - follows the owner
-- ...a3 stranger  - follows nobody
--
-- Note the follow edge is there for realism only. RLS does not consider the
-- follow graph at all: a public trip is public. Who sees what in the feed is a
-- query concern, and "follower" and "stranger" are expected to agree on every
-- assertion here. They're kept separate so that stays visible if it changes.
-- ---------------------------------------------------------------------------
insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password,
  email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data
)
values
  ('00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-0000000000a1',
   'authenticated', 'authenticated', 'owner@rls-test.invalid', '',
   now(), now(), now(), '{}', '{}'),
  ('00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-0000000000a2',
   'authenticated', 'authenticated', 'follower@rls-test.invalid', '',
   now(), now(), now(), '{}', '{}'),
  ('00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-0000000000a3',
   'authenticated', 'authenticated', 'stranger@rls-test.invalid', '',
   now(), now(), now(), '{}', '{}');

insert into public.users (id, username, display_name) values
  ('00000000-0000-0000-0000-0000000000a1', 'rls_owner',    'RLS Owner'),
  ('00000000-0000-0000-0000-0000000000a2', 'rls_follower', 'RLS Follower'),
  ('00000000-0000-0000-0000-0000000000a3', 'rls_stranger', 'RLS Stranger');

insert into public.follows (follower_id, following_id) values
  ('00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-0000000000a1');

-- b1 public live, b2 private live, b3 completed (stands in for a pre-migration trip)
insert into public.trips (id, author_id, title, destination, start_date, end_date, status, visibility, completed_at) values
  ('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1',
   'Public Live', 'Bangkok, Thailand', current_date - 2, current_date + 2, 'live', 'public', null),
  ('00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-0000000000a1',
   'Private Live', 'Lisbon, Portugal', current_date - 2, current_date + 2, 'live', 'private', null),
  ('00000000-0000-0000-0000-0000000000b3', '00000000-0000-0000-0000-0000000000a1',
   'Old Completed', 'Rome, Italy', current_date - 30, current_date - 25, 'completed', 'public', now() - interval '25 days'),
  ('00000000-0000-0000-0000-0000000000b5', '00000000-0000-0000-0000-0000000000a1',
   'Just Started', 'Tokyo, Japan', current_date, current_date + 4, 'live', 'public', null);

-- c1 posted, c2 unposted (both on the public live trip)
-- c3 posted but on the private live trip
-- c4 an 'import' day, as the migration backfills them
insert into public.days (id, trip_id, day_index, date, published_at, published_via) values
  ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-0000000000b1', 1, current_date - 2, now() - interval '2 days', 'post_day'),
  ('00000000-0000-0000-0000-0000000000c2', '00000000-0000-0000-0000-0000000000b1', 2, current_date - 1, null, null),
  ('00000000-0000-0000-0000-0000000000c3', '00000000-0000-0000-0000-0000000000b2', 1, current_date - 2, now() - interval '2 days', 'post_day'),
  ('00000000-0000-0000-0000-0000000000c4', '00000000-0000-0000-0000-0000000000b3', 1, current_date - 30, now() - interval '25 days', 'import'),
  -- c5: the only day of a live trip that has posted nothing yet.
  ('00000000-0000-0000-0000-0000000000c5', '00000000-0000-0000-0000-0000000000b5', 1, current_date, null, null);

-- d1 on the posted day, d2 on the unposted day, d3 assigned to no day at all
insert into public.stops (id, day_id, trip_id, name, category, location, order_in_day) values
  ('00000000-0000-0000-0000-0000000000d1', '00000000-0000-0000-0000-0000000000c1',
   '00000000-0000-0000-0000-0000000000b1', 'Posted Stop', 'sight', 'SRID=4326;POINT(100.5018 13.7563)', 0),
  ('00000000-0000-0000-0000-0000000000d2', '00000000-0000-0000-0000-0000000000c2',
   '00000000-0000-0000-0000-0000000000b1', 'Unposted Stop', 'food', 'SRID=4326;POINT(100.5100 13.7600)', 0),
  ('00000000-0000-0000-0000-0000000000d3', null,
   '00000000-0000-0000-0000-0000000000b1', 'Unassigned Stop', 'other', 'SRID=4326;POINT(100.5200 13.7700)', 1);

insert into public.stop_photos (id, stop_id, storage_path, order_index) values
  ('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000d1', 'b1/d1/0.jpg', 0),
  ('00000000-0000-0000-0000-0000000000e2', '00000000-0000-0000-0000-0000000000d2', 'b1/d2/0.jpg', 0);

-- ---------------------------------------------------------------------------
-- Case 1: the owner sees their own unposted day, its stop, and its photo.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}';

do $$
declare n int;
begin
  select count(*) into n from public.days where id = '00000000-0000-0000-0000-0000000000c2';
  if n <> 1 then raise exception 'FAIL 1a: owner should see their unposted day, saw % row(s)', n; end if;

  select count(*) into n from public.stops where id = '00000000-0000-0000-0000-0000000000d2';
  if n <> 1 then raise exception 'FAIL 1b: owner should see the unposted day''s stop, saw % row(s)', n; end if;

  select count(*) into n from public.stop_photos where id = '00000000-0000-0000-0000-0000000000e2';
  if n <> 1 then raise exception 'FAIL 1c: owner should see the unposted day''s photo, saw % row(s)', n; end if;

  select count(*) into n from public.stops where id = '00000000-0000-0000-0000-0000000000d3';
  if n <> 1 then raise exception 'FAIL 1d: owner should see their day-less stop, saw % row(s)', n; end if;

  select count(*) into n from public.trips where id = '00000000-0000-0000-0000-0000000000b2';
  if n <> 1 then raise exception 'FAIL 1e: owner should see their own private live trip, saw % row(s)', n; end if;

  raise notice 'PASS 1: owner sees their unposted day, its stop and photo, a day-less stop, and their private trip';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 2: a follower sees the posted day of a public live trip - and the live
-- trip itself, which the old policy (status = 'published') would have hidden.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000a2","role":"authenticated"}';

do $$
declare n int;
begin
  select count(*) into n from public.trips where id = '00000000-0000-0000-0000-0000000000b1';
  if n <> 1 then raise exception 'FAIL 2a: follower should see the public live trip, saw % row(s)', n; end if;

  select count(*) into n from public.days where id = '00000000-0000-0000-0000-0000000000c1';
  if n <> 1 then raise exception 'FAIL 2b: follower should see the posted day, saw % row(s)', n; end if;

  select count(*) into n from public.stops where id = '00000000-0000-0000-0000-0000000000d1';
  if n <> 1 then raise exception 'FAIL 2c: follower should see the posted day''s stop, saw % row(s)', n; end if;

  select count(*) into n from public.stop_photos where id = '00000000-0000-0000-0000-0000000000e1';
  if n <> 1 then raise exception 'FAIL 2d: follower should see the posted day''s photo, saw % row(s)', n; end if;

  select count(*) into n from public.days where id = '00000000-0000-0000-0000-0000000000c2';
  if n <> 0 then raise exception 'FAIL 2e: follower should NOT see the unposted day, saw % row(s)', n; end if;

  raise notice 'PASS 2: follower sees the live trip and its posted day, not its unposted day';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 3: a stranger cannot see the unposted day, its stop, or its photo -
-- and cannot see a stop that was never assigned to a day.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000a3","role":"authenticated"}';

do $$
declare n int;
begin
  select count(*) into n from public.days where id = '00000000-0000-0000-0000-0000000000c2';
  if n <> 0 then raise exception 'FAIL 3a: stranger should NOT see the unposted day, saw % row(s)', n; end if;

  select count(*) into n from public.stops where id = '00000000-0000-0000-0000-0000000000d2';
  if n <> 0 then raise exception 'FAIL 3b: stranger should NOT see the unposted day''s stop, saw % row(s)', n; end if;

  select count(*) into n from public.stop_photos where id = '00000000-0000-0000-0000-0000000000e2';
  if n <> 0 then raise exception 'FAIL 3c: stranger should NOT see the unposted day''s photo, saw % row(s)', n; end if;

  select count(*) into n from public.stops where id = '00000000-0000-0000-0000-0000000000d3';
  if n <> 0 then raise exception 'FAIL 3d: stranger should NOT see a day-less stop, saw % row(s)', n; end if;

  -- The posted half of the same trip stays readable.
  select count(*) into n from public.days where id = '00000000-0000-0000-0000-0000000000c1';
  if n <> 1 then raise exception 'FAIL 3e: stranger should see the posted day, saw % row(s)', n; end if;

  raise notice 'PASS 3: stranger sees only the posted day of a public live trip';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 4: a private live trip is invisible to non-owners, posted days included.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000a3","role":"authenticated"}';

do $$
declare n int;
begin
  select count(*) into n from public.trips where id = '00000000-0000-0000-0000-0000000000b2';
  if n <> 0 then raise exception 'FAIL 4a: stranger should NOT see the private live trip, saw % row(s)', n; end if;

  select count(*) into n from public.days where id = '00000000-0000-0000-0000-0000000000c3';
  if n <> 0 then raise exception 'FAIL 4b: stranger should NOT see a posted day of a private trip, saw % row(s)', n; end if;

  raise notice 'PASS 4: private live trip is invisible to non-owners, posted day included';
end $$;
reset role;

-- Same for the follower: following someone grants nothing extra.
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000a2","role":"authenticated"}';

do $$
declare n int;
begin
  select count(*) into n from public.trips where id = '00000000-0000-0000-0000-0000000000b2';
  if n <> 0 then raise exception 'FAIL 4c: follower should NOT see the private live trip, saw % row(s)', n; end if;

  raise notice 'PASS 4c: following does not expose a private trip';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 5: backfilled 'import' days of a completed trip stay readable, so the
-- migration does not hide anything that was public before it ran.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000a3","role":"authenticated"}';

do $$
declare n int;
begin
  select count(*) into n from public.trips where id = '00000000-0000-0000-0000-0000000000b3';
  if n <> 1 then raise exception 'FAIL 5a: stranger should see the completed trip, saw % row(s)', n; end if;

  select count(*) into n from public.days where id = '00000000-0000-0000-0000-0000000000c4';
  if n <> 1 then raise exception 'FAIL 5b: stranger should see the completed trip''s import day, saw % row(s)', n; end if;

  raise notice 'PASS 5: completed trips and their backfilled days remain public';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 6: the same rules hold for a signed-out reader.
-- ---------------------------------------------------------------------------
set local role anon;
set local request.jwt.claims to '';

do $$
declare n int;
begin
  select count(*) into n from public.days where id = '00000000-0000-0000-0000-0000000000c1';
  if n <> 1 then raise exception 'FAIL 6a: anon should see the posted day, saw % row(s)', n; end if;

  select count(*) into n from public.days where id = '00000000-0000-0000-0000-0000000000c2';
  if n <> 0 then raise exception 'FAIL 6b: anon should NOT see the unposted day, saw % row(s)', n; end if;

  select count(*) into n from public.trips where id = '00000000-0000-0000-0000-0000000000b2';
  if n <> 0 then raise exception 'FAIL 6c: anon should NOT see the private live trip, saw % row(s)', n; end if;

  raise notice 'PASS 6: signed-out readers get the same answers';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 7: a draft trip is still owner-only. The lifecycle rename must not have
-- widened anything at the bottom of the funnel.
-- ---------------------------------------------------------------------------
insert into public.trips (id, author_id, title, destination, start_date, end_date, status, visibility) values
  ('00000000-0000-0000-0000-0000000000b4', '00000000-0000-0000-0000-0000000000a1',
   'A Draft', 'Tokyo, Japan', current_date, current_date + 1, 'draft', 'public');

set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000a3","role":"authenticated"}';

do $$
declare n int;
begin
  select count(*) into n from public.trips where id = '00000000-0000-0000-0000-0000000000b4';
  if n <> 0 then raise exception 'FAIL 7: stranger should NOT see a draft trip, saw % row(s)', n; end if;

  raise notice 'PASS 7: drafts stay private to their author';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 8: the constraints hold the two publish columns together, and reject a
-- publish marker the app does not know about.
-- ---------------------------------------------------------------------------
do $$
declare ok boolean;
begin
  begin
    update public.days set published_at = now() where id = '00000000-0000-0000-0000-0000000000c2';
    ok := false;
  exception when check_violation then ok := true;
  end;
  if not ok then raise exception 'FAIL 8a: published_at without published_via should violate days_published_pair_check'; end if;

  begin
    update public.days set published_at = now(), published_via = 'somehow'
    where id = '00000000-0000-0000-0000-0000000000c2';
    ok := false;
  exception when check_violation then ok := true;
  end;
  if not ok then raise exception 'FAIL 8b: an unknown published_via should violate days_published_via_check'; end if;

  raise notice 'PASS 8: publish-state constraints hold';
end $$;

-- ---------------------------------------------------------------------------
-- Case 9: the helpers are not reachable as PostgREST RPCs. `private` is not an
-- exposed schema, and the API roles have no rights on it beyond execute.
-- ---------------------------------------------------------------------------
do $$
declare n int;
begin
  select count(*) into n
  from information_schema.role_table_grants
  where table_schema = 'private' and grantee in ('anon', 'authenticated');
  if n <> 0 then raise exception 'FAIL 9: API roles hold table grants in private (% found)', n; end if;

  if has_schema_privilege('anon', 'private', 'create') then
    raise exception 'FAIL 9: anon can create objects in private';
  end if;

  raise notice 'PASS 9: private schema is execute-only for the API roles';
end $$;

-- ---------------------------------------------------------------------------
-- Case 10: an owner cannot hand their trip to somebody else. A USING-only
-- policy allows it, because USING reads the row as it stands - WITH CHECK is
-- what re-tests the row as it would be.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}';

do $$
declare ok boolean; n int;
begin
  begin
    update public.trips
    set author_id = '00000000-0000-0000-0000-0000000000a3'
    where id = '00000000-0000-0000-0000-0000000000b1';
    -- A WITH CHECK failure raises; anything else must have changed no rows.
    get diagnostics n = row_count;
    ok := (n = 0);
  exception when insufficient_privilege then ok := true;
  end;
  if not ok then raise exception 'FAIL 10a: owner was able to reassign author_id'; end if;

  -- An edit they are entitled to still goes through.
  update public.trips set title = 'Public Live, retitled'
  where id = '00000000-0000-0000-0000-0000000000b1';
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL 10b: owner should still be able to edit their own trip, % row(s)', n; end if;

  raise notice 'PASS 10: author_id is pinned across an update, ordinary edits still allowed';
end $$;
reset role;

-- A non-owner cannot take a trip either.
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000a3","role":"authenticated"}';

do $$
declare n int;
begin
  update public.trips
  set author_id = '00000000-0000-0000-0000-0000000000a3'
  where id = '00000000-0000-0000-0000-0000000000b1';
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL 10c: a stranger updated a trip that is not theirs, % row(s)', n; end if;

  raise notice 'PASS 10c: a stranger cannot claim a trip';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 11: a comment must name the stop's real trip. Written unqualified, the
-- check compared stops.trip_id with itself and any trip_id passed.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000a3","role":"authenticated"}';

do $$
declare ok boolean;
begin
  begin
    -- Stop d1 belongs to trip b1, but this claims b3.
    insert into public.stop_comments (stop_id, trip_id, author_id, body)
    values ('00000000-0000-0000-0000-0000000000d1', '00000000-0000-0000-0000-0000000000b3',
            '00000000-0000-0000-0000-0000000000a3', 'mismatched trip');
    ok := false;
  exception when insufficient_privilege then ok := true;
  end;
  if not ok then raise exception 'FAIL 11a: a comment naming the wrong trip was accepted'; end if;

  -- The honest version of the same comment is fine.
  insert into public.stop_comments (stop_id, trip_id, author_id, body)
  values ('00000000-0000-0000-0000-0000000000d1', '00000000-0000-0000-0000-0000000000b1',
          '00000000-0000-0000-0000-0000000000a3', 'lovely spot');

  raise notice 'PASS 11: stop_id and trip_id must agree, and a correct comment still inserts';
end $$;
reset role;

-- A comment on a stop of an unposted day is refused outright.
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000a3","role":"authenticated"}';

do $$
declare ok boolean;
begin
  begin
    insert into public.stop_comments (stop_id, trip_id, author_id, body)
    values ('00000000-0000-0000-0000-0000000000d2', '00000000-0000-0000-0000-0000000000b1',
            '00000000-0000-0000-0000-0000000000a3', 'how did I see this');
    ok := false;
  exception when insufficient_privilege then ok := true;
  end;
  if not ok then raise exception 'FAIL 11b: commented on a stop of an unposted day'; end if;

  raise notice 'PASS 11b: an invisible stop cannot be commented on';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 12: a live trip that has posted nothing is invisible. Starting a trip
-- must not announce where you are before you have said anything.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000a3","role":"authenticated"}';

do $$
declare n int;
begin
  select count(*) into n from public.trips where id = '00000000-0000-0000-0000-0000000000b5';
  if n <> 0 then raise exception 'FAIL 12a: stranger should NOT see a live trip with no posted days, saw % row(s)', n; end if;

  select count(*) into n from public.days where id = '00000000-0000-0000-0000-0000000000c5';
  if n <> 0 then raise exception 'FAIL 12b: stranger should NOT see its day, saw % row(s)', n; end if;

  raise notice 'PASS 12: a live trip with nothing posted is invisible to non-owners';
end $$;
reset role;

-- Its author still sees it, or they could never post the first day.
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000a1","role":"authenticated"}';

do $$
declare n int;
begin
  select count(*) into n from public.trips where id = '00000000-0000-0000-0000-0000000000b5';
  if n <> 1 then raise exception 'FAIL 12c: owner should see their not-yet-posted live trip, saw % row(s)', n; end if;

  raise notice 'PASS 12c: its author still sees it';
end $$;
reset role;

rollback;
