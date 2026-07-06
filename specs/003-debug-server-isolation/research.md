# Phase 0 Research: Debug Build Server Isolation

## Current-state findings

- No hardcoded production URL exists anywhere in the app today. Server URL is entered at runtime via `SetupScreen.kt` (first run) or `SettingsScreen.kt`/`SettingsViewModel.kt` (later), stored as plain text in `SharedPreferences("settings")` by `SettingsManager.kt` (`app/src/main/java/dev/zun/flux/data/repo/SettingsManager.kt:38-40`). `SettingsManager` itself does zero validation — it's a bare passthrough.
- Both UI entry points funnel through a single validation function: `normalizeOptionalServerUrl(raw, allowHttp: Boolean)` in `app/src/main/java/dev/zun/flux/util/ServerUrls.kt` (37 lines total). This is the one existing choke point for URL rules (already enforces "release requires https") and is the natural place to add a production-hostname reject rule rather than duplicating a check in both screens.
- `BuildConfig.DEBUG` is already imported and used in both call sites (`SetupScreen.kt:39`, `SettingsViewModel.kt:6`) purely to compute `allowHttp`. No `buildConfigField` for a server URL exists today; the only current field is `SENTRY_DSN` (`app/build.gradle.kts:100`).
- No product flavors exist — only `debug`/`release` build types, distinguished by `applicationIdSuffix`, `network_security_config.xml` (debug allows cleartext + user CAs), and an `easylauncher` "DEV" ribbon (`app/build.gradle.kts:199-207`) plus an `app_name` override in `app/src/debug/res/values/strings.xml`. This `src/debug/` source-set-override mechanism is the established pattern for anything debug-only.
- Only test coverage in this area is `app/src/test/java/dev/zun/flux/util/ServerUrlsTest.kt` (8 tests). No ViewModel/Compose-level tests exist for Setup/Settings.
- Error surfacing requires no new UI mechanism, but the two call sites do **not** treat the thrown message identically — this matters for the exact wording chosen: `SetupScreen.kt:177` catches with `error = t.toUserMessage("connect")`, while `SettingsViewModel.kt:110` catches with `error = t.message ?: "Invalid connection settings"` (raw message, no `toUserMessage`). `toUserMessage` (`app/src/main/java/dev/zun/flux/util/ErrorMessages.kt:20-37`) only passes an exception's own message through verbatim in its `else` branch, and only `if (it.isNotBlank() && it.length < 80)` — anything 80 characters or longer is replaced with the generic `"unknown error"`. So **any new rejection message must stay under 80 characters**, or the Setup-screen path (but not the Settings path) will silently show a useless generic error instead of it. The existing precedent (`"Release builds require https:// server URLs"`, 44 chars) already respects this; the new production-hostname message must too.
- `zun-rust-server`'s default bind port is `8080` (from `config.example.toml`), and it in turn talks to `zun-flux-pipeline`'s ComfyUI process on `127.0.0.1:8188`. Neither sibling repo needs code changes for this feature — see decision below.

## Decisions

### Decision: Reject rule lives inside `ServerUrls.normalizeOptionalServerUrl`, as a new optional parameter

**Rationale**: It's already the single validation choke point both UI screens call through (Principle II: no new abstraction, no duplicated logic across two screens). Adding a parameter (e.g. `blockHost: String? = null`) that the caller supplies only when `BuildConfig.DEBUG` keeps the function itself build-type-agnostic and unit-testable without touching `BuildConfig`.

**Alternatives considered**: Duplicating an `if (BuildConfig.DEBUG && ...)` check in both `SetupScreen.kt` and `SettingsViewModel.kt` — rejected, violates DRY and risks the two copies drifting.

### Decision: Match on exact hostname only (`uri.host`), case-insensitive, regardless of scheme or port

**Rationale**: Per user's explicit answer during `/speckit-specify` clarification — restriction targets only the specific known production hostname (`zun.h.doremysweet.com`), not a general "non-local" heuristic. Matching on host alone (not full URL string) means `http://zun.h.doremysweet.com`, `https://zun.h.doremysweet.com:443`, `https://ZUN.H.DOREMYSWEET.COM` are all still caught, which is the safer reading of "hard block" without being so broad it starts rejecting legitimate other remote hosts.

**Alternatives considered**: Exact string match on the full input — rejected, trivially bypassed by scheme/port/case variation, which defeats the purpose of a *hard* block.

### Decision: Debug default server URL sourced from a Gradle property in `local.properties` (gitignored), with an emulator-friendly fallback

The debug build type gets `buildConfigField("String", "DEFAULT_SERVER_URL", ...)` populated from a `local.properties` key named `DEBUG_DEFAULT_SERVER_URL` (all-caps, matching the existing `SENTRY_DSN`/`SENTRY_AUTH_TOKEN` naming convention in `app/build.gradle.kts`), falling back to `"http://10.0.2.2:8080"` (the Android emulator's host-loopback alias, matching `zun-rust-server`'s default port) when the property is absent. The release build type gets an empty string.

**Rationale**: Keeps the developer's actual LAN address/hostname for their Ubuntu GPU workstation out of version control — it varies by network and is exactly the kind of machine-specific value `local.properties` already carries for keystore signing and the Sentry DSN (`docs/build.md`). The emulator fallback means a fresh checkout still gets a working local-first default with zero setup when the emulator and server run on the same machine (the common case, per the spec's assumption that the Ubuntu GPU workstation is used ~95% of the time).

**Alternatives considered**:
- Hardcoding a specific LAN IP/hostname as a plain `buildConfigField` default — rejected, ties the repo to one physical network topology and leaks a home-network detail into git history.
- Automatic discovery (mDNS/network scan) of the local server — rejected as over-engineered for a single-developer tool (Principle II).
- No prefill, guardrail only — rejected, doesn't satisfy FR-001/SC-001, which require a non-blank local address to already be in place before any manual step.

### Decision: No changes required in `zun-rust-server` or `zun-flux-pipeline`

**Rationale**: The restriction is a pure client-side string comparison against a known hostname; it needs no server-provided environment identifier or API contract change. This narrows the spec's Assumptions section, which only conditionally anticipated a server change ("if" one is needed) — investigation confirms none is.

**Alternatives considered**: Having the server expose an `environment: "production" | "local"` field the client checks — rejected as unnecessary indirection for a value the client already knows statically (Principle II).

## Testing approach

- Extend `ServerUrlsTest.kt` with unit tests for: production hostname rejected when `blockHost` supplied (asserting the *exact* thrown message, per the existing `rejectsHttpWhenDisallowed` precedent — this is also what would have caught the message-length regression described above); scheme/port/case variations of the production host still rejected; every other host (local, LAN, arbitrary remote) unaffected when `blockHost` supplied; behavior unchanged when `blockHost` is `null` (release path). This is full, fast, automated coverage of the core logic (Principle III).
- The Setup-screen prefill and end-to-end save/reject flow have no existing Compose/ViewModel test scaffolding in this repo (confirmed — only `AppDatabaseMigrationTest.kt` exists under `androidTest`). Per Principle III's allowance for "an explicit manual verification path," these are verified manually per `quickstart.md` rather than adding new UI test infrastructure as part of this feature (avoids scope creep beyond what was asked, per Principle II).
