# Implementation Plan: Fix Setup Screen Keyboard Layout Squish

**Branch**: `005-fix-setup-keyboard-layout` | **Date**: 2026-07-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-fix-setup-keyboard-layout/spec.md`

## Summary

On the Setup screen (and the identical-layout Settings credentials screen), the on-screen
keyboard's inset is applied twice — once via `Scaffold(contentWindowInsets =
WindowInsets.safeDrawing)` (which already includes `ime`) and again via the inner
`Column`'s `.imePadding()` — squeezing all content into roughly half the space it should
have. Confirmed by code review and by reproducing on the reporter's actual Z Fold 7
(`research.md`). Fix: exclude `ime` from the `Scaffold`'s insets on both screens so
`.imePadding()` remains the single handler of the keyboard inset.

## Technical Context

**Language/Version**: Kotlin, Jetpack Compose (Material 3)

**Primary Dependencies**: `androidx.compose.foundation.layout.WindowInsets`,
`androidx.compose.material3.Scaffold` — no new dependencies

**Storage**: N/A — no data model change (see `data-model.md`)

**Testing**: JVM unit tests (`app/src/test`, Robolectric where Compose rendering is
involved) + instrumented tests (`app/src/androidTest`) per existing repo conventions

**Target Platform**: Android (min/target SDK per `gradle/libs.versions.toml`); bug is
sharpest on tall/narrow aspect ratios (confirmed on a folded Galaxy Z Fold 7) where the
keyboard consumes a larger fraction of window height, but the same double-counting exists
on every device

**Project Type**: Single-module Android app (mobile-app)

**Performance Goals**: N/A — layout correctness fix, no performance target

**Constraints**: Fix must not alter behavior on screens that don't share the bug (see
`research.md`, "Scope of the fix"); must not touch `AndroidManifest.xml`
`windowSoftInputMode` (on-device evidence shows the OS-level window is already correctly
un-resized — see `research.md`)

**Scale/Scope**: 2 files touched (`SetupScreen.kt`, `SettingsScreen.kt`), one modifier
chain edit each

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Privacy & Security by Default | PASS | No change to token storage, `FLAG_SECURE`, biometric lock, or HTTPS enforcement — layout-only fix. |
| II. Surgical, Simplicity-First Changes | PASS | One modifier-chain edit per affected screen (`WindowInsets.safeDrawing.exclude(WindowInsets.ime)`); no new abstraction, no restructuring of the existing scroll/padding pattern. |
| III. Verify Before Claiming Done | **Needs attention in tasks** | This is a bug fix, so a reproducing-then-passing test is required in principle. Faking a real IME inset in a headless JVM/Robolectric test is nontrivial (no real keyboard); `/speckit-tasks` should first attempt an automated regression test that dispatches a synthetic IME `WindowInsets` and asserts measured content height differs pre/post-fix, falling back to the manual on-device verification path in `quickstart.md` (already proven reproducible — see `research.md`) if that proves impractical. Either path is disclosed, not silently skipped. |
| IV. Offline-Capable by Design | N/A | Doesn't touch the gallery/result read path. |
| V. Server Contract Fidelity | N/A | No API/server-side change. |
| VI. Development/Production Environment Isolation | N/A | No server-address handling change. |
| Quality Gates — baseline profile | PASS (no regen needed) | Neither `SetupScreen`/`SettingsScreen` is on the baseline profile generator's exercised path (cold start + Gallery grid only — established in feature 004); confirm this still holds at Polish time. |

## Project Structure

### Documentation (this feature)

```text
specs/005-fix-setup-keyboard-layout/
├── plan.md              # This file
├── research.md          # Phase 0 output — root cause + scope + fix approach
├── data-model.md         # Phase 1 output — no new entities
├── quickstart.md         # Phase 1 output — automated + manual validation
└── tasks.md              # Phase 2 output (/speckit-tasks — not yet generated)
```

No `contracts/` — this feature exposes no interface (no new API, no new public
composable surface consumed outside this app); it's a same-file modifier-chain fix on two
existing internal screens.

### Source Code (repository root)

```text
app/src/main/java/dev/zun/flux/ui/settings/
├── SetupScreen.kt        # Fix: Scaffold's contentWindowInsets excludes ime
└── SettingsScreen.kt     # Same fix — identical root cause (research.md)
```

**Structure Decision**: Single-module Android app (existing `app/` module, no new
modules/packages). Both edits stay within the existing `ui/settings/` package; no new
files beyond whatever test file `/speckit-tasks` decides to add for Principle III.

## Complexity Tracking

*No violations — Constitution Check is all PASS/N/A, no entries required.*
