-- RLS checks for 20261009_likes_bookmarks_visibility.sql.
--
-- Run in the SQL editor after the migration. One transaction ending in
-- ROLLBACK: it leaves nothing behind. Every action is done as the user (role
-- authenticated or anon + a JWT claim); fixtures are inserted directly.
--
-- A failure raises an exception, which aborts the script. Silence past the last
-- NOTICE means a case failed - read the error, not the absence of output.

begin;

-- ---------------------------------------------------------------------------
-- Fixtures
--   ...5a1 author  - trips 5b1 public, 5b2 private, 5b3 draft, 5b4 unlisted
--   ...5a2 viewer  - an ordinary signed-in user
--   ...5a3 blocked - blocked by the author
-- 5a1 likes their own private trip (so there's a like the viewer mustn't see).
-- ---------------------------------------------------------------------------
insert into auth.users (instance_id, id, aud, role, email, encrypted_password,
                        email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data)
select '00000000-0000-0000-0000-000000000000', u::uuid, 'authenticated', 'authenticated',
       'lb' || right(u, 3) || '@likes-test.invalid', '', now(), now(), now(), '{}', '{}'
  from unnest(array['00000000-0000-0000-0000-0000000005a1', '00000000-0000-0000-0000-0000000005a2',
                    '00000000-0000-0000-0000-0000000005a3']) u;

insert into public.users (id, username, display_name) values
  ('00000000-0000-0000-0000-0000000005a1', 'lb_author',  'LB Author'),
  ('00000000-0000-0000-0000-0000000005a2', 'lb_viewer',  'LB Viewer'),
  ('00000000-0000-0000-0000-0000000005a3', 'lb_blocked', 'LB Blocked');

insert into public.trips (id, author_id, title, destination, start_date, end_date, status, visibility, completed_at) values
  ('00000000-0000-0000-0000-0000000005b1', '00000000-0000-0000-0000-0000000005a1', 'LB Public',   'Porto', current_date - 9, current_date - 7, 'completed', 'public',   now()),
  ('00000000-0000-0000-0000-0000000005b2', '00000000-0000-0000-0000-0000000005a1', 'LB Private',  'Porto', current_date - 9, current_date - 7, 'completed', 'private',  now()),
  ('00000000-0000-0000-0000-0000000005b3', '00000000-0000-0000-0000-0000000005a1', 'LB Draft',    'Porto', current_date - 9, current_date - 7, 'draft',     'public',   null),
  ('00000000-0000-0000-0000-0000000005b4', '00000000-0000-0000-0000-0000000005a1', 'LB Unlisted', 'Porto', current_date - 9, current_date - 7, 'completed', 'unlisted', now());

insert into public.likes (user_id, trip_id) values
  ('00000000-0000-0000-0000-0000000005a1', '00000000-0000-0000-0000-0000000005b2'),
  ('00000000-0000-0000-0000-0000000005a1', '00000000-0000-0000-0000-0000000005b1');

insert into public.blocks (blocker_id, blocked_id) values
  ('00000000-0000-0000-0000-0000000005a1', '00000000-0000-0000-0000-0000000005a3');

-- True if the insert as the current role was refused by RLS.
create function pg_temp.refused(sql text) returns boolean
language plpgsql as $$
begin
  execute sql;
  return false;
exception when insufficient_privilege then
  return true;
end $$;

-- ---------------------------------------------------------------------------
-- Case 1: the viewer can like and bookmark public and unlisted trips
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000005a2","role":"authenticated"}', true);
do $$
begin
  insert into public.likes (user_id, trip_id) values ('00000000-0000-0000-0000-0000000005a2', '00000000-0000-0000-0000-0000000005b1');
  insert into public.likes (user_id, trip_id) values ('00000000-0000-0000-0000-0000000005a2', '00000000-0000-0000-0000-0000000005b4');
  insert into public.bookmarks (user_id, trip_id) values ('00000000-0000-0000-0000-0000000005a2', '00000000-0000-0000-0000-0000000005b1');
  insert into public.bookmarks (user_id, trip_id) values ('00000000-0000-0000-0000-0000000005a2', '00000000-0000-0000-0000-0000000005b4');
  raise notice 'PASS 1: likes and bookmarks on public and unlisted trips are accepted';
end $$;

-- ---------------------------------------------------------------------------
-- Case 2: ...but not on a private trip or a draft
-- ---------------------------------------------------------------------------
do $$
begin
  if not pg_temp.refused($q$insert into public.likes (user_id, trip_id) values ('00000000-0000-0000-0000-0000000005a2', '00000000-0000-0000-0000-0000000005b2')$q$) then
    raise exception 'FAIL 2a: liked a private trip';
  end if;
  if not pg_temp.refused($q$insert into public.likes (user_id, trip_id) values ('00000000-0000-0000-0000-0000000005a2', '00000000-0000-0000-0000-0000000005b3')$q$) then
    raise exception 'FAIL 2b: liked a draft';
  end if;
  if not pg_temp.refused($q$insert into public.bookmarks (user_id, trip_id) values ('00000000-0000-0000-0000-0000000005a2', '00000000-0000-0000-0000-0000000005b2')$q$) then
    raise exception 'FAIL 2c: bookmarked a private trip';
  end if;
  if not pg_temp.refused($q$insert into public.bookmarks (user_id, trip_id) values ('00000000-0000-0000-0000-0000000005a2', '00000000-0000-0000-0000-0000000005b3')$q$) then
    raise exception 'FAIL 2d: bookmarked a draft';
  end if;
  raise notice 'PASS 2: likes and bookmarks on private trips and drafts are refused';
