# Reporting and blocking

Schema: `Supabase/migrations/20261005_reports_and_blocks.sql`. RLS tests:
`Supabase/tests/reports_blocks_rls.sql`.

## What a block does

Enforced by the database (both directions, through `private.is_blocked_pair`):

- Trips are hidden, and with them their days, stops, photos and comments.
- Comments between the two are hidden, including on your own trips.
- Existing follows between the two are removed, and new ones are refused.
- Notifications and trip shares between the two are hidden and can't be created.

Done by the app as a convenience (the database already refuses the actions):

- Blocked accounts are left out of follower and following lists.
- A blocked profile opened another way shows "You've blocked this account"
  with Unblock, and no Follow button.
- A follow refused because of a block shows the same neutral
  "Couldn't follow this account." as any other failure.

## Known limitations

- `users` and `follows` stay readable by everyone, by design. So a blocked
  user can still open the blocker's profile header - name, bio, avatar and
  follower/following counts - for example through someone else's follower
  list. They can't see the blocker's trips, and can't follow, comment on or
  share to them.
- The blocker's side is filtered in the app only for follower/following
  lists and the profile screen. People search in the Share sheet and plan
  invites doesn't filter blocked accounts yet; sharing to one is refused by
  the database, plan invites are not covered by blocks at all.
