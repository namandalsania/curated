-- Run once in the Supabase SQL editor after schema.sql and storage.sql.
-- Adds likes + notifications for the engagement layer, and an avatars bucket
-- for profile setup.

create table likes (
  user_id uuid not null references users(id) on delete cascade,
  trip_id uuid not null references trips(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, trip_id)
);
create index on likes (trip_id);

alter table likes enable row level security;

create policy "likes_select_all" on likes for select using (true);
create policy "likes_insert_own" on likes for insert with check (auth.uid() = user_id);
create policy "likes_delete_own" on likes for delete using (auth.uid() = user_id);

create table notifications (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null references users(id) on delete cascade,
  actor_id uuid references users(id) on delete set null,
  type text not null check (type in ('follow','new_trip')),
  trip_id uuid references trips(id) on delete cascade,
  created_at timestamptz not null default now(),
  read_at timestamptz
);
create index on notifications (recipient_id, created_at desc);

alter table notifications enable row level security;

create policy "notifications_select_own" on notifications for select using (auth.uid() = recipient_id);
create policy "notifications_insert_as_actor" on notifications for insert with check (auth.uid() = actor_id);
create policy "notifications_update_own" on notifications for update using (auth.uid() = recipient_id);
create policy "notifications_delete_own" on notifications for delete using (auth.uid() = recipient_id);

-- Realtime: let clients subscribe to inserts on their own notifications.
alter publication supabase_realtime add table notifications;

-- Avatars bucket for profile setup, same shape as the stop-photos bucket.
insert into storage.buckets (id, name, public)
values ('avatars', 'avatars', true)
on conflict (id) do nothing;

create policy "avatars_bucket_read_all"
on storage.objects for select
using (bucket_id = 'avatars');

create policy "avatars_bucket_insert_own"
on storage.objects for insert
with check (bucket_id = 'avatars' and auth.role() = 'authenticated');

create policy "avatars_bucket_update_own"
on storage.objects for update
using (bucket_id = 'avatars' and owner = auth.uid());

create policy "avatars_bucket_delete_own"
on storage.objects for delete
using (bucket_id = 'avatars' and owner = auth.uid());
