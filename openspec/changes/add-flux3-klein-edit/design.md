# Design

## Context

`Workflows.DEFAULT_EDIT` feeds `DEFAULT_CUSTOM_WORKFLOW` in two files:
- `HomeViewModel.kt:41`: job submission at :440 and prompt creation at :495.
- `ResultScreen.kt:87`: regenerate at :202 and prompt creation at :511. `ResultScreen` is a composable, so these two paths can't be unit-tested.

How the workflow is chosen and sent:
- The app reads `/capabilities` only to gate Try-harder, and ignores the server's `default` flag.
- The app picks a workflow before looking at the prompt selection. `JobUploader` (:74-106, :141) then sends `workflow` for saved prompts too.
- The server ignores `workflow` when `prompt_id` is set (`resolve_prompt`; API contract :190).
- `updatePrompt` (`RealJobRepository.kt:120`) sends only label and text, so a prompt's workflow is fixed at creation.

`workflow` is stored as an opaque string in Room (`JobEntity.workflow`) and in the DTOs.

## Goals / Non-Goals

**Goals:**
- A one-constant switch to FLUX.3, with behaviour pinned by unit tests where the code is testable and by manual checks where it isn't.

**Non-Goals:**
- No adoption of the `/capabilities` `default` flag.
- No change to which fields the uploader sends.
- No Try-harder cleanup.
- No new UI.

## Decisions

1. **Flip the constant rather than read `default` from `/capabilities`.**
   - Why: it is the smallest change (constitution II), and submission keeps working before capabilities load.
   - Alternative: dynamic default with the constant as fallback. It is more robust to server rollbacks, but adds state for a single-user setup where the operator controls deploy order.
   - Revisit if the server ever changes defaults independently of app releases.

2. **Keep sending the redundant `workflow` with `prompt_id`.**
   - Why: the server ignores it, so the stored workflow stays authoritative. Changing the uploader is out of scope.
   - The spec only constrains `prompt_id` / `prompt_text`, so the tests assert those.

3. **Leave legacy fixtures alone.** `FakeJobRepository` prompts and `JobEntityTest` rows that use `flux2_klein_edit` represent pre-existing data.

4. **Result-screen paths are verified manually** in task 2.1, because the composable has no ViewModel seam. Adding one would be out of scope.

## Risks / Trade-offs

- **The app is updated before the server** → custom-prompt submits *and* new saved prompts fail with the server's unsupported-workflow error.
  → Mitigation: deploy order. Release notes name the concrete server tag.
- **A server rollback to a FLUX 2-only build** breaks custom prompts and prompt creation until the app is rolled back too. Saved prompts already bound to FLUX.3 must be recreated after such a rollback.
  → Accepted; the server's design Decision 7 covers the coordinated procedure.
- **Regenerating an old FLUX 2 custom job now runs FLUX.3.** This is intended, and it is covered by a spec scenario.

## Migration Plan

Release after the server change is deployed. Rollback: revert the constant before or together with any server rollback.
