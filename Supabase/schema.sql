create extension if not exists postgis;

create type trip_status as enum ('draft', 'published');
create type trip_visibility as enum ('public', 'unlisted', 'private');

create table users (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique not null,
  display_name text not null,
  avatar_url text,
  bio text,
  created_at timestamptz not null default now()
);

create table follows (
  follower_id uuid not null references users(id) on delete cascade,
  following_id uuid not null references users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (follower_id, following_id),
  check (follower_id <> following_id)
);
create index on follows (following_id);

create table trips (
  id uuid primary key default gen_random_uuid(),
  author_id uuid not null references users(id) on delete cascade,
  title text not null,
  destination text not null,
  start_date date not null,
  end_date date not null,
  cover_photo_url text,
  budget_tag text check (budget_tag in ('budget','mid_range','luxury')),
  season_tag text check (season_tag in ('spring','summer','fall','winter')),
  status trip_status not null default 'draft',
  visibility trip_visibility not null default 'public',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index on trips (author_id);
create index on trips (status, created_at desc);

create table days (
  id uuid primary key default gen_random_uuid(),
  trip_id uuid not null references trips(id) on delete cascade,
  day_index int not null,
  date date,
  unique (trip_id, day_index),
  unique (id, trip_id) -- lets stops FK-validate day belongs to same trip
);

create table stops (
  id uuid primary key default gen_random_uuid(),
  day_id uuid references days(id) on delete set null,
  trip_id uuid not null references trips(id) on delete cascade,
  name text not null,
  category text not null check (category in ('food','sight','hotel','transport','other')),
  location geography(Point, 4326) not null,
  latitude double precision generated always as (ST_Y(location::geometry)) stored,
  longitude double precision generated always as (ST_X(location::geometry)) stored,
  order_in_day int not null,
  caption text,
  cost numeric(10,2),
  tips text,
  place_name text,
  created_at timestamptz not null default now(),
  foreign key (day_id, trip_id) references days(id, trip_id)
);
create index stops_location_gix on stops using gist (location);
create index on stops (day_id);
create index on stops (trip_id);

create table stop_photos (
  id uuid primary key default gen_random_uuid(),
  stop_id uuid not null references stops(id) on delete cascade,
  storage_path text not null,
  taken_at timestamptz,
  order_index int not null,
  created_at timestamptz not null default now()
);
create index on stop_photos (stop_id);

create table bookmarks (
  user_id uuid not null references users(id) on delete cascade,
  trip_id uuid not null references trips(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, trip_id)
);

-- Row Level Security

alter table users enable row level security;
alter table follows enable row level security;
alter table trips enable row level security;
alter table days enable row level security;
alter table stops enable row level security;
alter table stop_photos enable row level security;
alter table bookmarks enable row level security;

-- users: public read, self write
create policy "users_select_all" on users for select using (true);
create policy "users_insert_self" on users for insert with check (auth.uid() = id);
create policy "users_update_self" on users for update using (auth.uid() = id);

-- follows: public read, self write
create policy "follows_select_all" on follows for select using (true);
create policy "follows_insert_self" on follows for insert with check (auth.uid() = follower_id);
create policy "follows_delete_self" on follows for delete using (auth.uid() = follower_id);

-- trips: read if published+public/unlisted, or owner; write only by owner
create policy "trips_select_visible_or_own" on trips for select using (
  (status = 'published' and visibility in ('public','unlisted'))
  or auth.uid() = author_id
);
create policy "trips_insert_own" on trips for insert with check (auth.uid() = author_id);
create policy "trips_update_own" on trips for update using (auth.uid() = author_id);
create policy "trips_delete_own" on trips for delete using (auth.uid() = author_id);

-- days: access follows parent trip
create policy "days_select_via_trip" on days for select using (
  exists (
    select 1 from trips t where t.id = days.trip_id
    and ((t.status = 'published' and t.visibility in ('public','unlisted')) or auth.uid() = t.author_id)
  )
);
create policy "days_insert_via_trip_owner" on days for insert with check (
  exists (select 1 from trips t where t.id = trip_id and auth.uid() = t.author_id)
);
create policy "days_update_via_trip_owner" on days for update using (
  exists (select 1 from trips t where t.id = trip_id and auth.uid() = t.author_id)
);
create policy "days_delete_via_trip_owner" on days for delete using (
  exists (select 1 from trips t where t.id = trip_id and auth.uid() = t.author_id)
);

-- stops: access follows parent trip (trip_id is denormalized directly on stops)
create policy "stops_select_via_trip" on stops for select using (
  exists (
    select 1 from trips t where t.id = stops.trip_id
    and ((t.status = 'published' and t.visibility in ('public','unlisted')) or auth.uid() = t.author_id)
  )
);
create policy "stops_insert_via_trip_owner" on stops for insert with check (
  exists (select 1 from trips t where t.id = trip_id and auth.uid() = t.author_id)
);
create policy "stops_update_via_trip_owner" on stops for update using (
  exists (select 1 from trips t where t.id = trip_id and auth.uid() = t.author_id)
);
create policy "stops_delete_via_trip_owner" on stops for delete using (
  exists (select 1 from trips t where t.id = trip_id and auth.uid() = t.author_id)
);

-- stop_photos: access follows stop -> trip
create policy "stop_photos_select_via_trip" on stop_photos for select using (
  exists (
    select 1 from stops s join trips t on t.id = s.trip_id
    where s.id = stop_photos.stop_id
    and ((t.status = 'published' and t.visibility in ('public','unlisted')) or auth.uid() = t.author_id)
  )
);
create policy "stop_photos_insert_via_trip_owner" on stop_photos for insert with check (
  exists (
    select 1 from stops s join trips t on t.id = s.trip_id
    where s.id = stop_id and auth.uid() = t.author_id
  )
);
create policy "stop_photos_update_via_trip_owner" on stop_photos for update using (
  exists (
    select 1 from stops s join trips t on t.id = s.trip_id
    where s.id = stop_id and auth.uid() = t.author_id
  )
);
create policy "stop_photos_delete_via_trip_owner" on stop_photos for delete using (
  exists (
    select 1 from stops s join trips t on t.id = s.trip_id
    where s.id = stop_id and auth.uid() = t.author_id
  )
);

-- bookmarks: fully private to the bookmarking user
create policy "bookmarks_select_own" on bookmarks for select using (auth.uid() = user_id);
create policy "bookmarks_insert_own" on bookmarks for insert with check (auth.uid() = user_id);
create policy "bookmarks_delete_own" on bookmarks for delete using (auth.uid() = user_id);
