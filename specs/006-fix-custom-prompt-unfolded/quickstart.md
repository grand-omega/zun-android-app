# Quickstart: Validating the Custom Prompt Field Fix (Wide/Unfolded Home)

## Prerequisites

- A debug build installed on a physical Samsung Galaxy Z Fold, in both folded
  and unfolded states.
- No `zun-rust-server`/network access required — this is a pure client-side
  layout fix, reproducible from Home with no submission needed.

## Automated checks

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Per Constitution Principle III, a layout-bounds regression test should be
added (Robolectric/Compose test measuring `PromptLibraryContent`'s rendered
semantics-tree bounds under a constrained height, asserting the custom
field's `OutlinedTextField` stays within the available space and doesn't
overlap/get clipped by sibling content) — this doesn't require pixel access
(unlike a screenshot), only layout bounds, so it isn't blocked by
`FLAG_SECURE` the way visual verification is.

## Manual validation

**Note on visual verification**: `MainActivity` sets `FLAG_SECURE`, which
blocks screenshot capture for everyone — including the device owner's own
screenshot button, not just `adb`/automation. This means the on-device steps
below can only be confirmed by directly looking at the real screen; no
screenshot can be attached as evidence, by design (this protects the same
token/image content discussed in feature 005's research).

1. **Unfolded, custom prompt field is fully visible and usable (Story 1 / SC-001)**
   Unfold the Z Fold, open Home (with an image already picked, so the wide
   two-column layout is showing the prompt library pane). Tap "Write your
   own…" and type a prompt. Confirm: the field renders normally (no visual
   distortion), the typed text is clearly visible and readable, and — if the
   combined content is taller than the available pane height — the pane
   scrolls to keep the field reachable rather than overlapping/clipping
   anything below it.

2. **Folded, no regression (Story 1 / FR-003)**
   Fold the device, reopen the prompt picker (now the modal bottom sheet),
   repeat validation 1. Confirm identical, already-correct behavior — no
   change from before this fix.

3. **Fold/unfold transition mid-entry (Edge Case)**
   With the custom prompt field open and some text typed, fold or unfold the
   device. Confirm the already-typed text survives the transition and the
   field recovers to a correct appearance without needing to reopen it.

4. **Single entry point confirmed (Story 2 / FR-004)**
   Per `research.md`, `PromptLibraryContent` has exactly two call sites
   (the folded modal sheet and the wide embedded pane) and no other screen
   references the editable custom-prompt field — so validating 1 and 2 above
   covers every occurrence; there is no third place to separately check.