end $$;

-- ---------------------------------------------------------------------------
-- Case 3: nobody likes or bookmarks as someone else
-- ---------------------------------------------------------------------------
do $$
begin
  if not pg_temp.refused($q$insert into public.likes (user_id, trip_id) values ('00000000-0000-0000-0000-0000000005a3', '00000000-0000-0000-0000-0000000005b4')$q$) then
    raise exception 'FAIL 3a: liked as someone else';
  end if;
  if not pg_temp.refused($q$insert into public.bookmarks (user_id, trip_id) values ('00000000-0000-0000-0000-0000000005a3', '00000000-0000-0000-0000-0000000005b4')$q$) then
    raise exception 'FAIL 3b: bookmarked as someone else';
  end if;
  raise notice 'PASS 3: likes and bookmarks can''t be made for someone else';
end $$;

-- ---------------------------------------------------------------------------
-- Case 4: the viewer sees likes on visible trips only
-- ---------------------------------------------------------------------------
do $$
declare n int;
begin
  select count(*) into n from public.likes where trip_id = '00000000-0000-0000-0000-0000000005b2';
  if n <> 0 then raise exception 'FAIL 4a: viewer sees % like(s) on a private trip', n; end if;
  select count(*) into n from public.likes where trip_id = '00000000-0000-0000-0000-0000000005b1';
  if n <> 2 then raise exception 'FAIL 4b: viewer should see both likes on the public trip, saw %', n; end if;
  raise notice 'PASS 4: likes on a private trip are hidden; likes on a public trip are visible';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 5: blocks - the blocked user can't like or bookmark the blocker's trip,
-- and doesn't see its likes
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000005a3","role":"authenticated"}', true);
do $$
declare n int;
begin
  if not pg_temp.refused($q$insert into public.likes (user_id, trip_id) values ('00000000-0000-0000-0000-0000000005a3', '00000000-0000-0000-0000-0000000005b1')$q$) then
    raise exception 'FAIL 5a: blocked user liked the blocker''s trip';
  end if;
  if not pg_temp.refused($q$insert into public.bookmarks (user_id, trip_id) values ('00000000-0000-0000-0000-0000000005a3', '00000000-0000-0000-0000-0000000005b1')$q$) then
    raise exception 'FAIL 5b: blocked user bookmarked the blocker''s trip';
  end if;
  select count(*) into n from public.likes where trip_id = '00000000-0000-0000-0000-0000000005b1';
  if n <> 0 then raise exception 'FAIL 5c: blocked user sees % like(s) on the blocker''s trip', n; end if;
  raise notice 'PASS 5: a blocked user can''t like, bookmark or see likes on the blocker''s trips';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 6: the author sees every like on their own trips, private included;
-- signed out sees likes on public trips only
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000005a1","role":"authenticated"}', true);
do $$
declare n int;
begin
  select count(*) into n from public.likes where trip_id = '00000000-0000-0000-0000-0000000005b2';
  if n <> 1 then raise exception 'FAIL 6a: the author should see the like on their private trip, saw %', n; end if;
end $$;
reset role;
set local role anon;
select set_config('request.jwt.claims', '{"role":"anon"}', true);
do $$
declare n int;
begin
  select count(*) into n from public.likes where trip_id = '00000000-0000-0000-0000-0000000005b2';
  if n <> 0 then raise exception 'FAIL 6b: signed out sees % like(s) on a private trip', n; end if;
  select count(*) into n from public.likes where trip_id = '00000000-0000-0000-0000-0000000005b1';
  if n <> 2 then raise exception 'FAIL 6c: signed out should see 2 likes on the public trip, saw %', n; end if;
  raise notice 'PASS 6: authors see all likes on their trips; signed out sees likes on public trips only';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 7: you can still see and remove your own like after the trip is hidden
-- ---------------------------------------------------------------------------
update public.trips set visibility = 'private' where id = '00000000-0000-0000-0000-0000000005b4';
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000005a2","role":"authenticated"}', true);
do $$
declare n int;
begin
  select count(*) into n from public.likes
   where trip_id = '00000000-0000-0000-0000-0000000005b4' and user_id = '00000000-0000-0000-0000-0000000005a2';
  if n <> 1 then raise exception 'FAIL 7a: your own like on a now-private trip should stay visible to you, saw %', n; end if;
  delete from public.likes where trip_id = '00000000-0000-0000-0000-0000000005b4' and user_id = '00000000-0000-0000-0000-0000000005a2';
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL 7b: couldn''t remove your own like on a now-private trip'; end if;
  delete from public.bookmarks where trip_id = '00000000-0000-0000-0000-0000000005b4' and user_id = '00000000-0000-0000-0000-0000000005a2';
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL 7c: couldn''t remove your own bookmark on a now-private trip'; end if;
  raise notice 'PASS 7: your own like and bookmark stay removable after the trip is hidden from you';
end $$;
reset role;

rollback;
