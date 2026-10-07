// deno test Supabase/functions/delete-account/handler_test.ts

import { assertEquals } from "jsr:@std/assert@1";
import { type Deps, handleDeleteAccount, isRecent, type LookupResult, type StorageObject } from "./handler.ts";

const NOW = new Date("2026-10-06T12:00:00Z");
const ME = "11111111-1111-1111-1111-111111111111";
const SOMEONE_ELSE = "22222222-2222-2222-2222-222222222222";

/** An in-memory project: users, their files, and switches to make steps fail. */
class FakeProject {
  users = new Map<string, { lastSignInAt: string | null }>();
  files: (StorageObject & { owner: string })[] = [];
  tokens = new Map<string, string>(); // token -> user id
  failRemoveOnce = false;
  failDeleteOnce = false;
  removeKeepsFiles = false;
  removeCalls: { bucket: string; count: number }[] = [];
  deleteCalls: string[] = [];

  addUser(id: string, minutesSinceSignIn: number | null, files: StorageObject[] = []) {
    this.users.set(id, {
      lastSignInAt: minutesSinceSignIn === null ? null : new Date(NOW.getTime() - minutesSinceSignIn * 60_000).toISOString(),
    });
    this.tokens.set(`token-${id}`, id);
    for (const f of files) this.files.push({ ...f, owner: id });
  }

  deps(): Deps {
    return {
      getUser: (token): Promise<LookupResult> => {
        const id = this.tokens.get(token);
        if (!id) return Promise.resolve({ kind: "invalid" });
        const user = this.users.get(id);
        if (!user) return Promise.resolve({ kind: "not_found" });
        return Promise.resolve({ kind: "ok", id, lastSignInAt: user.lastSignInAt });
      },
      listObjects: (userId) =>
        Promise.resolve(this.files.filter((f) => f.owner === userId).map(({ bucket, name }) => ({ bucket, name }))),
      removeObjects: (bucket, names) => {
        this.removeCalls.push({ bucket, count: names.length });
        if (this.failRemoveOnce) {
          this.failRemoveOnce = false;
          // Half the batch goes before the failure.
          const gone = new Set(names.slice(0, Math.ceil(names.length / 2)));
          this.files = this.files.filter((f) => !(f.bucket === bucket && gone.has(f.name)));
          return Promise.reject(new Error("storage unavailable"));
        }
        if (!this.removeKeepsFiles) {
          const gone = new Set(names);
          this.files = this.files.filter((f) => !(f.bucket === bucket && gone.has(f.name)));
        }
        return Promise.resolve();
      },
      deleteUser: (userId) => {
        this.deleteCalls.push(userId);
        if (this.failDeleteOnce) {
          this.failDeleteOnce = false;
          return Promise.reject(new Error("database unavailable"));
        }
        this.users.delete(userId);
        return Promise.resolve();
      },
      now: () => NOW,
      log: () => {},
    };
  }
}

function request(token: string | null, body?: unknown, method = "POST"): Request {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return new Request("http://localhost/delete-account", {
    method,
    headers,
    body: method === "POST" && body !== undefined ? JSON.stringify(body) : undefined,
  });
}

async function call(project: FakeProject, req: Request) {
  const res = await handleDeleteAccount(req, project.deps());
  return { status: res.status, body: await res.json() };
}

const myFiles: StorageObject[] = [
  { bucket: "avatars", name: `${ME}-1.jpg` },
  { bucket: "stop-photos", name: "trip-a/stop-1/0.jpg" },
  { bucket: "stop-photos", name: "trip-a/stop-1/1.jpg" },
];

Deno.test("deletes the caller's files, then the caller", async () => {
  const p = new FakeProject();
  p.addUser(ME, 2, myFiles);
  p.addUser(SOMEONE_ELSE, 2, [{ bucket: "avatars", name: `${SOMEONE_ELSE}-1.jpg` }]);

  const r = await call(p, request(`token-${ME}`));

  assertEquals(r, { status: 200, body: { status: "deleted" } });
  assertEquals(p.users.has(ME), false);
  assertEquals(p.files.filter((f) => f.owner === ME), []);
  assertEquals(p.users.has(SOMEONE_ELSE), true);
  assertEquals(p.files.filter((f) => f.owner === SOMEONE_ELSE).length, 1);
});

