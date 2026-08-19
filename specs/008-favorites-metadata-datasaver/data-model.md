# Phase 1 Data Model: Favorites, Generation Details, and Cellular Data Control

## `JobEntity.isFavorite` (new field)

- **Type**: `Boolean`, default `false`. Stored as SQLite `INTEGER NOT NULL DEFAULT 0`.
- **Represents**: whether the user has marked this generated image as a favorite. Purely local —
  never sent to or read from the server.
- **Lifecycle**: starts `false` for every new job. Toggled directly by the user from the photo
  viewer (`FR-001`). Must be preserved across a background server resync (see research.md
  Decision 1 — `carryForwardLineage` must carry it forward, or `OnConflictStrategy.REPLACE`
  silently resets it to `false`). Removed along with the rest of the row on delete — no separate
  cleanup needed (Edge Case 1 in spec.md).
- **Validation**: none beyond the type itself — any `Boolean` value is valid; no cross-field
  constraints.
- **Migration**: schema v5 → v6, additive column with a default, following the existing
  `addColumnIfMissing` idempotent-DDL pattern (four prior migrations already use it) — no data
  backfill needed since every existing row correctly defaults to "not favorited."

## `JobSummaryDto.isFavorite` (new field, mirrors the entity)

- Added so `GalleryViewModel`'s in-memory `jobs` `StateFlow` (used by `PhotoViewerScreen`'s
  pager) can apply the "favorites only" filter without a second database round-trip, matching
  how the rest of that DTO already mirrors `JobEntity` fields.

## Network mode preference (new setting)

- **Type**: a two-state preference — "Wi-Fi only" (default) or "allow cellular data" — stored
  through `SettingsManager` alongside existing preferences like `gallerySortNewestFirst`.
- **Represents**: whether AI-job upload and automatic result-fetch download may proceed over a
  metered (cellular) connection.
- **Lifecycle**: read once per upload-enqueue (to choose the `WorkManager` `NetworkType`
  constraint) and once per automatic prefetch attempt (to decide whether to run it or skip it).
  Not read for explicit user-initiated actions (Save to device, Share) — those are intentionally
  unaffected (FR-010).
- **Validation**: none — a plain boolean/enum toggle with a fixed default.

## No new entities

Generation details (Story 2) introduce no new stored data — the sheet is a read-only
presentation of fields `JobEntity`/`JobStatusDto` already capture (`promptText`, `workflow`,
`seed`, `createdAt`, `completedAt`).
