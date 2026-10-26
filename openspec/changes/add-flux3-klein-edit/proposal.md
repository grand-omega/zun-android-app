# Proposal

## Why

The server is switching its default image-edit workflow to FLUX.3 Image Klein (`flux3_klein_edit`; see the zun-rust-server change `add-flux3-klein-edit`). The app hardcodes the workflow it sends for custom prompts and new saved prompts (`Workflows.DEFAULT_EDIT`). Until that constant changes, the app keeps running FLUX 2.

## What Changes

- `Workflows.DEFAULT_EDIT` changes from `flux2_klein_edit` to `flux3_klein_edit`. These four paths then use FLUX.3:
  - custom-prompt jobs from Home;
  - custom-prompt regenerate on the Result screen;
  - new saved prompts created from Home;
  - new saved prompts created from the Result screen.
- Existing saved prompts keep the workflow the server stored for them. The app keeps sending its redundant `workflow` field alongside `prompt_id`, and the server ignores it.
- Job history rows that say `flux2_klein_edit` keep displaying.
- No UI string, DTO or Room schema changes.

Non-goals:
- No model picker.
- The default is not read dynamically from `/capabilities`.
- No change to which fields the uploader sends.
- No Try-harder rework.

Reported, not fixed (constitution II): `TRY_HARDER_EDIT = "flux2_klein_9b_kv_edit"` names a workflow that no server build enables, so the Try-harder toggle is always hidden. It is left as is.

## Capabilities

### New Capabilities
- `edit-workflow-selection`: which server workflow name the app sends when submitting edit jobs and creating saved prompts.

### Modified Capabilities
<!-- None: the repo has no specs under openspec/specs/ yet. -->

## Impact

- **Code:** `app/src/main/java/dev/zun/flux/data/api/Workflows.kt` (one constant). It is consumed at `HomeViewModel.kt:41` (submit at :440, save at :495) and `ResultScreen.kt:87` (regenerate at :202, save at :511).
- **Tests:**
  - `HomeViewModelTest.kt` changes.
  - `RecordingRepository.kt` and `FakeJobRepository.kt` follow the constant automatically.
  - Prompt fixtures that hardcode `flux2_klein_edit` stay as they are; they represent legacy saved prompts.
- **Server contract** (constitution V): this needs the server tag that enables `flux3_klein_edit`. Against an older server, both custom-prompt submissions and new saved prompts are rejected as an unsupported workflow. Deploy order: pipeline → server → app.
