# Research: Save Drag-Reveal Results

## Decision 1: Resolve `beforeModel`/`afterModel` to real `Bitmap`s via Coil's `ImageLoader`, not a new download path

**Decision**: A new `suspend fun resolveToBitmap(context: Context, model: Any?): Bitmap?` builds a
`coil3.request.ImageRequest` for the model and executes it via `SingletonImageLoader.get(context)`,
extracting the decoded `Bitmap` from a successful result. Runs on a background dispatcher.

**Rationale**: Confirmed directly in the codebase: `saveToPictures`/`shareImages`
(`MediaStoreSaver.kt`, `ShareUtils.kt`) `when (source)`-branch on `Uri` vs `String` and byte-copy
verbatim — they never decode an image, so they can't produce a *composited* result, only pass
through an already-whole file. There's also no existing "Coil model → Bitmap" utility anywhere in
this codebase to reuse. Coil is already this app's image-loading dependency and already
transparently handles both cases this feature needs — a cached `file://` `Uri` (from
`OfflineImageCache`, `previewModel`'s common case) and a plain remote URL `String` (`inputModel`'s
*only* case, per Decision 2) — through the exact same request API, matching the one existing
precedent for using Coil this way at `PhotoViewerScreen.kt:337-338` (pre-warming the cache before
promoting to full-res, a different purpose but the same underlying mechanism).

**Alternatives considered**:
- *Extend `saveToPictures`/`shareImages` to decode+recompose internally*: rejected — those
  utilities' whole contract is "hand me a source, I'll get its bytes onto disk/into a share
  intent" for a *single already-final* image; conflating that with "resolve, composite, *then*
  save" would blur two genuinely different responsibilities into one function.
- *Manual OkHttp download for URL sources* (mirroring `ShareUtils`' own fallback path): rejected —
  would duplicate Coil's own caching/decoding logic for no benefit, and wouldn't transparently
  handle the `file://` `Uri` case the same way.

## Decision 2: `inputModel` (the "before" image) is never disk-cached — this is a pre-existing gap, not something to silently paper over

**Decision**: Accept that saving/sharing can require a live network fetch of the original photo,
and make the failure mode explicit rather than generic. `resolveToBitmap` returning `null` (or
throwing) for the before-image specifically is treated as a distinct, expected failure — surfaced
with this app's existing "needs network"/offline-unavailable framing (the same message language
already used elsewhere, e.g. `NeedsNetworkIcon`), not a bare "save failed."

**Rationale**: Verified in `RealJobRepository.kt`: `inputModel` always resolves to a plain URL with
no `OfflineImageCache` check at all (unlike `previewModel`/`resultModel`, which check the cache
first). This is a structural characteristic of how the app already works, not something this
feature regresses — but Constitution Principle IV requires that any feature touching the result
read path show an explicit "unavailable offline" state for uncached content, so this plan commits
to that framing specifically rather than letting a network failure surface as an undifferentiated
error (which spec.md's Edge Cases already requires a "clear failure message" for, just without
this specific offline framing pinned down until now).

**Alternatives considered**:
- *Fix `inputModel` to check `OfflineImageCache` first, as a prerequisite for this feature*:
  rejected as out of scope — that's a pre-existing characteristic of a different, already-shipped
  code path (feature 004/008's offline-cache work), not something this feature's spec asked for;
  changing it would be a surgical-scope violation (Constitution Principle II) for a feature about
  saving a composite, not about fixing input-image caching.

## Decision 3: Map the on-screen mask into source-image space by computing each image's own `ContentScale.Fit` rect — the one pure, testable function

**Decision**: A pure function `mapRectToSource(containerSize: IntSize, imageIntrinsicSize:
IntSize, pointInContainer: Offset): Offset` (or equivalent for a full rect) computes where a
screen-space coordinate — inside the shared on-screen container both images are rendered into via
`ContentScale.Fit` — lands within a given image's own intrinsic pixel dimensions, accounting for
`Fit`'s letterboxing (uniform scale + centered offset, not a raw ratio). The composite step calls
this once per image (before-image and mask against the after-image's own resolution, since that's
the canonical output canvas per Decision 4) rather than assuming a naive 1:1 or aspect-locked
mapping.

**Rationale**: Confirmed in the codebase that the mask (`createOpaqueMask`, sized via
`onSizeChanged`) is created at the on-screen container's *display* pixel size, entirely unrelated
to either source image's real resolution — and `ContentScale.Fit` letterboxes (uniform scale,
centered, with empty bars on one axis unless the aspect ratios happen to match exactly), so a
naive "scale factor = source width / container width" mapping would misplace strokes whenever
there's any letterboxing. Extracting this as its own pure function (container size + image size +
a point/rect in) — no Compose/Canvas/Bitmap dependency — is what makes it the one piece of this
feature's math that's genuinely unit-testable, the same pattern feature 010 used for
`interpolateStampPoints`.

**Alternatives considered**:
- *Assume before/after always share the exact same aspect ratio and skip per-image mapping*:
  rejected — before/after are the same underlying photo in the overwhelming common case, but nothing
  in this codebase actually guarantees an edit can't change the output aspect ratio; computing the
  mapping properly per image costs little and removes the assumption entirely rather than leaving
  a latent misalignment bug for the rare case it doesn't hold.

## Decision 4: The transformed (after) image's own resolution is the output canvas; the before-image and mask are mapped into it, not the other way around

**Decision**: `compositeReveal(after: Bitmap, before: Bitmap, mask: ImageBitmap, ...): Bitmap`
creates its output canvas at `after`'s own width/height, draws `after` as the base layer
unscaled, then draws `before` (resampled to fit, per Decision 3's mapping) masked by the
same-mapped reveal mask on top.

**Rationale**: The after/transformed image is already the base layer on screen (`ScratchRevealCompare.kt`
draws it first, unmasked) and is conceptually "the result" this whole feature is revealing pieces
of — using its own native resolution as the export canvas (rather than, say, always downsampling
to the smaller of the two, or the before-image's resolution) matches FR-003's "quality consistent
with this app's other saved images," since saving the plain result elsewhere in this app already
uses this same image at its own resolution.

**Alternatives considered**:
- *Downsample everything to a fixed cap (e.g. 2048px, matching `prepareImageForUpload`'s upload-prep
  convention)*: not rejected outright — worth carrying into `tasks.md` as a safety bound (results
  can be uncapped-resolution PNGs per research), but the *canonical* target is the after-image's
  own size, with a cap applied only if it exceeds a sane bound for on-device compositing, not as
  the primary sizing decision.

## Open item carried into tasks.md

Exact placement of the new save/share action row within `ScratchRevealCompare`'s existing chrome
(hint pill at top-center, brush slider at bottom-center; the mode-toggle and reset buttons live one
level up in `CompareModeSwitcher`, at bottom-start/bottom-end) is a layout detail worth iterating on
during implementation rather than pre-deciding pixel-for-pixel here — same treatment the mode-toggle
button already went through post-implementation in feature 010's UX polish round.
