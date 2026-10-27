# Proposal

## Why

An audit on 2026-10-27 found 21 of 44 build items behind their latest stable release: library versions, the Gradle wrapper, AGP, and one CI action. Open Renovate PRs #24–#28 cover only 5 of them, and #26 proposes the wrong Guava flavor (`33.7.2-jre` instead of `-android`). `benchmark` is still on a release candidate even though 1.5.0 stable has shipped.

## What Changes

- **`gradle/libs.versions.toml`:**

  | key | from | to | gap |
  |---|---|---|---|
  | agp | 9.3.1 | 9.4.1 | minor (Renovate #27) |
  | kotlin | 2.4.10 | 2.4.21 | patch |
  | ksp | 2.3.11 | 2.3.12 | patch |
  | composeBom | 2026.08.00 | 2026.09.00 | minor |
  | navigationCompose | 2.9.8 | 2.10.2 | minor |
  | work | 2.11.2 | 2.12.0 | minor |
  | kotlinxSerialization | 1.11.0 | 1.12.0 | minor |
  | coil | 3.5.0 | 3.6.3 | minor (Renovate #28) |
  | robolectric | 4.16.1 | 4.17 | minor |
  | benchmark | 1.5.0-rc01 | 1.5.0 | rc → stable |
  | camerax | 1.6.1 | 1.6.2 | patch (Renovate #25) |
  | spotless | 8.10.0 | 8.10.4 | patch (Renovate #24) |
  | guava | 33.7.1-android | 33.7.2-android | patch (supersedes Renovate #26) |
  | room | 2.8.4 | 2.8.5 | patch |
  | paging, pagingCompose | 3.5.1 | 3.5.3 | patch |
  | okhttp | 5.5.0 | 5.5.1 | patch |
  | coreKtx | 1.19.0 | 1.19.1 | patch |

- **Gradle wrapper:** 9.7.1 → 9.8.2, via `./gradlew wrapper --gradle-version 9.8.2` so the wrapper jar and checksum are regenerated.
- **CI:** `actions/setup-java@v5` → `@v6` in all three jobs of `.github/workflows/android.yml`.
- Renovate PRs #24, #25, #27 and #28 are superseded and closed after the merge. #26 is closed as the wrong flavor.
- Release as v3.3.1.

Non-goals:
- **JDK 25:** the toolchain stays on 21, which is still a maintained LTS. Moving to 25 changes `gradle-daemon-jvm.properties`, CI and `docs/build.md`, so it gets its own change.
- No UI or behaviour change. No Room schema change.
- No Renovate config change.

## Capabilities

### New Capabilities
- `dependency-currency`: how the app keeps its build dependencies, toolchain wrapper and CI actions current and verified.

### Modified Capabilities
<!-- None. -->

## Impact

- **Files:**
  - `gradle/libs.versions.toml`
  - `gradle/wrapper/*`
  - `.github/workflows/android.yml`
  - any source or test changes forced by the minor bumps (navigation 2.10, work 2.12, serialization 1.12, robolectric 4.17)
  - `lint-baseline.xml` only if a new lint check fires on existing code (see design Decision 3)
- **Release:** v3.3.1 through the existing tag-triggered signed release, which Obtainium picks up.
