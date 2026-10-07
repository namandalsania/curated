// The delete-account logic, with every outside call passed in so it can be
// tested without a Supabase project. index.ts wires it to the real clients.
//
// Who is deleted comes only from the caller's verified access token. The
// request body is never read, so there is no way to name another account.
//
// Order of operations, and why:
//   1. Verify the token and that the caller signed in recently.
//   2. Remove the caller's Storage files (stop photos, avatars).
//   3. Check none are left.
//   4. Delete the auth user. auth.users -> public.users cascades, and the
//      database removes or scrubs every row that references the account in
//      the same transaction (see migrations/20261006_account_deletion.sql).
//
// Files go first because the account is the only thing that ties them to the
// caller: once it's deleted, the token no longer resolves to a user, so a
// retry couldn't find or authorise them, and the owner -> auth.users
// constraint (where a project still has it) refuses the delete while files
// remain. The database delete is one transaction and the last step, so it is
// the commit point:
//   - fail in 2 or 3: some files may be gone, the account and all its rows are
//     intact; calling again lists what's left and carries on.
//   - fail in 4: files are gone, rows intact; calling again finds no files and
//     retries the delete.
//   - succeed in 4 but the response is lost: calling again gets "user not
//     found" for a token that was valid, which is answered as already deleted.

export type StorageObject = { bucket: string; name: string };

export type LookupResult =
  | { kind: "ok"; id: string; lastSignInAt: string | null }
  | { kind: "not_found" }
  | { kind: "invalid" };

export interface Deps {
  /** Verifies the access token with Supabase Auth. */
  getUser(token: string): Promise<LookupResult>;
  /** The caller's files, via public.account_storage_objects. */
  listObjects(userId: string): Promise<StorageObject[]>;
  /** Removes files from one bucket. Throws on failure. */
  removeObjects(bucket: string, names: string[]): Promise<void>;
  /** Deletes the auth user (hard delete). Throws on failure. */
  deleteUser(userId: string): Promise<void>;
  now(): Date;
  log(message: string): void;
}

/** How recent the last sign-in must be; the app asks for the password just before. */
export const MAX_SIGN_IN_AGE_MS = 10 * 60 * 1000;
/** Storage's remove() takes a list; keep each call modest. */
export const REMOVE_BATCH_SIZE = 100;

export async function handleDeleteAccount(req: Request, deps: Deps): Promise<Response> {
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });

  const token = bearerToken(req);
  if (!token) return json(401, { error: "not_signed_in" });

  const caller = await deps.getUser(token);
  if (caller.kind === "invalid") return json(401, { error: "not_signed_in" });
  if (caller.kind === "not_found") {
    // A token that verified but whose user is gone: an earlier call finished.
    return json(200, { status: "already_deleted" });
  }

  if (!isRecent(caller.lastSignInAt, deps.now())) {
    return json(403, { error: "reauthentication_required" });
  }

  const userId = caller.id;
  try {
    await removeAllFiles(userId, deps);
  } catch (e) {
    deps.log(`delete-account ${userId}: storage step failed: ${describe(e)}`);
    return json(500, { error: "storage_failed", retryable: true });
  }

  try {
    await deps.deleteUser(userId);
  } catch (e) {
    deps.log(`delete-account ${userId}: delete step failed: ${describe(e)}`);
    return json(500, { error: "delete_failed", retryable: true });
  }

  deps.log(`delete-account ${userId}: deleted`);
  return json(200, { status: "deleted" });
}

async function removeAllFiles(userId: string, deps: Deps): Promise<void> {
  const objects = await deps.listObjects(userId);
  const byBucket = new Map<string, string[]>();
  for (const o of objects) {
    byBucket.set(o.bucket, [...(byBucket.get(o.bucket) ?? []), o.name]);
  }
  for (const [bucket, names] of byBucket) {
    for (let i = 0; i < names.length; i += REMOVE_BATCH_SIZE) {
      await deps.removeObjects(bucket, names.slice(i, i + REMOVE_BATCH_SIZE));
    }
  }
  // Storage can report success and keep a file (a name it didn't match, say).
  // Never delete the account - the only way back to its files - with files left.
  const left = await deps.listObjects(userId);
  if (left.length > 0) throw new Error(`${left.length} file(s) still present after removal`);
}

export function isRecent(lastSignInAt: string | null, now: Date): boolean {
  if (!lastSignInAt) return false;
  const at = Date.parse(lastSignInAt);
  if (Number.isNaN(at)) return false;
  const age = now.getTime() - at;
  return age >= -60_000 && age <= MAX_SIGN_IN_AGE_MS; // a minute of clock skew either way
}

function bearerToken(req: Request): string | null {
  const header = req.headers.get("Authorization") ?? "";
  const match = /^Bearer\s+(.+)$/i.exec(header.trim());
  return match ? match[1] : null;
}

function describe(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
