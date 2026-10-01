-- Assertions for 20260927b_trips_select_own_first.sql.
--
-- Run in the Supabase SQL editor after that migration. One transaction ending in
-- ROLLBACK, so it leaves nothing behind.

begin;

insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password,
  email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data
)
values
  ('00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-0000000000e1',
   'authenticated', 'authenticated', 'returning-author@rls-test.invalid', '', now(), now(), now(), '{}', '{}'),
  ('00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-0000000000e2',
   'authenticated', 'authenticated', 'returning-stranger@rls-test.invalid', '', now(), now(), now(), '{}', '{}');

insert into public.users (id, username, display_name) values
  ('00000000-0000-0000-0000-0000000000e1', 'rls_returning_author', 'Returning Author'),
  ('00000000-0000-0000-0000-0000000000e2', 'rls_returning_stranger', 'Returning Stranger');

-- ---------------------------------------------------------------------------
-- Case 1: insert ... returning works for the author - draft and live alike.
-- This is what failed before the migration.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000e1","role":"authenticated"}';

do $$
declare got uuid;
begin
  insert into public.trips (author_id, title, destination, start_date, end_date, status)
  values ('00000000-0000-0000-0000-0000000000e1', 'Draft', 'Porto, Portugal', current_date, current_date, 'draft')
  returning id into got;
  if got is null then raise exception 'FAIL 1a: draft insert returned no row'; end if;

  insert into public.trips (id, author_id, title, destination, start_date, end_date, status)
  values ('00000000-0000-0000-0000-0000000000e9', '00000000-0000-0000-0000-0000000000e1',
          'Live', 'Porto, Portugal', current_date, current_date, 'live')
  returning id into got;
  if got is null then raise exception 'FAIL 1b: live insert returned no row'; end if;

  raise notice 'PASS 1: an author can insert ... returning their own trip';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 2: nothing widened - a stranger still can't see that live trip, which
-- has posted no day yet.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000e2","role":"authenticated"}';

do $$
declare n int;
begin
  select count(*) into n from public.trips where id = '00000000-0000-0000-0000-0000000000e9';
  if n <> 0 then raise exception 'FAIL 2: stranger sees a live trip with nothing posted (% rows)', n; end if;
  raise notice 'PASS 2: the policy is no wider than before';
end $$;
reset role;

rollback;
