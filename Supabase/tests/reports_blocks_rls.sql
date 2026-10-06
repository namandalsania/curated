-- RLS assertions for reports and blocks.
--
-- Run in the Supabase SQL editor after 20261005_reports_and_blocks.sql. The
-- whole script is one transaction ending in ROLLBACK, so it leaves nothing
-- behind - including the fixture auth users it creates.
--
-- Every case goes through the policies (set role + a JWT claim), not by calling
-- private.is_blocked_pair() directly. Cases that expect a refusal catch the
-- error inside their own block and raise if it didn't happen.
--
-- A failure raises an exception, which aborts the script. Silence past the last
-- NOTICE means a case failed - read the error, not the absence of output.

begin;

-- ---------------------------------------------------------------------------
-- Fixtures
--
-- ...a1 blocker   - author of trip b1; blocks a2 in case 3
-- ...a2 blocked   - author of trip b2; commented on b1, followed a1, notified a1
-- ...a3 bystander - unrelated; sees everything public throughout
-- ---------------------------------------------------------------------------
insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password,
  email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data
)
values
  ('00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-0000000001a1',
   'authenticated', 'authenticated', 'blocker@rls-test.invalid', '',
   now(), now(), now(), '{}', '{}'),
  ('00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-0000000001a2',
   'authenticated', 'authenticated', 'blocked@rls-test.invalid', '',
   now(), now(), now(), '{}', '{}'),
  ('00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-0000000001a3',
   'authenticated', 'authenticated', 'bystander@rls-test.invalid', '',
   now(), now(), now(), '{}', '{}');

insert into public.users (id, username, display_name) values
  ('00000000-0000-0000-0000-0000000001a1', 'rls_blocker',   'RLS Blocker'),
  ('00000000-0000-0000-0000-0000000001a2', 'rls_blocked',   'RLS Blocked'),
  ('00000000-0000-0000-0000-0000000001a3', 'rls_bystander', 'RLS Bystander');

-- Follows both ways between a1 and a2, plus a3 -> a1 which must survive.
insert into public.follows (follower_id, following_id) values
  ('00000000-0000-0000-0000-0000000001a1', '00000000-0000-0000-0000-0000000001a2'),
  ('00000000-0000-0000-0000-0000000001a2', '00000000-0000-0000-0000-0000000001a1'),
  ('00000000-0000-0000-0000-0000000001a3', '00000000-0000-0000-0000-0000000001a1');

-- b1: a1's public completed trip. b2: a2's public completed trip.
insert into public.trips (id, author_id, title, destination, start_date, end_date, status, visibility, completed_at) values
  ('00000000-0000-0000-0000-0000000001b1', '00000000-0000-0000-0000-0000000001a1',
   'Blocker Trip', 'Lisbon, Portugal', current_date - 10, current_date - 8, 'completed', 'public', now() - interval '8 days'),
  ('00000000-0000-0000-0000-0000000001b2', '00000000-0000-0000-0000-0000000001a2',
   'Blocked Trip', 'Rome, Italy', current_date - 20, current_date - 18, 'completed', 'public', now() - interval '18 days');

insert into public.days (id, trip_id, day_index, date, published_at, published_via) values
  ('00000000-0000-0000-0000-0000000001c1', '00000000-0000-0000-0000-0000000001b1', 1, current_date - 10, now() - interval '8 days', 'import');

insert into public.stops (id, day_id, trip_id, name, category, location, order_in_day) values
  ('00000000-0000-0000-0000-0000000001d1', '00000000-0000-0000-0000-0000000001c1',
   '00000000-0000-0000-0000-0000000001b1', 'Blocker Stop', 'sight', 'SRID=4326;POINT(-9.1393 38.7223)', 0);

insert into public.stop_photos (id, stop_id, storage_path, order_index) values
  ('00000000-0000-0000-0000-0000000001e1', '00000000-0000-0000-0000-0000000001d1', 'b1/d1/0.jpg', 0);

-- a2's comment on a1's stop, made before any block.
insert into public.stop_comments (id, stop_id, trip_id, author_id, body) values
  ('00000000-0000-0000-0000-0000000001f1', '00000000-0000-0000-0000-0000000001d1',
   '00000000-0000-0000-0000-0000000001b1', '00000000-0000-0000-0000-0000000001a2', 'Lovely spot');

