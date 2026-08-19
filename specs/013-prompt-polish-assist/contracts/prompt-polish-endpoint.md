# Contract: `POST /api/v1/prompts/polish`

**Status**: Proposed — not yet implemented. This is a cross-repo dependency on `zun-rust-server`
(see plan.md's Complexity Tracking); this document is the contract the Android client (this repo)
is built against, and what `zun-rust-server`'s own implementation and `docs/API_CONTRACT.md` entry
should match. Follows this API's existing conventions (`docs/API_CONTRACT.md`'s "Conventions"
section: JSON in/out, uniform error shape, bearer-token auth on all non-health endpoints).

## Request

```json
{
  "text": "a cat but make it look cooler"
}
```

- `text`: the user's rough, as-typed custom prompt. Required, non-empty after trim.
- No other fields — this is a stateless rewrite; nothing else about the user/session is needed.

## Response — `200`

```json
{
  "text": "A confident, stylish cat rendered in a bold, high-contrast portrait, dynamic pose, dramatic lighting."
}
```

- `text`: the rewritten, structured prompt, following whichever reference style the server has
  been configured with (research.md's "Open item" — the exact template content lives entirely on
  the server side, e.g. baked into its system prompt; the client never sees or supplies it).

## Response — failure

Uses this API's existing uniform error shape (`docs/API_CONTRACT.md`):

```json
{ "error": "<message>", "code": "<machine_code>" }
```

Expected codes for this endpoint:
- `bad_request` (400) — `text` missing or empty after trim.
- `not_ready` (409) — the configured self-hosted LLM endpoint is unreachable or timed out. This is
  the expected "polishing isn't available right now" case (spec FR-005) — the client treats this
  as a normal, recoverable failure, not an exceptional error.
- `internal` (500) — the configured endpoint responded, but the response couldn't be parsed into a
  usable rewrite (e.g., truncated with no usable `content` — see research.md Decision 2).

## Non-goals for this endpoint

- No streaming — the client waits for one complete rewrite, matching the "one tap, one result"
  framing (spec Assumptions: no regenerate-loop/rating in scope).
- No per-user history or logging of prompt text beyond this API's existing request handling —
  nothing new is persisted (data-model.md).
- Never proxies to a third-party AI service, under any circumstance (FR-008) — if the configured
  self-hosted endpoint is down, the correct response is `not_ready`, never a silent fallback
  elsewhere.
