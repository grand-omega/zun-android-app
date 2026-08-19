# Phase 1 Data Model: Local Composite Gallery Entries

## No new Room table, no schema migration

Per research.md Decision 1, a saved composite is a normal `JobEntity` row — the schema
(`app/src/main/java/dev/zun/flux/data/local/JobEntity.kt`) is unchanged. The values it's populated
with:

| Column | Value for a local composite |
|---|---|
| `id` | `"local-composite-${UUID.randomUUID()}"` — the reserved prefix every consumption site checks |
| `status` | `"done"` — so it passes every paged gallery query's existing `status = 'done'` filter unchanged |
| `inputId` | `null` |
| `promptId` | `null` — deliberately, so it's excluded from any specific-prompt filtered view |
| `promptText` | A fixed, non-blank placeholder string (e.g. "Saved reveal") — needed so `pagedDoneJobsCustom`'s `promptId IS NULL AND promptText IS NOT NULL` filter includes it under the "Custom" tag view too, and so `resolvePromptLabel()` never falls back to "Unknown prompt" for it |
| `workflow` | `null` |
| `seed` | `null` |
| `progress` | `null` |
| `error` | `null` |
| `createdAt` | Real save timestamp — drives FR-006's chronological ordering alongside AI results |
| `startedAt` / `completedAt` | `null` (or `createdAt` for both, if `JobDetailsSheet`'s existing null-handling prefers a value — a task-level detail, not a data-model one) |
| `durationSeconds` | `null` |
| `width` / `height` | The composite bitmap's real dimensions |
| `sourceSha256` / `resultSha256` | `null` — never set via `updateSourceLineage`/`updateResultHash`, which is what keeps a composite from ever being pulled into an existing stack (research confirmed the stacking subqueries only match on `lineageRootId`, never on hash equality) |
| `lineageRootId` | `null` — confirmed by research: with this null, `COALESCE(lineageRootId, id)` resolves to the row's own unique id, and the stacking subquery's match condition can then only ever equal itself, so `stackCount` is always exactly `1` |
| `isFavorite` | `false` default — same favorite mechanic as any other job (FR-005) |

## New file storage location

- **Path**: `context.filesDir/local_composites/<id>/composite.jpg`
- **Written by**: the new `saveLocalComposite` repository method, once, at save time.
- **Read by**: `previewModel`/`resultModel`/`thumbModel` — all three resolve to this same single
  file (no separate thumb/preview/full-res tiers; the composite is already downsampled to at most
  2048px per feature 014's `compositeReveal`, and Coil downsamples further for on-screen display as
  needed).
- **Deleted by**: `deleteJob`, when the id carries the reserved prefix (research.md Decision 3).
- **Never touched by**: `OfflineImageCache` (`prune()`, `listCachedJobs()`, `confirmClear()`) —
  this is a sibling directory to `OfflineImageCache`'s `rootDir`, not a subdirectory of it
  (research.md Decision 2 — this is what makes FR-011 hold).

## Relationship to existing data

- Reuses `JobRepository`/`ImageSourceRepository` (interfaces gain one method/branch each) and
  `JobDao`'s existing insert/delete/favorite-toggle machinery unchanged — a local composite flows
  through the exact same Paging 3 + Room + Compose pipeline as any AI-generated job from the moment
  it's inserted, with no separate code path for rendering, favoriting, or paging.
- `ImageCompositor.kt` (feature 014's `resolveToBitmap`/`snapshotMask`/`compositeReveal`) is entirely
  unchanged — this feature only changes what happens to the `Bitmap` `compositeReveal` already
  produces.
