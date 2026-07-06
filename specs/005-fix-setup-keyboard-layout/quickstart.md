# Quickstart: Validating the Setup/Settings Keyboard Layout Fix

## Prerequisites

- A debug build installed on a physical Samsung Galaxy Z Fold (folded and unfolded states)
  and, ideally, a standard-aspect-ratio phone or emulator for comparison.
- No `zun-rust-server`/network access is required — this is a pure client-side layout fix
  and can be verified purely on the Setup screen before ever connecting.

## Automated checks

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

There is no existing Compose test scaffolding for `SetupScreen.kt`/`SettingsScreen.kt`
(unlike `HomeActiveJobsBannerTest.kt`/`PhotoViewerScreenTest.kt`'s Robolectric pattern for
other screens) — per Constitution Principle III, if `/speckit-tasks` decides adding one is
in scope, it should assert the `Scaffold`'s `contentWindowInsets` no longer includes `ime`
in the same insets object as a child `.imePadding()` call; otherwise this is verified
manually below.

## Manual validation

1. **Cover-screen state, keyboard squish is gone (Story 1 / SC-001, SC-003)**
   On a Z Fold folded (cover-screen) with fresh app data, open the app, land on Setup, tap
   the API token field. Confirm: the heading/description/section title/detail text and the
   "Connect" button remain reachable by scrolling, the token field itself renders at its
   normal height (not compressed), and no field renders overlapping/behind the top app bar.

2. **Unfolded state, same check (Story 1 / SC-001, FR-005)**
   Repeat validation 1 on the same device unfolded. Confirm the same graceful behavior in
   the wider/taller aspect ratio.

3. **Keyboard dismiss returns to normal (FR-004)**
   With the keyboard open per validation 1, dismiss it (tap outside the field or back).
   Confirm the screen returns to its full pre-keyboard layout with no leftover empty space
   or visible jump.

4. **Settings screen shares the fix (Story 2 / FR-006)**
   From within the app (Settings), open the credentials-editing screen and repeat
   validation 1 there. Confirm the same graceful behavior — this screen shares
   `SetupScreen.kt`'s exact root cause (see `research.md`) and must receive the same fix.

5. **Standard-aspect comparison (regression check)**
   Repeat validation 1 on a normal-aspect-ratio phone/emulator. Confirm behavior there is
   at least as good as before the fix (no regression), even though the bug was far less
   visible there due to the keyboard occupying a smaller screen fraction.

## What NOT to expect to change

- Home's prompt composer and every other screen in the app (`ResultScreen`, `Gallery`,
  `PhotoViewerScreen`, `EditHistoryScreen`, progress screens) do not call `.imePadding()`
  and are outside this bug's blast radius (see `research.md`, "Scope of the fix") — no
  behavior change is expected there.
