-- Checks for 20261008_notifications_from_triggers.sql.
--
-- Run in the SQL editor after the migration. One transaction ending in
-- ROLLBACK: it leaves nothing behind. Every action is done AS the user (role
-- authenticated + a JWT claim), so the table policies apply exactly as they do
-- for the app; only the fixtures are inserted directly.
--
-- A failure raises an exception, which aborts the script. Silence past the last
-- NOTICE means a case failed - read the error, not the absence of output.

begin;

-- ---------------------------------------------------------------------------
-- Fixtures
--   ...4a1 author   - owns trips 4b1 (draft), 4b2 (live, public), 4b3 (live, unlisted)
--   ...4a2 fan      - follows nobody yet; does the following, liking, commenting
--   ...4a3 follower - already follows author
--   ...4a4 blocked  - follows author before the block (removed by it)
-- ---------------------------------------------------------------------------
insert into auth.users (instance_id, id, aud, role, email, encrypted_password,
                        email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data)
select '00000000-0000-0000-0000-000000000000', u::uuid, 'authenticated', 'authenticated',
       'n' || right(u, 3) || '@notify-test.invalid', '', now(), now(), now(), '{}', '{}'
  from unnest(array['00000000-0000-0000-0000-0000000004a1', '00000000-0000-0000-0000-0000000004a2',
                    '00000000-0000-0000-0000-0000000004a3', '00000000-0000-0000-0000-0000000004a4']) u;

insert into public.users (id, username, display_name) values
  ('00000000-0000-0000-0000-0000000004a1', 'nt_author',   'NT Author'),
  ('00000000-0000-0000-0000-0000000004a2', 'nt_fan',      'NT Fan'),
  ('00000000-0000-0000-0000-0000000004a3', 'nt_follower', 'NT Follower'),
  ('00000000-0000-0000-0000-0000000004a4', 'nt_blocked',  'NT Blocked');

insert into public.follows (follower_id, following_id) values
  ('00000000-0000-0000-0000-0000000004a3', '00000000-0000-0000-0000-0000000004a1'),
  ('00000000-0000-0000-0000-0000000004a4', '00000000-0000-0000-0000-0000000004a1');

insert into public.trips (id, author_id, title, destination, start_date, end_date, status, visibility) values
  ('00000000-0000-0000-0000-0000000004b1', '00000000-0000-0000-0000-0000000004a1', 'NT Draft',    'Porto',  current_date - 9, current_date - 7, 'draft', 'public'),
  ('00000000-0000-0000-0000-0000000004b2', '00000000-0000-0000-0000-0000000004a1', 'NT Live',     'Seville', current_date - 1, current_date + 3, 'live', 'public'),
  ('00000000-0000-0000-0000-0000000004b3', '00000000-0000-0000-0000-0000000004a1', 'NT Unlisted', 'Bilbao', current_date - 1, current_date + 3, 'live', 'unlisted');

insert into public.days (id, trip_id, day_index, date) values
  ('00000000-0000-0000-0000-0000000004c1', '00000000-0000-0000-0000-0000000004b2', 1, current_date - 1),
  ('00000000-0000-0000-0000-0000000004c2', '00000000-0000-0000-0000-0000000004b3', 1, current_date - 1),
  ('00000000-0000-0000-0000-0000000004c3', '00000000-0000-0000-0000-0000000004b1', 1, current_date - 9);