-- a2 -> a1 notification and share, made before any block.
insert into public.notifications (id, recipient_id, actor_id, type) values
  ('00000000-0000-0000-0000-000000000191', '00000000-0000-0000-0000-0000000001a1',
   '00000000-0000-0000-0000-0000000001a2', 'follow');
insert into public.trip_shares (id, trip_id, sender_id, recipient_id) values
  ('00000000-0000-0000-0000-000000000192', '00000000-0000-0000-0000-0000000001b2',
   '00000000-0000-0000-0000-0000000001a2', '00000000-0000-0000-0000-0000000001a1');

-- ---------------------------------------------------------------------------
-- Case 1: reports - insert your own, nothing else.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000001a3","role":"authenticated"}';

do $$
declare refused boolean;
begin
  -- reporter_id comes from the column default.
  insert into public.reports (target_type, target_id, reason, note)
  values ('trip', '00000000-0000-0000-0000-0000000001b2', 'spam', 'Looks like an ad');

  refused := false;
  begin
    insert into public.reports (target_type, target_id, reason)
    values ('trip', '00000000-0000-0000-0000-0000000001b2', 'other');
  exception when unique_violation then refused := true;
  end;
  if not refused then raise exception 'FAIL 1a: a second report on the same target should be refused'; end if;

  refused := false;
  begin
    insert into public.reports (reporter_id, target_type, target_id, reason)
    values ('00000000-0000-0000-0000-0000000001a2', 'profile', '00000000-0000-0000-0000-0000000001a1', 'harassment');
  exception when insufficient_privilege then refused := true;
  end;
  if not refused then raise exception 'FAIL 1b: reporting as someone else should be refused'; end if;

  refused := false;
  begin
    insert into public.reports (target_type, target_id, reason, status)
    values ('stop', '00000000-0000-0000-0000-0000000001d1', 'misleading', 'actioned');
  exception when insufficient_privilege then refused := true;
  end;
  if not refused then raise exception 'FAIL 1c: a report filed as already actioned should be refused'; end if;

  refused := false;
  begin
    insert into public.reports (target_type, target_id, reason, note)
    values ('comment', '00000000-0000-0000-0000-0000000001f1', 'other', repeat('x', 501));
  exception when check_violation then refused := true;
  end;
  if not refused then raise exception 'FAIL 1d: a note over 500 characters should be refused'; end if;

  refused := false;
  begin
    insert into public.reports (target_type, target_id, reason)
    values ('trip', '00000000-0000-0000-0000-0000000001b1', 'rude');
  exception when check_violation then refused := true;
  end;
  if not refused then raise exception 'FAIL 1e: a reason outside the list should be refused'; end if;

  refused := false;
  begin
    perform 1 from public.reports;
  exception when insufficient_privilege then refused := true;
  end;
  if not refused then raise exception 'FAIL 1f: reading reports back should be refused'; end if;

  refused := false;
  begin
    update public.reports set status = 'reviewed';
  exception when insufficient_privilege then refused := true;
  end;
  if not refused then raise exception 'FAIL 1g: updating a report should be refused'; end if;

  refused := false;
  begin
    delete from public.reports;
  exception when insufficient_privilege then refused := true;
  end;
  if not refused then raise exception 'FAIL 1h: deleting a report should be refused'; end if;

  raise notice 'PASS 1: a report can be filed once, as yourself, as open, within the limits - and never read, changed or removed';
end $$;
reset role;

set local role anon;
do $$
declare refused boolean := false;
begin
  begin
    insert into public.reports (reporter_id, target_type, target_id, reason)
    values ('00000000-0000-0000-0000-0000000001a3', 'trip', '00000000-0000-0000-0000-0000000001b1', 'spam');
  exception when insufficient_privilege then refused := true;
  end;
  if not refused then raise exception 'FAIL 1i: a signed-out caller should not be able to report'; end if;
  raise notice 'PASS 1i: signed out, reporting is refused';
end $$;
reset role;

do $$
declare n int;
begin
  select count(*) into n from public.reports where reporter_id = '00000000-0000-0000-0000-0000000001a3';
  if n <> 1 then raise exception 'FAIL 1j: exactly one report should have been stored, found %', n; end if;
  raise notice 'PASS 1j: exactly the one valid report was stored';
