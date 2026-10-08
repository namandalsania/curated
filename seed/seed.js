// Standalone demo-data seeder for the Curated Supabase project.
//
// NOT part of the Android app - run manually, from your machine.
// See README.md for setup and usage.
//
// Idempotent: every row gets a deterministic id derived from what it is (author,
// city, day index, ...) and is written with upsert, and every "random" choice
// comes from a seeded PRNG keyed by that same id rather than Math.random. Run it
// twice and you get the same rows, not twice as many - which is what makes it
// usable as a fixture for checking RLS on a device.
//
// Pass --reset to delete the fake users' trips first. Rows from an older,
// pre-idempotent run have random ids that nothing here will ever match, so that
// is the way to clear them out. --reset deletes EVERY trip the fake users own,
// including ones made in the app while signed in as them.
//
// Times are relative to today: finished trips were published over the last ~10
// days, the live trip is two days in. Each run re-anchors them to the day it
// runs (same day, same times), so the demo never goes stale.
//
// Notifications are not written here. The database makes them (triggers since
// 20261008_notifications_from_triggers.sql) when follows, trips, posted days and
// likes are inserted; the last step then dates each one to its content - the
// follow, the like, the trip going up, the day being posted - instead of the
// moment the seed ran.

import 'dotenv/config';
import { createClient } from '@supabase/supabase-js';
import { createHash } from 'node:crypto';
import readline from 'node:readline/promises';
import { stdin, stdout } from 'node:process';
import {
  FAKE_USERS,
  DESTINATIONS,
  BUDGET_TAGS,
  SEASON_TAGS,
  TRIP_NOUNS,
  TIPS_BY_CATEGORY,
  TIP_PROBABILITY,
  avatarUrlFor
} from './data.js';

const SEED_PASSWORD = 'Seed1234!';
const EMAIL_DOMAIN = 'curated-seed.test'; // reserved test TLD (RFC 2606) - never a real inbox
const RESET = process.argv.includes('--reset');

function requireEnv(name) {
  const value = process.env[name];
  if (!value) {
    console.error(`Missing required environment variable: ${name}`);
    console.error('Copy .env.example to .env and fill it in, or export it in your shell. See README.md.');
    process.exit(1);
  }
  return value;
}

const SUPABASE_URL = requireEnv('SUPABASE_URL');
const SERVICE_ROLE_KEY = requireEnv('SUPABASE_SERVICE_ROLE_KEY');

const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
  auth: { autoRefreshToken: false, persistSession: false }
});

// ---------- determinism ----------

function digest(parts) {
  return createHash('sha1').update(parts.join('\u0000')).digest('hex');
}

/** A stable UUID for a row, shaped like a v5 so nothing downstream blinks at it. */
function stableUuid(...parts) {
  const h = digest(parts);
  const variant = ((parseInt(h.slice(16, 18), 16) & 0x3f) | 0x80).toString(16);
  return [h.slice(0, 8), h.slice(8, 12), `5${h.slice(13, 16)}`, variant + h.slice(18, 20), h.slice(20, 32)].join('-');
}

/**
 * A small deterministic PRNG (mulberry32) keyed by the same parts as the id, so
 * a row's contents are as stable as its primary key.
 */
