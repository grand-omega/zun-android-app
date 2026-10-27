# Spec Delta

## Purpose

Defines what "current" means for the app's build dependencies, wrapper and CI actions, and how an update is verified before release.

## ADDED Requirements

### Requirement: Build dependencies are on their latest stable release
Every version in `gradle/libs.versions.toml` SHALL be on its latest stable release, with no alpha, beta, rc, dev or SNAPSHOT version. Guava SHALL use its `-android` flavor. The Gradle wrapper SHALL be on its latest stable release. The only exception is an entry recorded under Follow-ups in the change's design.md, with the reason it is held back.

#### Scenario: Audit after the update
- **WHEN** each catalog version and the wrapper are compared with the newest stable versions on Google Maven, Maven Central and gradle.org
- **THEN** every entry matches, except those listed as held back with a reason

#### Scenario: No prerelease versions remain
- **WHEN** `gradle/libs.versions.toml` is searched for `alpha`, `beta`, `rc`, `dev` or `SNAPSHOT`
- **THEN** there are no matches

### Requirement: CI actions are on their latest major
Every GitHub Action in `.github/workflows/` SHALL reference its latest major version tag.

#### Scenario: setup-java
- **WHEN** `.github/workflows/android.yml` is read
- **THEN** every `actions/setup-java` step uses `@v6`

### Requirement: Updates pass every gate without behaviour change
A dependency update SHALL pass `spotlessCheck`, `lint`, `testDebugUnitTest`, `assembleDebug`, an R8 `assembleRelease`, and the CI instrumented tests. No test SHALL be removed or weakened, and user-visible behaviour SHALL NOT change.

#### Scenario: Local gates
- **WHEN** the local gate commands run with JDK 21
- **THEN** all succeed, and the unit-test count is not lower than before the update

#### Scenario: Installed release smoke test
- **WHEN** the released APK is installed and the user opens the gallery, submits a FLUX.3 edit and opens the result
- **THEN** each step works, and the job reaches `done`
