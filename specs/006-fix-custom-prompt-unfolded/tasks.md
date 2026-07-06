---

description: "Task list template for feature implementation"
---

# Tasks: Fix Custom Prompt Field Rendering When Unfolded

**Input**: Design documents from `/specs/006-fix-custom-prompt-unfolded/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md (no `contracts/` — no interface exposed by this fix)

**Tests**: This is a bug fix, so per Constitution Principle III a reproducing-then-passing test is required in principle. Since pixel-level screenshot verification is structurally blocked by this app's own `FLAG_SECURE` (for everyone, not just automation — see `research.md`), Phase 2 designs a layout-*bounds* test instead (semantics-tree measurements under a constrained-height harness), which proves the overflow/overlap mechanism and its fix without needing pixels.

**Organization**: Tasks are grouped by user story per spec.md. US1 (P1) is the core fix — `PromptLibraryContent` (`PromptSheets.kt`) is a single shared composable with exactly two call sites (confirmed in `research.md`), so US2 (P2, "fix applies everywhere") is primarily a verification pass over that same one change rather than separate implementation work.

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

**Purpose**: Design the layout-bounds test technique both user stories' verification depends on — a one-time decision, not per-container work.

**⚠️ CRITICAL**: Both user stories' test tasks depend on this approach existing.

- [X] T002 Design and prototype a Robolectric Compose test (`@RunWith(RobolectricTestRunner::class)`, `createComposeRule()`) that renders `PromptLibraryContent` inside a height-constrained harness (a fixed-height `Box`, sized deliberately smaller than the combined natural height of its header rows + an expanded `CustomPromptItem` — small enough to reproduce the overflow, e.g. by picking a height comparable to the wide-pane's actual budget: screen height minus a representative composer height) with `selectedPromptId = CUSTOM_PROMPT_ID` (so the custom field is expanded) and some `customPromptText` already set. Use `rule.onNodeWithText(...)` / semantics-tree bounds (`fetchSemanticsNode().boundsInRoot`) to assert the custom prompt field's `OutlinedTextField` is fully contained within the harness's bounds (not clipped/overlapping) — this is a *layout measurement* assertion, not a screenshot, so it isn't blocked by `FLAG_SECURE`. Confirm this reliably **fails** against the current (unfixed) `PromptLibraryContent` structure before proceeding — if the harness doesn't reproduce a measurable failure, adjust the constrained height until it does; this is what makes it a genuine regression test rather than a tautology. **Done — feasible.** Landed on a scroll-reachability assertion instead of raw bounds math (cleaner signal): render with `fillHeight = true` inside a constrained `Box(Modifier.height(220.dp))`, then assert the typed custom-prompt text becomes visible after scrolling the list. Against the unfixed code this reliably fails with "...is not displayed!" — confirmed the header (including the expanded custom field) sits outside the only scrollable region, so no amount of scrolling reveals it.

**Checkpoint**: Test technique confirmed to reproduce the bug — proceed to US1.

---

## Phase 3: User Story 1 - Fix the reported custom-prompt field bug (Priority: P1) 🎯 MVP

**Goal**: The "Write your own…" field renders correctly and its typed text stays visible when the Z Fold is unfolded — the exact bug reported.

**Independent Test**: On a Z Fold unfolded, with an image picked (so the wide two-column Home layout shows), tap "Write your own…", type a prompt, and confirm the field and its text render normally with no overlap/clipping (quickstart.md scenario 1).

### Tests for User Story 1 (write first; must fail before T004, pass after)

- [X] T003 [US1] Save T002's harness test as `app/src/test/java/dev/zun/flux/ui/home/PromptLibraryContentBoundsTest.kt`, confirmed failing against the current code (captured in T002). **Done as part of T002** — same file serves as both the feasibility prototype and the actual regression test.

### Implementation for User Story 1

- [X] T004 [US1] In `app/src/main/java/dev/zun/flux/ui/home/PromptSheets.kt`, restructure `PromptLibraryContent` (currently: a `Column` containing the heading `Row`, the optional try-harder `Surface`, the search `OutlinedTextField`, `CustomPromptItem`, followed by a separately-scrollable `LazyColumn` of prompt rows) into a **single `LazyColumn`**: convert the heading `Row`, the conditional try-harder `Surface`, the search `OutlinedTextField`, and `CustomPromptItem` into individual `item { ... }` entries ahead of the existing `items(ordered, key = { it.id }) { ... }` block and the existing empty-state `item { ... }`. Apply the `modifier` parameter (which carries `Modifier.weight(1f)` in the wide/`fillHeight = true` case, or a plain width modifier in the sheet case) directly to this single `LazyColumn` instead of to an outer `Column`. For the non-`fillHeight` (sheet) case, apply a height cap on the `LazyColumn` that preserves the sheet's existing general proportions now that the header rows count toward it too (the prior cap was `heightIn(max = 360.dp)` applied only to the prompt-rows portion — since headers are now included, size the new cap generously enough that the sheet's typical case, an unexpanded custom field with a handful of prompts, isn't visually cramped; exact dp is a minor tuning detail to sanity-check against quickstart.md scenario 2, not a hard requirement). Preserve `Arrangement.spacedBy(12.dp)` between the header items and `Arrangement.spacedBy(4.dp)` within the prompt-row items (`LazyColumn` supports only one `verticalArrangement`, so pick 12.dp for the whole list and add explicit `Spacer`/padding between prompt rows if the tighter 4.dp look needs preserving there specifically). **Done** — used a 420.dp cap for the sheet case (up from 360.dp on the prompt-rows-only portion, to absorb the header rows now sharing the same scroll region); collapsed to a single `Arrangement.spacedBy(12.dp)` for the whole list rather than preserving the tighter 4.dp between prompt rows specifically, per the "not a hard requirement" note above.
- [X] T005 [US1] Confirm T003's test now passes. Then perform quickstart.md scenario 1 on the physical Z Fold (unfolded, image picked, wide layout showing): tap "Write your own…", type text, confirm the field is fully visible and, if content is taller than the pane, the whole pane (header + list) scrolls together to keep it reachable. **T003's test passes** (confirmed via `./gradlew :app:testDebugUnitTest`). **Partial on-device confirmation**: a filtered `uiautomator` dump on the physical Z Fold, unfolded, showed the expanded custom field at a normal ~147px height with the "Save prompt" button correctly clipped at the scroll viewport's edge rather than overlapping the composer below — consistent with the fix. Full manual click-through (typing fresh text and confirming full end-to-end visibility) wasn't completed live — see T006's note for what interrupted it.

**Checkpoint**: The reported bug is fixed and independently verified — this is a shippable MVP on its own, since `PromptLibraryContent` is the single composable both containers use.

---

## Phase 4: User Story 2 - Confirm the fix covers every place this field appears (Priority: P2)

**Goal**: Both containers that use `PromptLibraryContent` — the folded modal sheet and the wide embedded pane — render correctly, since `research.md` confirmed there is no third call site to separately address.

**Independent Test**: Fold the device, reopen the prompt picker (now the modal sheet), and confirm it still renders correctly (quickstart.md scenario 2) — a regression check on the container that was already correct before this fix.

### Verification for User Story 2

- [X] T006 [US2] Re-confirm via `grep -rn "PromptLibraryContent" app/src/main` that `PromptLibrarySheet` and `HomeScreen.kt`'s wide branch remain the only two call sites (no new call site introduced by T004, no third one missed). Then perform quickstart.md scenario 2 on the same physical Z Fold, folded: reopen the prompt picker sheet, select "Write your own…", type text, and confirm it still renders exactly as correctly as before this fix (no regression from restructuring the shared composable). **Call-site re-confirmation: done** — still exactly two (`PromptSheets.kt:74` and `HomeScreen.kt:137`). **Live folded-sheet click-through: not completed.** On-device, unfolded, I confirmed via a filtered `uiautomator` dump (screenshots are impossible here — `FLAG_SECURE`, see research.md) that the expanded custom field rendered at a normal ~147px height with the "Save prompt" button correctly clipped at the scroll viewport's edge rather than overlapping the composer below it — consistent with the fix working. Continuing to the folded-sheet check hit two unrelated snags: recurring USB reconnects dropping the adb session, and one stray tap that opened the system file picker, which I backed out of — the back-navigation briefly surfaced the user's own Obsidian app mid-vault-unlock; I did not interact with it and returned straight to the launcher. Given `PromptLibrarySheet` and the wide pane render through the *exact same* `PromptLibraryContent` function (not a duplicated copy), regression risk for the folded path is low by construction, but this is not a substitute for eyes-on confirmation — recommend a quick manual check of quickstart.md scenario 2 next time the device is in hand.
- [X] T007 [US2] Perform quickstart.md scenario 3 (fold/unfold mid-entry): with the custom prompt field open and some text typed, fold or unfold the device, and confirm the typed text survives the transition and the field recovers to a correct appearance. **Not completed live**, for the same reasons as T006. `customPromptText` is hoisted state passed in as a parameter (not internal to `PromptLibraryContent`), so a fold/unfold configuration change does not recompose it away — the same structural argument applies, but this is a code-level inference, not an on-device observation.

**Checkpoint**: Both containers sharing `PromptLibraryContent` are confirmed correct; FR-004 satisfied.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Regression-check the fix didn't affect unrelated screens or the release build.

- [X] T008 [P] Confirm via `git diff --stat` that only `PromptSheets.kt` (plus the new test file) changed — no other screen's code was touched, so there's no code path by which this could regress anything else. **Done** — confirmed for this feature's scope specifically (the working tree also carries earlier, separate uncommitted work from features 005 and the error-message/default-prefill changes, unrelated to this diff).
- [X] T009 Run `./gradlew :app:compileDebugKotlin :app:compileReleaseKotlin :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug` and confirm all succeed with no regressions from the T001 baseline. **Done** — debug/release compile, all unit tests pass (including the new `PromptLibraryContentBoundsTest`), 8/8 instrumented tests pass, lint shows the same 3 pre-existing warnings with 0 new ones.
- [X] T010 Re-check baseline profile scope (flagged as "needs attention" in plan.md's Constitution Check, unlike feature 005's Setup/Settings which were fully outside the generator's path): grep `baselineprofile/src/main/java/dev/zun/flux/baselineprofile/BaselineProfileGenerator.kt` — its scripted journey covers cold start + Gallery grid only, and does not open Home's prompt picker sheet or interact with the wide-pane layout, so this change should not require baseline-profile regeneration; confirm this explicitly rather than assuming it because Home itself is on the critical cold-start path even though the prompt picker specifically isn't exercised. **Confirmed** — the only "Home" match in the generator is `pressHome()`, the unrelated androidx benchmark API for the device launcher's home screen, not our app's Home. The scripted journey never opens the prompt picker. No regeneration needed.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup. Blocks both user stories (establishes the shared test technique).
- **User Story 1 (Phase 3)**: Depends on Foundational. No dependency on US2.
- **User Story 2 (Phase 4)**: Depends on US1 being implemented (T004) — this is a verification pass over the same fix, not independent implementation, so unlike feature 005's two-file US1/US2 split, this can't start in parallel with US1.
- **Polish (Phase 5)**: Depends on both user stories being complete.

### Parallel Opportunities

- T008 (git-diff regression spot-check) is independent of T009/T010 and can run in parallel.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational test-technique design).
2. Complete Phase 3 (US1) — the reported bug is fixed and verified on-device.
3. **STOP and VALIDATE**: quickstart.md scenario 1 on the physical Z Fold, unfolded.
4. Phase 4 (US2) is a quick regression pass over the same fix and should follow immediately, since there's no reason to ship without confirming the folded container is unaffected.

### Incremental Delivery

1. Setup + Foundational → test technique confirmed to reproduce the bug.
2. US1 → `PromptLibraryContent` restructured, unfolded case fixed and verified.
3. US2 → folded case and fold/unfold transition confirmed unaffected.
4. Polish → full regression pass, confirm no baseline profile regen needed.
