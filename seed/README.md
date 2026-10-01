# Curated demo data seeder

A standalone Node.js script that populates your Supabase project with
realistic demo data — fake users, completed and live trips, days, stops,
photos, follows, likes, and notifications — so Home, Explore, and Profile
have real content to render instead of empty states.

It is idempotent: every row's id is derived from what the row *is*, and every
random-looking choice comes from a PRNG seeded by that same id. Run it twice
and you get the same rows, not twice as many.

**This is not part of the Android app.** It talks directly to your Supabase
project over the network using the Admin API and the `service_role` key. It
is meant to be run manually, from your own machine — never from the app,
never in CI, never with the key committed anywhere.

## What it creates

- 6 fake users (real `auth.users` rows via the Admin API + matching `users`
  profile rows), with pravatar.cc avatars and short bios
- 1–3 completed trips per user, across Italy, Japan, Thailand, Portugal, and
  Mexico, with realistic titles, dates, `budget_tag`, and `season_tag`
- one **live** trip (Sofia Almeida, Bangkok) with days 1–2 posted and day 3
  written but held back — the fixture for checking that unposted days stay
  private to their author
- 3–6 days per trip, 2–4 stops per day, with real-sounding place names,
  categories, captions, and coordinates jittered around the actual city
  (not random points on the globe)
- 1–2 photos per stop from picsum.photos (deterministic per stop, no API key
  needed), with `trips.cover_photo_url` derived from the first stop's first
  photo, matching the app's own publish logic
- A partial follow graph between the fake users (not a complete graph), plus
  — if you provide your own user UUID when prompted — your account follows
  2–3 of the fake users so your Home feed has content immediately
- A handful of likes and follow/new-trip notifications for realism

## Setup

1. `cd seed`
2. `npm install`
3. Copy `.env.example` to `.env` and fill in:
   - `SUPABASE_URL` — your project URL (Project Settings → API)
   - `SUPABASE_SERVICE_ROLE_KEY` — the **secret / `service_role`** key from
     the same page. **Not** the publishable/anon key used in the Android
     app — this key bypasses Row Level Security entirely, so treat it like a
     password. `.env` is already gitignored; never commit it, never paste it
     into the app, never send it anywhere client-side.

## Running it

```
npm run seed
```

It will prompt for your own Supabase user UUID (find it in Supabase →
Authentication → Users, or via `select id from auth.users where email =
'you@example.com'` in the SQL editor). Press Enter to skip if you'd rather
not wire up your own account's follows — everything else still gets seeded.

Takes a minute or two — it's making a real network request per row, on
purpose, so you can watch it in the Supabase dashboard as it goes.

All fake accounts share the password `Seed1234!`, with emails like
`wanderlust_maya@curated-seed.test` (a reserved test domain, never real
inboxes) — you can sign into the app as any of them if you want to test
"as" a seeded user.

## Re-running

Safe. Every row is upserted under a deterministic id, so a second run
converges to the same data rather than duplicating it. That also means it
resets anything you changed by hand on the device back to a known state,
which is what makes it usable as a fixture for RLS checks.

Timestamps are seeded too, never left to the database's `now()`: a finished
trip's `created_at`, `completed_at` and its days' `published_at` land 1–10
days after its end date, and the live trip's days 1 and 2 were posted at 20:00
UTC on their own dates. So nothing reads "just now" after a `--reset`.

Dates are anchored to today at midnight UTC, so re-running on the same day
changes nothing at all; run it tomorrow and trip dates shift by a day while
row counts stay put.

```
npm run seed -- --reset
```

`--reset` deletes the fake users' trips before seeding (days, stops and
photos follow by cascade). You need it once if your project still holds rows
from an older, pre-idempotent run: those have random ids that nothing here
will ever match, so they would otherwise sit alongside the new data forever.

## Why this lives outside `android/`

This script has nothing to do with the app's runtime — it's a one-time data
fixture for local development and demos, run from a developer's machine with
a highly privileged key that must never ship inside the app or touch a CI
pipeline. Keeping it in its own `seed/` folder with its own
`package.json`/`.env` makes that boundary obvious.
