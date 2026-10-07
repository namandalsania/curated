-- Stop anyone from LISTING the photo buckets.
--
-- storage.sql and phase2.sql gave every caller, signed in or not, SELECT on
-- storage.objects in 'stop-photos' and 'avatars'. Storage's list endpoint uses
-- that policy, so anyone with the anon key could enumerate every trip and
-- stop folder and every file - including photos of unlisted and private trips,
-- whose addresses are otherwise unguessable.
--
-- Public URLs (/storage/v1/object/public/...) don't go through these policies
-- for a public bucket, so the app's images keep loading. Uploads don't need
-- SELECT unless they upsert, and the app never upserts or lists. The
-- delete-account function uses the service role, which bypasses RLS.
--
-- After this: an uploader can still see their own objects; nobody else can
-- list anything. Safe to re-run.

begin;

drop policy if exists "stop_photos_bucket_read_all" on storage.objects;
drop policy if exists "stop_photos_bucket_read_own" on storage.objects;
create policy "stop_photos_bucket_read_own"
on storage.objects for select
using (bucket_id = 'stop-photos' and owner = (select auth.uid()));

drop policy if exists "avatars_bucket_read_all" on storage.objects;
drop policy if exists "avatars_bucket_read_own" on storage.objects;
create policy "avatars_bucket_read_own"
on storage.objects for select
using (bucket_id = 'avatars' and owner = (select auth.uid()));

commit;
