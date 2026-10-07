# delete-account

Deletes the signed-in caller's account: their Storage files, then the auth
user, which cascades to every row that references them.

- **Who** is deleted comes only from the verified access token in the
  `Authorization` header. The request body is never read, so a request cannot
  name another account.
- The caller must have signed in within the last 10 minutes. The app asks for
  the password again just before calling, which refreshes that.
- The service role key is only ever read from the function's environment.

## Before deploying

Apply `Supabase/migrations/20261006_account_deletion.sql` in the SQL editor
first, then run `Supabase/tests/account_deletion.sql` (it ends in ROLLBACK and
should print 9 PASS notices). The function needs
`public.account_storage_objects`, which that migration creates; without it,
every call fails at the Storage step, so nothing gets deleted.

## Deploy

From the repository root, with the [Supabase CLI](https://supabase.com/docs/guides/cli)
installed and logged in (`supabase login`):

```sh
supabase functions deploy delete-account --project-ref <your-project-ref>
```

The project ref is the subdomain of your Supabase URL. The CLI looks for
`supabase/functions/delete-account`; this repository's folder is `Supabase/`
(capital S), which matches on Windows and macOS. On a case-sensitive file
system, run the command from a checkout where the folder is lower-case, or
rename it.

Leave JWT verification on (the default): the gateway then refuses requests
without a valid token before the function runs. Do not pass `--no-verify-jwt`.

### Secrets

None to set. The function reads two variables that Supabase provides to every
Edge Function automatically:

| Name | What |
|---|---|
| `SUPABASE_URL` | the project URL |
| `SUPABASE_SERVICE_ROLE_KEY` | the service role key |

Never put the service role key in the app, a commit, or a request.

### Check it after deploying

With a throwaway account signed in on a debug build, use Settings → Delete
account. Or call it directly with that account's access token:

```sh
curl -X POST "https://<your-project-ref>.supabase.co/functions/v1/delete-account" \
  -H "Authorization: Bearer <throwaway account's access token>"
```

`{"status":"deleted"}` is success. Never test with a real or seed account.

## Order of operations, and why

1. **Verify** the token and that the sign-in is recent. Nothing is touched if
   either fails.
2. **Remove the caller's files** from the `stop-photos` and `avatars` buckets,
   as listed by `public.account_storage_objects`: files they uploaded, avatars
   named `<user id>-…`, and photos under their trips' folders. This includes
   photos of trips they deleted earlier, which the app leaves in Storage.
3. **Check no files are left.** Storage can report success and keep a file;
   the account is never deleted with files remaining.
4. **Delete the auth user.** One database transaction: `auth.users` cascades
   to `public.users` and from there to every referencing row (table below).
   This is the commit point.

Files go first because the account is the only link between them and the
caller. Once it's gone the token no longer resolves to a user, so a retry
couldn't find or authorise them. On projects where `storage.objects.owner`
still references `auth.users`, the database also refuses the delete while
files remain.

**Retrying is always safe:**

- *Fails in step 2 or 3:* some files may be gone; the account and all its rows
  are intact. Calling again lists what's left and carries on.
- *Fails in step 4:* the files are gone and the rows are intact. Calling again
  finds no files and retries the delete.
- *Succeeds, but the response is lost:* calling again with the same token gets
  "user not found" from Auth, which is answered `{"status":"already_deleted"}`.

Responses: `200 deleted | already_deleted`, `401 not_signed_in`,
`403 reauthentication_required`, `405`, `500 storage_failed | delete_failed`
(both with `"retryable": true`).

## What happens to each table

Every foreign key in `public`, as of `20261006_account_deletion.sql`. "Via
trips" means the row goes when the user's trip is deleted by the cascade.

| Table.column | References | On delete | Result for a deleted user |
|---|---|---|---|
| `users.id` | `auth.users` | CASCADE | profile deleted |
| `trips.author_id` | `users` | CASCADE | their trips deleted |
| `days.trip_id`, `stops.trip_id` | `trips` | CASCADE | via trips |
| `stop_photos.stop_id` | `stops` | CASCADE | via trips; files removed in step 2 |
| `stop_comments.author_id` | `users` | CASCADE | their comments deleted |
| `stop_comments.trip_id`, `.stop_id` | `trips`, `stops` | CASCADE | others' comments on their trips deleted |
| `likes.user_id`, `bookmarks.user_id` | `users` | CASCADE | their likes and saves deleted |
| `likes.trip_id`, `bookmarks.trip_id` | `trips` | CASCADE | others' likes and saves of their trips deleted |
| `follows.follower_id`, `.following_id` | `users` | CASCADE | follows both ways deleted |
| `notifications.recipient_id` | `users` | CASCADE | their notifications deleted |
| `notifications.actor_id` | `users` | **CASCADE** (was SET NULL) | notifications they caused deleted, not left as "Someone…" |
| `notifications.trip_id`, `.stop_id`, `.plan_id` | `trips`, `stops`, `plans` | CASCADE | notifications about their trips and plans deleted |
| `trip_shares.sender_id`, `.recipient_id` | `users` | CASCADE | shares sent or received deleted |
| `trip_shares.trip_id` | `trips` | CASCADE | shares of their trips deleted |
| `saved_places.user_id` | `users` | CASCADE | their saved places deleted |
| `saved_places.stop_id`, `.source_trip_id` | `stops`, `trips` | SET NULL | others' saves of their stops keep the place; **name, trip title and photo are cleared** by a trigger |
| `plans.user_id` | `users` | CASCADE | their plans deleted, with members and items |
| `plan_members.user_id` | `users` | CASCADE | their memberships of others' plans deleted |
| `plan_members.invited_by` | `users` | SET NULL | people they invited stay in those plans |
| `plan_items.added_by` | `users` | SET NULL | places they added to others' plans stay (the plan owner's) with no "added by" |
| `plan_items.stop_id`, `.source_trip_id` | `stops`, `trips` | SET NULL | as saved places: **name, trip title and photo cleared** |
| `reports.reporter_id` | `users` | CASCADE | reports they filed deleted |
| `reports.target_id` | (no FK) | trigger | reports **about** their profile, trips, stops or comments stay, with `target_deleted_at` set |
| `blocks.blocker_id`, `.blocked_id` | `users` | CASCADE | blocks both ways deleted |

Nothing references `users` with RESTRICT or NO ACTION, so nothing blocks the
delete. (`stops (trip_id, day_id) → days` is NO ACTION, but both sides go in
the same cascade.)

**Known gap:** a saved place whose source trip the author had *already*
deleted has `source_trip_id` null, so it can't be linked back; it keeps the
author name copied at save time.