function rngFor(...parts) {
  let state = parseInt(digest(parts).slice(0, 8), 16) >>> 0;
  return function next() {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// ---------- small helpers, all driven by a passed-in rng ----------

function randomInt(rand, min, max) {
  return Math.floor(rand() * (max - min + 1)) + min;
}

function pickOne(rand, array) {
  return array[randomInt(rand, 0, array.length - 1)];
}

function pickN(rand, array, n) {
  // Fisher-Yates, so the order depends only on the rng.
  const shuffled = [...array];
  for (let i = shuffled.length - 1; i > 0; i -= 1) {
    const j = randomInt(rand, 0, i);
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }
  return shuffled.slice(0, Math.min(n, array.length));
}

function jitter(rand, value, maxDegrees) {
  return value + (rand() * 2 - 1) * maxDegrees;
}

function toDateOnly(date) {
  return date.toISOString().slice(0, 10);
}

function addDays(date, days) {
  const copy = new Date(date);
  copy.setUTCDate(copy.getUTCDate() + days);
  return copy;
}

/** Today at midnight UTC: an anchor, so a re-run on the same day changes nothing. */
function today() {
  const now = new Date();
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
}

/** [hours] before today's midnight-UTC anchor: always in the past, whenever the seed runs. */
function hoursAgo(hours) {
  return new Date(today().getTime() - hours * 60 * 60 * 1000);
}

/**
 * A finished trip's timeline: published 4 hours to 10 days before today's
 * anchor, having ended 0-2 days before that. Its own rng, so the titles, stops
 * and photos each trip already has don't reshuffle.
 */
function finishedTimeline(tripId, dayCount) {
  const rand = rngFor('trip-timeline', tripId);
  const completedAt = hoursAgo(randomInt(rand, 4, 240));
  const endDate = addDays(new Date(Date.UTC(
    completedAt.getUTCFullYear(), completedAt.getUTCMonth(), completedAt.getUTCDate()
  )), -randomInt(rand, 0, 2));
  const startDate = addDays(endDate, -(dayCount - 1));
  // Written up in the hours before it went public.
  const createdAt = new Date(completedAt.getTime() - randomInt(rand, 1, 6) * 60 * 60 * 1000);
  return { startDate, endDate, completedAt, createdAt };
}

// ---------- step 1: fake users ----------

async function findExistingAuthUserByEmail(email) {
  // Admin API has no direct "get by email"; page through and match.
  let page = 1;
  for (;;) {
    const { data, error } = await supabase.auth.admin.listUsers({ page, perPage: 200 });
    if (error) throw error;
    const match = data.users.find((u) => u.email === email);
    if (match) return match;
    if (data.users.length < 200) return null;
    page += 1;
  }
}

async function createFakeUsers() {
  const created = [];
  for (const profile of FAKE_USERS) {
    const email = `${profile.username}@${EMAIL_DOMAIN}`;

    let authUser;
    const { data, error } = await supabase.auth.admin.createUser({
      email,
      password: SEED_PASSWORD,
      email_confirm: true,
      user_metadata: { username: profile.username, display_name: profile.displayName }
    });

    if (error) {
      const alreadyExists =
        error.status === 422 ||
        error.code === 'email_exists' ||
        error.code === 'user_already_exists' ||
        /already registered|already exists/i.test(error.message ?? '');
      if (alreadyExists) {
        console.log(`  auth user ${email} already exists, reusing it`);
        authUser = await findExistingAuthUserByEmail(email);
        if (!authUser) throw new Error(`Could not find existing auth user for ${email}`);
      } else {
        throw error;
      }
    } else {
      authUser = data.user;
    }

    const { error: profileError } = await supabase
      .from('users')
      .upsert(
        {
          id: authUser.id,
          username: profile.username,
          display_name: profile.displayName,
          avatar_url: avatarUrlFor(profile.username),
          bio: profile.bio
        },
        { onConflict: 'id' }
      );
    if (profileError) throw profileError;

    created.push({ id: authUser.id, ...profile });
    console.log(`  ✓ ${profile.displayName} (@${profile.username})`);
  }
  return created;
}

/** Deletes the fake users' trips. Days, stops and photos go with them by cascade. */
async function resetSeedTrips(users) {
  const { error } = await supabase
    .from('trips')
    .delete()
    .in('author_id', users.map((u) => u.id));
  if (error) throw error;
  console.log('  ✓ removed existing seed trips');
}

// ---------- step 2-5: trips, days, stops, photos ----------

function pickStopTemplate(rand, destination, isFirstStopOfTrip) {
  if (isFirstStopOfTrip) {
    return { name: pickOne(rand, destination.hotels), category: 'hotel' };
  }
  const roll = rand();
  if (roll < 0.4) return { name: pickOne(rand, destination.sights), category: 'sight' };
  if (roll < 0.75) return { name: pickOne(rand, destination.food), category: 'food' };
  return pickOne(rand, destination.misc);
}

function pickImageSeed(destination, stopName, index) {
  const slug = `${destination.city}-${stopName}-${index}`.toLowerCase().replace(/[^a-z0-9]+/g, '-');
  return slug;
}

async function createStopPhotos(rand, stopId, destination, stopName, takenAtBase) {
  const count = randomInt(rand, 1, 2);
  const photoUrls = [];
  for (let i = 0; i < count; i += 1) {
    const seed = pickImageSeed(destination, stopName, i);
    const url = `https://picsum.photos/seed/${seed}/900/600`;
    const takenAt = new Date(takenAtBase.getTime() + i * 20 * 60 * 1000); // +20min apart

    const { error } = await supabase.from('stop_photos').upsert(
      {
        id: stableUuid('photo', stopId, String(i)),
        stop_id: stopId,
        storage_path: url,
        taken_at: takenAt.toISOString(),
        order_index: i
      },
      { onConflict: 'id' }
    );
    if (error) throw error;
    photoUrls.push(url);
  }
  return photoUrls;
}

/**
 * One day and its stops.
 *
 * `publish` is null for a day the author hasn't posted, or { at, via } for one
 * they have - the same two columns the app writes when a day goes public.
 * Returns the first photo url seen, so the caller can derive a cover.
 */
async function createDay(tripId, destination, startDate, dayIndex, rand, publish) {
  const dayId = stableUuid('day', tripId, String(dayIndex));

  const { error: dayError } = await supabase.from('days').upsert(
    {
      id: dayId,
      trip_id: tripId,
      day_index: dayIndex,
      date: toDateOnly(addDays(startDate, dayIndex - 1)),
      published_at: publish ? publish.at.toISOString() : null,
      published_via: publish ? publish.via : null
    },
    { onConflict: 'id' }
  );
  if (dayError) throw dayError;

  let firstPhotoUrl = null;
  const stopsThisDay = randomInt(rand, 2, 4);
  for (let orderInDay = 0; orderInDay < stopsThisDay; orderInDay += 1) {
    const isFirstStopOfTrip = dayIndex === 1 && orderInDay === 0;
    const template = pickStopTemplate(rand, destination, isFirstStopOfTrip);
    const lat = jitter(rand, destination.lat, 0.03);
    const lng = jitter(rand, destination.lng, 0.03);
    const takenAtBase = new Date(addDays(startDate, dayIndex - 1));
    takenAtBase.setUTCHours(9 + orderInDay * 3, randomInt(rand, 0, 59), 0, 0);
    // Arrival time mirrors the first photo's wall-clock time.
    const hh = String(takenAtBase.getUTCHours()).padStart(2, '0');
    const mm = String(takenAtBase.getUTCMinutes()).padStart(2, '0');

    const stopId = stableUuid('stop', dayId, String(orderInDay));
    const { error: stopError } = await supabase.from('stops').upsert(
      {
        id: stopId,
        day_id: dayId,
        trip_id: tripId,
        name: template.name,
        category: template.category,
        location: `SRID=4326;POINT(${lng} ${lat})`,
        order_in_day: orderInDay,
        caption: captionFor(template.category, template.name),
        tips: rand() < TIP_PROBABILITY ? pickOne(rand, TIPS_BY_CATEGORY[template.category]) : null,
        arrival_time: `${hh}:${mm}:00`,
        place_name: template.name
      },
      { onConflict: 'id' }
    );
    if (stopError) throw stopError;

    const photoUrls = await createStopPhotos(rand, stopId, destination, template.name, takenAtBase);
    if (firstPhotoUrl === null && photoUrls.length > 0) firstPhotoUrl = photoUrls[0];
  }

  return firstPhotoUrl;
}

async function setCover(tripId, coverUrl) {
  if (!coverUrl) return;
  const { error } = await supabase.from('trips').update({ cover_photo_url: coverUrl }).eq('id', tripId);
  if (error) throw error;
}

/** A finished trip: completed, every day public and marked 'import'. */
async function createTripForUser(user, destination, tripIndex) {
  const tripId = stableUuid('trip', user.username, destination.city, String(tripIndex));
  const rand = rngFor('trip', user.username, destination.city, String(tripIndex));

  const dayCount = randomInt(rand, 3, 6);
  // Drawn and dropped: the old start date's draw, kept so every later draw
  // from this rng - title, tags, stops, photos - stays what it was.
  randomInt(rand, 14, 300);
  const { startDate, endDate, completedAt, createdAt } = finishedTimeline(tripId, dayCount);

  const trip = {
    id: tripId,
    author_id: user.id,
    title: `${destination.city} ${pickOne(rand, TRIP_NOUNS)}`,
    destination: `${destination.city}, ${destination.name}`,
    start_date: toDateOnly(startDate),
    end_date: toDateOnly(endDate),
    budget_tag: pickOne(rand, BUDGET_TAGS),
    season_tag: pickOne(rand, SEASON_TAGS),
    status: 'completed',
    visibility: 'public',
    completed_at: completedAt.toISOString(),
    // Written explicitly: left to the column default they'd read "just now".
    created_at: createdAt.toISOString(),
    updated_at: completedAt.toISOString()
  };
  const { error: tripError } = await supabase.from('trips').upsert(trip, { onConflict: 'id' });
  if (tripError) throw tripError;

  let cover = null;
  for (let dayIndex = 1; dayIndex <= dayCount; dayIndex += 1) {
    const firstPhotoUrl = await createDay(tripId, destination, startDate, dayIndex, rand, {
      at: completedAt,
      via: 'import'
    });
    if (cover === null) cover = firstPhotoUrl;
  }
  await setCover(tripId, cover);

  return { ...trip, cover_photo_url: cover };
}

/**
 * A trip in progress: days 1-2 posted, day 3 written but not posted.
 *
 * The unposted day is the point of it - its author sees it, nobody else does,
 * and it must never reach anyone's feed.
 */
async function createLiveTripForUser(user, destination) {
  const tripId = stableUuid('live-trip', user.username, destination.city);
  const rand = rngFor('live-trip', user.username, destination.city);

  const startDate = addDays(today(), -2);
  // Started the morning of day 1, before that evening's post.
  const createdAt = new Date(startDate.getTime() + 8 * 60 * 60 * 1000);
  const trip = {
    id: tripId,
    author_id: user.id,
    title: `${destination.city} ${pickOne(rand, TRIP_NOUNS)}`,
    destination: `${destination.city}, ${destination.name}`,
    start_date: toDateOnly(startDate),
    // end_date is not nullable yet, so a live trip carries its best guess.
    end_date: toDateOnly(addDays(startDate, 4)),
    budget_tag: pickOne(rand, BUDGET_TAGS),
    season_tag: pickOne(rand, SEASON_TAGS),
    status: 'live',
    visibility: 'public',
    completed_at: null,
    created_at: createdAt.toISOString(),
    updated_at: createdAt.toISOString()
  };
  const { error } = await supabase.from('trips').upsert(trip, { onConflict: 'id' });
  if (error) throw error;

  let cover = null;
  for (let dayIndex = 1; dayIndex <= 3; dayIndex += 1) {
    // Posted that evening, so day 1 and day 2 land a day apart in the feed.
    const publish =
      dayIndex < 3
        ? { at: new Date(addDays(startDate, dayIndex - 1).getTime() + 20 * 60 * 60 * 1000), via: 'post_day' }
        : null;
    const firstPhotoUrl = await createDay(tripId, destination, startDate, dayIndex, rand, publish);
    if (dayIndex === 1) cover = firstPhotoUrl;
  }
  await setCover(tripId, cover);

  console.log(`  ✓ ${user.displayName}: "${trip.title}" is live - days 1-2 posted, day 3 held back`);
  return { ...trip, cover_photo_url: cover };
}

function captionFor(category, name) {
  switch (category) {
    case 'hotel':
      return `Checked into ${name} - comfortable base for the trip.`;
    case 'food':
      return `${name} did not disappoint. Already thinking about going back.`;
    case 'sight':
      return `Finally saw ${name} in person. Worth every bit of the hype.`;
    case 'transport':
      return `Passing through ${name} on the way to the next stop.`;
    default:
      return `Wandered through ${name} for a bit.`;
  }
}

async function createTripsForAllUsers(users) {
  const allTrips = [];
  for (const user of users) {
    const rand = rngFor('trips-for', user.username);
    const tripCount = randomInt(rand, 1, 3);
    const destinations = pickN(rand, DESTINATIONS, tripCount);
    for (const [index, destination] of destinations.entries()) {
      const trip = await createTripForUser(user, destination, index);
      allTrips.push(trip);
      console.log(`  ✓ ${user.displayName}: "${trip.title}" (${trip.start_date} to ${trip.end_date})`);
    }
  }
  return allTrips;
}

// ---------- step 6: follow graph ----------

/** When a follow happened: 1-10 days back, stable per pair. */
function followedAt(followerId, followingId) {
  return hoursAgo(randomInt(rngFor('follow-at', followerId, followingId), 24, 240));
}

async function insertFollow(followerId, followingId) {
  if (followerId === followingId) return false;
  const { error } = await supabase.from('follows').upsert(
    { follower_id: followerId, following_id: followingId, created_at: followedAt(followerId, followingId).toISOString() },
    { onConflict: 'follower_id,following_id' }
  );
  if (error) throw error;
  return true;
}

async function createFakeFollowGraph(users) {
  const edges = [];
  for (const user of users) {
    const rand = rngFor('follows', user.username);
    const others = users.filter((u) => u.id !== user.id);
    const targets = pickN(rand, others, randomInt(rand, 1, 3));
    for (const target of targets) {
      const inserted = await insertFollow(user.id, target.id);
      if (inserted) edges.push({ followerId: user.id, followingId: target.id });
    }
  }
  console.log(`  ✓ ${edges.length} fake-to-fake follows`);
  return edges;
}

async function createRealUserFollows(realUserId, users) {
  const edges = [];
  const rand = rngFor('real-follows', realUserId);
  const targets = pickN(rand, users, randomInt(rand, 2, 3));
  for (const target of targets) {
    const inserted = await insertFollow(realUserId, target.id);
    if (inserted) {
      edges.push({ followerId: realUserId, followingId: target.id });
      console.log(`  ✓ you now follow @${target.username}`);
    }
  }
  return edges;
}

// ---------- step 7: likes + notifications ----------

async function createLikes(users, trips) {
  const rows = new Map();
  const rand = rngFor('likes');
  const attempts = Math.min(20, users.length * trips.length);
  for (let i = 0; i < attempts; i += 1) {
    const user = pickOne(rand, users);
    const trip = pickOne(rand, trips);
    if (trip.author_id === user.id) continue;
    // Some hours after the trip went up, never later than the anchor.
    const after = randomInt(rngFor('like-at', user.id, trip.id), 1, 48) * 60 * 60 * 1000;
    const at = new Date(Math.min(new Date(trip.completed_at).getTime() + after, today().getTime() - 60 * 1000));
    rows.set(`${user.id}:${trip.id}`, { user_id: user.id, trip_id: trip.id, created_at: at.toISOString() });
  }
  if (rows.size === 0) return;
  const { error } = await supabase
    .from('likes')
    .upsert([...rows.values()], { onConflict: 'user_id,trip_id' });
  if (error) throw error;
  console.log(`  ✓ ${rows.size} likes`);
}

/**
 * Dates the notifications the database made for seed content to that content:
 * a follow to when it happened, a like to the like, a new trip to when it went
 * up, a new day to when it was posted. Covers notifications from earlier runs
 * too, so re-running brings them all to today's timeline.
 */
async function dateSeedNotifications(users) {
  const ids = users.map((u) => u.id);
  const { data: notifications, error } = await supabase
    .from('notifications')
    .select('id, type, recipient_id, actor_id, trip_id, day_id, created_at')
    .in('actor_id', ids)
    .in('type', ['follow', 'like', 'new_trip', 'new_day']);
  if (error) throw error;

  const [{ data: follows, error: fErr }, { data: likes, error: lErr }, { data: trips, error: tErr }] = await Promise.all([
    supabase.from('follows').select('follower_id, following_id, created_at').in('follower_id', ids),
    supabase.from('likes').select('user_id, trip_id, created_at').in('user_id', ids),
    supabase.from('trips').select('id, completed_at, created_at').in('author_id', ids)
  ]);
  if (fErr || lErr || tErr) throw fErr || lErr || tErr;
  const dayIds = notifications.filter((n) => n.day_id).map((n) => n.day_id);
  const { data: days, error: dErr } = dayIds.length
    ? await supabase.from('days').select('id, published_at').in('id', dayIds)
    : { data: [], error: null };
  if (dErr) throw dErr;

  const followAt = new Map(follows.map((f) => [`${f.follower_id}:${f.following_id}`, f.created_at]));
  const likeAt = new Map(likes.map((l) => [`${l.user_id}:${l.trip_id}`, l.created_at]));
  const tripAt = new Map(trips.map((t) => [t.id, t.completed_at ?? t.created_at]));
  const dayAt = new Map(days.map((d) => [d.id, d.published_at]));

  let changed = 0;
  for (const n of notifications) {
    const at =
      n.type === 'follow' ? followAt.get(`${n.actor_id}:${n.recipient_id}`) :
      n.type === 'like' ? likeAt.get(`${n.actor_id}:${n.trip_id}`) :
      n.type === 'new_trip' ? tripAt.get(n.trip_id) :
      dayAt.get(n.day_id);
    if (!at || new Date(at).getTime() === new Date(n.created_at).getTime()) continue;
    const { error: uErr } = await supabase.from('notifications').update({ created_at: at }).eq('id', n.id);
    if (uErr) throw uErr;
    changed += 1;
  }
  console.log(`  ✓ dated ${changed} of ${notifications.length} notifications to their content`);
}

// ---------- orchestration ----------

async function promptForRealUserId() {
  const rl = readline.createInterface({ input: stdin, output: stdout });
  const answer = await rl.question(
    '\nYour own Supabase user UUID, so your account follows a few fake users (press Enter to skip): '
  );
  rl.close();
  const trimmed = answer.trim();
  return trimmed.length > 0 ? trimmed : null;
}

async function verifyRealUserExists(userId) {
  const { data, error } = await supabase.from('users').select('id, username').eq('id', userId).maybeSingle();
  if (error) throw error;
  return data;
}

async function main() {
  console.log('Curated demo data seeder\n');

  const realUserId = await promptForRealUserId();
  let realUser = null;
  if (realUserId) {
    realUser = await verifyRealUserExists(realUserId);
    if (!realUser) {
      console.warn(
        `  ! No row in "users" for id ${realUserId} - open the app and finish sign-up/profile setup first. Skipping your follows.`
      );
    }
  }

  console.log('\n1. Creating fake users...');
  const users = await createFakeUsers();

  if (RESET) {
    console.log('\n1b. --reset: clearing existing seed trips...');
    await resetSeedTrips(users);
  }

  // Follows first: publishing a trip or posting a day notifies whoever
  // follows its author at that moment.
  console.log('\n2. Creating follow graph...');
  const fakeEdges = await createFakeFollowGraph(users);
  let realEdges = [];
  if (realUser) {
    realEdges = await createRealUserFollows(realUser.id, users);
  }
  const allEdges = [...fakeEdges, ...realEdges];

  console.log('\n3-5. Creating trips, days, stops, and photos...');
  const trips = await createTripsForAllUsers(users);

  console.log('\n5b. Creating one live trip...');
  const sofia = users.find((u) => u.username === 'wanderer_sofia') ?? users[0];
  const bangkok = DESTINATIONS.find((d) => d.city === 'Bangkok') ?? DESTINATIONS[0];
  const liveTrip = await createLiveTripForUser(sofia, bangkok);

  console.log('\n6. Creating likes...');
  // Finished trips only: a live trip isn't in anyone's Latest yet.
  await createLikes(users, trips);

  console.log('\n7. Dating notifications...');
  await dateSeedNotifications(users);

  console.log('\nDone.');
  console.log(`  Users:   ${users.length}`);
  console.log(`  Trips:   ${trips.length} completed + 1 live ("${liveTrip.title}")`);
  console.log(`  Follows: ${allEdges.length}`);
  console.log(`\nAll fake accounts share the password: ${SEED_PASSWORD}`);
  console.log('Sign in as any of them in the app using "<username>@curated-seed.test" to poke around as that user.');
  console.log('Re-running is safe: same rows, not more of them. Use --reset to clear pre-idempotent leftovers.');
}

main().catch((error) => {
  console.error('\nSeed script failed:', error);
  process.exit(1);
});
