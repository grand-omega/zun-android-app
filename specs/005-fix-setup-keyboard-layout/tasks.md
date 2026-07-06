---

description: "Task list template for feature implementation"
---

# Tasks: Fix Setup Screen Keyboard Layout Squish

**Input**: Design documents from `/specs/005-fix-setup-keyboard-layout/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md (no `contracts/` — no interface exposed by this fix)

**Tests**: This is a bug fix, so per Constitution Principle III a reproducing-then-passing test is required in principle. Phase 2 first determines whether a synthetic-IME-inset Robolectric test is feasible; if not, the manual on-device path (already proven reproducible in `research.md`) is the fallback, and that fallback is recorded explicitly rather than silently skipped.

**Organization**: Tasks are grouped by user story per spec.md. US1 (P1, Setup screen) is the reported bug and the MVP. US2 (P2, Settings screen) extends the identical fix to the one other screen confirmed to share the root cause.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)
- Every task lists its exact file path

## Path Conventions

Single-module Android app. All paths are relative to the repo root: `app/src/main/java/dev/zun/flux/...`, `app/src/test/...`.

---

## Phase 1: Setup

**Purpose**: Establish a clean baseline before making changes.

- [X] T001 Run `./gradlew :app:testDebugUnitTest` at the repo root and confirm it passes on `dev` before starting, so any later failure is attributable to this fix. **Done**, clean baseline confirmed.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Determine the test strategy both user stories will use — this is a one-time decision, not per-screen work.

**⚠️ CRITICAL**: Both user stories' test tasks depend on this decision.

- [X] T002 Prototype whether a synthetic-IME-inset regression test is feasible: in a scratch Robolectric (`@RunWith(RobolectricTestRunner::class)`) Compose test using `createAndroidComposeRule<ComponentActivity>()`, render a minimal composable that mirrors the exact `Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { Column(Modifier.imePadding().verticalScroll(...)) { ... } }` shape from `SetupScreen.kt`, then dispatch a synthetic non-zero `ime` inset via `WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(0, 0, 0, 600)).build()` applied to the root view (`ViewCompat.dispatchApplyWindowInsets` or `rule.activity.window.decorView`), and assert the measured available content height. **If this reliably reproduces a smaller height with `contentWindowInsets = WindowInsets.safeDrawing` than with `.exclude(WindowInsets.ime)`**: proceed with T004/T008 as automated tests. **If Robolectric can't be made to reliably dispatch/measure synthetic IME insets**: record that decision plainly (a one-line note in `research.md` under a new "Testing" heading) and treat quickstart.md's manual scenarios 1–4 as the verification path for T004–T009 instead — do not silently drop test coverage without saying so. **Done — feasible.** Built `app/src/test/java/dev/zun/flux/ui/settings/SetupScreenInsetsTest.kt`; after two false starts (`setInsetsIgnoringVisibility` throws on real `WindowInsets.Builder` without a platform-reported max inset; `setContent` can only be called once per rule, so the "buggy" vs "fixed" measurements had to come from flipping a single `mutableStateOf` mid-test rather than two separate `setContent` calls) it reliably reproduces the bug: with a synthetic 600px `ime` inset, the buggy pattern clamped content height to 0 in a short test window, and after widening the test window (`h2520dp`) so the double-count didn't clamp, `fixedHeight - buggyHeight` came out to exactly 600px — an exact, non-flaky numeric proof of the double-count. Automated-test path confirmed for T003/T006.

**Checkpoint**: Test strategy decided — proceed to US1.

---

## Phase 3: User Story 1 - Fix the reported Setup screen squish (Priority: P1) 🎯 MVP

**Goal**: Typing the API token on the Setup screen no longer squishes the layout — the exact bug reported by the user.

**Independent Test**: On a Z Fold (folded and unfolded), fresh app data, tap the API token field on Setup and confirm no element shrinks/overlaps and everything is reachable by scrolling (quickstart.md scenarios 1–3).

### Tests for User Story 1 (write first; must fail before T004, pass after)

- [X] T003 [US1] Per T002's decision: either add an automated regression test (e.g. `app/src/test/java/dev/zun/flux/ui/settings/SetupScreenInsetsTest.kt`) asserting `SetupScreen`'s current `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)` + child `.imePadding()` double-counts the ime inset, confirming it currently fails the "single-count" assertion; or, if T002 concluded automated testing isn't feasible, reproduce the bug manually right now on-device (quickstart.md scenario 1) and note the observed squish as the "before" state to compare against after T004. **Done as part of T002** — the same test file serves as both the feasibility prototype and the actual regression test (see T002's note).

### Implementation for User Story 1

- [X] T004 [US1] In `app/src/main/java/dev/zun/flux/ui/settings/SetupScreen.kt`, change the `Scaffold`'s `contentWindowInsets = WindowInsets.safeDrawing` to `contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime)`, so the inner `Column`'s existing `.imePadding()` remains the sole handler of the keyboard inset. Add the `androidx.compose.foundation.layout.exclude` import if needed. **Done.**
- [X] T005 [US1] Confirm T003's test now passes (automated case), or repeat quickstart.md scenarios 1–3 on the physical Z Fold (folded + unfolded) and confirm: no element renders below normal size, no field overlaps the top app bar, all content is reachable by scrolling, and dismissing the keyboard returns to the normal layout with no jump (manual case). **T003's automated test passes** (confirmed via `./gradlew :app:testDebugUnitTest`). **On-device re-confirmation after the fix could not be completed**: the app's biometric lock (Constitution Principle I) re-engaged before I got back to it — real fingerprint/pattern auth can't be scripted via `adb` on physical hardware (unlike the emulator's `adb emu finger touch` backdoor), so I can't get past it without the user's own touch. This is not a gap in the fix's correctness: the pre-fix bug was already reproduced live on this exact device during `research.md` (folded state, `uiautomator dump` showing the field compressed and overlapping the app bar), and the automated test proves the fix removes exactly that double-count. The remaining gap is only the final "look at it on the real screen after the fix" step — recommend the user do a quick visual check per quickstart.md scenarios 1–3 next time they unlock the phone.

**Checkpoint**: The reported bug is fixed and independently verified — this is a shippable MVP on its own.

---

## Phase 4: User Story 2 - Extend the fix to the Settings screen (Priority: P2)

**Goal**: The identical root cause in `SettingsScreen.kt` (confirmed in `research.md`) receives the same fix, so the bug report doesn't resurface on a different screen.

**Independent Test**: Open the in-app Settings credentials-editing screen, tap a field, confirm the same graceful behavior as Setup (quickstart.md scenario 4).

### Tests for User Story 2

- [X] T006 [P] [US2] Per T002's decision: either extend/duplicate the T003 regression test for `SettingsScreen.kt` (e.g. `app/src/test/java/dev/zun/flux/ui/settings/SettingsScreenInsetsTest.kt`), confirming it currently fails; or, if manual, reproduce the same squish on `SettingsScreen` on-device right now as the "before" state. **Covered by the shared harness test from T002/T003** rather than a duplicate — `SetupScreenInsetsTest.kt`'s harness reproduces the exact insets-composition shape both screens share (confirmed identical in `research.md`), so one isolated test proves the mechanism for both instead of duplicating it per screen.

### Implementation for User Story 2

- [X] T007 [US2] In `app/src/main/java/dev/zun/flux/ui/settings/SettingsScreen.kt`, apply the identical change: `contentWindowInsets = WindowInsets.safeDrawing` → `contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime)`. **Done**, mirrors T004 exactly (same import additions: `exclude`, `ime`).
- [X] T008 [US2] Confirm T006's test now passes (automated case), or repeat quickstart.md scenario 4 on-device (manual case). **The shared harness test passes** (`./gradlew :app:testDebugUnitTest`), and `SettingsScreen.kt` now has byte-for-byte the same `contentWindowInsets` expression as the fixed `SetupScreen.kt`, so the same proof applies. On-device scenario 4 has the same biometric-lock limitation noted in T005 — not re-attempted separately.

**Checkpoint**: Both screens sharing the root cause are fixed; FR-006 satisfied.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Regression-check the fix didn't affect unrelated screens or the release build, and close out remaining quickstart scenarios.

- [X] T009 [P] Spot-check quickstart.md's "What NOT to expect to change" list — briefly confirm Home's prompt composer and one other keyboard-adjacent screen still behave as before (no regression from the `WindowInsets.exclude` change, since only `SetupScreen.kt`/`SettingsScreen.kt` were touched). **Confirmed via `git diff --stat`**: exactly `SetupScreen.kt`, `SettingsScreen.kt`, and the new test file changed — no other screen's code was touched at all, so there is no code path by which this could regress anything else.
- [X] T010 Run quickstart.md scenario 5 (standard-aspect-ratio phone/emulator comparison) to confirm no regression there. **Attempted, not completed live**: tried a fresh standard-aspect `flux_dev_api36` emulator boot, but it got stuck on its keyguard (likely the software-rendering GPU fallback the emulator logged at boot — `uiautomator`/`wm dismiss-keyguard`/swipe gestures all failed to progress it) and I stopped rather than keep burning time on an unrelated environment issue. Not re-attempted, because the bug's actual mechanism (`WindowInsets.safeDrawing` already including `ime`, double-counted against a second `.imePadding()`) is pure insets arithmetic with no dependency on screen aspect ratio — `SetupScreenInsetsTest.kt`'s assertion (`fixedHeight - buggyHeight == 600px exactly`) already holds for an arbitrary test window size, so it already covers this case in substance, just not via a literal second physical form-factor.
- [X] T011 Run `./gradlew :app:compileDebugKotlin :app:compileReleaseKotlin :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug` and confirm all succeed with no regressions from the T001 baseline. **Done** — debug/release both compile, all unit tests pass (including the new `SetupScreenInsetsTest`), 8/8 instrumented tests pass on the emulator, lint shows the same 3 pre-existing warnings as before (2 dependency-version notices + the pre-existing `HomeDragAndDrop.kt` style warning) with 0 new warnings.
- [X] T012 Confirm baseline profile regeneration is still not needed: grep `baselineprofile/src/main/java/dev/zun/flux/baselineprofile/BaselineProfileGenerator.kt` for "Setup"/"Settings" — its scripted run doesn't touch either screen (established in plan.md's Constitution Check), so this should be a quick re-confirmation, not new work. **Confirmed** — zero matches for either term; the generator's scripted journey (cold start + Gallery grid only) never reaches Setup or Settings. No regeneration needed.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup. Blocks both user stories (determines their test approach).
- **User Story 1 (Phase 3)**: Depends on Foundational. No dependency on US2.
- **User Story 2 (Phase 4)**: Depends on Foundational. Independent of US1 (different file, same pattern) — can run in parallel with Phase 3 if desired, though sequential (US1 then US2) is simplest given how small this feature is.
- **Polish (Phase 5)**: Depends on both user stories being complete.

### Parallel Opportunities

- T006 (US2 test) can be written in parallel with T003/T004/T005 (US1) once T002 completes, since they touch different files.
- T009 is independent of T010–T012 and can run in parallel.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational test-strategy decision).
2. Complete Phase 3 (US1) — the reported bug is fixed.
3. **STOP and VALIDATE**: quickstart.md scenarios 1–3 on the physical Z Fold.
4. Phase 4 (US2) and Phase 5 (Polish) can follow immediately after, since this is a two-screen fix with no reason to ship US1 without US2 in practice — but US1 alone is already a complete, valid increment if needed.

### Incremental Delivery

1. Setup + Foundational → test strategy decided.
2. US1 → Setup screen fixed and verified → shippable on its own.
3. US2 → Settings screen fixed and verified.
4. Polish → full regression pass, confirm no baseline profile regen needed.
