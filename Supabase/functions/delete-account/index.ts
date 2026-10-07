// Edge Function: deletes the signed-in caller's account. See handler.ts for the
// order of operations and README.md for deploying.
//
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are provided to every Edge
// Function by Supabase. The service role key never leaves this function.

import { createClient } from "npm:@supabase/supabase-js@2";
import { type Deps, handleDeleteAccount, type LookupResult, type StorageObject } from "./handler.ts";

const url = Deno.env.get("SUPABASE_URL");
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
if (!url || !serviceRoleKey) throw new Error("SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY must be set");

const admin = createClient(url, serviceRoleKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});

const deps: Deps = {
  async getUser(token: string): Promise<LookupResult> {
    const { data, error } = await admin.auth.getUser(token);
    if (error) {
      // A valid token for a user that no longer exists.
      if (error.code === "user_not_found") return { kind: "not_found" };
      return { kind: "invalid" };
    }
    if (!data.user) return { kind: "invalid" };
    return { kind: "ok", id: data.user.id, lastSignInAt: data.user.last_sign_in_at ?? null };
  },

  async listObjects(userId: string): Promise<StorageObject[]> {
    const { data, error } = await admin.rpc("account_storage_objects", { p_user: userId });
    if (error) throw new Error(`account_storage_objects: ${error.message}`);
    return (data as { bucket_id: string; name: string }[]).map((r) => ({ bucket: r.bucket_id, name: r.name }));
  },

  async removeObjects(bucket: string, names: string[]): Promise<void> {
    const { error } = await admin.storage.from(bucket).remove(names);
    if (error) throw new Error(`remove from ${bucket}: ${error.message}`);
  },

  async deleteUser(userId: string): Promise<void> {
    const { error } = await admin.auth.admin.deleteUser(userId, false);
    if (error) throw new Error(`deleteUser: ${error.message}`);
  },

  now: () => new Date(),
  log: (message: string) => console.log(message),
};

Deno.serve((req) => handleDeleteAccount(req, deps));
