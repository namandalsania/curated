-- Plan collaboration: the owner of a plan invites people by username as a
-- viewer (read only) or editor (add, move, remove places and days). Only the
-- owner can rename or delete the plan, or manage who's on it.
--
-- Run once in the Supabase SQL editor, after 20260919_saved_places_and_plans.sql.
-- Safe to re-run. Existing plans keep every item.

-- ---------------------------------------------------------------------------
-- 1. Plan items carry their own copy of the place.
--
-- Until now an item only pointed at the owner's saved place. Saved places are
-- private, so collaborators couldn't read them, couldn't add their own, and a
-- place would vanish from a shared plan the moment its saver unsaved it.
-- ---------------------------------------------------------------------------
alter table plan_items
  add column if not exists stop_id uuid references stops(id) on delete set null,
  add column if not exists source_trip_id uuid references trips(id) on delete set null,
  add column if not exists source_trip_title text,
  add column if not exists source_author_name text,
  add column if not exists name text,
  add column if not exists category text,
  add column if not exists latitude double precision,
  add column if not exists longitude double precision,
  add column if not exists place_name text,
  add column if not exists photo_url text,
  add column if not exists city text,
  add column if not exists country_code text,
  add column if not exists country_name text,
  add column if not exists added_by uuid references users(id) on delete set null;

-- Backfill existing items from their saved place, credited to the plan owner.
update plan_items i set
  stop_id = s.stop_id,
  source_trip_id = s.source_trip_id,
  source_trip_title = s.source_trip_title,
  source_author_name = s.source_author_name,
  name = s.name,
  category = s.category,
  latitude = s.latitude,
  longitude = s.longitude,
  place_name = s.place_name,
  photo_url = s.photo_url,
  city = s.city,
  country_code = s.country_code,
  country_name = s.country_name,
  added_by = p.user_id
from saved_places s, plans p
where s.id = i.saved_place_id and p.id = i.plan_id and i.name is null;

alter table plan_items
  alter column name set not null,
  alter column category set not null,
  alter column latitude set not null,
  alter column longitude set not null;

alter table plan_items drop constraint if exists plan_items_category_check;
alter table plan_items add constraint plan_items_category_check
  check (category in ('food','sight','hotel','transport','other'));

-- saved_place_id is now just "which saved place this came from": unsaving no
-- longer deletes the item from anyone's plan.
alter table plan_items alter column saved_place_id drop not null;
alter table plan_items drop constraint if exists plan_items_saved_place_id_fkey;
alter table plan_items add constraint plan_items_saved_place_id_fkey
  foreign key (saved_place_id) references saved_places(id) on delete set null;

-- Two collaborators who saved the same stop can't both add it.
create unique index if not exists plan_items_plan_stop on plan_items (plan_id, stop_id) where stop_id is not null;

-- ---------------------------------------------------------------------------
-- 2. Members. The owner is plans.user_id and never has a row here.
-- ---------------------------------------------------------------------------
create table if not exists plan_members (
  plan_id uuid not null references plans(id) on delete cascade,
  user_id uuid not null references users(id) on delete cascade,
  role text not null check (role in ('viewer','editor')),
  status text not null default 'pending' check (status in ('pending','accepted')),
  invited_by uuid references users(id) on delete set null,
  created_at timestamptz not null default now(),
  primary key (plan_id, user_id)
);
create index if not exists plan_members_user on plan_members (user_id);

alter table plan_members enable row level security;

-- ---------------------------------------------------------------------------
-- 3. Access helpers. SECURITY DEFINER so policies on plans, plan_items and
-- plan_members can call them without recursing into each other's RLS.
-- ---------------------------------------------------------------------------

-- The caller's role on a plan: 'owner', 'editor', 'viewer', or null for none.
-- A pending invite is not a role yet.
create or replace function plan_role(p_plan_id uuid) returns text
language sql stable security definer set search_path = public
as $$
  select case
    when exists (select 1 from plans where id = p_plan_id and user_id = auth.uid()) then 'owner'
    else (
      select role from plan_members
      where plan_id = p_plan_id and user_id = auth.uid() and status = 'accepted'
    )
  end;
$$;

-- Whether the caller has an invite (pending or accepted) to a plan, so an
-- invitee can see the plan's name before accepting.
create or replace function plan_invited(p_plan_id uuid) returns boolean
language sql stable security definer set search_path = public
as $$
  select exists (select 1 from plan_members where plan_id = p_plan_id and user_id = auth.uid());
$$;

-- Reorder / move items in one transaction. Only day and position change, and
-- only for items of this plan - so an editor can't touch anything else.
create or replace function move_plan_items(p_plan_id uuid, p_moves jsonb) returns void
language plpgsql security definer set search_path = public
as $$
begin
  if coalesce(plan_role(p_plan_id), '') not in ('owner', 'editor') then
    raise exception 'Not allowed to edit this plan' using errcode = '42501';
  end if;
  update plan_items i
  set day_number = (m->>'day_number')::int,
      position = (m->>'position')::int
  from jsonb_array_elements(p_moves) m
  where i.id = (m->>'id')::uuid and i.plan_id = p_plan_id;
  update plans set updated_at = now() where id = p_plan_id;
