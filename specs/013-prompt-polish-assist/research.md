# Research: Prompt Polish Assist

## Decision 1: The server calls a generic, config-driven self-hosted chat-completions endpoint — not a hardcoded specific model

**Decision**: `zun-rust-server` gets a new config value (e.g. `polish_llm_url`, mirroring the
existing `comfy_url` pattern in `config.rs`) pointing at any OpenAI-Chat-Completions-compatible
HTTP endpoint. The new `/api/v1/prompts/polish` handler is a thin proxy: build a chat-completions
request (system prompt + the user's rough text as the user message), call the configured URL,
extract `choices[0].message.content`, return it to the client.

**Rationale**:
- Confirmed live during planning: the developer's own workstation already runs a self-hosted LLM
  server (`llama.cpp`'s server, exposing an OpenAI-compatible API at `/v1/chat/completions` and
  `/v1/models`) for a separate, unrelated purpose. This is exactly the kind of infrastructure FR-008
  requires (self-hosted, operator-controlled) — but it belongs to the developer personally, not to
  this app or `zun-rust-server`. Hardcoding this specific instance/model into the server would
  couple the feature to one person's personal tool rather than a general "bring your own self-hosted
  model" capability. A config value keeps the same self-hosted guarantee (FR-008 is still satisfied
  — it's still never a third-party service) while not assuming which model or instance.
- Matches the existing `comfy_url` precedent exactly (`config.rs`, `default_comfy_url()`) — this
  codebase already has a clean pattern for "a configurable URL to a service this server depends on
  but doesn't run itself."

**Alternatives considered**:
- *Hardcode a specific model/endpoint into the server*: rejected — couples the feature to one
  person's personal, unrelated tool; not portable if that tool changes or the server is deployed
  elsewhere.
- *Bundle a model directly in `zun-rust-server`'s own process (e.g. an embedded inference
  library)*: rejected as unnecessary complexity — the config-driven HTTP approach reuses whatever
  self-hosted LLM serving setup the operator already runs (llama.cpp, vLLM, Ollama, etc. all speak
  this same API shape), rather than this app needing to embed and manage model weights/inference
  itself.

## Decision 2: Treat "reasoning-style" model output as a first-class case in the request contract

**Decision**: The request to the configured LLM MUST allow a generous token budget (research found
a real failure mode with a `max_tokens` too small to be safe as a default — see below), and the
response-parsing contract is specifically "read `choices[0].message.content`; if it's empty while
`finish_reason` indicates the response was cut off, treat this as a failed polish request" (FR-005),
not as a malformed response to error hard on.

**Rationale**: Live-tested against the reference self-hosted endpoint during planning. Some modern
self-hosted models (the one currently running locally is one such example) emit an internal
"reasoning" pass before the actual answer, exposed separately as a `reasoning_content` field
alongside the real answer in `content`. With a small `max_tokens` (30), the response cut off
mid-reasoning with an *empty* `content` field and `finish_reason: "length"` — a real, observed
failure mode, not a hypothetical edge case. With a larger budget (300), the same request completed
correctly in ~0.5s with the actual rewrite in `content`. Since the server endpoint is designed to
work with *whatever* self-hosted model the operator configures (Decision 1), and reasoning-style
models are an increasingly common self-hosted choice, the contract needs to account for this
rather than assuming every configured model answers immediately.

**Alternatives considered**:
- *Assume `content` is always populated correctly with a small, fixed token budget*: rejected —
  this is the exact failure mode reproduced above; would silently break for reasoning-style models.
- *Parse/use `reasoning_content` specifically*: rejected — that field's presence and meaning is
  model-specific (not part of the standard OpenAI chat-completions shape), so relying on it would
  couple the contract to one model family. Reading only the standard `content` field, with a
  generous token allowance, works uniformly across compatible models.

## Decision 3: Client-side revert is a single stored "before" string, not a multi-step undo history

**Decision**: `HomeViewModel` tracks one extra piece of composer state — the text as it was
immediately before the most recent successful polish — and "revert" restores exactly that. Tapping
polish again after a revert (or after further edits) simply starts a new polish/revert cycle from
whatever's currently in the field.

**Rationale**: Spec FR-003 requires reverting to "original wording," and the Assumptions section
settled on this being a single-level safety net, not a full undo stack — matches the "fix for me"
framing (a lightweight, low-ceremony action) the feature was originally described as, not a
document-editing undo system.

**Alternatives considered**:
- *Full undo/redo history across multiple polish taps*: rejected as unnecessary complexity for a
  short prompt text field — Principle II (no speculative configurability beyond what's asked).

## Open item: the exact reference style/template is still pending

The spec (FR-002, Assumptions) deliberately does not pin down the exact structured-prompt style
the rewrite should follow — the user indicated this will be supplied separately. The system prompt
sent to the configured LLM (Decision 1) is where that reference style ultimately gets encoded, but
its exact wording is out of scope for this plan and is called out as a blocking prerequisite for
the `zun-rust-server`-side implementation (see plan.md's Complexity Tracking) — the Android-side
work in this repo (client method, composer UI, loading/error/revert states) does not depend on
knowing the exact template and can proceed and be fully tested against a fake response now.
