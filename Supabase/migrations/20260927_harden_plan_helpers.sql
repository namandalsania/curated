-- Harden the plan-collaboration access helpers.
--
-- Run once in the Supabase SQL editor, after 20260926_live_trips.sql.
-- Safe to re-run.
--
-- plan_role() and plan_invited() (20260919b) live in `public` with
-- `search_path = public`. Two problems with that:
--
--   1. `public` is a Data API schema, so both are callable as PostgREST RPCs:
--      POST /rest/v1/rpc/plan_role {"p_plan_id": "..."} answers "owner" /
--      "editor" / "viewer" / null for any id, and plan_invited() answers
--      whether you hold an invite. Harmless for a well-behaved client, but it
--      is a probe nobody needs.
--   2. A non-empty search_path on a SECURITY DEFINER function means an
--      unqualified name can resolve somewhere the author didn't intend.
--
-- They move to `private` (created by 20260926_live_trips.sql, not exposed by
-- the Data API) with `search_path = ''` and every name schema-qualified - the
-- same shape as private.trip_is_visible() and friends. Every policy that calls
-- them is recreated against the new names with its rule unchanged, and
-- move_plan_items() is rewritten the same way because it calls plan_role().
--
-- Deliberately separate from the live-trips migration, so either can be rolled
-- back without the other.

begin;

create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to anon, authenticated;

-- ---------------------------------------------------------------------------
-- 1. The helpers, in private.
--
-- Bodies are the 20260919b logic, schema-qualified. auth.uid() is wrapped in a
-- scalar subquery so it is evaluated once per statement, not once per row.
-- ---------------------------------------------------------------------------

-- The caller's role on a plan: 'owner', 'editor', 'viewer', or null for none.
-- A pending invite is not a role yet.
create or replace function private.plan_role(p_plan_id uuid) returns text
language sql stable security definer set search_path = ''
as $$
  select case
    when exists (
      select 1 from public.plans p
      where p.id = p_plan_id and p.user_id = (select auth.uid())
    ) then 'owner'
    else (
      select m.role from public.plan_members m
      where m.plan_id = p_plan_id
        and m.user_id = (select auth.uid())
        and m.status = 'accepted'
    )
  end;
$$;

-- Whether the caller holds an invite (pending or accepted), so an invitee can
-- see a plan's name before accepting.
create or replace function private.plan_invited(p_plan_id uuid) returns boolean
language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from public.plan_members m
    where m.plan_id = p_plan_id and m.user_id = (select auth.uid())
  );
$$;

-- Policies are evaluated as the querying role, so both API roles need execute:
-- without it an anon read of plans would error instead of returning no rows.
-- `private` has no RPC route, so this grants nothing callable from outside.
revoke execute on function private.plan_role(uuid)    from public;
revoke execute on function private.plan_invited(uuid) from public;
grant  execute on function private.plan_role(uuid)    to anon, authenticated;
grant  execute on function private.plan_invited(uuid) to anon, authenticated;

-- ---------------------------------------------------------------------------
-- 2. Recreate every policy that called the public versions. Same rules as
-- 20260919b, only the function names change.
-- ---------------------------------------------------------------------------
drop policy if exists "plans_select" on public.plans;
drop policy if exists "plans_update" on public.plans;
-- user_id is checked directly first: plan_role() is STABLE and can't see a row
-- inserted by the same statement, so `insert ... returning` (creating a plan)
-- would otherwise fail its own read-back.
create policy "plans_select" on public.plans for select
  using (
    (select auth.uid()) = user_id
    or private.plan_role(id) is not null
    or private.plan_invited(id)
  );
create policy "plans_update" on public.plans for update
  using (coalesce(private.plan_role(id), '') in ('owner', 'editor'))
  with check (coalesce(private.plan_role(id), '') in ('owner', 'editor'));
-- plans_insert and plans_delete test user_id directly and are left as they are.

drop policy if exists "plan_items_select" on public.plan_items;
drop policy if exists "plan_items_insert" on public.plan_items;
drop policy if exists "plan_items_update" on public.plan_items;
drop policy if exists "plan_items_delete" on public.plan_items;
create policy "plan_items_select" on public.plan_items for select
  using (private.plan_role(plan_id) is not null);
create policy "plan_items_insert" on public.plan_items for insert
  with check (
    coalesce(private.plan_role(plan_id), '') in ('owner', 'editor')
    and added_by = (select auth.uid())
    -- If it names a saved place, that must be the adder's own.
    and (saved_place_id is null or exists (
      select 1 from public.saved_places s
      where s.id = plan_items.saved_place_id and s.user_id = (select auth.uid())
    ))
  );
create policy "plan_items_update" on public.plan_items for update
  using (coalesce(private.plan_role(plan_id), '') in ('owner', 'editor'))
  with check (coalesce(private.plan_role(plan_id), '') in ('owner', 'editor'));
create policy "plan_items_delete" on public.plan_items for delete
  using (coalesce(private.plan_role(plan_id), '') in ('owner', 'editor'));

drop policy if exists "plan_members_select" on public.plan_members;
drop policy if exists "plan_members_insert" on public.plan_members;
drop policy if exists "plan_members_update" on public.plan_members;
drop policy if exists "plan_members_delete" on public.plan_members;
-- Everyone on a plan sees who else is; an invitee sees their own invite.
create policy "plan_members_select" on public.plan_members for select
  using (user_id = (select auth.uid()) or private.plan_role(plan_id) is not null);
-- Only the owner invites, always as a pending invite, never themselves.
create policy "plan_members_insert" on public.plan_members for insert
  with check (
    private.plan_role(plan_id) = 'owner'
    and invited_by = (select auth.uid())
    and status = 'pending'
    and user_id <> (select auth.uid())
  );
-- Only the owner changes roles. Accepting goes through accept_plan_invite().
create policy "plan_members_update" on public.plan_members for update
  using (private.plan_role(plan_id) = 'owner')
  with check (private.plan_role(plan_id) = 'owner');
-- The owner removes anyone; anyone can decline an invite or leave.
create policy "plan_members_delete" on public.plan_members for delete
  using (private.plan_role(plan_id) = 'owner' or user_id = (select auth.uid()));

-- ---------------------------------------------------------------------------
-- 3. move_plan_items() stays in public - the app calls it as an RPC - but
-- gets the same search_path treatment and calls the private helper.
-- ---------------------------------------------------------------------------
create or replace function public.move_plan_items(p_plan_id uuid, p_moves jsonb) returns void
language plpgsql security definer set search_path = ''
as $$
begin
  if coalesce(private.plan_role(p_plan_id), '') not in ('owner', 'editor') then
    raise exception 'Not allowed to edit this plan' using errcode = '42501';
  end if;
  update public.plan_items i
  set day_number = (m->>'day_number')::int,
      position = (m->>'position')::int
  from jsonb_array_elements(p_moves) m
  where i.id = (m->>'id')::uuid and i.plan_id = p_plan_id;
  update public.plans set updated_at = now() where id = p_plan_id;
end;
$$;

-- Signed-in users only: a signed-out caller has no plans to move.
revoke execute on function public.move_plan_items(uuid, jsonb) from public, anon;
grant  execute on function public.move_plan_items(uuid, jsonb) to authenticated;

-- ---------------------------------------------------------------------------
-- 4. Drop the public versions. No CASCADE: if anything still depends on them,
-- this should fail and roll the whole migration back rather than silently
-- take a policy with it.
-- ---------------------------------------------------------------------------
drop function if exists public.plan_role(uuid);
drop function if exists public.plan_invited(uuid);

notify pgrst, 'reload schema';

commit;