end;
$$;

create or replace function accept_plan_invite(p_plan_id uuid) returns void
language sql security definer set search_path = public
as $$
  update plan_members set status = 'accepted'
  where plan_id = p_plan_id and user_id = auth.uid();
$$;

-- Editors may add/remove days (day_count), but only the owner renames, and
-- nobody moves ownership. RLS is row-level, so this is a trigger.
create or replace function plans_guard_update() returns trigger
language plpgsql security definer set search_path = public
as $$
begin
  if new.user_id <> old.user_id then
    raise exception 'A plan''s owner can''t change' using errcode = '42501';
  end if;
  if new.title <> old.title and old.user_id <> auth.uid() then
    raise exception 'Only the owner can rename a plan' using errcode = '42501';
  end if;
  return new;
end;
$$;
drop trigger if exists plans_guard_update on plans;
create trigger plans_guard_update before update on plans
  for each row execute function plans_guard_update();

-- ---------------------------------------------------------------------------
-- 4. Policies (replacing the owner-only ones from the previous migration).
-- ---------------------------------------------------------------------------
drop policy if exists "plans_own" on plans;
drop policy if exists "plans_select" on plans;
drop policy if exists "plans_insert" on plans;
drop policy if exists "plans_update" on plans;
drop policy if exists "plans_delete" on plans;
-- user_id is checked directly first: plan_role() is STABLE and can't see a row
-- inserted by the same statement, so `insert ... returning` (creating a plan)
-- would otherwise fail its own read-back.
create policy "plans_select" on plans for select
  using (auth.uid() = user_id or plan_role(id) is not null or plan_invited(id));
create policy "plans_insert" on plans for insert
  with check (auth.uid() = user_id);
create policy "plans_update" on plans for update
  using (coalesce(plan_role(id), '') in ('owner', 'editor'))
  with check (coalesce(plan_role(id), '') in ('owner', 'editor'));
create policy "plans_delete" on plans for delete
  using (auth.uid() = user_id);

drop policy if exists "plan_items_via_plan_owner" on plan_items;
drop policy if exists "plan_items_select" on plan_items;
drop policy if exists "plan_items_insert" on plan_items;
drop policy if exists "plan_items_update" on plan_items;
drop policy if exists "plan_items_delete" on plan_items;
create policy "plan_items_select" on plan_items for select
  using (plan_role(plan_id) is not null);
create policy "plan_items_insert" on plan_items for insert
  with check (
    coalesce(plan_role(plan_id), '') in ('owner', 'editor')
    and added_by = auth.uid()
    -- If it names a saved place, that must be the adder's own.
    and (saved_place_id is null or exists (
      select 1 from saved_places s where s.id = saved_place_id and s.user_id = auth.uid()
    ))
  );
create policy "plan_items_update" on plan_items for update
  using (coalesce(plan_role(plan_id), '') in ('owner', 'editor'))
  with check (coalesce(plan_role(plan_id), '') in ('owner', 'editor'));
create policy "plan_items_delete" on plan_items for delete
  using (coalesce(plan_role(plan_id), '') in ('owner', 'editor'));

drop policy if exists "plan_members_select" on plan_members;
drop policy if exists "plan_members_insert" on plan_members;
drop policy if exists "plan_members_update" on plan_members;
drop policy if exists "plan_members_delete" on plan_members;
-- Everyone on a plan sees who else is; an invitee sees their own invite.
create policy "plan_members_select" on plan_members for select
  using (user_id = auth.uid() or plan_role(plan_id) is not null);
-- Only the owner invites, always as a pending invite, never themselves.
create policy "plan_members_insert" on plan_members for insert
  with check (
    plan_role(plan_id) = 'owner' and invited_by = auth.uid()
    and status = 'pending' and user_id <> auth.uid()
  );
-- Only the owner changes roles. Accepting goes through accept_plan_invite().
create policy "plan_members_update" on plan_members for update
  using (plan_role(plan_id) = 'owner')
  with check (plan_role(plan_id) = 'owner');
-- The owner removes anyone; anyone can decline an invite or leave.
create policy "plan_members_delete" on plan_members for delete
  using (plan_role(plan_id) = 'owner' or user_id = auth.uid());

-- ---------------------------------------------------------------------------
-- 5. Invite notifications.
-- ---------------------------------------------------------------------------
alter table notifications add column if not exists plan_id uuid references plans(id) on delete cascade;
-- Also lists 20260919c's types: the live project ran 19c before this file, and
-- a narrower list here would reject its comment and share notifications.
alter table notifications drop constraint if exists notifications_type_check;
alter table notifications add constraint notifications_type_check
  check (type in ('follow', 'new_trip', 'plan_invite', 'stop_comment', 'trip_share'));

-- ---------------------------------------------------------------------------
-- 6. Live updates: collaborators see each other's edits. Realtime applies the
-- select policies above, so nobody receives changes to plans they can't see.
-- ---------------------------------------------------------------------------
do $$
begin
  begin alter publication supabase_realtime add table plan_items; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table plans; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table plan_members; exception when duplicate_object then null; end;
end $$;

notify pgrst, 'reload schema';