-- The draft's day is published as importing does, so the stop is visible once
-- the trip is ('import' days don't notify; 'new_trip' covers them).
update public.days set published_at = now(), published_via = 'import' where id = '00000000-0000-0000-0000-0000000004c3';

insert into public.stops (id, day_id, trip_id, name, category, location, order_in_day) values
  ('00000000-0000-0000-0000-0000000004d1', '00000000-0000-0000-0000-0000000004c3',
   '00000000-0000-0000-0000-0000000004b1', 'NT Stop', 'sight', 'SRID=4326;POINT(-8.61 41.15)', 0);

insert into public.plans (id, user_id, title) values
  ('00000000-0000-0000-0000-0000000004e1', '00000000-0000-0000-0000-0000000004a1', 'NT Plan');

-- Count of notifications of one type to one recipient, seen with RLS off.
create function pg_temp.n(p_recipient text, p_type text) returns int
language sql as $$
  select count(*)::int from public.notifications
   where recipient_id = p_recipient::uuid and type = p_type
$$;

create function pg_temp.expect(label text, got int, want int) returns void
language plpgsql as $$
begin
  if got <> want then raise exception 'FAIL %: expected %, got %', label, want, got; end if;
end $$;

-- ---------------------------------------------------------------------------
-- Case 1: follow fires once; unfollow + refollow doesn't repeat it
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000004a2","role":"authenticated"}', true);
insert into public.follows (follower_id, following_id) values
  ('00000000-0000-0000-0000-0000000004a2', '00000000-0000-0000-0000-0000000004a1');
delete from public.follows where follower_id = '00000000-0000-0000-0000-0000000004a2';
insert into public.follows (follower_id, following_id) values
  ('00000000-0000-0000-0000-0000000004a2', '00000000-0000-0000-0000-0000000004a1');
reset role;
select pg_temp.expect('case 1 follow',
  (select count(*)::int from public.notifications
    where recipient_id = '00000000-0000-0000-0000-0000000004a1' and actor_id = '00000000-0000-0000-0000-0000000004a2' and type = 'follow'), 1);
do $$ begin raise notice 'PASS 1: a follow notifies once; unfollow and refollow add nothing'; end $$;

-- ---------------------------------------------------------------------------
-- Case 2: new_trip - draft published as public notifies each follower once,
-- and making it private then public again doesn't repeat
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000004a1","role":"authenticated"}', true);
update public.trips set status = 'completed', completed_at = now() where id = '00000000-0000-0000-0000-0000000004b1';
update public.trips set visibility = 'private' where id = '00000000-0000-0000-0000-0000000004b1';
update public.trips set visibility = 'public'  where id = '00000000-0000-0000-0000-0000000004b1';
reset role;
select pg_temp.expect('case 2 new_trip fan',      pg_temp.n('00000000-0000-0000-0000-0000000004a2', 'new_trip'), 1);
select pg_temp.expect('case 2 new_trip follower', pg_temp.n('00000000-0000-0000-0000-0000000004a3', 'new_trip'), 1);
select pg_temp.expect('case 2 new_trip author',   pg_temp.n('00000000-0000-0000-0000-0000000004a1', 'new_trip'), 0);
do $$ begin raise notice 'PASS 2: publishing notifies each follower once; toggling visibility doesn''t repeat it'; end $$;

-- ---------------------------------------------------------------------------
-- Case 3: like fires once to the author; unlike + like doesn't repeat;
-- liking your own trip notifies nobody
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000004a2","role":"authenticated"}', true);
insert into public.likes (user_id, trip_id) values ('00000000-0000-0000-0000-0000000004a2', '00000000-0000-0000-0000-0000000004b1');
delete from public.likes where user_id = '00000000-0000-0000-0000-0000000004a2';
insert into public.likes (user_id, trip_id) values ('00000000-0000-0000-0000-0000000004a2', '00000000-0000-0000-0000-0000000004b1');
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000004a1","role":"authenticated"}', true);
insert into public.likes (user_id, trip_id) values ('00000000-0000-0000-0000-0000000004a1', '00000000-0000-0000-0000-0000000004b1');
reset role;
select pg_temp.expect('case 3 like', pg_temp.n('00000000-0000-0000-0000-0000000004a1', 'like'), 1);
do $$ begin raise notice 'PASS 3: a like notifies the author once; unlike/like adds nothing; own like notifies nobody'; end $$;

-- ---------------------------------------------------------------------------
-- Case 4: each comment notifies the author; the author's own comment doesn't
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000004a2","role":"authenticated"}', true);
insert into public.stop_comments (stop_id, trip_id, author_id, body) values
  ('00000000-0000-0000-0000-0000000004d1', '00000000-0000-0000-0000-0000000004b1', '00000000-0000-0000-0000-0000000004a2', 'one'),
  ('00000000-0000-0000-0000-0000000004d1', '00000000-0000-0000-0000-0000000004b1', '00000000-0000-0000-0000-0000000004a2', 'two');
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000004a1","role":"authenticated"}', true);
insert into public.stop_comments (stop_id, trip_id, author_id, body) values
  ('00000000-0000-0000-0000-0000000004d1', '00000000-0000-0000-0000-0000000004b1', '00000000-0000-0000-0000-0000000004a1', 'reply');
reset role;
select pg_temp.expect('case 4 comments', pg_temp.n('00000000-0000-0000-0000-0000000004a1', 'stop_comment'), 2);
do $$ begin raise notice 'PASS 4: each comment notifies the trip author once; the author''s own comment notifies nobody'; end $$;

-- ---------------------------------------------------------------------------
-- Case 5: a share notifies once; sending the same trip again is refused by the
-- new unique constraint and adds nothing
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000004a2","role":"authenticated"}', true);
insert into public.trip_shares (trip_id, sender_id, recipient_id) values
  ('00000000-0000-0000-0000-0000000004b1', '00000000-0000-0000-0000-0000000004a2', '00000000-0000-0000-0000-0000000004a3');
do $$
begin
  insert into public.trip_shares (trip_id, sender_id, recipient_id) values
    ('00000000-0000-0000-0000-0000000004b1', '00000000-0000-0000-0000-0000000004a2', '00000000-0000-0000-0000-0000000004a3');
  raise exception 'FAIL case 5: a repeat share was accepted';
exception when unique_violation then null;
end $$;
reset role;
select pg_temp.expect('case 5 share', pg_temp.n('00000000-0000-0000-0000-0000000004a3', 'trip_share'), 1);
select pg_temp.expect('case 5 share rows',
  (select count(*)::int from public.trip_shares where sender_id = '00000000-0000-0000-0000-0000000004a2'), 1);
do $$ begin raise notice 'PASS 5: a share notifies once; the same share again is refused (unique_violation) and adds nothing'; end $$;

-- ---------------------------------------------------------------------------
-- Case 6: a plan invite notifies the invitee once
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000004a1","role":"authenticated"}', true);
insert into public.plan_members (plan_id, user_id, role, status, invited_by) values
  ('00000000-0000-0000-0000-0000000004e1', '00000000-0000-0000-0000-0000000004a2', 'viewer', 'pending', '00000000-0000-0000-0000-0000000004a1');
reset role;
select pg_temp.expect('case 6 plan invite', pg_temp.n('00000000-0000-0000-0000-0000000004a2', 'plan_invite'), 1);
do $$ begin raise notice 'PASS 6: a plan invite notifies the invitee once'; end $$;

-- ---------------------------------------------------------------------------
-- Case 7: blocks. The author blocks 4a4 (removing 4a4's follow). Then:
--   - 4a4 likes the author's trip: no notification (either direction)
--   - a follow inserted bypassing policy (as if one existed) still notifies nobody
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000004a1","role":"authenticated"}', true);
insert into public.blocks (blocker_id, blocked_id) values ('00000000-0000-0000-0000-0000000004a1', '00000000-0000-0000-0000-0000000004a4');
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000004a4","role":"authenticated"}', true);
-- Since 20261009_likes_bookmarks_visibility the like itself is refused; before
-- it, the like went in and only the trigger stopped the notification. Either
-- way, nothing may reach the author.
do $$
begin
  insert into public.likes (user_id, trip_id) values ('00000000-0000-0000-0000-0000000004a4', '00000000-0000-0000-0000-0000000004b1');
exception when insufficient_privilege then null;
end $$;
reset role;
-- Direct inserts, as the trigger's only line of defence: blocked -> author,
-- and author -> blocked.
delete from public.notifications where actor_id = '00000000-0000-0000-0000-0000000004a4' or recipient_id = '00000000-0000-0000-0000-0000000004a4';
insert into public.follows (follower_id, following_id) values
  ('00000000-0000-0000-0000-0000000004a4', '00000000-0000-0000-0000-0000000004a1'),
  ('00000000-0000-0000-0000-0000000004a1', '00000000-0000-0000-0000-0000000004a4');
select pg_temp.expect('case 7 blocked',
  (select count(*)::int from public.notifications
    where actor_id = '00000000-0000-0000-0000-0000000004a4' or recipient_id = '00000000-0000-0000-0000-0000000004a4'), 0);
-- Undo the bypass so the follow-on cases see the real graph.
delete from public.follows where '00000000-0000-0000-0000-0000000004a4' in (follower_id, following_id);
do $$ begin raise notice 'PASS 7: nothing is created between a blocked pair, in either direction'; end $$;

-- ---------------------------------------------------------------------------
-- Case 8: new_day - a day posted live on a public trip notifies each follower
-- once (re-saving it doesn't repeat); on an unlisted trip, nobody
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000004a1","role":"authenticated"}', true);
update public.days set published_at = now(), published_via = 'post_day' where id = '00000000-0000-0000-0000-0000000004c1';
update public.days set published_at = now() + interval '1 minute' where id = '00000000-0000-0000-0000-0000000004c1';
update public.days set published_at = now(), published_via = 'post_day' where id = '00000000-0000-0000-0000-0000000004c2';
reset role;
select pg_temp.expect('case 8 new_day fan',      pg_temp.n('00000000-0000-0000-0000-0000000004a2', 'new_day'), 1);
select pg_temp.expect('case 8 new_day follower', pg_temp.n('00000000-0000-0000-0000-0000000004a3', 'new_day'), 1);
select pg_temp.expect('case 8 day_id set',
  (select count(*)::int from public.notifications where type = 'new_day' and day_id = '00000000-0000-0000-0000-0000000004c1'), 2);
do $$ begin raise notice 'PASS 8: a day posted on a public live trip notifies each follower once; unlisted trips notify nobody'; end $$;

-- ---------------------------------------------------------------------------
-- Case 9: clients can't create notifications - not for someone else, not even
-- as themselves - and can still read their own
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000004a2","role":"authenticated"}', true);
do $$
begin
  insert into public.notifications (recipient_id, actor_id, type)
  values ('00000000-0000-0000-0000-0000000004a3', '00000000-0000-0000-0000-0000000004a2', 'follow');
  raise exception 'FAIL case 9: a client inserted a notification for someone else';
exception when insufficient_privilege then null;
end $$;
do $$
begin
  insert into public.notifications (recipient_id, actor_id, type)
  values ('00000000-0000-0000-0000-0000000004a3', '00000000-0000-0000-0000-0000000004a1', 'like');
  raise exception 'FAIL case 9: a client forged a notification from another actor';
exception when insufficient_privilege then null;
end $$;
do $$
begin
  if (select count(*) from public.notifications where recipient_id = '00000000-0000-0000-0000-0000000004a2') = 0 then
    raise exception 'FAIL case 9: the recipient can no longer read their own notifications';
  end if;
end $$;
reset role;
do $$ begin raise notice 'PASS 9: clients are refused (42501) creating notifications; recipients still read theirs'; end $$;

rollback;
