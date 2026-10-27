# Tasks

Gradle needs JDK 21: prefix every command with `JAVA_HOME=$HOME/tools/jdk-21`.

## 1. Versions

- [ ] 1.1 Update every key in the proposal table in `gradle/libs.versions.toml`. Guava stays on the `-android` flavor. Verify: `./gradlew :app:compileDebugKotlin` succeeds.
- [ ] 1.2 Regenerate the wrapper: `./gradlew wrapper --gradle-version 9.8.2`, then run it a second time so the jar is refreshed. Verify: `./gradlew --version` reports 9.8.2.
- [ ] 1.3 Fix only what the bumps break in the build or tests (design Decisions 3–4). Record any lint baseline additions or deferred deprecations under Follow-ups. Verify: `./gradlew spotlessCheck lint testDebugUnitTest assembleDebug` succeeds, and the unit-test count is not lower than the 178 at v3.3.0.
- [ ] 1.4 Run `./gradlew assembleRelease` with `CI=true` (unsigned), so R8 and the baseline-profile plugin are exercised. Verify: it succeeds.

## 2. CI

- [ ] 2.1 In `.github/workflows/android.yml`, change `actions/setup-java@v5` to `@v6` in all three jobs. Verify: the PR's CI run passes, including `instrumented-tests`.

## 3. Release

- [ ] 3.1 **[operator]** Merge, make the release PR, tag v3.3.1 on the dev-side commit, and confirm that the GitHub release carries the signed APK. Then close Renovate PRs #24–#28 with a pointer to the merged PR.
- [ ] 3.2 **[operator]** After Obtainium installs v3.3.1: open the gallery, submit one FLUX.3 edit, and open a result in the viewer. Verify: all three work, and the job reaches `done`.

## Workflow follow-up

- Archive with `openspec archive update-dependencies-2026-10` after release.
