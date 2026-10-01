-- Anything can go in a plan: a coffee shop, a restaurant, "laundry", a place
-- nobody has published a trip about.
--
-- Until now a plan item had to come from a saved place, so it always carried
-- coordinates. A typed-in place may have none - you know the name before you
-- know where exactly it is.
--
-- Run once in the Supabase SQL editor, after 20260919c_comments_and_shares.sql.
-- Safe to re-run.

alter table plan_items alter column latitude drop not null;
alter table plan_items alter column longitude drop not null;

-- Either both coordinates or neither: half a location isn't a location.
alter table plan_items drop constraint if exists plan_items_location_pair;
alter table plan_items add constraint plan_items_location_pair
  check ((latitude is null) = (longitude is null));

notify pgrst, 'reload schema';
