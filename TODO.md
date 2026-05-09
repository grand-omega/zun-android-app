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

## SQLCipher → plain-SQLite migration code

Two files still carry migration code for the SQLCipher → plain Room switch (commit `8f05e6b`) and the offline-image-cache decryption removal (commit `49636c9`):

- `app/src/main/java/dev/zun/flux/data/local/AppDatabase.kt` — first-launch passphrase wipe handling
- `app/src/main/java/dev/zun/flux/data/repo/OfflineImageCache.kt` — `evictLegacyEncryptedFiles()`

Both are load-bearing for users upgrading past those commits. **Keep through at least one full release cycle**, then file a follow-up to remove once telemetry / time has shown all installs have rolled forward.
