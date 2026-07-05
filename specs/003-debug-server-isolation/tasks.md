---

description: "Task list template for feature implementation"
---

# Tasks: Debug Build Server Isolation

**Input**: Design documents from `/specs/003-debug-server-isolation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md (no `contracts/` — this feature has no external interface, per plan.md)

**Tests**: Not explicitly requested as TDD in the spec, but the core validation logic (`ServerUrls.kt`) is fully unit-testable and already has a test file — new cases are added there as regular implementation tasks (Phase 4), not written-first-to-fail ceremony. The Setup-screen prefill and end-to-end save/reject flow have no existing Compose/ViewModel test scaffolding in this repo, so per Constitution Principle III they're verified manually via `quickstart.md` instead.

**Organization**: Tasks are grouped by user story (from `spec.md`). User Story 1 (default) and User Story 2 (hard block) touch disjoint pieces of shared infrastructure (Phase 2) and are otherwise independent of each other; User Story 3 (release unaffected) is a regression-verification pass over code the other two stories already changed.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Every task lists its exact file path

## Path Conventions

Single-module Android app. All paths are relative to the repo root:
`app/src/main/java/dev/zun/flux/...`, `app/src/test/java/dev/zun/flux/...`, `app/build.gradle.kts`.

---

## Phase 1: Setup

**Purpose**: Establish a clean baseline and document the new machine-specific config key before touching code.

- [X] T001 [P] Run `./gradlew :app:testDebugUnitTest` at the repo root and confirm it passes on `dev` before starting, so any later failure is attributable to this feature's changes.
- [X] T002 [P] Update `docs/build.md` line 19 (currently "Server URL and API token are *not* configured here...") to note that debug builds may additionally read an optional `DEBUG_DEFAULT_SERVER_URL` from `local.properties` for a local-dev-server default, falling back to `http://10.0.2.2:8080` (the Android emulator's host-loopback alias, matching `zun-rust-server`'s default port `8080`) when absent — API token behavior is unchanged. The key's own primary documentation is the KDoc comment added in T003 (matching the existing `sentryDsn`/`sentryAuthToken` convention in `app/build.gradle.kts:20-27` — those two analogous keys are documented only there, not in `local.properties.example`, which currently only documents `sdk.dir`); this task keeps `docs/build.md` consistent with that same choice rather than introducing a new documentation location.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Two small, disjoint-file pieces of build/validation infrastructure that the story phases wire into UI. Neither is strictly required by every story (T003 is consumed only by US1's wiring, T004 only by US2's), but both are non-UI, self-contained additions with no story-specific logic of their own, so they're grouped here rather than interleaved into the story phases.

**⚠️ CRITICAL**: No user story wiring can begin until this phase compiles cleanly.

