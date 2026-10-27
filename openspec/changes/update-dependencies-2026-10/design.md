# Design

## Context

- **Audit:** read-only, against `origin/main` at v3.3.0. 23 of 44 items are current, including every other CI action.
- **Toolchain:** JDK 21 is pinned in `gradle/gradle-daemon-jvm.properties` with auto-download off. Locally it lives in `~/tools/jdk-21`; run Gradle with `JAVA_HOME=$HOME/tools/jdk-21`.
- **Gates:** the pre-push hook runs `spotlessCheck testDebugUnitTest assembleDebug lintDebug`. CI also runs instrumented tests on an API 36 emulator and an R8 release build.
- **Constitution:** versions live in `gradle.properties` / `libs.versions.toml` and `docs/build.md`, which remain the source of truth.

## Goals / Non-Goals

**Goals:**
- Every audited item is on its latest stable release.
- All gates are green.
- Nothing changes in user-visible behaviour.

**Non-Goals:**
- JDK 25.
- Renovate configuration.
- Lint clean-up beyond what the bumps force.

## Decisions

1. **One change instead of merging the Renovate PRs one by one.**
   - The PRs cover 5 of the 21 items, and #26 is wrong.
   - Kotlin, KSP and Compose interact. Updating them together and running the full gate once is less work than five rebases.
2. **Regenerate the wrapper with Gradle itself.** Do not hand-edit `distributionUrl`, so the checksum and wrapper jar match.
3. **Lint baseline policy.** A new check that fires on existing code is fixed if the fix is mechanical and local. Otherwise it is added to `lint-baseline.xml` with a note under Follow-ups. Existing baseline entries are not touched.
4. **Source changes only where the build or tests force them.** API deprecations that are only warnings are left alone, except where lint treats them as errors. They are recorded under Follow-ups.
5. **Release as a patch (v3.3.1).** Same tag procedure as before (`docs/build.md`, "Where release tags go").

## Risks / Trade-offs

- **Compose BOM 2026.09 or navigation 2.10 changes rendering or navigation behaviour.**
  - Mitigations: the instrumented tests in CI and a manual smoke on the device after Obtainium installs the release.
- **AGP 9.4 with Gradle 9.8 raises new deprecations.**
  - Mitigation: these are warnings unless the build fails, and they are noted.
- **benchmark 1.5.0 stable changes the baseline-profile plugin.**
  - Mitigation: the release build in CI exercises it.

## Migration Plan

1. Merge to dev, then make a release PR to main. Tag v3.3.1 on the dev-side commit. CI publishes the signed APK.
2. Close Renovate PRs #24–#28 with a pointer to the merged PR.
3. **Rollback:** Obtainium can install v3.3.0 again from Releases.

## Follow-ups

- A separate change for JDK 25 (`upgrade-build-jdk-25`), if wanted.
