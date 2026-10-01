-- Run once in the Supabase SQL editor after schema.sql.
-- Creates the public bucket the Android app uploads stop photos into,
-- plus RLS policies so trip owners can manage their own photos.

insert into storage.buckets (id, name, public)
values ('stop-photos', 'stop-photos', true)
on conflict (id) do nothing;

create policy "stop_photos_bucket_read_all"
on storage.objects for select
using (bucket_id = 'stop-photos');

create policy "stop_photos_bucket_insert_own"
on storage.objects for insert
with check (bucket_id = 'stop-photos' and auth.role() = 'authenticated');

create policy "stop_photos_bucket_update_own"
on storage.objects for update
using (bucket_id = 'stop-photos' and owner = auth.uid());

create policy "stop_photos_bucket_delete_own"
on storage.objects for delete
using (bucket_id = 'stop-photos' and owner = auth.uid());
