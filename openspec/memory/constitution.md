<!--
Sync Impact Report
==================
Version change: 1.1.0 → 2.0.0
Rationale: Sentry was removed from the app entirely (SDK, Gradle plugin,
mapping upload, manifest auto-init, and `FluxApp.initSentry`). Principle I's
carve-out for it is now dead text, and leaving it standing would license
re-adding crash reporting without a further amendment. Removing a standing
exemption from a NON-NEGOTIABLE principle narrows what the principle permits,
so this is a MAJOR bump: a plan that previously passed the Constitution Check
by citing the Sentry carve-out would now fail it.

Modified principles:
  - I. Privacy & Security by Default (crash-reporting exemption removed; the
    prohibition on analytics/trackers/crash reporting is now unconditional)
Added principles: none
Added sections: none
Removed sections: none

Templates requiring updates:
  ✅ .specify/templates/plan-template.md — Constitution Check gate is populated
     dynamically from this file at plan time; no hardcoded principle names to sync.
  ✅ .specify/templates/spec-template.md — generic, no constitution-specific
     references found; no changes needed.
  ✅ .specify/templates/tasks-template.md — generic, no constitution-specific
     references found; no changes needed.
  ⚠ No .specify/templates/commands/*.md directory present in this install; skipped.

Follow-up TODOs: none. Historical references to Sentry in specs/003-* are
records of what was true at the time and are deliberately left unmodified.
-->

# FluxEdit (zun-android-app) Constitution

## Core Principles

### I. Privacy & Security by Default (NON-NEGOTIABLE)

No analytics, no third-party trackers, and no crash reporting — the app
MUST NOT report to any third-party service. API tokens MUST be stored via the
Android Keystore-backed encrypted store (`KeystoreSecureStore`) — never in
plain SharedPreferences, logs, or crash reports. App backups (`allowBackup`)
and Auto Backup MUST remain disabled. Biometric/device-credential lock MUST
gate app access according to the configured lockout window. Release builds
MUST enforce HTTPS; certificate pinning remains an opt-in user control under
Settings → Connection, not a default requirement (plain HTTP is intentionally
permitted for user-picked LAN servers).

**Rationale**: FluxEdit's entire value proposition, as stated in `README.md`,
is being a privacy-first client for a self-hosted server. A feature that
"works" but silently weakens any of these guarantees defeats the app's
reason for existing.

### II. Surgical, Simplicity-First Changes (NON-NEGOTIABLE)

Every changed line MUST trace directly to the task at hand. No speculative
abstractions, no unused configurability, no drive-by refactors of unrelated
code, and no new DI framework or architectural layer introduced without an
explicit constitution amendment. When multiple interpretations of a request
exist, state them and ask rather than picking silently. Match existing style
even when a different style would be preferred.

**Rationale**: Codifies this repo's existing `CLAUDE.md` behavioral
guidelines as a hard planning gate, not just a coding-style suggestion, so
that Spec Kit-driven features don't reintroduce the complexity those
guidelines exist to prevent.

### III. Verify Before Claiming Done

Every bug fix MUST ship with a test that reproduces the bug before the fix
and passes after. Every feature MUST ship with either an automated test or
an explicit manual verification path (e.g., driving the actual flow on an
emulator/device) before being marked complete. Passing type-checks or unit
tests is necessary but not sufficient evidence that a UI-facing change
actually works.

**Rationale**: Matches the Goal-Driven Execution principle already in
`CLAUDE.md`, and reflects this project's own history — the 2026-05
production-readiness audit added instrumented CI tests specifically because
a prior Room migration had shipped without one.

### IV. Offline-Capable by Design

Any feature touching the gallery or result read path MUST degrade
gracefully with no network: reads come from Room first, images resolve
through `OfflineImageCache` before falling back to a server URL, and
uncached content MUST show an explicit "unavailable offline" state rather
than an error or an indefinite spinner.

**Rationale**: This is a documented, load-bearing architectural invariant
(`docs/architecture.md`, "Offline read path"), not an incidental feature.
Regressing it breaks the core promise that "the gallery still loads when
the server is down."

### V. Server Contract Fidelity

`data/api/FluxApi.kt` is the single point of truth for what this app expects
from the server. It MUST stay in sync with
`../zun-rust-server/docs/API_CONTRACT.md`. Any change to a request/response
shape or error code MUST update both sides in the same change set, or the
plan MUST explicitly call out the required follow-up in the sibling
`zun-rust-server` repo. `zun-rust-server` in turn drives a second sibling
repo, `zun-flux-pipeline` (ComfyUI + FLUX inference), over HTTP; changes
that touch that boundary (e.g. workflow JSON contracts) MUST call out the
required follow-up in `zun-flux-pipeline` the same way. Any code change made
in `zun-rust-server` or `zun-flux-pipeline` on behalf of this app MUST land
on that repo's own `dev` branch, never directly on `main`, matching this
repo's branching practice.

**Rationale**: The client and server are developed together but live in
separate git histories with no shared CI. Without an explicit rule, the
contract drifts silently until a runtime 4xx/5xx surfaces it in the field.
`zun-flux-pipeline` is one hop further downstream and easy to forget about
precisely because this repo never imports its code directly.

### VI. Development/Production Environment Isolation

Debug builds MUST default to, and remain restricted to, a local development
server — never the production server — so that development activity can
never silently reach production. The production server address is a known,
specific value (not "any remote host"); debug builds MUST refuse to save
that exact address rather than gate it behind a warning, and this
restriction MUST NOT apply to the release build, which continues to reach
production exactly as before. The local development stack backing debug
builds is `zun-rust-server` plus the `zun-flux-pipeline` inference process
it depends on, both normally run together on the developer's Ubuntu Linux
GPU workstation — the machine used for the large majority of this project's
development work.

**Rationale**: This app is developed against the same production server
(`zun.h.doremysweet.com`) that a real device may be relying on. Without a
hard default and a hard block, a debug build is one typo away from mixing
test traffic into production data. This was reified as a standing principle
rather than a one-off feature after feature 003 (debug-server-isolation)
surfaced that it had no durable rule to point back to.

## Technology & Architecture Constraints

Single-module Android app, Kotlin + Jetpack Compose, Material 3 (Material 3
Expressive was evaluated on a spike branch and explicitly rejected — do not
reintroduce it without a fresh, explicit decision). No dependency-injection
framework: wiring stays manual through `FluxApp` and the narrow
`Repositories` bundle threaded into `AppNavHost`. Exact SDK/toolchain
versions live in `gradle.properties` / `gradle/libs.versions.toml` and
`docs/build.md`, which remain the source of truth — this constitution does
not duplicate them and MUST NOT be treated as overriding them.

The local development server ecosystem this app talks to in debug builds —
`zun-rust-server` (job orchestration/API) and `zun-flux-pipeline` (ComfyUI +
FLUX inference, GPU-bound) — are sibling repos with their own toolchains and
their own `dev`/`main` branch pairs; this repo does not vendor or duplicate
their build tooling.

## Quality Gates

CI MUST pass before merge, including `connectedDebugAndroidTest` on an API
36 emulator. Automated bot/code-review comments on a PR MUST be addressed —
either fixed or explicitly dismissed with stated reasoning — before merge,
consistent with this repo's existing practice. Baseline profiles MUST be
regenerated when a change touches a startup-path or benchmark-covered code
path.

## Governance

This constitution supersedes ad hoc practice for planning and reviewing
features built via the Spec Kit workflow (`/speckit-*` commands).
`CLAUDE.md` remains the authoritative day-to-day behavioral guide for the
coding agent; where the two conflict, this constitution's Core Principles
govern feature-level planning decisions, and `CLAUDE.md` governs
moment-to-moment coding style. The two documents MUST be kept mutually
consistent — a change to one that contradicts the other MUST be resolved by
amending one of them, not left standing.

Amendments require: updating this file, recording a Sync Impact Report as
an HTML comment at the top of the diff, and a semantic version bump —
MAJOR for backward-incompatible principle removal or redefinition, MINOR
for a new principle or materially expanded guidance, PATCH for wording or
typo clarifications. Every plan produced by `/speckit-plan` MUST include a
Constitution Check gate; violations MUST be justified in that plan's
Complexity Tracking table, or the plan MUST be revised to comply.

**Version**: 2.0.0 | **Ratified**: 2026-07-03 | **Last Amended**: 2026-08-18
