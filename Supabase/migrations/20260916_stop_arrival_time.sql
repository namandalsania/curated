-- Adds a per-stop arrival time ("5:30 PM").
-- Run once in the Supabase SQL editor after schema.sql, storage.sql, phase2.sql.
--
-- Plain `time` (no time zone) on purpose: it's the local wall-clock time at the
-- stop itself. The calendar date is already implied by the stop's day, and a
-- timestamptz would render shifted for viewers in other time zones.

alter table stops add column if not exists arrival_time time;

comment on column stops.arrival_time is
  'Local wall-clock arrival time at this stop (no tz). Date comes from the stop''s day.';

-- Existing RLS policies on stops are column-agnostic, so no policy changes needed.
-- Ask PostgREST to pick up the new column immediately.
notify pgrst, 'reload schema';
