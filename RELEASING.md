# Releasing FluxEdit

This is the manual release flow. CI builds an unsigned APK on every push to a
`v*` tag and attaches it to the GitHub Release; for an actually installable
build you need a local signing key.

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

CI then builds + creates a GitHub Release with the unsigned APK attached.

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

## Distribution

- **Personal sideload**: copy the signed APK to a private location (e.g. an
  SMB share, Tailscale Drive, or a self-hosted file host).
- **GitHub Release**: tag-triggered CI uploads an unsigned APK. Users would
  need to side-load and bypass "package not signed" warnings, so prefer the
  signed-APK route for anything you actually expect people to install.
- **Play Store**: not currently set up. To enable, you'd need to:
  1. Generate an upload key (separate from the release signing key).
  2. Add Play Console account.
  3. Add a CI workflow with secrets for upload-key + Play API credentials.
  Out of scope until there's user demand.

## Pre-release checklist

- [ ] `git status` clean — no `-dirty` suffix in versionName.
- [ ] On a release tag: `git describe --tags` returns the tag exactly.
- [ ] `./gradlew testDebugUnitTest` green (also runs in CI).
- [ ] `./gradlew lintDebug` no NEW warnings beyond `lint-baseline.xml`.
- [ ] `./gradlew spotlessCheck` passes.
- [ ] `./gradlew assembleRelease` produces a signed APK.
- [ ] Manual smoke test on a clean install (see above).
- [ ] Push tag → CI green → attach signed APK to the GitHub Release.