end $$;

-- ---------------------------------------------------------------------------
-- Case 2: before any block, a2 sees a1's trip and a1 sees a2's comment.
-- (Confirms the fixtures, so the later "hidden" results mean something.)
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000001a2","role":"authenticated"}';
do $$
declare n int;
begin
  select count(*) into n from public.trips where id = '00000000-0000-0000-0000-0000000001b1';
  if n <> 1 then raise exception 'FAIL 2a: before blocking, a2 should see a1''s trip, saw %', n; end if;
  raise notice 'PASS 2a: before blocking, a2 sees a1''s trip';
end $$;
reset role;

set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000001a1","role":"authenticated"}';
do $$
declare n int;
begin
  select count(*) into n from public.stop_comments where id = '00000000-0000-0000-0000-0000000001f1';
  if n <> 1 then raise exception 'FAIL 2b: before blocking, a1 should see a2''s comment, saw %', n; end if;
  select count(*) into n from public.notifications where id = '00000000-0000-0000-0000-000000000191';
  if n <> 1 then raise exception 'FAIL 2c: before blocking, a1 should see a2''s notification, saw %', n; end if;
  raise notice 'PASS 2b: before blocking, a1 sees a2''s comment and notification';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 3: blocks - a1 blocks a2. Own rows only; never yourself.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000001a1","role":"authenticated"}';
do $$
declare n int; refused boolean;
begin
  insert into public.blocks (blocked_id) values ('00000000-0000-0000-0000-0000000001a2');

  select count(*) into n from public.blocks;
  if n <> 1 then raise exception 'FAIL 3a: a1 should see their one block, saw %', n; end if;

  refused := false;
  begin
    insert into public.blocks (blocker_id, blocked_id)
    values ('00000000-0000-0000-0000-0000000001a3', '00000000-0000-0000-0000-0000000001a2');
  exception when insufficient_privilege then refused := true;
  end;
  if not refused then raise exception 'FAIL 3b: blocking on someone else''s behalf should be refused'; end if;

  refused := false;
  begin
    insert into public.blocks (blocked_id) values ('00000000-0000-0000-0000-0000000001a1');
  exception when check_violation then refused := true;
  end;
  if not refused then raise exception 'FAIL 3c: blocking yourself should be refused'; end if;

  raise notice 'PASS 3a: a1 can block a2, not on another''s behalf, not themselves';
end $$;
reset role;

-- The blocked side can't see or remove the block.
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000001a2","role":"authenticated"}';
do $$
declare n int;
begin
  select count(*) into n from public.blocks;
  if n <> 0 then raise exception 'FAIL 3d: a2 should not see that a1 blocked them, saw % row(s)', n; end if;

  delete from public.blocks where blocker_id = '00000000-0000-0000-0000-0000000001a1';
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL 3e: a2 should not be able to remove a1''s block, removed %', n; end if;

  raise notice 'PASS 3d: a2 can neither see nor remove the block';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 4: the block removed the follows between a1 and a2, both ways, and
-- left a3 -> a1 alone.
-- ---------------------------------------------------------------------------
do $$
declare n int;
begin
  select count(*) into n from public.follows
  where (follower_id = '00000000-0000-0000-0000-0000000001a1' and following_id = '00000000-0000-0000-0000-0000000001a2')
     or (follower_id = '00000000-0000-0000-0000-0000000001a2' and following_id = '00000000-0000-0000-0000-0000000001a1');
  if n <> 0 then raise exception 'FAIL 4a: the block should remove follows both ways, % remain', n; end if;

  select count(*) into n from public.follows
  where follower_id = '00000000-0000-0000-0000-0000000001a3' and following_id = '00000000-0000-0000-0000-0000000001a1';
  if n <> 1 then raise exception 'FAIL 4b: an unrelated follow should survive, found %', n; end if;

  raise notice 'PASS 4: blocking removed both follows and kept the bystander''s';
end $$;

