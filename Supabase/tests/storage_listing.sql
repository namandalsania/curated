-- Checks for 20261007_storage_no_public_listing.sql.
--
-- Run in the SQL editor after the migration. One transaction ending in
-- ROLLBACK: it leaves nothing behind. A failure raises an exception; silence
-- past the last NOTICE means a case failed.
--
-- This checks the policies (what the list endpoint is allowed to see). That
-- public URLs still load is a Storage-server behaviour; check it afterwards by
-- opening any trip in the app.

begin;

insert into auth.users (instance_id, id, aud, role, email, encrypted_password,
                        email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data)
values
  ('00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-0000000003a1',
   'authenticated', 'authenticated', 'uploader@storage-test.invalid', '', now(), now(), now(), '{}', '{}'),
  ('00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-0000000003a2',
   'authenticated', 'authenticated', 'stranger@storage-test.invalid', '', now(), now(), now(), '{}', '{}');

insert into storage.objects (bucket_id, name, owner) values
  ('stop-photos', 'storage-test-trip/storage-test-stop/0.jpg', '00000000-0000-0000-0000-0000000003a1'),
  ('avatars',     '00000000-0000-0000-0000-0000000003a1-1.jpg', '00000000-0000-0000-0000-0000000003a1');

-- Case 1: signed out sees nothing.
set local role anon;
select set_config('request.jwt.claims', '{"role":"anon"}', true);
do $$
begin
  if exists (select 1 from storage.objects where name like 'storage-test-%' or name like '%0003a1-%') then
    raise exception 'FAIL case 1: anon can list objects';
  end if;
  raise notice 'PASS 1: signed out, nothing in either bucket can be listed';
end $$;
reset role;

-- Case 2: another signed-in user sees nothing.
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000003a2","role":"authenticated"}', true);
do $$
begin
  if exists (select 1 from storage.objects where name like 'storage-test-%' or name like '%0003a1-%') then
    raise exception 'FAIL case 2: another user can list the uploader''s objects';
  end if;
  raise notice 'PASS 2: another signed-in user cannot list them';
end $$;
reset role;

-- Case 3: the uploader still sees their own.
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000003a1","role":"authenticated"}', true);
do $$
declare n int;
begin
  select count(*) into n from storage.objects where name like 'storage-test-%' or name like '%0003a1-%';
  if n <> 2 then raise exception 'FAIL case 3: uploader sees % of their 2 objects', n; end if;
  raise notice 'PASS 3: the uploader can still see their own 2 objects';
end $$;
reset role;

rollback;
