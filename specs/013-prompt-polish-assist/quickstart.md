# Quickstart: Validating Prompt Polish Assist

## Prerequisites

- Android-side work in this repo can be fully validated with a fake/mocked response for
  `POST /api/v1/prompts/polish` (contracts/prompt-polish-endpoint.md) — it does not require the
  real `zun-rust-server` endpoint to exist yet (that's a separate, cross-repo prerequisite; see
  plan.md's Complexity Tracking).
- For a real end-to-end check once the server endpoint exists: a debug build pointed at a local
  `zun-rust-server` that has `polish_llm_url` configured to a reachable, OpenAI-compatible
  self-hosted chat-completions endpoint.

## Automated checks

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew spotlessCheck
```

Per Constitution Principle III, this feature should add:
- A `HomeViewModel` unit test confirming: tapping polish on non-empty text moves through
  loading → replaces `customPromptText` with the (faked) rewritten result; a subsequent revert
  restores the exact pre-polish text; a failed/errored response leaves `customPromptText`
  completely untouched and doesn't block submission; a response arriving after the user has
  already changed the text is discarded, not applied.
- A test confirming the polish action is unavailable when the custom prompt field is empty
  (FR-006).

## Manual validation

1. **Rough idea to polished prompt (Story 1 / FR-001-002)**
   Type a short, informal idea into the custom prompt field (e.g. "cat but cooler"). Tap polish.
   Confirm a loading state shows briefly, then the field's text is replaced with a longer,
   structured rewrite.

2. **Revert (FR-003)**
   After a polish, tap the revert action. Confirm the field goes back to exactly what was typed
   before polishing — not empty, not some other intermediate state.

3. **Failure doesn't block submission (FR-005)**
   With the configured server-side LLM endpoint unreachable (or the whole server offline), tap
   polish. Confirm the original text is untouched and a submit is still possible with that
   original, unpolished text.

4. **Self-hosted only, no third-party fallback (FR-008)**
   Confirm — by reading the implemented request path, since this isn't something to click through
   — that there is no code path in the client or the (future) server endpoint that would ever
   reach a third-party AI API, including as a fallback when the configured self-hosted endpoint is
   down. An unreachable self-hosted endpoint must surface as the `not_ready` failure case
   (contracts/prompt-polish-endpoint.md), never a silent retry elsewhere.
