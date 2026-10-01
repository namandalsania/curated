-- Assertions for 20260927_harden_plan_helpers.sql.
--
-- Run in the Supabase SQL editor after that migration. One transaction ending
-- in ROLLBACK, so it leaves nothing behind - including its fixture auth users.
--
-- Two halves:
--   - Cases 1-3: the helpers are gone from `public` (so there is no PostgREST
--     RPC route to them), live in `private` with an empty search_path, and are
--     executable by exactly the roles the policies need.
--   - Cases 4-9: every rule the plan policies enforced before the move still
--     holds, exercised through the policies (set role + JWT claim) the way the
--     app hits them - not by calling the helpers directly.
--
-- A failure raises and aborts the script. Silence past the last NOTICE means a
-- case failed - read the error, not the absence of output.

begin;

-- ---------------------------------------------------------------------------
-- Case 1: no plan_role / plan_invited left in public.
--
-- PostgREST serves RPCs only from its exposed schemas (public here). With no
-- function of that name in public, POST /rest/v1/rpc/plan_role has nothing to
-- call and answers 404 - that's the probe this migration closes.
-- ---------------------------------------------------------------------------
do $$
begin
  if to_regprocedure('public.plan_role(uuid)') is not null then
    raise exception 'FAIL 1a: public.plan_role(uuid) still exists and is RPC-callable';
  end if;
  if to_regprocedure('public.plan_invited(uuid)') is not null then
    raise exception 'FAIL 1b: public.plan_invited(uuid) still exists and is RPC-callable';
  end if;
  if exists (
    select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where p.proname in ('plan_role', 'plan_invited') and n.nspname <> 'private'
  ) then
    raise exception 'FAIL 1c: a plan_role/plan_invited exists outside private';
  end if;

  raise notice 'PASS 1: plan_role and plan_invited exist only in private - no RPC route';
end $$;

-- ---------------------------------------------------------------------------
-- Case 2: the private versions are SECURITY DEFINER with search_path = ''.
-- ---------------------------------------------------------------------------
do $$
declare r record;
begin
  for r in
    select p.proname, p.prosecdef, p.proconfig
    from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'private' and p.proname in ('plan_role', 'plan_invited')
  loop
    if not r.prosecdef then
      raise exception 'FAIL 2a: private.% is not security definer', r.proname;
    end if;
    -- An empty search_path is stored as search_path="" .
    if r.proconfig is null or not ('search_path=""' = any (r.proconfig)) then
      raise exception 'FAIL 2b: private.% search_path is %, expected empty', r.proname, r.proconfig;
    end if;
  end loop;

  if (select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
      where n.nspname = 'private' and p.proname in ('plan_role', 'plan_invited')) <> 2 then
    raise exception 'FAIL 2c: expected both helpers in private';
  end if;

  -- move_plan_items stays in public (the app calls it) but must be pinned too.
  if not exists (
    select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'move_plan_items'
      and 'search_path=""' = any (p.proconfig)
  ) then
    raise exception 'FAIL 2d: public.move_plan_items is missing or not pinned to an empty search_path';
  end if;

  raise notice 'PASS 2: helpers are security definer with an empty search_path, move_plan_items too';
end $$;

-- ---------------------------------------------------------------------------
-- Case 3: execute rights are exactly what the policies need.
-- ---------------------------------------------------------------------------
do $$
begin
  -- PUBLIC (grantee oid 0) must hold nothing on the helpers.
  if exists (
    select 1
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    cross join lateral aclexplode(p.proacl) a
    where n.nspname = 'private' and p.proname in ('plan_role', 'plan_invited')
      and a.grantee = 0
  ) then
    raise exception 'FAIL 3a: PUBLIC still holds execute on a private plan helper';
  end if;

  -- Policies run as the querying role, so both API roles need execute.
  if not has_function_privilege('authenticated', 'private.plan_role(uuid)', 'execute')
     or not has_function_privilege('anon', 'private.plan_role(uuid)', 'execute')
     or not has_function_privilege('authenticated', 'private.plan_invited(uuid)', 'execute')
     or not has_function_privilege('anon', 'private.plan_invited(uuid)', 'execute') then
    raise exception 'FAIL 3b: anon/authenticated are missing execute on a helper their policies call';
  end if;

  if has_schema_privilege('anon', 'private', 'create')
     or has_schema_privilege('authenticated', 'private', 'create') then
    raise exception 'FAIL 3c: an API role can create objects in private';
  end if;

  if has_function_privilege('anon', 'public.move_plan_items(uuid, jsonb)', 'execute') then
    raise exception 'FAIL 3d: anon can execute move_plan_items';
  end if;

  raise notice 'PASS 3: PUBLIC holds nothing, API roles hold only execute, anon cannot move items';
end $$;

