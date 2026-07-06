# Research: Fix Setup Screen Keyboard Layout Squish

## Root cause (confirmed via code review + on-device reproduction)

**Decision**: The squish is caused by the keyboard (IME) inset being applied **twice** in
`SetupScreen.kt`: once by `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)`
(`safeDrawing` is `systemBars ∪ ime ∪ displayCutout` — it already includes the keyboard),
and a second time by the inner `Column`'s own `.imePadding()`. The two paddings stack, so
the available content height is reduced by roughly *2× the keyboard height* instead of
once, and the `Column`'s `verticalScroll` has to squeeze everything (including the
`OutlinedTextField`s themselves) into whatever sliver is left.

**Rationale**: Confirmed two ways:

1. **Code read**: `SetupScreen.kt:70` sets `contentWindowInsets = WindowInsets.safeDrawing`
   on the `Scaffold`, and `SetupScreen.kt:76` calls `.imePadding()` again on the inner
   `Column` that receives `Scaffold`'s already-ime-inclusive `inner` padding.
2. **On-device reproduction** (Galaxy Z Fold 7, folded/cover-screen state, debug build):
   - `dumpsys window` showed the Activity's actual window **frame stays full height**
     (`frame=[0,0][1080,2520]`, `sim={adjust=pan}`) while the keyboard is showing — so
     this is **not** an OS-level window-resize conflict (`adjustResize` double-counting
     with Compose), it's purely a Compose-side insets bug.
   - `uiautomator dump` (text-based accessibility-tree dump; pixel screenshots are
     unavailable because `MainActivity` sets `FLAG_SECURE` for token/image privacy — see
     `MainActivity.kt:38`) before/after focusing the token field showed:
     - Visible content area collapsed to a ~246px band (`y=278` to `y=524`) out of the
       window's full 2520px height.
     - The Server-URL field rendered at `y=[215,324]`, **overlapping the TopAppBar**
       (`y=[0,278]`) by 63px — i.e., partially hidden behind the app bar.
     - Both `OutlinedTextField`s measured shorter than their normal ~168px height (109px
       and 147px respectively).
     - The heading, description, "Server" section title/detail text, and the "Connect"
       button were absent from the tree entirely — squeezed out of the measured layout,
       not just scrolled off (scrolling should have kept them reachable, per FR-002).
   - The state was stable across repeated dumps 3+ seconds apart, ruling out a transient
     auto-scroll-into-view animation frame.

**Alternatives considered**:
- *OS-level `windowSoftInputMode` misconfiguration* (e.g., missing/adjustResize causing a
  real window resize on top of Compose's own inset handling) — ruled out by the
  `dumpsys window` frame dump showing the window frame never actually resizes.
- *Missing scroll/imePadding entirely* (the originally assumed cause before code review) —
  ruled out; both are already present, just double-applied via two separate mechanisms.

## Scope of the fix (FR-006 — other affected screens)

**Decision**: Grep-audited every screen for the `Scaffold(contentWindowInsets = ...)` +
child `.imePadding()` combination. Exactly one other screen has the identical bug:
`SettingsScreen.kt` (lines 122128 — same `WindowInsets.safeDrawing` + `.imePadding()`
pairing, same server-URL/token-editing UI reused for in-app credential changes). No other
screen (`HomeRoute.kt`, `ResultScreen.kt`, `BatchProgressScreen.kt`, `EditHistoryScreen.kt`,
`GalleryScreen.kt`, `PhotoViewerScreen.kt`, `ProgressScreen.kt`) calls `.imePadding()` at
all, so none of them double-count.

**Rationale**: `SettingsScreen.kt` is a near-identical copy of `SetupScreen.kt`'s
credentials-editing layout (same fields, same structure), so it inherited the same bug.
Home's prompt composer has no `.imePadding()` call and is out of this bug's blast radius
(a *missing* ime-handling screen is a different problem class than *double-applied*, and
isn't reported as broken — no evidence of squish there, only Setup/Settings are affected).

**Alternatives considered**: A blanket audit of every screen with any focusable field for
ime-handling correctness in general — rejected as over-scope; the spec (FR-006) only asks
to extend the fix to screens sharing *this specific* root cause, not to redesign
ime-handling app-wide.

## Fix approach

**Decision**: In both `SetupScreen.kt` and `SettingsScreen.kt`, change
`contentWindowInsets = WindowInsets.safeDrawing` to
`contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime)` on the
`Scaffold`. This makes `Scaffold`'s `inner` padding cover only system bars + display
cutout (as originally intended for the top app bar), while the inner `Column`'s existing
`.imePadding() + verticalScroll(...)` remains the *sole* handler of the keyboard inset —
exactly the pattern already used correctly by every other `Scaffold` in the app (which
don't need `.imePadding()` at all since they have no focusable fields needing it).

**Rationale**: Smallest possible change (one modifier chain edit per screen, matches
Constitution Principle II — surgical, no restructuring of the existing scroll/padding
pattern that already works correctly once the double-count is removed). Verified this
composes correctly: `WindowInsets.exclude()` is the standard Compose API for subtracting
one inset from a union.

**Alternatives considered**:
- Remove `.imePadding()` instead of excluding `ime` from `Scaffold`'s insets — rejected:
  `Scaffold`'s `inner` padding value doesn't animate with the keyboard the way
  `Modifier.imePadding()` does (no IME-open/close animation sync), so keeping
  `.imePadding()` as the ime handler and narrowing `Scaffold`'s insets is the more
  correct half to remove.
- Set `android:windowSoftInputMode` explicitly in the manifest — rejected: the on-device
  dump already shows the window itself isn't resizing (the OS side is already correct
  as-is), so a manifest change would address a cause that isn't actually present.
