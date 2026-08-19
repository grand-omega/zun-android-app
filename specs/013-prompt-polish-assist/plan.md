# Implementation Plan: Prompt Polish Assist

**Branch**: `013-prompt-polish-assist` | **Date**: 2026-07-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-prompt-polish-assist/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

A "polish" action on the custom ("write your own") prompt field in `HomeViewModel`'s composer
that sends the user's rough `customPromptText` to a new `zun-rust-server` endpoint, which forwards
it to a self-hosted, OpenAI-Chat-Completions-compatible LLM endpoint (config-driven URL — the
user's choice of model/instance, not hardcoded to any specific one) and returns a rewritten,
structured prompt. The Android side replaces the field's text with the result, keeps the original
around for one-tap revert, and never blocks submission if the request fails — matching FR-005/008.
The server-side endpoint itself is a cross-repo dependency (Constitution Principle V) — this plan
defines its contract but implementation lives in a `zun-rust-server`-scoped session, not here.

## Technical Context

**Language/Version**: Kotlin 2.4.0 (Android client, this repo); Rust (server endpoint, sibling
`zun-rust-server` repo — see Complexity Tracking for why this cross-repo split is unavoidable)

**Primary Dependencies (this repo, Android client)**: Retrofit/OkHttp only — the existing
`FluxApi` pattern, calling the one new `zun-rust-server` endpoint (contracts/). The client has no
dependency on, and no awareness of, which LLM is used or how the prompt is processed — that is
entirely `zun-rust-server`'s concern, never the client's.

**Primary Dependencies (informational — sibling `zun-rust-server` repo, not implemented here)**:
`reqwest` calling an OpenAI-compatible `/v1/chat/completions` endpoint, matching the existing
`comfy.rs` HTTP-client pattern — noted here only so this plan's cross-repo dependency is
traceable, not because this repo implements or configures any of it.

**Storage**: None — no new persisted data on either side (see data-model.md)

**Testing**: JUnit4 + Robolectric for the Android composer/ViewModel logic (loading/success/
failure/revert states); a fake `FluxApi` response for the new endpoint, matching this codebase's
existing fake-repository test pattern

**Target Platform**: Android (this repo) + a cross-repo dependency on `zun-rust-server` (Linux)

**Project Type**: mobile-app (single-module Android app) with one new cross-repo API dependency

**Performance Goals**: A polish request should complete in a few seconds at most under normal
conditions. Live-tested against the reference self-hosted setup (a llama.cpp server exposing
`/v1/chat/completions`, already running on the developer's own workstation for unrelated use — see
research.md): a short rewrite completed in ~0.5s end to end. No specific p95 target is set in the
spec itself (FR/SC deliberately technology-agnostic); this is a planning-level sizing note, not a
hard requirement — a generous client-side timeout (research.md) is what actually protects UX.

**Constraints**: MUST NOT ever call a third-party AI service (FR-008, non-negotiable per
Constitution Principle I) — only a self-hosted endpoint the app operator controls. MUST NOT block
prompt submission if unavailable (FR-005). MUST NOT persist the rewrite or the pre-rewrite text
anywhere beyond the current composer session (spec Assumptions).

**Scale/Scope**: One new Android composer action + one new `FluxApi` client method (this repo);
one new `zun-rust-server` endpoint + one new outbound HTTP client to a configurable self-hosted
LLM endpoint (cross-repo dependency, documented here, implemented separately).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Privacy & Security by Default | FR-008 makes self-hosted-only a hard requirement, not just an assumption — no third-party AI service, even as a fallback. No new permissions, no analytics | PASS |
| II. Surgical, Simplicity-First Changes | Reuses the existing custom-prompt composer state (`customPromptText`) and the existing `FluxApi`/Retrofit client pattern; no new screen, no new persisted entity, one new endpoint following the existing Axum routing convention | PASS |
| III. Verify Before Claiming Done | Android-side loading/success/failure/revert states each need a real test; the server endpoint needs its own test suite in its own repo (cross-repo, out of this plan's direct scope but called out explicitly) | PASS (planned in tasks) |
| IV. Offline-Capable by Design | Deliberately out of scope for this feature — Principle IV scopes offline-capability to "the gallery or result read path," not job creation/submission, which already requires connectivity. Polishing degrades to "unavailable, submit as-is" rather than blocking, consistent with FR-005 | PASS (explicitly not offline-capable, and that's fine per the principle's actual scope) |
| V. Server Contract Fidelity | This is the one real gate finding: adding a client call requires a matching server endpoint + `API_CONTRACT.md` update in `zun-rust-server`, which this session cannot implement directly (sibling repo, own session scope). Handled by defining the contract in `contracts/` here and explicitly calling out the required `zun-rust-server` follow-up as a cross-repo dependency, per the principle's own escape hatch ("or the plan MUST explicitly call out the required follow-up in the sibling repo") | PASS (with explicit cross-repo follow-up documented) |
| VI. Development/Production Environment Isolation | Not touched — no debug/release server-address logic in scope | N/A |

No unjustified violations. The one notable item (Principle V's cross-repo split) is documented in
Complexity Tracking below per the principle's own stated escape hatch, not treated as a blocker.

## Project Structure

### Documentation (this feature)

```text
specs/013-prompt-polish-assist/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/            # Phase 1 output (/speckit-plan command) -- new for this feature
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

Unlike features 003/005/006/007/008/009 (purely internal to this single Android app), this
feature has a genuine external interface — a new HTTP endpoint between this app and
`zun-rust-server` — so it gets a `contracts/` directory, following the template's guidance to
include one "if project has external interfaces."

### Source Code (repository root)

```text
app/src/main/java/dev/zun/flux/data/api/
├── FluxApi.kt                    # + polishPrompt(text): PolishPromptResponse (new endpoint call)
└── Dto.kt                        # + PolishPromptRequest/PolishPromptResponse

app/src/main/java/dev/zun/flux/ui/home/
├── HomeViewModel.kt               # + polish state (idle/loading/error) alongside customPromptText;
│                                    + polishPrompt()/revertPolish() actions
└── HomeRoute.kt / HomeScreen.kt    # + "Polish" action UI on the custom-prompt field, loading
                                     indicator, revert affordance

# Cross-repo (documented here, NOT implemented in this repo/session):
../zun-rust-server/src/
├── prompt_polish.rs (new)         # POST /api/v1/prompts/polish handler; calls the configured
│                                    self-hosted chat-completions endpoint
├── config.rs                      # + polish_llm_url (or similar), mirroring comfy_url's pattern
└── docs/API_CONTRACT.md            # + new endpoint documented
```

**Structure Decision**: Single-module Android app (existing structure). The client-side change is
additive to the existing Home composer, no new screen. The server-side endpoint is a documented
cross-repo dependency (see Complexity Tracking) — this plan's `tasks.md` will only cover the
Android-side implementation directly; the server endpoint is a prerequisite tracked as an external
dependency, to be implemented in a `zun-rust-server`-scoped session.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| Cross-repo dependency on an unimplemented `zun-rust-server` endpoint (Principle V) | The rewrite must be self-hosted server-side per FR-008 (non-negotiable, Principle I) — there is no way to satisfy that requirement from client code alone | Bundling an on-device model was considered and explicitly rejected during brainstorming (memory footprint, APK bloat, device fragmentation) before this spec was even written; there is no simpler alternative that keeps the rewrite off-device and self-hosted without a server endpoint |
