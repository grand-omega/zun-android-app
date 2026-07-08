# Phase 1 Data Model: Prompt Polish Assist

## No new persisted entities

Per the spec's Assumptions, nothing about this feature is saved to Room, `SharedPreferences`, or
the server's database. Everything below is either in-memory client state or a request/response
shape passed through in a single HTTP call.

## Client-side composer state (in-memory only, `HomeViewModel`)

- **Polish request state**: one of `Idle`, `InProgress`, or `Failed(message)` — mirrors the
  existing pattern this codebase already uses for other async composer actions. Drives FR-004's
  loading indicator and FR-005's failure messaging.
- **Pre-polish text**: the `customPromptText` value immediately before the most recent successful
  polish, held only for as long as the composer session is active. This is what FR-003's revert
  restores. Cleared whenever the composer itself resets (e.g., after submitting, per the existing
  `customPromptText = ""` reset already in `HomeViewModel`).
- **Stale-response guard**: the in-flight request is tagged (e.g. with the `customPromptText`
  value it was launched for, or a monotonically increasing request id); if the text has changed by
  the time a response arrives, the response is discarded rather than overwriting the user's newer
  edits (spec Edge Cases).

None of this is new *architecture* — it's the same shape as other in-flight-action state already
tracked elsewhere in `HomeViewModel` (e.g. upload progress), just for this new action.

## Request/response shape (client ↔ `zun-rust-server`)

- **Polish request**: the rough prompt text the user typed. No other fields — no user id, no
  history, nothing else needed for a stateless rewrite.
- **Polish response (success)**: the rewritten, structured prompt text.
- **Polish response (failure)**: a uniform error shape, matching this API's existing
  `{ "error": "...", "code": "..." }` convention (`API_CONTRACT.md`), so the client's existing
  error-handling patterns apply unchanged.

Full HTTP-level shape (paths, status codes) is in `contracts/`, not duplicated here.

## Server-side (informational only — implemented in `zun-rust-server`, not this repo)

- **Configured polish LLM endpoint**: a single config value (a URL), analogous to the existing
  `comfy_url` config entry — not a new "entity" in this app's data model, just noted here for
  completeness since research.md Decision 1 depends on it existing.
