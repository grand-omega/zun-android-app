# Implementation Plan: Fix Custom Prompt Field Rendering When Unfolded

**Branch**: `006-fix-custom-prompt-unfolded` | **Date**: 2026-07-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-fix-custom-prompt-unfolded/spec.md`

## Summary

`PromptLibraryContent` (`PromptSheets.kt`) is shared between the folded-phone
modal bottom sheet (near-full-screen height) and, since commit `239b8cf`, an
embedded wide-screen pane on unfolded Home that shares its column with a
fixed-height composer below it. Its header elements (heading, try-harder
toggle, search field, and the expandable "Write your own…" custom-prompt
item) sit above the one scrollable `LazyColumn` with no scroll container of
their own — fine when there's near-full-screen headroom (folded/sheet), but
able to overflow/overlap once the custom field expands in the tighter
wide/embedded pane. Fix: make the whole content — header rows and prompt list
alike — one continuous scrollable `LazyColumn`, so there's no fixed-vs-scroll
split to overflow in either container.

## Technical Context

**Language/Version**: Kotlin, Jetpack Compose (Material 3)

**Primary Dependencies**: `androidx.compose.foundation.lazy.LazyColumn` (already used); no new dependencies

**Storage**: N/A — no data model change (see `data-model.md`)

**Testing**: JVM unit tests (`app/src/test`, Robolectric where Compose rendering is involved) per existing repo conventions

**Target Platform**: Android; bug is specific to the wide/unfolded window size class (`currentWindowAdaptiveInfo().windowSizeClass` in `HomeRoute.kt`), reproduced conceptually via git-history/code analysis on a Galaxy Z Fold 7 — direct pixel confirmation is blocked by this app's own `FLAG_SECURE` (see `research.md`)

**Project Type**: Single-module Android app (mobile-app)

**Performance Goals**: N/A — layout correctness fix, no performance target

**Constraints**: Fix must apply identically to both containers `PromptLibraryContent` is used in (confirmed to be exactly two — the modal sheet and the wide pane); must not change folded-mode behavior, which is already correct

**Scale/Scope**: 1 file touched (`PromptSheets.kt`), restructuring one composable's internal layout from Column+LazyColumn to a single LazyColumn

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Privacy & Security by Default | PASS | No change to token storage, `FLAG_SECURE`, biometric lock, or HTTPS enforcement — layout-only fix. Notably, `FLAG_SECURE` is *why* this plan can't include a screenshot as evidence (see research.md) — that's an accepted limitation, not a violation. |
| II. Surgical, Simplicity-First Changes | PASS | Collapses two adjacent scroll regions (implicit fixed header + explicit `LazyColumn`) into one `LazyColumn` — a structural simplification of existing code, not a new abstraction. Single file touched. |
| III. Verify Before Claiming Done | **Needs attention in tasks** | This is a bug fix, so a reproducing-then-passing test is required in principle. Since pixel-level screenshot verification is structurally blocked by `FLAG_SECURE` (not just inconvenient — actually impossible, for anyone), `/speckit-tasks` should design a layout-*bounds* test (semantics-tree measurements under a constrained-height harness, not a screenshot) that can still objectively prove the overflow/overlap bug and its fix, per the pattern quickstart.md describes. Manual on-device confirmation (by the user, who has real eyes on the real screen) remains the visual-truth check. |
| IV. Offline-Capable by Design | N/A | Doesn't touch the gallery/result read path. |
| V. Server Contract Fidelity | N/A | No API/server-side change. |
| VI. Development/Production Environment Isolation | N/A | No server-address handling change. |
| Quality Gates — baseline profile | Needs re-check in tasks | The baseline profile generator's scripted journey (established in feature 004) covers cold start + Gallery grid only, not Home's prompt picker — tasks should re-confirm this still holds, since unlike Setup/Settings (feature 005), Home *is* on the critical cold-start path even if the prompt sheet specifically isn't opened by the script. |

## Project Structure

### Documentation (this feature)

```text
specs/006-fix-custom-prompt-unfolded/
├── plan.md              # This file
├── research.md          # Phase 0 output — root cause + scope + fix approach
├── data-model.md         # Phase 1 output — no new entities
├── quickstart.md         # Phase 1 output — automated + manual validation
└── tasks.md              # Phase 2 output (/speckit-tasks — not yet generated)
```

No `contracts/` — this feature exposes no interface; it's a same-file
Compose layout restructuring internal to `PromptLibraryContent`.

### Source Code (repository root)

```text
app/src/main/java/dev/zun/flux/ui/home/
└── PromptSheets.kt   # Fix: PromptLibraryContent's Column+LazyColumn split
                      # becomes one continuous scrollable LazyColumn
```

**Structure Decision**: Single-module Android app (existing `app/` module, no
new modules/packages). The edit stays within the existing `ui/home/`
package, touching one existing file; no new production files beyond
whatever test file `/speckit-tasks` adds for Principle III.

## Complexity Tracking

*No violations — Constitution Check is all PASS/N/A/needs-tasks-attention, no entries required.*