-- ---------------------------------------------------------------------------
-- Fixtures
--
-- ...f1 owner     - owns plan p1
-- ...f2 editor    - accepted editor on p1
-- ...f3 viewer    - accepted viewer on p1
-- ...f4 invitee   - pending editor invite on p1
-- ...f5 stranger  - nothing to do with p1
-- ---------------------------------------------------------------------------
insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password,
  email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data
)
select '00000000-0000-0000-0000-000000000000', id::uuid, 'authenticated', 'authenticated',
       email, '', now(), now(), now(), '{}', '{}'
from (values
  ('00000000-0000-0000-0000-0000000000f1', 'plan-owner@rls-test.invalid'),
  ('00000000-0000-0000-0000-0000000000f2', 'plan-editor@rls-test.invalid'),
  ('00000000-0000-0000-0000-0000000000f3', 'plan-viewer@rls-test.invalid'),
  ('00000000-0000-0000-0000-0000000000f4', 'plan-invitee@rls-test.invalid'),
  ('00000000-0000-0000-0000-0000000000f5', 'plan-stranger@rls-test.invalid')
) as u(id, email);

insert into public.users (id, username, display_name) values
  ('00000000-0000-0000-0000-0000000000f1', 'rls_plan_owner',    'Plan Owner'),
  ('00000000-0000-0000-0000-0000000000f2', 'rls_plan_editor',   'Plan Editor'),
  ('00000000-0000-0000-0000-0000000000f3', 'rls_plan_viewer',   'Plan Viewer'),
  ('00000000-0000-0000-0000-0000000000f4', 'rls_plan_invitee',  'Plan Invitee'),
  ('00000000-0000-0000-0000-0000000000f5', 'rls_plan_stranger', 'Plan Stranger');

insert into public.plans (id, user_id, title, day_count) values
  ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-0000000000f1', 'Lisbon weekend', 2);

insert into public.plan_members (plan_id, user_id, role, status, invited_by) values
  ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-0000000000f2', 'editor', 'accepted', '00000000-0000-0000-0000-0000000000f1'),
  ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-0000000000f3', 'viewer', 'accepted', '00000000-0000-0000-0000-0000000000f1'),
  ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-0000000000f4', 'editor', 'pending',  '00000000-0000-0000-0000-0000000000f1');

insert into public.plan_items (id, plan_id, day_number, position, name, category, added_by) values
  ('00000000-0000-0000-0000-00000000b001', '00000000-0000-0000-0000-00000000a001', 1, 0,
   'Time Out Market', 'food', '00000000-0000-0000-0000-0000000000f1');

-- ---------------------------------------------------------------------------
-- Case 4: the owner and accepted members see the plan and its items.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000f1","role":"authenticated"}';
do $$
declare n int;
begin
  select count(*) into n from public.plans where id = '00000000-0000-0000-0000-00000000a001';
  if n <> 1 then raise exception 'FAIL 4a: owner should see their plan, saw % row(s)', n; end if;
  select count(*) into n from public.plan_members where plan_id = '00000000-0000-0000-0000-00000000a001';
  if n <> 3 then raise exception 'FAIL 4b: owner should see all 3 member rows, saw %', n; end if;
end $$;
reset role;

set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000f3","role":"authenticated"}';
do $$
declare n int;
begin
  select count(*) into n from public.plan_items where plan_id = '00000000-0000-0000-0000-00000000a001';
  if n <> 1 then raise exception 'FAIL 4c: viewer should see the plan''s items, saw % row(s)', n; end if;
  raise notice 'PASS 4: owner and accepted members see the plan, its items and its members';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 5: a pending invitee sees the plan's name, not its items.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000f4","role":"authenticated"}';
do $$
declare n int;
begin
  select count(*) into n from public.plans where id = '00000000-0000-0000-0000-00000000a001';
  if n <> 1 then raise exception 'FAIL 5a: invitee should see the plan they were invited to, saw %', n; end if;
  select count(*) into n from public.plan_items where plan_id = '00000000-0000-0000-0000-00000000a001';
  if n <> 0 then raise exception 'FAIL 5b: a pending invitee should NOT see items yet, saw %', n; end if;
  raise notice 'PASS 5: pending invite shows the plan, not its contents';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 6: a stranger, and a signed-out reader, see nothing - and the anon read
-- returns zero rows rather than a permission error from the helper.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000f5","role":"authenticated"}';
do $$
declare n int;
begin
  select count(*) into n from public.plans where id = '00000000-0000-0000-0000-00000000a001';
  if n <> 0 then raise exception 'FAIL 6a: stranger should NOT see the plan, saw %', n; end if;
  select count(*) into n from public.plan_items where plan_id = '00000000-0000-0000-0000-00000000a001';
  if n <> 0 then raise exception 'FAIL 6b: stranger should NOT see its items, saw %', n; end if;
  select count(*) into n from public.plan_members where plan_id = '00000000-0000-0000-0000-00000000a001';
  if n <> 0 then raise exception 'FAIL 6c: stranger should NOT see its members, saw %', n; end if;
