# Accessibility Audit

Status: initial pass, 2026-05.

## What we did this round

| Issue | Fix | File |
|------|-----|------|
| Gallery thumbnails had `contentDescription = null`. TalkBack skipped them entirely — a blind user couldn't tell the grid had any content. | Each thumbnail now describes itself as "Generation: \<prompt label>". | `gallery/GalleryScreen.kt:577` |
| Photo viewer pager pages had no semantic announcement of position. Screen readers couldn't tell the user what page they were on. | Each page exposes `"Image N of M"` via `Modifier.semantics`. | `gallery/PhotoViewerScreen.kt:240-265` |
| Health dot (top of Home) had no label. | Already had `semantics { contentDescription = ... }` from the start; verified messages match the actual state. | `home/HomeHealthUi.kt:24` |
| Material 3 dropdowns vs old radio rows. | Migrated lockout + connection mode to `OptionDropdown` in Batch A, which inherits Material's combobox semantics for free. | `settings/SettingsScreen.kt` |

## Manual TalkBack checklist (run before each release)

Run on a debug install with TalkBack on. Capture findings here as new tickets.

### Setup → Home
- [ ] Setup form fields announced as text inputs with their labels.
- [ ] "Connect" button announces its loading state ("Testing connection...") while busy.
- [ ] Health dot announces the right state on Home (Connected / Checking / Network error / Invalid token).
- [ ] Recent thumbnails announce prompt + (eventually) creation date.

### Gallery
- [ ] Tile thumbnails announce as "Generation: \<prompt>".
- [ ] Selection mode announces "\<count> selected" when count changes.
- [ ] Filter dropdown announces selected option + count.
- [ ] Empty / error states are read out, not silent.

### Photo viewer
- [ ] Each page announces "Image N of M".
- [ ] Action bar buttons (Compare, Use input, Save, Details, Delete) all announce.
- [ ] Details modal sheet is readable top-to-bottom.

### Settings
- [ ] Both dropdowns (lockout, connection mode) announce as combobox.
- [ ] Cert pin "Pin Current" announces "Pinning..." while busy.
- [ ] Diagnostics panel: recent error rows readable; "No errors recorded" pill labeled.
- [ ] API token field announces "show/hide" toggle correctly when state flips.

### Stress
- [ ] 200% font scale: dropdown rows, status pills, dialog buttons don't clip or overlap.
- [ ] Dark theme with high-contrast settings: status pill outlines (currently 0.24 alpha) still visible — currently borderline; consider bumping if reports come in.

## Known limitations

- Pager swipe gestures are still gesture-only on TalkBack. Users navigate via "explore by touch" + double-tap; we don't expose explicit "next page / previous page" actions yet. Adding `Modifier.semantics { customActions = ... }` would help; deferred until requested.
- `MissingImageState` (e.g. when an offline tile fails to load) shows a label but doesn't visually distinguish "needs network" from "image deleted server-side". Functionally fine; cosmetic.

## Reference

- TalkBack docs: https://support.google.com/accessibility/android/answer/6283677
- Compose semantics: https://developer.android.com/jetpack/compose/accessibility