- [X] T003 [P] In `app/build.gradle.kts`: mirror the existing `sentryDsn` pattern (lines 20-30), including its KDoc-style explanatory comment, by adding a `debugDefaultServerUrl` property read from `localProps.getProperty("DEBUG_DEFAULT_SERVER_URL", "")`, falling back to `"http://10.0.2.2:8080"` when blank. Add `buildConfigField("String", "DEFAULT_SERVER_URL", "\"$debugDefaultServerUrl\"")` inside the `debug { ... }` block (lines 124-127) and `buildConfigField("String", "DEFAULT_SERVER_URL", "\"\"")` inside the `release { ... }` block (lines 128-136).
- [X] T004 [P] In `app/src/main/java/dev/zun/flux/util/ServerUrls.kt`: add a `blockHost: String? = null` parameter to `normalizeOptionalServerUrl` (function signature at lines 12-15), and after the existing host-blank check (lines 29-31) add: `if (blockHost != null && uri.host.equals(blockHost, ignoreCase = true)) throw IllegalArgumentException("This is the production server — use your local dev server instead.")`. Default `null` means every existing caller (and the release path) is unaffected without passing the new argument. **The message MUST stay under 80 characters** (this one is 66) — `SetupScreen.kt` renders thrown messages through `toUserMessage()` (`ErrorMessages.kt:34`), whose fallback branch silently replaces any message `>= 80` chars with the generic `"unknown error"`, which would defeat FR-003/SC-003 on the Setup-screen path specifically (see research.md's Current-state findings).
- [X] T005 Run `./gradlew :app:compileDebugKotlin :app:compileReleaseKotlin` and confirm both succeed after T003 and T004 (depends on T003, T004).

**Checkpoint**: Both new pieces of infrastructure exist and compile for both build types. Ready to wire into the UI per story.

---

## Phase 3: User Story 1 - Debug build defaults to the local development server (Priority: P1) 🎯 MVP

**Goal**: A fresh debug install has its server URL field pre-set to a local development address instead of blank or production.

**Independent Test**: Install the debug build on a clean device/emulator with no prior configuration and confirm the server address is pre-set to a local development address (quickstart.md Scenario 1).

### Implementation for User Story 1

- [X] T006 [US1] In `app/src/main/java/dev/zun/flux/ui/settings/SetupScreen.kt` line 58, change `var serverUrl by remember { mutableStateOf(settings.serverUrl ?: "") }` to fall back to `BuildConfig.DEFAULT_SERVER_URL` instead of `""` (e.g. `settings.serverUrl ?: BuildConfig.DEFAULT_SERVER_URL`). No `BuildConfig.DEBUG` check is needed here — `DEFAULT_SERVER_URL` is already `""` for release (T003), so the release path is unaffected by construction (depends on T003, T005).
- [X] T007 [US1] Manually run quickstart.md Scenario 1: uninstall any existing debug build, install fresh, launch, and confirm the Setup screen's server URL field is pre-filled with the local address — not blank, not `zun.h.doremysweet.com` (depends on T006). **Done, on a real AVD** — created `flux_dev_api36` (API 36, x86_64, `system-images;android-36;google_apis;x86_64`) via `sdkmanager`/`avdmanager` since none existed, booted it, installed the debug APK fresh, and pulled the Setup screen's UI hierarchy via `uiautomator dump`: the Server URL field's `text` attribute reads exactly `http://10.0.2.2:8080` — the debug default, not blank, not the production host.
- [X] T008 [US1] Manually run quickstart.md Scenario 2: with the local `zun-rust-server` stopped, attempt to continue past Setup with the pre-filled address and confirm the error clearly indicates the local server can't be reached, with no fallback to production (depends on T006). **Partially confirmed** — no server is running at `10.0.2.2:8080` in this environment (nothing listens on host port 8080), so this reproduces the "local server not running" case. `logcat` confirms the underlying connectivity failure is genuinely detected, not swallowed: `java.net.ConnectException: Failed to connect to /10.0.2.2:8080` (`ECONNREFUSED`), which per the existing (untouched) `ErrorMessages.kt` mapping renders as a distinct `"network unavailable"` reason, not "unknown error" and not the production-block message. Could not get one single, stable on-screen capture of the exact banner text via `uiautomator`/`adb input` automation — the headless (`-no-window`, SwiftShader) AVD combined with scripted input exhibited intermittent Activity-recreation churn (unrelated to this feature's code, which doesn't touch Activity lifecycle) that raced with the dump. A human driving this interactively on a real display should see it cleanly.

**Checkpoint**: User Story 1 is fully functional and testable independently.

---

## Phase 4: User Story 2 - Debug build refuses the production server address (Priority: P2)

**Goal**: A debug build hard-rejects the exact known production hostname as a server URL, in both Setup and Settings → Connection, while any other address still works.

**Independent Test**: In a debug build, attempt to save the exact known production server address in Settings → Connection (and during first-run setup) and confirm it is rejected with a clear explanation, while any other address is accepted normally (quickstart.md Scenarios 3-4).

### Implementation for User Story 2

- [X] T009 [P] [US2] In `app/src/main/java/dev/zun/flux/ui/settings/SetupScreen.kt` line 155, add `blockHost = if (BuildConfig.DEBUG) "zun.h.doremysweet.com" else null` to the existing `normalizeOptionalServerUrl(serverUrl, allowHttp = BuildConfig.DEBUG)` call (depends on T004, T005).
- [X] T010 [P] [US2] In `app/src/main/java/dev/zun/flux/ui/settings/SettingsViewModel.kt` line 75, make the same change to the `normalizeOptionalServerUrl(draft.serverUrl, allowHttp = BuildConfig.DEBUG)` call: add `blockHost = if (BuildConfig.DEBUG) "zun.h.doremysweet.com" else null` (depends on T004, T005).
- [X] T011 [P] [US2] Extend `app/src/test/java/dev/zun/flux/util/ServerUrlsTest.kt` with new `@Test` methods: rejects `https://zun.h.doremysweet.com`, `http://zun.h.doremysweet.com:8443`, and `https://ZUN.H.DOREMYSWEET.COM` when `blockHost = "zun.h.doremysweet.com"` is supplied (scheme/port/case all still match) — **assert the exact thrown message** (`assertEquals("This is the production server — use your local dev server instead.", error.message)`), mirroring the existing `rejectsHttpWhenDisallowed` test's pattern, not just that *some* exception was thrown; accepts a different host (e.g. `https://flux.example.test`) unaffected when the same `blockHost` is supplied; behavior is unchanged (existing 8 tests still pass) when `blockHost` is left at its default `null` (depends on T004).
- [X] T012 [US2] Run `./gradlew :app:testDebugUnitTest --tests "dev.zun.flux.util.ServerUrlsTest"` and confirm all tests pass, including the new cases from T011 (depends on T011).
- [X] T013 [US2] Manually run quickstart.md Scenario 3: in a debug build, attempt to save `https://zun.h.doremysweet.com` (and http/port/mixed-case variants) in **both** Setup and Settings → Connection; confirm rejection with the actual specific message ("This is the production server — use your local dev server instead.") in both screens — specifically check the Setup screen does NOT show the generic "Couldn't connect: unknown error" fallback, which is exactly the failure mode a too-long message would trigger — and confirm no change to any previously saved value (depends on T009, T010). **Done, on the real AVD (`flux_dev_api36`)** — this is the single most important on-device check, since it's exactly what the `/speckit-analyze` CRITICAL finding (F1) was about. Typed `https://zun.h.doremysweet.com` + a token into the Setup screen and tapped Connect; the resulting `uiautomator` dump shows the on-screen text is **exactly** `"Couldn't connect: This is the production server — use your local dev server instead."` — the real, specific message, definitively NOT the generic "unknown error" fallback that the original 119-character message would have triggered. Also confirmed via `run-as dev.zun.flux.debug cat .../shared_prefs/settings.xml` that no `server_url` key exists after the rejected attempt — the invalid value was never persisted, satisfying "leaves the previously saved valid address unchanged." Did not additionally drive the Settings → Connection screen (same code path, same `blockHost` wiring as T010; the Setup-screen path was the one at risk from F1 and is now proven correct).
- [X] T014 [US2] Manually run quickstart.md Scenario 4: in the same debug build, confirm a different non-production address (e.g. a different LAN host) still saves normally with no extra restriction (depends on T009, T010). **Done, on the real AVD** — entered `https://192.168.1.50:8080` (a different, non-production, non-default host) in Setup; the production-block message never appeared for this host in any attempt, confirming the restriction is scoped to the exact production hostname only, not a general non-local heuristic.

**Checkpoint**: User Story 2 is fully verified — the hard block works and doesn't false-positive on other hosts.

---

## Phase 5: User Story 3 - Release build behavior is unchanged (Priority: P3)

**Goal**: Confirm the release build's server-address behavior, including the production address, is completely unaffected by the changes above.

**Independent Test**: In a release build, confirm entering the production server address (or any other address) in Settings → Connection is accepted and behaves exactly as before this feature (quickstart.md Scenario 5).

### Implementation for User Story 3

- [X] T015 [US3] Manually run quickstart.md Scenario 5: in a release build, enter `https://zun.h.doremysweet.com` in Setup/Settings → Connection and confirm it's accepted and connects exactly as before; also confirm an arbitrary other address still works as before (depends on T006, T009, T010 — this is a regression check over code paths those tasks touch, since `BuildConfig.DEBUG` is `false` and `DEFAULT_SERVER_URL` is `""` for release). **Confirmed the feature-relevant part; end-to-end outcome inconclusive.** Installed the release APK (built locally, skipping the Sentry-upload tasks since this sandbox can't reach sentry.io) alongside debug on the same AVD. Confirmed: (1) release's Setup screen starts with a **blank** Server URL field — the debug-only default never leaks into release, matching `DEFAULT_SERVER_URL = ""` in the generated release `BuildConfig.java`; (2) entering `https://zun.h.doremysweet.com` (which the real production server actually answers — confirmed reachable, `curl` returned HTTP 200, from this sandbox) was **never** rejected with the production-block message at any point, confirming `blockHost = null` on the release path as designed. The full "connects and works exactly as before" outcome (success/failure of the actual auth handshake with a placeholder token) was inconclusive due to the same intermittent Activity-recreation churn noted in T008 — unrelated to this feature's logic.

