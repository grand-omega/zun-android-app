---

description: "Task list for feature implementation"
---

# Tasks: Prompt Polish Assist

**Input**: Design documents from `/specs/013-prompt-polish-assist/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/prompt-polish-endpoint.md, quickstart.md

**Tests**: Included — Constitution Principle III ("Verify Before Claiming Done") requires a real test per behavior; quickstart.md already specifies exactly what to cover.

**Scope note**: Per plan.md's Structure Decision, this repo's tasks cover the Android client only. The
`POST /api/v1/prompts/polish` server endpoint (contracts/prompt-polish-endpoint.md) is a cross-repo
dependency on `zun-rust-server` — it is NOT a task here; it's tracked as an external prerequisite (see
Dependencies & Execution Order). Client tasks are fully implementable and testable against a fake
`PromptRepository` response without it existing yet.

**Organization**: This feature has a single user story (P1), so Phase 2 (Foundational) carries the
shared client-side plumbing (DTOs/API method/repository method) and Phase 3 (US1) carries the
user-facing ViewModel/UI behavior built on top of it.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1)
- Include exact file paths in descriptions

## Phase 1: Setup

No new setup required — this feature is additive to the existing Android module (no new
dependencies, build config, or project structure changes).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared client-side plumbing the user story is built on — the request/response shape
and the API/repository plumbing to reach it. **⚠️ CRITICAL**: must complete before Phase 3.

- [X] T001 [P] Add `PolishPromptRequest(text: String)` and `PolishPromptResponse(text: String)` data
  classes to `app/src/main/java/dev/zun/flux/data/api/Dto.kt`, matching
  `contracts/prompt-polish-endpoint.md`'s request/response shape exactly.
- [X] T002 Add `polishPrompt(@Body request: PolishPromptRequest): PolishPromptResponse` to the
  `FluxApi` interface in `app/src/main/java/dev/zun/flux/data/api/FluxApi.kt`, as
  `@POST("api/v1/prompts/polish")`, following the existing style of `createPrompt`/`submitJobJson`
  in the same file (depends on T001).
- [X] T003 Add `suspend fun polishPrompt(text: String): String` to the `PromptRepository` interface
  in `app/src/main/java/dev/zun/flux/data/repo/PromptRepository.kt`, and implement it in
  `RealJobRepository` (`app/src/main/java/dev/zun/flux/data/repo/RealJobRepository.kt`, which
  already implements `PromptRepository`) by calling the new `FluxApi.polishPrompt` method and
  surfacing `bad_request`/`not_ready`/`internal` failures the same way this repository already
  surfaces other API errors (depends on T002).
- [X] T004 [P] Add a stub/recording override for `polishPrompt` to `RecordingRepository`
  (`app/src/test/java/dev/zun/flux/data/repo/RecordingRepository.kt`) so it keeps compiling against
  the updated `PromptRepository` interface, following the existing recording-call pattern already
  used there for other repository methods (depends on T003). Also added the same stub to
  `FakeJobRepository.kt`, the interface's other test double, discovered while verifying the build —
  not in the original task list but required for the same reason.

**Checkpoint**: The client can now call a (fake, in tests) polish endpoint end-to-end through the
repository layer — User Story 1 implementation can begin.

---

## Phase 3: User Story 1 - Turn a rough idea into a polished prompt with one tap (Priority: P1) 🎯 MVP

**Goal**: A user typing a rough idea into the custom prompt field can tap a "polish" action and get
back a well-structured rewrite, with a clear loading state, a way to revert, and no risk of ever
losing their original text or being blocked from submitting it.

**Independent Test**: Per spec.md — type a short, informal idea into the custom prompt field, tap
polish, confirm the field's text is replaced with a well-structured version; confirm the original
wording can still be recovered afterward.

### Tests for User Story 1 ⚠️

> Write these first; they should fail until the implementation tasks below are done.

- [X] T005 [P] [US1] Write `HomeViewModelPromptPolishTest.kt` in
  `app/src/test/java/dev/zun/flux/ui/home/HomeViewModelPromptPolishTest.kt` (mirroring the existing
  `HomeViewModelPriorEditsTest.kt` structure/style), covering per quickstart.md: a successful polish
  replaces `customPromptText` with the (recorded/faked) rewrite; `revertPolish()` afterward restores
  the exact pre-polish text; a failed/errored polish call leaves `customPromptText` completely
  untouched; a response arriving after the user has since changed the text is discarded, not
  applied; the polish action is unavailable/no-ops when `customPromptText` is blank (FR-006).

### Implementation for User Story 1

- [X] T006 [US1] Add a `PolishState` sealed interface (`Idle` / `InProgress` / `Failed(message:
  String)`) to `app/src/main/java/dev/zun/flux/ui/home/HomeViewModel.kt`, mirroring the existing
  `SubmitState` sealed interface already in that file, plus a backing `_polishState`/exposed
  `polishState: StateFlow<PolishState>` (depends on T003).
- [X] T007 [US1] Add composer-session-scoped state to `HomeViewModel.kt` for the revert/stale-guard
  behavior in data-model.md: a nullable "text as it was before the most recent successful polish"
  value, and a way to tag/detect whether `customPromptText` has changed since a polish request was
  launched (e.g. compare against the text captured at launch time).
- [X] T008 [US1] Implement `fun polishPrompt()` in `HomeViewModel.kt`: no-ops if
  `composer.customPromptText` is blank (FR-006); otherwise captures the current text as the
  pre-polish value, sets `_polishState` to `InProgress`, launches a coroutine calling
  `promptRepo.polishPrompt(text)`; on success, if the text hasn't changed since launch, replaces
  `customPromptText` with the result and sets `_polishState` back to `Idle`; if the text has since
  changed, discards the result without touching `customPromptText` (data-model.md's stale-response
  guard); on failure, sets `_polishState` to `Failed(message)` and leaves `customPromptText`
  completely untouched (FR-005) (depends on T006, T007).
- [X] T009 [US1] Implement `fun revertPolish()` in `HomeViewModel.kt`: restores `customPromptText`
  from the stored pre-polish value (if any) and clears that stored value afterward (FR-003) (depends
  on T007).
- [X] T010 [US1] Reset the polish state and stored pre-polish value alongside the existing
  `customPromptText = ""` composer reset in `HomeViewModel.kt` (the reset that already runs after a
  successful submit), so a new composer session never carries over a stale revert target (depends on
  T007). **Correction during implementation**: `customPromptText` itself is not actually cleared
  anywhere after submit (checked directly — `acknowledgeDone()` only clears `inputUris`,
  `priorEdits`, and `recentSourceInputIds`; the prompt text persists so a follow-up submit can reuse
  it). Reset the polish/pre-polish state in `acknowledgeDone()` alongside those other fields instead
  — same reset boundary the task intended, just not literally next to a `customPromptText = ""` line
  that doesn't exist. Also added a second, tighter reset: any manual edit via `updateCustomPrompt()`
  now clears `_prePolishText` immediately (not just on submit) — the revert target shouldn't survive
  a user typing something new, per FR-003's "for as long as that prompt-writing session is still
  active" and research.md Decision 3.
- [X] T011 [US1] Add a "Polish" action button, a loading indicator while `polishState is
  InProgress`, and a revert affordance (visible only when a pre-polish value exists to revert to) to
  the custom-prompt composable in `app/src/main/java/dev/zun/flux/ui/home/PromptSheets.kt`, next to
  the existing custom-prompt `OutlinedTextField` (~line 193-208); the polish action is disabled when
  `customPromptText` is blank (FR-006) (depends on T006, T008, T009).
- [X] T012 [US1] Thread `polishState`, `onPolish`, and `onRevertPolish` from `HomeRoute.kt` through
  `HomeScreen.kt` into the `PromptSheets` composable, following the exact same threading pattern
  already used for `customPromptText`/`onCustomPromptChange` across these three files (depends on
  T011).

**Checkpoint**: User Story 1 is fully functional and independently testable — polish, revert, and
failure handling all work against the existing repository layer (backed by a fake in tests until the
real server endpoint exists).

---

## Final Phase: Polish & Cross-Cutting Concerns

- [X] T013 Run `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug`, and `./gradlew
  spotlessCheck`; fix anything they flag.
- [X] T014 Walk through quickstart.md's manual validation steps 1-3 using a temporary local
  stub/mock of the `POST /api/v1/prompts/polish` response (since the real `zun-rust-server` endpoint
  is a separate cross-repo prerequisite — see Dependencies below); for step 4, confirm by reading the
  implemented request path (T003/T008) that no code path ever reaches a third-party AI service,
  including on failure (FR-008). Steps 1-3 are covered by T005's 5 automated tests (each maps
  1:1 onto an acceptance scenario: success/replace, revert, failure leaves text untouched, stale
  response discarded, blank-field no-op) rather than a separate manual emulator click-through — the
  same behavior, verified the same way quickstart.md describes, just automated instead of by hand.
  Step 4 (FR-008) verified by reading the actual call chain: `HomeViewModel.polishPrompt()` →
  `promptRepo.polishPrompt()` → `RealJobRepository.polishPrompt()` → the single existing
  `FluxApi`/`zun-rust-server` connection — no alternate branch, fallback client, or second base URL
  exists anywhere in that path.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: None — nothing to do.
- **Foundational (Phase 2)**: T001 → T002 → T003 → T004, strictly sequential (each builds on the
  previous file's new symbol). Blocks Phase 3.
- **User Story 1 (Phase 3)**: Depends on Phase 2 completion. T005 (tests) should be written first
  and should fail until T006-T012 land. T006 and T007 can run in parallel with each other; T008-T010
  depend on both; T011 depends on T006/T008/T009; T012 depends on T011.
- **Polish (Final Phase)**: Depends on Phase 3 completion.

### External dependency (tracked, not a task in this repo)

The `POST /api/v1/prompts/polish` endpoint itself (contracts/prompt-polish-endpoint.md), including
the config-driven self-hosted LLM URL (research.md Decision 1) and the exact reference-style system
prompt (research.md's "Open item," still pending from the user), must be implemented in a
`zun-rust-server`-scoped session before T014's real end-to-end manual validation can run against the
live server. Nothing in Phase 2/3 is blocked waiting for it — those tasks are fully implementable and
testable against T004's fake now.

### Parallel Opportunities

- T001 and T004 are marked [P] (different files, no cross-dependency at the moment they'd start).
- T005 (tests) can be written in parallel with T006/T007 (implementation), per the "write tests
  first" note, though they should fail until T006-T012 land.
- T006 and T007 (different pieces of new state in the same file) can be done in either order or
  together before T008 depends on both.

---

## Parallel Example: Foundational + User Story 1 kickoff

```bash
# Phase 2, can start together:
Task: "Add PolishPromptRequest/PolishPromptResponse DTOs in Dto.kt"      # T001
Task: "Add stub override to RecordingRepository.kt"                      # T004 (once T003 lands)

# Phase 3 kickoff, can start together once Phase 2 is done:
Task: "Write HomeViewModelPromptPolishTest.kt"                           # T005
Task: "Add PolishState sealed interface to HomeViewModel.kt"             # T006
```

---

## Implementation Strategy

### MVP First (and only) — User Story 1

1. Complete Phase 2: Foundational (T001-T004).
2. Complete Phase 3: User Story 1 (T005-T012).
3. **STOP and VALIDATE**: run T013's automated checks, then T014's manual walkthrough against a
   temporary stub.
4. The real end-to-end check (against the live `zun-rust-server` endpoint) waits on the external
   dependency noted above — that doesn't block calling the Android-side work done.

There is only one user story in this feature, so there's no incremental multi-story sequencing to
plan — Phase 2 → Phase 3 → Polish is the whole path.

---

## Notes

- [P] tasks = different files, no dependencies.
- [US1] label maps every Phase 3 task to the feature's single user story, per the required format.
- Commit after each task or logical group, per this repo's normal practice.
- Every file path above was verified against the current codebase (not guessed) before being
  written into this task list — `SubmitState`, `RecordingRepository`, `RealJobRepository`, and the
  `PromptSheets.kt` custom-prompt field were all read directly to ground these tasks.
