# Quickstart: Validating Debug Build Server Isolation

## Prerequisites

- A local `zun-rust-server` instance runnable on the same machine/network as the test device (see that repo's `just setup && just run`, default port `8080`).
- A debug-build install with **no prior server configured** — uninstall any existing debug build first (`adb uninstall dev.zun.flux.debug`), since this feature ships with no automatic migration for pre-existing configurations (FR-007).
- A release-build install, for the regression checks in Story 3.

## Automated checks

```bash
./gradlew :app:testDebugUnitTest --tests "dev.zun.flux.util.ServerUrlsTest"
```

Expected: all existing cases plus the new production-hostname-reject cases (see `research.md` → Testing approach) pass.

## Manual validation

1. **Fresh debug install defaults to local (Story 1 / SC-001)**
   Install the debug build fresh, launch it, and open the Setup screen. Confirm the server URL field is already pre-filled with a local development address (not blank, not `zun.h.doremysweet.com`), per `data-model.md`.

2. **Local server unreachable is distinguishable (Story 1, edge case)**
   With the local `zun-rust-server` stopped, attempt to continue past Setup with the pre-filled address. Confirm the error clearly indicates the local server can't be reached, and that no fallback to production occurs.

3. **Debug build rejects the production hostname (Story 2 / SC-002, SC-003)**
   In **both** Setup and Settings → Connection (on a debug build with a valid local address already saved), type `https://zun.h.doremysweet.com` and attempt to save. Confirm:
   - The save is rejected.
   - The on-screen message is the actual specific reason ("This is the production server — use your local dev server instead."), **not** the generic "Couldn't connect: unknown error" fallback — check this in the Setup screen specifically, since it's the one that routes the message through `toUserMessage()`'s 80-character cutoff (`ErrorMessages.kt`).
   - The previously saved valid address (if any) is unchanged.
   Repeat with `http://zun.h.doremysweet.com`, a different port, and mixed case — all must be rejected the same way.

4. **Debug build accepts any other address (Story 2, edge case)**
   In the same debug build, enter a different non-production address (e.g. a different LAN host, or a friend's remote test server). Confirm it saves normally with no extra restriction.

5. **Release build is unaffected (Story 3 / SC-004)**
   In the release build, enter `https://zun.h.doremysweet.com` in Setup/Settings → Connection. Confirm it is accepted and the app connects exactly as before this feature. Also confirm entering an arbitrary other address still works as before.

6. **Debug/release isolation holds (edge case)**
   With both builds installed side by side, confirm each keeps its own independently stored server URL/token — changing one never affects the other (already true via separate app IDs; this is a regression check, not new behavior).
