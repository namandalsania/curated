-- Likes and bookmarks only for trips you can see.
--
-- Until now:
--   likes:     anyone could read every like (who liked what, including likes on
--              private, draft and blocked trips), and a signed-in user could
--              like any trip id they knew - private, a draft, or a blocked
--              author's - which also sent its author a notification.
--   bookmarks: rows were already private to their owner, but you could
--              bookmark a trip you couldn't see.
--
-- After this, both go through private.trip_is_visible() - the same rule as the
-- trip itself (author, or public/unlisted and published/posted, and no block
-- either way):
--   likes select:     likes on trips you can see, plus your own likes
--   likes insert:     your own, on a trip you can see
--   bookmarks insert: your own, on a trip you can see
-- Delete policies are unchanged: you can always remove your own like or
-- bookmark, even on a trip that has since become hidden from you.
--
-- Safe to re-run.

begin;

drop policy if exists "likes_select_all" on public.likes;
drop policy if exists "likes_select_visible" on public.likes;
create policy "likes_select_visible" on public.likes for select
  using (
    user_id = (select auth.uid())
    or private.trip_is_visible(trip_id)
  );

drop policy if exists "likes_insert_own" on public.likes;
create policy "likes_insert_own" on public.likes for insert
  with check (
    user_id = (select auth.uid())
    and private.trip_is_visible(trip_id)
  );

drop policy if exists "bookmarks_insert_own" on public.bookmarks;
create policy "bookmarks_insert_own" on public.bookmarks for insert
  with check (
    user_id = (select auth.uid())
    and private.trip_is_visible(trip_id)
  );

notify pgrst, 'reload schema';

commit;