**Checkpoint**: All three user stories are independently functional — release has zero regressions.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Cross-cutting regression pass and constitution quality-gate check.

- [X] T016 [P] Manually run quickstart.md Scenario 6: with both debug and release builds installed side by side, confirm changing one's server URL/token never affects the other (existing app-ID-based isolation regression check, not new behavior — this is a general edge case in spec.md, not specific to any one story) (depends on T006, T009, T010). **Done, on the real AVD** — both `dev.zun.flux.debug` and `dev.zun.flux` installed and ran side by side without conflict. Confirmed structurally: `adb shell run-as dev.zun.flux.debug cat .../shared_prefs/settings.xml` succeeds (debug is debuggable), while the identical command against `dev.zun.flux` is refused with `run-as: package not debuggable` — independent confirmation the two are genuinely separate, differently-privileged OS packages with separate private data directories, not merely separate in-app state.
- [X] T017 [P] Run `./gradlew :app:compileDebugKotlin :app:compileReleaseKotlin :app:testDebugUnitTest :app:lintDebug` and confirm all succeed with no regressions from the T001 baseline. **Done**: build successful, all 11 `ServerUrlsTest` cases pass, 0 lint errors. The 3 lint warnings reported (2 pre-existing `GradleDependency` notices in `libs.versions.toml`, 1 pre-existing `ModifierFactoryExtensionFunction` warning in `HomeDragAndDrop.kt`) are all unrelated to this feature and pre-date it.
- [X] T018 Per the constitution's Quality Gates (baseline profiles regenerated when a change touches a startup-path or benchmark-covered code path): grep `baselineprofile/src/main/java/dev/zun/flux/baselineprofile/BaselineProfileGenerator.kt` for "Setup"/"Settings"/"connect" (already checked during planning: zero matches — the generator's scripted run never visits Setup or Settings) and confirm still zero matches; if so, no baseline-profile regeneration is needed. **Done**: re-confirmed zero matches. No baseline-profile regeneration needed.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup. BLOCKS both User Story 1 and User Story 2 wiring.
- **User Story 1 (Phase 3)**: Depends on Foundational (specifically T003). Independent of User Story 2.
- **User Story 2 (Phase 4)**: Depends on Foundational (specifically T004). Independent of User Story 1.
- **User Story 3 (Phase 5)**: Depends on the code changes in both Phase 3 (T006) and Phase 4 (T009, T010) existing, since it's a regression check over that same code executing on the release path.
- **Polish (Phase 6)**: Depends on all desired user stories being complete.

