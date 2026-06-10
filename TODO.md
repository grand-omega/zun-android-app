# TODO

Outstanding items from the production-readiness audit (2026-05). None are release blockers, but each should be resolved before announcing or growing the user base.

## README screenshot placeholder

One `<!-- TODO -->` marker remains in `README.md`:

**Screenshots** — add files under `docs/img/` and reference them from the Features section. Even one phone-frame screenshot of Home + Gallery is enough.

## Resolved (2026-06)

- **Instrumented tests in CI** — `connectedDebugAndroidTest` now runs on an
  API 36 emulator in the `instrumented-tests` workflow job. The Room migration
  test was rewritten to build the v1 database by hand (schema JSONs for
  versions 1–3 were never exported, so `MigrationTestHelper.createDatabase`
  could not work) and to validate by opening the migrated DB through Room.
- **Window-inset coverage sweep** — all `Scaffold`s now set
  `contentWindowInsets = WindowInsets.safeDrawing`; Setup/Settings forms got
  `Modifier.imePadding()`.
- **Share-to-app image import + drag-and-drop target** — `ACTION_SEND` /
  `ACTION_SEND_MULTIPLE` intent filters feed the Home composer, and the Home
  image area is a `dragAndDropTarget`. `ACTION_VIEW` was deliberately left
  out: registering as an image *viewer* would put FluxEdit in every
  "Open with" chooser for an app that can't display arbitrary images.
- **Official SplashScreen API** — `Theme.ZunFlux.Splash` +
  `installSplashScreen()` in `MainActivity`.