Deno.test("a user id in the body is ignored: only the caller is deleted", async () => {
  const p = new FakeProject();
  p.addUser(ME, 2);
  p.addUser(SOMEONE_ELSE, 2);

  await call(p, request(`token-${ME}`, { user_id: SOMEONE_ELSE, id: SOMEONE_ELSE, userId: SOMEONE_ELSE }));

  assertEquals(p.deleteCalls, [ME]);
  assertEquals(p.users.has(SOMEONE_ELSE), true);
});

Deno.test("no token, or a token that doesn't verify, deletes nothing", async () => {
  const p = new FakeProject();
  p.addUser(ME, 2, myFiles);

  assertEquals((await call(p, request(null))).status, 401);
  assertEquals((await call(p, request("forged-token"))).status, 401);
  assertEquals(p.deleteCalls, []);
  assertEquals(p.removeCalls, []);
});

Deno.test("an old sign-in is refused before anything is touched", async () => {
  const p = new FakeProject();
  p.addUser(ME, 45, myFiles);

  const r = await call(p, request(`token-${ME}`));

  assertEquals(r, { status: 403, body: { error: "reauthentication_required" } });
  assertEquals(p.removeCalls, []);
  assertEquals(p.deleteCalls, []);
});

Deno.test("only POST", async () => {
  const p = new FakeProject();
  p.addUser(ME, 2);
  assertEquals((await call(p, request(`token-${ME}`, undefined, "GET"))).status, 405);
  assertEquals(p.deleteCalls, []);
});

Deno.test("storage fails halfway: the account stays, and a retry finishes the job", async () => {
  const p = new FakeProject();
  p.addUser(ME, 2, myFiles);
  p.failRemoveOnce = true;

  const first = await call(p, request(`token-${ME}`));
  assertEquals(first, { status: 500, body: { error: "storage_failed", retryable: true } });
  assertEquals(p.users.has(ME), true);
  assertEquals(p.deleteCalls, []);

  const second = await call(p, request(`token-${ME}`));
  assertEquals(second, { status: 200, body: { status: "deleted" } });
  assertEquals(p.files, []);
  assertEquals(p.users.has(ME), false);
});

Deno.test("the database step fails after files are gone: a retry finishes the job", async () => {
  const p = new FakeProject();
  p.addUser(ME, 2, myFiles);
  p.failDeleteOnce = true;

  const first = await call(p, request(`token-${ME}`));
  assertEquals(first, { status: 500, body: { error: "delete_failed", retryable: true } });
  assertEquals(p.files, []);
  assertEquals(p.users.has(ME), true);

  const second = await call(p, request(`token-${ME}`));
  assertEquals(second, { status: 200, body: { status: "deleted" } });
  assertEquals(p.users.has(ME), false);
});

Deno.test("calling again after it succeeded answers already_deleted", async () => {
  const p = new FakeProject();
  p.addUser(ME, 2, myFiles);
  await call(p, request(`token-${ME}`));

  const again = await call(p, request(`token-${ME}`));
  assertEquals(again, { status: 200, body: { status: "already_deleted" } });
  assertEquals(p.deleteCalls, [ME]);
});

Deno.test("files that survive removal stop the account being deleted", async () => {
  const p = new FakeProject();
  p.addUser(ME, 2, myFiles);
  p.removeKeepsFiles = true;

  const r = await call(p, request(`token-${ME}`));
  assertEquals(r.status, 500);
  assertEquals(p.deleteCalls, []);
  assertEquals(p.users.has(ME), true);
});

Deno.test("many files are removed in batches of 100 per bucket", async () => {
  const p = new FakeProject();
  const lots: StorageObject[] = Array.from({ length: 250 }, (_, i) => ({ bucket: "stop-photos", name: `t/s/${i}.jpg` }));
  p.addUser(ME, 2, [...lots, { bucket: "avatars", name: `${ME}-1.jpg` }]);

  await call(p, request(`token-${ME}`));

  assertEquals(p.removeCalls, [
    { bucket: "stop-photos", count: 100 },
    { bucket: "stop-photos", count: 100 },
    { bucket: "stop-photos", count: 50 },
    { bucket: "avatars", count: 1 },
  ]);
});

Deno.test("sign-in recency", () => {
  const ago = (min: number) => new Date(NOW.getTime() - min * 60_000).toISOString();
  assertEquals(isRecent(ago(0), NOW), true);
  assertEquals(isRecent(ago(9), NOW), true);
  assertEquals(isRecent(ago(11), NOW), false);
  assertEquals(isRecent(null, NOW), false);
  assertEquals(isRecent("not a date", NOW), false);
  assertEquals(isRecent(ago(-30), NOW), false); // far in the future
});