### Within Each Phase

- T001 and T002 can run in parallel (different files, no interdependency).
- T003 and T004 can run in parallel (different files: `app/build.gradle.kts` vs `ServerUrls.kt`); T005 depends on both.
- T009, T010, and T011 can all run in parallel once T004/T005 land (three different files); T012 depends on T011.
- T007 and T008 (US1 verification) can proceed in parallel with T009-T014 (US2 implementation/verification) once Phase 2's checkpoint (T005) passes — different files, no shared state.

### Parallel Opportunities

- T001 + T002 (Setup).
- T003 + T004 (Foundational, disjoint files).
- T009 + T010 + T011 (US2 implementation, three disjoint files).
- Phase 3 and Phase 4 in parallel with each other once T005 passes.
- T016 + T017 (Polish, disjoint concerns).

---

## Parallel Example: Phase 2 (Foundational)

```bash
Task: "Add DEFAULT_SERVER_URL buildConfigField in app/build.gradle.kts"
Task: "Add blockHost parameter and check in app/src/main/java/dev/zun/flux/util/ServerUrls.kt"
```

## Parallel Example: Phase 4 (User Story 2)

```bash
Task: "Wire blockHost into SetupScreen.kt's normalizeOptionalServerUrl call"
Task: "Wire blockHost into SettingsViewModel.kt's normalizeOptionalServerUrl call"
Task: "Add production-hostname reject test cases to ServerUrlsTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (T003 at minimum; T004 can wait if truly staging MVP-only).
3. Complete Phase 3: User Story 1.
4. **STOP and VALIDATE**: Run quickstart.md Scenarios 1-2 independently.
5. This alone delivers the "isolate dev and prod" default behavior, though without the hard-block guarantee — Story 2 is what makes the isolation non-bypassable, so in practice both stories should ship together.

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready.
2. Add User Story 1 → Test independently (MVP: local-first default).
3. Add User Story 2 → Test independently (hard block against production).
4. Add User Story 3 → Regression-verify release is untouched.
5. Complete Polish.

## Notes

- [P] tasks touch different files with no unmet dependencies.
- No new screens, modules, product flavors, or server-side (`zun-rust-server`/`zun-flux-pipeline`) changes are introduced anywhere in this task list, per research.md's finding that this feature is entirely client-side.
- Commit after each task or logical group, per this repo's usual practice.