-- ---------------------------------------------------------------------------
-- Case 5: the blocked user (a2) - a1's trip and everything under it is gone,
-- they can't follow, comment, notify or share to a1.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000001a2","role":"authenticated"}';
do $$
declare n int; refused boolean;
begin
  select count(*) into n from public.trips where id = '00000000-0000-0000-0000-0000000001b1';
  if n <> 0 then raise exception 'FAIL 5a: a2 should not see a1''s trip, saw %', n; end if;
  select count(*) into n from public.days where id = '00000000-0000-0000-0000-0000000001c1';
  if n <> 0 then raise exception 'FAIL 5b: a2 should not see a1''s day, saw %', n; end if;
  select count(*) into n from public.stops where id = '00000000-0000-0000-0000-0000000001d1';
  if n <> 0 then raise exception 'FAIL 5c: a2 should not see a1''s stop, saw %', n; end if;
  select count(*) into n from public.stop_photos where id = '00000000-0000-0000-0000-0000000001e1';
  if n <> 0 then raise exception 'FAIL 5d: a2 should not see a1''s photo, saw %', n; end if;

  -- a2 still sees their own trip.
  select count(*) into n from public.trips where id = '00000000-0000-0000-0000-0000000001b2';
  if n <> 1 then raise exception 'FAIL 5e: a2 should still see their own trip, saw %', n; end if;

  refused := false;
  begin
    insert into public.follows (follower_id, following_id)
    values ('00000000-0000-0000-0000-0000000001a2', '00000000-0000-0000-0000-0000000001a1');
  exception when insufficient_privilege then refused := true;
  end;
  if not refused then raise exception 'FAIL 5f: a2 should not be able to follow a1'; end if;

  refused := false;
  begin
    insert into public.stop_comments (stop_id, trip_id, author_id, body)
    values ('00000000-0000-0000-0000-0000000001d1', '00000000-0000-0000-0000-0000000001b1',
            '00000000-0000-0000-0000-0000000001a2', 'Still here');
  exception when insufficient_privilege then refused := true;
  end;
  if not refused then raise exception 'FAIL 5g: a2 should not be able to comment on a1''s trip'; end if;

  refused := false;
  begin
    insert into public.notifications (recipient_id, actor_id, type)
    values ('00000000-0000-0000-0000-0000000001a1', '00000000-0000-0000-0000-0000000001a2', 'follow');
  exception when insufficient_privilege then refused := true;
  end;
  if not refused then raise exception 'FAIL 5h: a2 should not be able to notify a1'; end if;

  refused := false;
  begin
    insert into public.trip_shares (trip_id, sender_id, recipient_id)
    values ('00000000-0000-0000-0000-0000000001b2', '00000000-0000-0000-0000-0000000001a2', '00000000-0000-0000-0000-0000000001a1');
  exception when insufficient_privilege then refused := true;
  end;
  if not refused then raise exception 'FAIL 5i: a2 should not be able to share to a1'; end if;

  select count(*) into n from public.trip_shares where id = '00000000-0000-0000-0000-000000000192';
  if n <> 0 then raise exception 'FAIL 5j: a2 should no longer see the share they sent a1, saw %', n; end if;

  raise notice 'PASS 5: the blocked user sees none of a1''s trip and can''t follow, comment, notify or share';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 6: the blocker (a1) - the other direction. a2's trip, a2's comment on
