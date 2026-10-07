# Phase B — device test checklist

Live trips: start, post a day, edit a posted day, end. Run on the `Pixel_10`
emulator with two accounts: **A** (your own) and **B**, a seeded account
(`<username>@curated-seed.test` / `Seed1234!`) that follows A — or have A
follow nobody and use B only as "someone else".

Before starting: `npm run seed -- --reset` in `seed/`, then build and install.

## 1. Start a live trip
- [ ] Create → fill title + destination, leave dates empty → **I'm on this trip now** is enabled, hint says "starting today".
- [ ] Pick a start date in the future → the button disables and the hint turns red.
- [ ] Pick today (or a past date) → tap it → lands on the live screen with a **LIVE** tag and "Day N" in the subtitle.
- [ ] Back → Profile shows the trip under **Live now** (no delete icon). Tapping **Open** returns to it.
- [ ] As **B**: the trip is not in Home, Explore, search, or A's profile grid.

## 2. Post Day 1
- [ ] Day 1 card says **Not posted**. Tap **Add Day 1** → Post Day screen.
- [ ] Tap **Post Day 1** with no places → inline error "Add at least one place before posting this day." Nothing is posted.
- [ ] **Add photos from this day** → pick some photos from that date *and* at least one from another date.
  - [ ] Stops appear, named by reverse geocode.
  - [ ] The summary says how many photos were skipped as "taken on other days".
  - [ ] Photos without GPS are reported as "had no location".
- [ ] **Add a place** manually → it appears on the day.
- [ ] Edit a stop (name, category, caption) → saves.
- [ ] Reorder stops with the arrows → the order sticks after leaving and returning.
- [ ] **Post Day 1** → spinner on the button, then back to the live screen; Day 1 now shows **Posted**.
- [ ] As **B**: open the trip by link / from Supabase id → Day 1 and its stops/photos are visible; Day 2 is not. (The feed items themselves are phase C.)

## 3. Edit a posted day
- [ ] Tap **Edit Day 1** → the screen shows the **Posted** tag, a "Changes save as you make them" note, and **Done** instead of Post.
- [ ] Change a caption, add a place → as **B**, the change is visible on refresh.
- [ ] In Supabase, `days.published_at` for Day 1 is unchanged by the edits.

## 4. Unassigned stops (validation)
- [ ] In Supabase, set one of the trip's stops `day_id = null`.
- [ ] Reopen the live trip → a red **Not on a day** card lists it. Tapping it offers **Choose a day**.
- [ ] Move it onto a day → the card disappears.

## 5. End trip
- [ ] Leave Day 2 with places but unposted, and Day 3 empty.
- [ ] **End trip** → sheet says "1 unposted day will be published with this trip (Day 2)." plus the note that empty days are left out.
- [ ] **Review days first** closes the sheet without ending.
- [ ] **End trip** again → spinner → lands on Trip Detail for the completed trip.
- [ ] Supabase: `trips.status = 'completed'`, `completed_at` set, `end_date` = Day 2's date, cover set (and not the hotel photo if another stop had one).
- [ ] Supabase: Day 2 `published_via = 'end_trip'`; Day 1 still `post_day` with its original `published_at`; Day 3 still unpublished.
- [ ] As **B**: the trip now appears in Explore and search, and on A's profile. B got one "new trip" notification.
- [ ] A's Profile no longer lists it under **Live now**; it's in the trips grid.

## 6. Import flow (regression + the 'import' fix)
- [ ] Create → Write it myself / Start from photos → publish as before.
- [ ] Supabase: every day of that trip has `published_via = 'import'` and `published_at` set.
- [ ] As **B**: the trip's stops and photos are visible in Trip Detail. (Before this change they weren't — the days were left unpublished.)

## 7. Visibility (step 1)
- [ ] In Supabase set one of A's completed trips to `visibility = 'unlisted'`, another to `'private'`.
- [ ] As **A**: both still in A's profile grid, labelled **Unlisted** / **Private**.
- [ ] As **B**: neither appears on A's profile, Home, Explore or search. The unlisted one still opens by link; the private one doesn't.
