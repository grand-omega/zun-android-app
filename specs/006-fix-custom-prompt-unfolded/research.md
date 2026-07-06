# Research: Fix Custom Prompt Field Rendering When Unfolded

## Root cause (strong hypothesis from code + git history — see caveat below)

**Decision**: The custom "Write your own…" field lives in `PromptLibraryContent`
(`PromptSheets.kt`), a single composable **shared** between two very different
containers:

1. **Folded/phone** (`isWide == false`): reached via `PromptLibrarySheet`, a
   `ModalBottomSheet` with `skipPartiallyExpanded = true` — the sheet gets
   nearly the *entire* screen height to itself, since it's the only thing on
   screen while open.
2. **Unfolded** (`isWide == true`, added in commit `239b8cf`, "Embed the
   prompt library as the second pane on wide Home"): `PromptLibraryContent`
   is embedded directly as the right-hand pane of a two-column `Row`
   (`HomeScreen.kt:112-155`), sharing its column with `composer(false)`
   stacked *below* it. Available height here is the screen height minus the
   composer's own height (source row + upload-progress + submit button) —
   meaningfully less than case 1's near-full-screen sheet.

`PromptLibraryContent`'s internal structure (`PromptSheets.kt:100-230`) is a
plain `Column` containing, in order: a heading row, an optional "try harder"
toggle `Surface`, a search `OutlinedTextField`, `CustomPromptItem` (which
itself grows by roughly another field-plus-button's worth of height when
expanded — the exact scenario in the bug report), and finally a `LazyColumn`
that is the *only* scrollable element (`Modifier.weight(1f)` when
`fillHeight = true`, i.e. in the wide/embedded case). None of the header
elements above the `LazyColumn` are wrapped in any scroll container.

**Rationale**: This is architecturally the same class of bug as feature 005
(insufficient/uncounted vertical space causing a text field to render
incorrectly) but via a different mechanism: instead of a double-counted
inset, it's a **plain, non-scrollable `Column` whose fixed header content can
exceed its `weight(1f)` height budget** once `CustomPromptItem` expands,
specifically in the one layout path (wide/embedded) that has *less* headroom
than the other (folded/sheet). A `Column` without `verticalScroll()` doesn't
shrink already-measured children to fit — when the total height of
non-weighted siblings approaches or exceeds the space left over after
`composer(false)` takes its share, the remaining budget for the weighted
`LazyColumn` shrinks toward zero and the fixed elements above it (including
the just-expanded custom-prompt field) have nowhere correct to go, producing
exactly the "looks wired, can't see the text" symptom — while the folded
case, with far more headroom, doesn't hit this ceiling.

**Caveat — could not visually confirm on-device**: `MainActivity` sets
`FLAG_SECURE` (see `research.md` in feature 005), which blocks pixel-level
screen capture *for anyone*, including the device owner's own screenshot
button — not just `adb`. There is no way to get a pixel-level look at this
bug via automation, so this root cause is derived entirely from static
analysis (the code structure, the git history showing this is newly-added
and untested against this exact scenario, and the general Compose layout
principle involved), not a reproduced screenshot. Confidence is high given
how precisely the mechanism matches the reported symptom (works when
folded/more headroom, breaks when unfolded/less headroom, specifically
affects the expandable custom field), but implementation should include a
layout-bounds test (measuring, not visual) to convert this from a hypothesis
into a verified fact before considering the bug fixed.

**Alternatives considered**:
- *Hardcoded/theme-mismatched text color* (a color-contrast bug) — considered
  because "can't see the text" often means invisible-due-to-color, but ruled
  out as the primary suspect: `OutlinedTextField` in both call sites
  (`PromptSheets.kt:179` search field, `:525` custom field) uses zero custom
  `colors(...)` overrides in either container, so there's no code path where
  the field would get different text/border colors between the sheet and the
  embedded pane — a pure Compose-layout (space) explanation fits the
  works-in-one-container-but-not-the-other symptom much better than a color
  explanation would, since color wouldn't plausibly differ by container.
- *`windowSoftInputMode`/IME double-counting* (feature 005's mechanism) —
  considered given the similar symptom description, but this screen doesn't
  call `.imePadding()` anywhere in `PromptSheets.kt`/`HomeScreen.kt`
  (confirmed via grep), so that specific mechanism doesn't apply here; the
  bug is about the Column's own height budget, not the keyboard inset.

## Scope of the fix (FR-004 — other affected screens)

**Decision**: `PromptLibraryContent` has exactly two call sites:
`PromptLibrarySheet` (folded) and `HomeScreen.kt`'s wide branch (unfolded).
`ResultScreen.kt` only references a *different*, unrelated string resource
(`result_write_your_own`, a label, not an editable field) and does not use
`PromptLibraryContent`/`CustomPromptItem` at all. So the "Write your own…"
editable field the user is describing exists in exactly one composable, used
in exactly the two containers described above — there is no second entry
point to separately fix.

**Rationale**: Confirmed via grep across the whole `app/src/main` tree for
`PromptLibraryContent`, `CustomPromptItem`, and the `write_your_own` string
keys — only the two call sites above reference the editable custom-prompt
composable.

## Fix approach

**Decision**: Restructure `PromptLibraryContent` so the whole thing —
heading, try-harder toggle, search field, custom-prompt item, and the prompt
list — scrolls together as a single `LazyColumn` (header rows as individual
`item { }` entries ahead of the existing `items(ordered) { }` block), rather
than splitting "fixed Column header" from "the one scrollable LazyColumn at
the bottom." This makes the available-height budget question moot in both
containers: whatever doesn't fit simply scrolls, in the sheet and in the
wide pane alike, and the fix is a single change to the shared composable
rather than two divergent per-container patches.

**Rationale**: Matches Constitution Principle II (surgical, no new
abstraction) — collapsing two adjacent scroll regions (an implicit
non-scrolling header + an explicit `LazyColumn`) into one `LazyColumn` is a
structural simplification, not new complexity, and fixes the bug identically
everywhere `PromptLibraryContent` is used instead of special-casing the wide
branch.

**Alternatives considered**:
- *Wrap only the header section in `Modifier.verticalScroll(...)`, leaving
  the `LazyColumn` separate* — rejected: nesting a scrollable `Column` above
  a separately-scrollable `LazyColumn` inside one parent produces two
  independent scroll gestures fighting for the same finger-drag, a worse UX
  than one continuous scroll.
- *Only fix the wide/embedded branch (e.g., give it more height or shrink the
  composer)* — rejected: doesn't fix the same class of bug should the
  folded/sheet case ever also run short on height (e.g., a phone with on
  screen nav bar + IME both showing at once), and FR-004 already established
  there's only one composable to fix, not one per container.
- *Cap `CustomPromptItem`'s expanded height so it can never contribute enough
  to overflow* — rejected: would silently clip the field's own content
  (defeating the purpose — the user needs to see what they typed) instead of
  making it reachable via scroll.
