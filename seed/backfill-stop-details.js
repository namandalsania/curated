// One-off backfill for stops seeded before stops.arrival_time existed and
// before the seeder wrote tips. Only fills NULLs - never overwrites anything a
// user entered - so it's safe to re-run.
//
// Requires Supabase/migrations/20260916_stop_arrival_time.sql to be applied.
// Usage: node backfill-stop-details.js

import 'dotenv/config';
import { createClient } from '@supabase/supabase-js';
import { TIPS_BY_CATEGORY, TIP_PROBABILITY } from './data.js';

const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_ROLE_KEY, {
  auth: { persistSession: false }
});

// Deterministic per stop, so re-runs pick the same tip/decision.
function hash(text) {
  let h = 0;
  for (const ch of text) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return h;
}

const { data: stops, error } = await supabase
  .from('stops')
  .select('id,category,order_in_day,tips,arrival_time,stop_photos(taken_at,order_index)');
if (error) throw error;

let timesSet = 0;
let tipsSet = 0;
for (const stop of stops) {
  const patch = {};

  if (stop.arrival_time === null) {
    const firstPhoto = [...stop.stop_photos].sort((a, b) => a.order_index - b.order_index)[0];
    const at = firstPhoto?.taken_at ? new Date(firstPhoto.taken_at) : null;
    const hours = at ? at.getUTCHours() : 9 + stop.order_in_day * 3;
    const minutes = at ? at.getUTCMinutes() : hash(stop.id) % 60;
    patch.arrival_time = `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:00`;
  }

  if (stop.tips === null && (hash(stop.id) % 100) / 100 < TIP_PROBABILITY) {
    const pool = TIPS_BY_CATEGORY[stop.category] ?? TIPS_BY_CATEGORY.other;
    patch.tips = pool[hash(`${stop.id}-tip`) % pool.length];
  }

  if (Object.keys(patch).length === 0) continue;
  const { error: updateError } = await supabase.from('stops').update(patch).eq('id', stop.id);
  if (updateError) throw updateError;
  if (patch.arrival_time) timesSet += 1;
  if (patch.tips) tipsSet += 1;
}

console.log(`Checked ${stops.length} stops: set ${timesSet} arrival times, ${tipsSet} tips.`);
