-- Close the Supabase advisor finding `rls_disabled_in_public` on
-- public.spatial_ref_sys.
--
-- Run once in the Supabase SQL editor. Safe to re-run.
--
-- spatial_ref_sys is PostGIS's catalogue of coordinate systems, created by
-- `create extension postgis` in schema.sql. It holds no app data, but because it
-- sits in `public` with Data API grants, anyone with the project URL could edit
-- or delete its rows - deleting SRID 4326 would break stops.location.
--
-- The advisor's usual fixes don't fit:
--   * Moving PostGIS out of `public` isn't supported by PostGIS once columns
--     (stops.location) depend on it.
--   * `enable row level security` needs table ownership, and on Supabase the
--     extension's objects usually belong to supabase_admin, not postgres.
--
-- So: try RLS (with a read-only policy, since PostGIS itself must keep reading
-- it), and either way take write access away from the API roles. The app never
-- touches this table; PostGIS functions read it as the calling role, so select
-- stays granted.

do $$
begin
  begin
    alter table public.spatial_ref_sys enable row level security;
    drop policy if exists "spatial_ref_sys_read" on public.spatial_ref_sys;
    create policy "spatial_ref_sys_read" on public.spatial_ref_sys
      for select using (true);
    raise notice 'RLS enabled on public.spatial_ref_sys';
  exception when insufficient_privilege then
    raise notice 'Not the owner of public.spatial_ref_sys - relying on revoked grants instead';
  end;
end
$$;

revoke insert, update, delete, truncate, references, trigger
  on public.spatial_ref_sys from anon, authenticated;

-- Check: can_write must be false for both roles. If it's still true, postgres
-- isn't the role that granted it, and the revoke was a silent no-op.
select r.rolname,
       has_table_privilege(r.rolname, 'public.spatial_ref_sys', 'select') as can_read,
       has_table_privilege(r.rolname, 'public.spatial_ref_sys', 'insert')
         or has_table_privilege(r.rolname, 'public.spatial_ref_sys', 'update')
         or has_table_privilege(r.rolname, 'public.spatial_ref_sys', 'delete') as can_write
from pg_roles r
where r.rolname in ('anon', 'authenticated');
