# Spec Delta

## Purpose

Defines which server workflow name the app sends for image-edit jobs and for newly created saved prompts, and how it treats workflow names on existing data.

## ADDED Requirements

### Requirement: Custom-prompt jobs use FLUX.3 klein
When the user submits or regenerates an edit with custom prompt text and Try-harder off, the app SHALL send `workflow: "flux3_klein_edit"` in `POST /api/v1/jobs`. This applies on both the Home and Result screens.

#### Scenario: Custom prompt submission from Home
- **WHEN** the user picks an image, enters custom prompt text, leaves Try-harder off and submits
- **THEN** the job request carries `prompt_text` and `workflow` = `flux3_klein_edit`

#### Scenario: Regenerate an old FLUX 2 custom job
- **WHEN** the user opens the result of a custom-prompt job that ran on `flux2_klein_edit` and regenerates it
- **THEN** the new job request carries `workflow` = `flux3_klein_edit`

### Requirement: New saved prompts bind to FLUX.3 klein
When the user saves a new prompt from the Home composer or from the Result screen, the app SHALL create it with `workflow: "flux3_klein_edit"`.

#### Scenario: Save prompt from Home
- **WHEN** the user saves custom prompt text as a new saved prompt from Home
- **THEN** the create-prompt request carries `workflow` = `flux3_klein_edit`

### Requirement: Existing saved prompts keep their workflow
When the user submits or regenerates with an existing saved prompt, the app SHALL send that prompt's `prompt_id` and no `prompt_text`. The workflow stored with the prompt on the server SHALL decide which model runs.

#### Scenario: Legacy FLUX 2 saved prompt
- **WHEN** the user submits with a saved prompt whose stored workflow is `flux2_klein_edit`
- **THEN** the job request carries that `prompt_id` and no `prompt_text`, and the server runs the job on `flux2_klein_edit`

### Requirement: History with older workflow names still displays
Jobs stored locally or returned by the server with `workflow` = `flux2_klein_edit` SHALL keep loading and displaying in the gallery and photo viewer.

#### Scenario: Open an old FLUX 2 job
- **WHEN** the user opens a gallery item whose job workflow is `flux2_klein_edit`
- **THEN** the item loads normally and shows its workflow name