-- a1's own trip, and a2's notification and share are hidden; a1 can't follow,
-- notify or share to a2. a1 still sees their own trip.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000001a1","role":"authenticated"}';
do $$
declare n int; refused boolean;
begin
  select count(*) into n from public.trips where id = '00000000-0000-0000-0000-0000000001b2';
  if n <> 0 then raise exception 'FAIL 6a: a1 should not see a2''s trip, saw %', n; end if;

  select count(*) into n from public.trips where id = '00000000-0000-0000-0000-0000000001b1';
  if n <> 1 then raise exception 'FAIL 6b: a1 should still see their own trip, saw %', n; end if;
  select count(*) into n from public.stops where id = '00000000-0000-0000-0000-0000000001d1';
  if n <> 1 then raise exception 'FAIL 6c: a1 should still see their own stop, saw %', n; end if;

  select count(*) into n from public.stop_comments where id = '00000000-0000-0000-0000-0000000001f1';
  if n <> 0 then raise exception 'FAIL 6d: a1 should not see a2''s comment on a1''s own trip, saw %', n; end if;

  select count(*) into n from public.notifications where id = '00000000-0000-0000-0000-000000000191';
  if n <> 0 then raise exception 'FAIL 6e: a1 should not see a2''s notification, saw %', n; end if;

  select count(*) into n from public.trip_shares where id = '00000000-0000-0000-0000-000000000192';
  if n <> 0 then raise exception 'FAIL 6f: a1 should not see a2''s share, saw %', n; end if;

  refused := false;
  begin
    insert into public.follows (follower_id, following_id)
    values ('00000000-0000-0000-0000-0000000001a1', '00000000-0000-0000-0000-0000000001a2');
  exception when insufficient_privilege then refused := true;
  end;
  if not refused then raise exception 'FAIL 6g: a1 should not be able to follow a2 while blocking them'; end if;

  refused := false;
  begin
    insert into public.notifications (recipient_id, actor_id, type)
    values ('00000000-0000-0000-0000-0000000001a2', '00000000-0000-0000-0000-0000000001a1', 'follow');
  exception when insufficient_privilege then refused := true;
  end;
  if not refused then raise exception 'FAIL 6h: a1 should not be able to notify a2'; end if;

  raise notice 'PASS 6: the blocker sees none of a2''s trip, comment, notification or share, and can''t follow or notify them';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 7: the bystander (a3) is unaffected - sees both trips and the comment,
-- and can still notify a1.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000001a3","role":"authenticated"}';
do $$
declare n int;
begin
  select count(*) into n from public.trips
  where id in ('00000000-0000-0000-0000-0000000001b1', '00000000-0000-0000-0000-0000000001b2');
  if n <> 2 then raise exception 'FAIL 7a: the bystander should see both trips, saw %', n; end if;

  select count(*) into n from public.stop_comments where id = '00000000-0000-0000-0000-0000000001f1';
  if n <> 1 then raise exception 'FAIL 7b: the bystander should see a2''s comment, saw %', n; end if;

  insert into public.notifications (recipient_id, actor_id, type)
  values ('00000000-0000-0000-0000-0000000001a1', '00000000-0000-0000-0000-0000000001a3', 'follow');

  raise notice 'PASS 7: a block between two people changes nothing for anyone else';
end $$;
reset role;

-- Signed out: a block between two users doesn't hide anything from anon.
set local role anon;
do $$
declare n int;
begin
  select count(*) into n from public.trips
  where id in ('00000000-0000-0000-0000-0000000001b1', '00000000-0000-0000-0000-0000000001b2');
  if n <> 2 then raise exception 'FAIL 7c: signed out, both public trips should be visible, saw %', n; end if;
  raise notice 'PASS 7c: signed out, both public trips are visible';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 8: unblocking restores visibility, but not the removed follows.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000001a1","role":"authenticated"}';
do $$
declare n int;
begin
  delete from public.blocks where blocked_id = '00000000-0000-0000-0000-0000000001a2';
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL 8a: a1 should be able to remove their block, removed %', n; end if;

  select count(*) into n from public.trips where id = '00000000-0000-0000-0000-0000000001b2';
  if n <> 1 then raise exception 'FAIL 8b: after unblocking, a1 should see a2''s trip again, saw %', n; end if;
  select count(*) into n from public.stop_comments where id = '00000000-0000-0000-0000-0000000001f1';
  if n <> 1 then raise exception 'FAIL 8c: after unblocking, a1 should see a2''s comment again, saw %', n; end if;
  select count(*) into n from public.notifications where id = '00000000-0000-0000-0000-000000000191';
  if n <> 1 then raise exception 'FAIL 8d: after unblocking, a1 should see a2''s notification again, saw %', n; end if;

  select count(*) into n from public.follows
  where follower_id = '00000000-0000-0000-0000-0000000001a1' and following_id = '00000000-0000-0000-0000-0000000001a2';
  if n <> 0 then raise exception 'FAIL 8e: unblocking should not bring back the follow, found %', n; end if;

  -- And following is allowed again.
  insert into public.follows (follower_id, following_id)
  values ('00000000-0000-0000-0000-0000000001a1', '00000000-0000-0000-0000-0000000001a2');

  raise notice 'PASS 8: unblocking restores what was hidden, leaves follows removed, and allows following again';
end $$;
reset role;

do $$ begin raise notice 'ALL REPORTS/BLOCKS RLS CASES PASSED'; end $$;

rollback;
