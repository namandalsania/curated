-- Saved places and plans: save individual stops (a hotel, a castle, a pizza
-- place) from anyone's trip, then assemble them into your own private,
-- day-by-day plan.
--
-- Run once in the Supabase SQL editor, after schema.sql, storage.sql,
-- phase2.sql and 20260916_stop_arrival_time.sql. Safe to re-run.

-- A place someone saved from a trip. The fields a saved place shows are
-- copied from the stop at save time, so it survives the creator editing or
-- deleting their trip: stop_id / source_trip_id go null, the place stays.
create table if not exists saved_places (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  stop_id uuid references stops(id) on delete set null,
  source_trip_id uuid references trips(id) on delete set null,
  source_trip_title text,
  source_author_name text,
  name text not null,
  category text not null check (category in ('food','sight','hotel','transport','other')),
  latitude double precision not null,
  longitude double precision not null,
  place_name text,
  photo_url text,
  -- Geocoded once at save time, for grouping by country and city.
  city text,
  country_code text,
  country_name text,
  created_at timestamptz not null default now()
);
-- One save per user per stop. Rows whose stop was deleted (null stop_id)
-- don't collide, since Postgres treats nulls as distinct.
create unique index if not exists saved_places_user_stop on saved_places (user_id, stop_id);
create index if not exists saved_places_user_recent on saved_places (user_id, created_at desc);

create table if not exists plans (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  title text not null,
  day_count int not null default 1 check (day_count between 1 and 60),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists plans_user_recent on plans (user_id, updated_at desc);

-- A saved place slotted into a plan. Unsaving the place removes it from its
-- plans too (the app warns first).
create table if not exists plan_items (
  id uuid primary key default gen_random_uuid(),
  plan_id uuid not null references plans(id) on delete cascade,
  saved_place_id uuid not null references saved_places(id) on delete cascade,
  day_number int not null check (day_number >= 1),
  position int not null,
  created_at timestamptz not null default now(),
  unique (plan_id, saved_place_id)
);
create index if not exists plan_items_plan_order on plan_items (plan_id, day_number, position);

alter table saved_places enable row level security;
alter table plans enable row level security;
alter table plan_items enable row level security;

-- All private: only the owner can read or change any of it.
drop policy if exists "saved_places_own" on saved_places;
create policy "saved_places_own" on saved_places for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists "plans_own" on plans;
create policy "plans_own" on plans for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

-- Items follow their plan's owner, and may only point at the owner's own saved places.
drop policy if exists "plan_items_via_plan_owner" on plan_items;
create policy "plan_items_via_plan_owner" on plan_items for all
  using (exists (select 1 from plans p where p.id = plan_items.plan_id and p.user_id = auth.uid()))
  with check (
    exists (select 1 from plans p where p.id = plan_id and p.user_id = auth.uid())
    and exists (select 1 from saved_places s where s.id = saved_place_id and s.user_id = auth.uid())
  );

notify pgrst, 'reload schema';