end $$;
reset role;

set local role anon;
set local request.jwt.claims to '';
do $$
declare n int;
begin
  select count(*) into n from public.plans;
  if n <> 0 then raise exception 'FAIL 6d: anon should see no plans, saw %', n; end if;
  select count(*) into n from public.plan_items;
  if n <> 0 then raise exception 'FAIL 6e: anon should see no plan items, saw %', n; end if;
  raise notice 'PASS 6: strangers and signed-out readers see nothing, without errors';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 7: editors write items; viewers cannot.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000f2","role":"authenticated"}';
do $$
begin
  insert into public.plan_items (plan_id, day_number, position, name, category, added_by)
  values ('00000000-0000-0000-0000-00000000a001', 1, 1, 'Pastéis de Belém', 'food',
          '00000000-0000-0000-0000-0000000000f2');
  raise notice 'PASS 7a: an editor can add an item';
end $$;
reset role;

set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000f3","role":"authenticated"}';
do $$
declare ok boolean; n int;
begin
  begin
    insert into public.plan_items (plan_id, day_number, position, name, category, added_by)
    values ('00000000-0000-0000-0000-00000000a001', 1, 2, 'Sneaky add', 'other',
            '00000000-0000-0000-0000-0000000000f3');
    ok := false;
  exception when insufficient_privilege then ok := true;
  end;
  if not ok then raise exception 'FAIL 7b: a viewer was able to add an item'; end if;

  update public.plan_items set position = 9 where id = '00000000-0000-0000-0000-00000000b001';
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL 7c: a viewer updated an item (% rows)', n; end if;

  raise notice 'PASS 7b: a viewer cannot add or change items';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 8: move_plan_items works for an editor and refuses a viewer.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000f2","role":"authenticated"}';
do $$
declare d int;
begin
  perform public.move_plan_items(
    '00000000-0000-0000-0000-00000000a001',
    '[{"id":"00000000-0000-0000-0000-00000000b001","day_number":2,"position":0}]'::jsonb
  );
  select day_number into d from public.plan_items where id = '00000000-0000-0000-0000-00000000b001';
  if d <> 2 then raise exception 'FAIL 8a: editor move did not apply, day_number is %', d; end if;
  raise notice 'PASS 8a: an editor can move items through the RPC';
end $$;
reset role;

set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000f3","role":"authenticated"}';
do $$
declare ok boolean;
begin
  begin
    perform public.move_plan_items(
      '00000000-0000-0000-0000-00000000a001',
      '[{"id":"00000000-0000-0000-0000-00000000b001","day_number":1,"position":0}]'::jsonb
    );
    ok := false;
  exception when insufficient_privilege then ok := true;
  end;
  if not ok then raise exception 'FAIL 8b: a viewer moved items through the RPC'; end if;
  raise notice 'PASS 8b: a viewer is refused by move_plan_items';
end $$;
reset role;

-- ---------------------------------------------------------------------------
-- Case 9: membership rules - only the owner invites or changes roles; anyone
-- can leave.
-- ---------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000f2","role":"authenticated"}';
do $$
declare ok boolean; n int;
begin
  begin
    insert into public.plan_members (plan_id, user_id, role, status, invited_by)
    values ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-0000000000f5',
            'viewer', 'pending', '00000000-0000-0000-0000-0000000000f2');
    ok := false;
  exception when insufficient_privilege then ok := true;
  end;
  if not ok then raise exception 'FAIL 9a: an editor was able to invite someone'; end if;

  update public.plan_members set role = 'editor'
  where plan_id = '00000000-0000-0000-0000-00000000a001' and user_id = '00000000-0000-0000-0000-0000000000f3';
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'FAIL 9b: an editor changed someone''s role (% rows)', n; end if;
end $$;
reset role;

set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000f1","role":"authenticated"}';
do $$
begin
  insert into public.plan_members (plan_id, user_id, role, status, invited_by)
  values ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-0000000000f5',
          'viewer', 'pending', '00000000-0000-0000-0000-0000000000f1');
end $$;
reset role;

set local role authenticated;
set local request.jwt.claims to '{"sub":"00000000-0000-0000-0000-0000000000f3","role":"authenticated"}';
do $$
declare n int;
begin
  delete from public.plan_members
  where plan_id = '00000000-0000-0000-0000-00000000a001' and user_id = '00000000-0000-0000-0000-0000000000f3';
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'FAIL 9c: a member could not leave the plan (% rows)', n; end if;
  raise notice 'PASS 9: only the owner invites and assigns roles; members can leave';
end $$;
reset role;

rollback;
