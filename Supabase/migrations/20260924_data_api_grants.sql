-- Data API grants for every public table.
--
-- From 2026-10-30 Supabase no longer grants Data API access to new tables in
-- `public` automatically. Without a grant, PostgREST (and so supabase-js) answers
-- "permission denied" no matter what the RLS policies say.
--
-- The already-deployed project keeps the grants it was given automatically, so
-- this migration is a no-op there. It matters for anything that replays the
-- migrations from scratch: `supabase db reset`, preview branches, a new project.
--
-- Grants and RLS are different gates. A grant decides whether the Data API can
-- reach the table at all; the RLS policies still decide which rows each caller
-- sees. Nothing here loosens a policy.

do $$
declare
  t text;
begin
  foreach t in array array[
    'users', 'follows', 'trips', 'days', 'stops', 'stop_photos', 'bookmarks',
    'likes', 'notifications', 'saved_places', 'plans', 'plan_items',
    'plan_members', 'stop_comments', 'trip_shares'
  ]
  loop
    -- Skip anything a partly-applied migration never created, so this file
    -- can't fail the whole chain over one missing table.
    if to_regclass('public.' || t) is null then
      raise notice 'skipping public.%: table does not exist', t;
      continue;
    end if;

    execute format('grant select on public.%I to anon', t);
    execute format('grant select, insert, update, delete on public.%I to authenticated', t);
    execute format('grant all on public.%I to service_role', t);
  end loop;
end
$$;

-- Sequences backing any identity/serial columns need their own grant, or an
-- insert that relies on a default fails even with the table granted.
grant usage, select on all sequences in schema public to anon, authenticated;
grant all on all sequences in schema public to service_role;
