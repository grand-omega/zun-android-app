# TODO

Outstanding items from the production-readiness audit (2026-05). None are release blockers, but each should be resolved before announcing or growing the user base.

## Lint baseline rationale is stale

`app/lint-baseline.xml` (audit comment dated 2026-05) claims:

> The release variant ships with `cleartextTrafficPermitted=false` and system trust anchors only.

That is **not** what ships. `app/src/main/res/xml/network_security_config.xml` has `cleartextTrafficPermitted="true"` for both debug and release. The threat-model comment in the config file itself argues this is intentional (self-hosted LAN servers, no auto-connect, user-confirmed URL before any token is sent). The behaviour is fine — the **lint baseline rationale is wrong** and will mislead a future auditor.

**Fix:** rewrite the `AcceptsUserCertificates / InsecureBaseConfiguration` paragraph in `app/lint-baseline.xml` to match reality: cleartext is permitted in both variants by design; the user-cert override is debug-only; HTTPS is enforced at input time via `ServerUrls.kt:92` (release builds reject `http://` URLs at save time).

## No instrumented tests in CI

`app/src/androidTest/` contains the Room migration test (`AppDatabaseMigrationTest`). It runs locally on a connected device, but **CI does not run it**. Migration regressions could ship.

**Options:**
- Add a Firebase Test Lab step on tag builds: `gcloud firebase test android run` against a Pixel emulator image.
- Or use `reactivecircus/android-emulator-runner` in the existing GitHub workflow to run `connectedDebugAndroidTest` on every PR.
- Or accept the risk and add a PR-template checkbox: "I ran `./gradlew connectedDebugAndroidTest` locally."

## README placeholders

Two `<!-- TODO -->` markers remain in `README.md`:

1. **Screenshots** — add files under `docs/img/` and reference them from the Features section. Even one phone-frame screenshot of Home + Gallery is enough.
2. **Releases page link** — once a fresh `v*` tag is published and the release artifact is attached, replace the placeholder with `https://github.com/<owner>/<repo>/releases`.

## Window-inset coverage sweep

Only `HomeRoute.kt` sets `contentWindowInsets = WindowInsets.safeDrawing`. Other screens with `Scaffold` (`PhotoViewerScreen`, `ResultScreen`, `SettingsScreen`, `BatchProgressScreen`, `LockScreen`, `SetupScreen`, `ProgressScreen`) rely on the Scaffold default, which covers system bars but not display cutouts or the IME. Text-input surfaces are the most exposed: the URL/token fields in `SetupScreen` and the prompt-save dialog probably let the keyboard cover the input on the cover display.

**Fix:** audit each `Scaffold` and add `contentWindowInsets = WindowInsets.safeDrawing` where appropriate; add `Modifier.imePadding()` to text-input columns so editable fields ride above the keyboard.

## Nice to have

Lower priority than the items above — pick up if/when the workstation use case starts to bite.

### Share-to-app image import + drag-and-drop target

Today the app exports images via `ACTION_SEND` (`ShareUtils.kt`) but receives nothing. To act like a foldable workstation app:

1. Add an `<intent-filter>` to `MainActivity` accepting `ACTION_SEND` / `ACTION_SEND_MULTIPLE` with `image/*` and `ACTION_VIEW` with `image/*`. FluxEdit then appears in every other app's share sheet.
2. Wire `Modifier.dragAndDropTarget(...)` on the home composer's image area so dragging a photo from Samsung Gallery, Files, or another window in DeX/multi-window drops it straight in as an input.

The picker flow already handles `Uri` inputs, so the receiving plumbing is small — mostly a route into `HomeViewModel.addInputUris(...)`.

### Official SplashScreen API

Currently `MainActivity.onCreate` goes straight to `setContent`, so the launch experience is whatever the system theme paints (a flash of the activity background) until Compose draws. The Android 12+ splash spec (`androidx.core:core-splashscreen`) gives a proper branded splash with the app icon and a clean handoff to first frame.

**Fix:**
1. Add `androidx.core:core-splashscreen` to `libs.versions.toml`.
2. Define `Theme.ZunFlux.Splash` in `themes.xml` with `android:windowSplashScreenBackground` + `android:windowSplashScreenAnimatedIcon`.
3. Set the activity's manifest theme to `Theme.ZunFlux.Splash` and call `installSplashScreen()` as the first line of `onCreate`, then switch to the regular theme.
