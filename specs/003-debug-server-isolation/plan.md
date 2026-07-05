# Implementation Plan: Debug Build Server Isolation

**Branch**: `003-debug-server-isolation` | **Date**: 2026-07-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-debug-server-isolation/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Debug builds must default to a local development server address instead of starting blank, and must hard-reject any attempt to save the exact known production hostname (`zun.h.doremysweet.com`) as their server URL; release builds are completely unaffected. Technical approach: extend the single existing URL-validation choke point (`ServerUrls.normalizeOptionalServerUrl`) with an optional host-block parameter supplied only by debug-build call sites, and pre-fill the Setup screen's server-URL field from a new debug-only `BuildConfig.DEFAULT_SERVER_URL` sourced from `local.properties` (falling back to the Android emulator's host-loopback alias). No server-side (`zun-rust-server`, `zun-flux-pipeline`) changes are required.

## Technical Context

**Language/Version**: Kotlin 2.4.0, JVM target 17

**Primary Dependencies**: Jetpack Compose (existing UI), no new dependencies added

**Storage**: `SharedPreferences` via existing `SettingsManager` (`serverUrl` key) — no schema change

**Testing**: JUnit unit tests (`app/src/test/java/dev/zun/flux/util/ServerUrlsTest.kt`) for the validation logic; manual verification (`quickstart.md`) for the Setup-screen prefill and end-to-end save/reject flow, since no Compose/ViewModel test scaffolding exists yet for this screen

**Target Platform**: Android (minSdk 36, targetSdk 36, compileSdk 37); single `dev.zun.flux` app module, `debug`/`release` build types (no product flavors)

**Project Type**: mobile-app (single Android module)

**Performance Goals**: N/A — validation-only logic, no measurable performance target beyond existing UI responsiveness

**Constraints**: Release-build behavior must not change at all (FR-005); no new Gradle product flavor, DI framework, or abstraction (Principle II); no `zun-rust-server`/`zun-flux-pipeline` code changes required

**Scale/Scope**: One validation function extended, one new build-type-scoped `buildConfigField`, two existing UI call sites (`SetupScreen.kt`, `SettingsViewModel.kt`) — no new screens or modules

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|---|---|---|
| I. Privacy & Security by Default | Production hostname is a public value, not a secret; the debug default local address is sourced via `local.properties` (gitignored), matching the existing pattern for keystore/Sentry machine-specific values, so no developer-specific network detail is committed to git. | **PASS** |
| II. Surgical, Simplicity-First | No new module, flavor, or DI framework. The block rule is added once, to the single existing validation choke point (`ServerUrls.kt`), rather than duplicated across `SetupScreen.kt` and `SettingsViewModel.kt`. Existing error-surfacing paths are reused as-is. | **PASS** |
| III. Verify Before Claiming Done | New unit tests extend `ServerUrlsTest.kt` covering the reject rule (the core, fully unit-testable logic). The Setup-screen prefill and end-to-end flow are verified manually per `quickstart.md`, since no existing Compose/ViewModel test scaffolding covers this screen — an explicit, documented manual path, as the principle allows. | **PASS** |
| IV. Offline-Capable by Design | Not applicable — feature doesn't touch the gallery or result read path. | **N/A** |
| V. Server Contract Fidelity | Confirmed during research: this feature is a pure client-side hostname comparison. No `FluxApi.kt`/`API_CONTRACT.md` shape changes and no `zun-rust-server` or `zun-flux-pipeline` code changes are needed. | **PASS** |
| VI. Development/Production Environment Isolation | This feature is the direct implementation of this principle: debug defaults to and is restricted to a local server, release is untouched. | **PASS** |

Also flag for Quality Gates: confirm `SetupScreen.kt`/`SettingsViewModel.kt` are not part of the generated baseline profile's covered critical user journey before merging; regenerate the baseline profile if they are.

No violations — Complexity Tracking table below is empty.

## Project Structure

### Documentation (this feature)

```text
specs/003-debug-server-isolation/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

No `contracts/` directory: this feature has no external interface (no new/changed API endpoints, CLI, or public library surface) — it's internal client-side validation logic, so contract docs don't apply.

### Source Code (repository root)

```text
app/                                          # single Android module, existing
├── build.gradle.kts                          # add per-build-type buildConfigField DEFAULT_SERVER_URL
├── src/main/java/dev/zun/flux/
│   ├── util/ServerUrls.kt                    # add blockHost param + production-hostname reject check
│   ├── data/repo/SettingsManager.kt          # unchanged (bare passthrough)
│   └── ui/settings/
│       ├── SetupScreen.kt                    # prefill field from BuildConfig.DEFAULT_SERVER_URL; pass blockHost when BuildConfig.DEBUG
│       ├── SettingsScreen.kt                 # unchanged (renders existing error state)
│       └── SettingsViewModel.kt              # pass blockHost when BuildConfig.DEBUG
├── src/debug/                                 # existing debug-only source set (network_security_config, strings, easylauncher) — no changes needed
└── src/test/java/dev/zun/flux/util/
    └── ServerUrlsTest.kt                      # extend with production-hostname reject cases
```

**Structure Decision**: Single existing Android module (`app/`), no new module/flavor. All changes are confined to the existing validation function and its two existing call sites, plus one new `buildConfigField`. This matches the "Surgical, Simplicity-First" constitution gate — no new project structure is introduced.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations — table intentionally left empty.
