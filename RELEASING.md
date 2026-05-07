# Releasing FluxEdit

This is the manual release flow. CI builds a signed APK on every push to a
`v*` tag and attaches it to the GitHub Release when the release-signing
secrets are configured.

## One-time keystore setup

```bash
keytool -genkey -v \
  -keystore flux-release.jks \
  -alias flux \
  -keyalg RSA -keysize 2048 \
  -validity 36500
```

Then:

```bash
cp keystore.properties.example keystore.properties
# Edit keystore.properties with the alias / passwords / file path you used.
```

Both `flux-release.jks` and `keystore.properties` are gitignored. Back them
up — losing the keystore means future updates can't replace existing installs.

## Versioning

`versionCode` and `versionName` are derived at build time from git
(`app/build.gradle.kts`):

- **versionCode**: `git rev-list --count HEAD` (every commit bumps it).
- **versionName**: `git describe --tags --dirty --always`. With a clean tag
  on HEAD it'll be e.g. `v1.2.0`; otherwise `v1.2.0-3-gabc1234[-dirty]`.

For a release, tag and push:

```bash
git tag v1.2.0
git push origin v1.2.0
```

CI then builds + creates a GitHub Release with the signed APK attached.

To pin exact values without a tag (e.g. for a hotfix), set environment vars:

```bash
VERSION_CODE_OVERRIDE=12345 VERSION_NAME_OVERRIDE=v1.2.0-hotfix \
  ./gradlew assembleRelease
```

## Building locally

```bash
# Signed APK (sideload-distribution)
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk

# Signed AAB (Play Store)
./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

Verify the signature before publishing:

```bash
$ANDROID_HOME/build-tools/<latest>/apksigner verify --verbose \
  app/build/outputs/apk/release/app-release.apk
```

You should see `Verified using v2 scheme (APK Signature Scheme v2): true`.

## Smoke test on a real device

The release APK is signed with a different key than the debug build, so
Android won't let it replace an installed debug. Either:

- **Test on a clean device / emulator** (preferred), then install via
  `adb install app-release.apk`.
- **Uninstall the debug app first** (loses user data; settings + cached
  thumbnails go away), then install release. The Keystore-backed token
  store will start empty and prompt for setup.

Things to verify on first launch of a fresh install:

1. Biometric unlock works (or a recoverable error if biometrics aren't set up).
2. Setup screen accepts LAN + Tailscale URLs and the API token.
3. Connection test passes; Home shows a connected health dot.
4. Generate one image end-to-end (Camera or Gallery → prompt → Submit → Result).
5. Reopen the app — token persisted, no re-prompt for setup.

## Distribution: tag a release

CI is configured to produce a **signed APK** when a `v*` tag is pushed,
provided four GitHub Secrets are set on the repo:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 flux-release.jks` of the keystore file |
| `KEYSTORE_STORE_PASSWORD` | `storePassword` from your `keystore.properties` |
| `KEYSTORE_KEY_ALIAS` | `keyAlias` from your `keystore.properties` |
| `KEYSTORE_KEY_PASSWORD` | `keyPassword` from your `keystore.properties` |

### One-time GitHub Secrets setup

```bash
# From the repo root, where flux-release.jks lives
gh secret set ANDROID_KEYSTORE_BASE64 < <(base64 -w0 flux-release.jks)
gh secret set KEYSTORE_STORE_PASSWORD --body 'your-store-password'
gh secret set KEYSTORE_KEY_ALIAS --body 'flux'
gh secret set KEYSTORE_KEY_PASSWORD --body 'your-key-password'
```

If any of these are missing, tag builds fail before creating a GitHub Release.
That keeps production APKs from being published unsigned or debug-signed by
accident.

### Tagging a release

```bash
# 1. Sanity check the working tree
git status                # clean
./gradlew testDebugUnitTest spotlessCheck lintDebug   # green

# 2. Tag and push
git tag v1.0.0
git push origin v1.0.0
```

CI runs the workflow, builds a signed `flux-edit-v1.0.0.apk`, and creates a
GitHub Release with the APK attached and auto-generated release notes from
the commit history.

### Other channels

- **Personal sideload**: skip GitHub entirely, just copy the local
  `app-release.apk` somewhere private (Tailscale Drive, SMB, etc.).
- **Play Store**: not currently wired. Would need a separate upload key,
  Play Console account, and `bundleRelease` (AAB) in the workflow.

## Pre-release checklist

- [ ] `git status` clean — no `-dirty` suffix in versionName.
- [ ] On a release tag: `git describe --tags` returns the tag exactly.
- [ ] `./gradlew testDebugUnitTest` green (also runs in CI).
- [ ] `./gradlew lintDebug` no NEW warnings beyond `lint-baseline.xml`.
- [ ] `./gradlew spotlessCheck` passes.
- [ ] `./gradlew assembleRelease` produces a signed APK.
- [ ] Manual smoke test on a clean install (see above).
- [ ] Push tag → CI green → attach signed APK to the GitHub Release.
