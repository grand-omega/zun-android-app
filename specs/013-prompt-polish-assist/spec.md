# Feature Specification: Prompt Polish Assist

**Feature Branch**: `013-prompt-polish-assist`

**Created**: 2026-07-07

**Status**: Draft

**Input**: User description: "用户在输入 prompt 的时候，他可能输入的都是一些比较零散的 idea，加入类似 'fix for me' 的 AI 辅助，把用户零散的 prompt 改写成标准结构化的、优质的 Flux prompt（用户会另外提供参考模板/风格)。" (When writing a custom prompt, a user's input is often a scattered, informal idea. Add an AI-assisted "fix for me"-style action that rewrites the user's rough prompt into a well-structured, high-quality prompt following a reference style the user will supply separately.)

## Clarifications

### Session 2026-07-07

- Q: Must the rewrite model stay strictly self-hosted on infrastructure the app operator controls, with no third-party AI service ever in the path? → A: Yes — must be self-hosted only; no third-party AI service, even as a fallback.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Turn a rough idea into a polished prompt with one tap (Priority: P1)

A user is writing their own custom prompt and has a rough, informal idea of what they want, but isn't sure how to phrase it as a well-structured prompt. They tap a "polish" action, and their rough text is rewritten into a clear, well-structured prompt following the app's established style — ready to submit as-is, or to keep editing further.

**Why this priority**: This is the entire feature — without it there's no assistance at all, just the existing plain text field.

**Independent Test**: Type a short, informal idea into the custom prompt field, tap the polish action, and confirm the field's text is replaced with a well-structured version of the same idea; confirm the original wording can still be recovered afterward.

**Acceptance Scenarios**:

1. **Given** the user has typed a rough, informal idea into the custom prompt field, **When** they tap the polish action, **Then** the field's text is replaced with a well-structured rewrite of that same idea, following the app's established prompt style.
2. **Given** a rewrite has just replaced the user's original text, **When** the user decides they preferred their own wording, **Then** they can revert back to exactly what they had typed before tapping polish.
3. **Given** a polish request is in progress, **When** the user is waiting for it, **Then** the field clearly shows it's being worked on without freezing or blocking the rest of the screen.
4. **Given** the polish request fails or the app has no connectivity, **When** that happens, **Then** the user's original text is left completely untouched and they can still submit it as-is.

### Edge Cases

- What happens if the prompt field is empty? The polish action isn't available — there's nothing to rewrite.
- What happens if the user edits the text again while a previous polish request is still in flight? The in-progress result is discarded when it comes back; it must not overwrite text the user has since changed.
- What happens with a very short input (a word or two)? It's still sent through and expanded into a coherent, structured prompt.
- What happens if the user taps polish again on text that's already been polished? Treated the same as any other polish request — it's simply re-run on whatever's currently in the field.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to trigger a rewrite of their currently-typed custom prompt into a well-structured, polished version, from within the custom ("write your own") prompt entry.
- **FR-002**: The rewritten prompt MUST follow the app's established structured-prompt style (the specific reference style/template is a separate design input, supplied outside this specification).
- **FR-003**: Users MUST be able to revert to their original wording after a rewrite replaces it, for as long as that prompt-writing session is still active.
- **FR-004**: While a rewrite is in progress, the system MUST show a clear waiting state without blocking other interaction on the screen.
- **FR-005**: If a rewrite fails or cannot be completed (e.g., no connectivity), the user's original text MUST remain fully intact and still submittable as-is — polishing is never required to submit a prompt.
- **FR-006**: The polish action MUST NOT be available when the prompt field is empty.
- **FR-007**: This feature applies only to the free-text custom prompt entry; prompts chosen from the existing saved-prompts library are unaffected, since they're already curated.
- **FR-008**: The rewrite MUST be performed entirely by infrastructure the app operator controls (self-hosted); it MUST NOT be routed through any third-party AI service or API, including as a fallback when self-hosted infrastructure is unavailable — if self-hosted rewriting can't be reached, that's treated as a failed request (FR-005), not an opportunity to fall back to a third party.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can go from a rough, informal idea to a submittable, well-structured prompt in a single tap.
- **SC-002**: 100% of failed or unavailable polish attempts leave the user's original prompt fully intact and still submittable.
- **SC-003**: A user can always recover their pre-polish wording without having to retype it from memory.
- **SC-004**: Prompts produced by this feature consistently follow the app's established structured style, judged by comparison against the reference style.

## Assumptions

- The rewrite is performed by a small language model hosted alongside the app's existing, self-hosted server infrastructure (per FR-008), not bundled into the Android app itself — this keeps the app's on-device memory/storage footprint unaffected, at the cost of requiring connectivity to use this feature. This is consistent with submitting a job for editing already requiring connectivity, so it isn't a new constraint beyond what the app's core flow already needs.
- The exact reference style/template the rewrite should follow, and the specific model used to produce it, are separate design inputs to be supplied and worked out during planning — this specification only requires that a consistent, well-structured style is followed, not what that style's exact wording/keywords are.
- Only the current, in-progress prompt text is ever "reverted" to — the original rough wording is not permanently retained anywhere once the user moves past that composer session (e.g., after submitting or navigating away).
- No rating, feedback loop, or "generate another rewrite" history is in scope for this version — one tap produces one rewrite; tapping again simply re-runs the transform on whatever text is currently present.
- No new permissions are required — this reuses the existing connection to the app's own server infrastructure, the same one already used for submitting jobs.
