# Build

## Toolchain

- **JDK 17** (the build pins `sourceCompatibility`, `targetCompatibility`, and `jvmTarget` to 17)
- **Android SDK** with `compileSdk = 36` (compileSdkMinor `1`), `minSdk = 30`, `targetSdk = 36`
- **Gradle** via the wrapper (`./gradlew`); AGP `9.2.1`, Kotlin `2.3.21`, KSP for Room
- **Git** — recommended; `versionCode` and `versionName` are derived from git history (see [Versioning](#versioning))

## One-time setup

1. **`local.properties`** — copy the example and set your SDK path:

   ```bash
   cp local.properties.example local.properties
   # edit and set sdk.dir=/path/to/Android/sdk
   ```

   Server URL and API token are *not* configured here — both are entered at runtime from the in-app Setup screen and stored in `EncryptedSharedPreferences`.

2. **`keystore.properties`** *(release builds only)* — copy the example and point it at a real keystore:

   ```bash
   cp keystore.properties.example keystore.properties
   keytool -genkey -v -keystore flux-release.jks -alias flux \
           -keyalg RSA -keysize 2048 -validity 36500
   ```

   `keystore.properties` and `*.jks` are gitignored. The build only fails on a missing keystore for `assembleRelease` / `bundleRelease` / `packageRelease` and only when `CI != "true"`. Debug builds work without it.

3. **Git hooks** *(recommended)* — hooks are checked into `.githooks/`. Point git at them once per clone:

   ```bash
   git config core.hooksPath .githooks
   ```

   Currently installed:
   - `pre-push` → mirrors CI: `spotlessCheck`, `testDebugUnitTest`, `assembleDebug`, `lintDebug`. Probes `JAVA_HOME` fallbacks if the configured one is stale. Bypass with `SKIP_PREPUSH=1 git push` or `git push --no-verify`.

## Common commands

```bash
./gradlew assembleDebug                    # build debug APK
./gradlew installDebug                     # build + install on connected device
./gradlew :app:lintDebug                   # lint (uses lint-baseline.xml)
./gradlew test                             # JVM unit tests (Robolectric for Compose)
./gradlew connectedDebugAndroidTest        # instrumented tests (Room migration)
./gradlew spotlessApply                    # format Kotlin sources
./gradlew assembleRelease                  # signed release APK (requires keystore.properties)
./gradlew bundleRelease                    # signed release AAB
```

## Versioning

`versionCode` and `versionName` are computed in `app/build.gradle.kts`:

- `versionCode` = `git rev-list --count HEAD` (monotonically increasing). Falls back to `1` if git is unavailable.
- `versionName` = `git describe --tags --dirty --always`. Examples: `v1.0.0`, `v1.0.0-3-gabc1234`, `v1.0.0-3-gabc1234-dirty`. Falls back to `1.0.0-dev`.

For CI tag builds where the working copy might be a shallow checkout, override either with environment variables:

```bash
VERSION_CODE_OVERRIDE=42 VERSION_NAME_OVERRIDE=v1.2.3 ./gradlew bundleRelease
```

## Room migrations

Schemas are exported by KSP to `app/schemas/dev.zun.flux.data.local.AppDatabase/`. When you bump the database version:

1. Add a `Migration` object in `AppDatabase.kt` and include it in the `Room.databaseBuilder` chain.
2. Run a debug build so KSP emits the new `<version>.json` schema.
3. Add a case to `app/src/androidTest/.../AppDatabaseMigrationTest.kt` covering the upgrade.
4. Commit the new schema JSON.

## Lint

- Baseline: `app/lint-baseline.xml` (regenerate with `./gradlew :app:updateLintBaseline` after intentional fixes).
- Project rules: `app/lint.xml`.

## Release notes

- R8 / shrinker is enabled for `release` (`isMinifyEnabled = true`, `isShrinkResources = true`). Rules live in `app/proguard-rules.pro`.
- The release `signingConfig` is only created when `keystore.properties` exists — otherwise the release variant is unsigned.
