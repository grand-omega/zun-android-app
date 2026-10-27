# Tasks

## 1. Default workflow switch

- [ ] 1.1 In `app/src/main/java/dev/zun/flux/data/api/Workflows.kt`, set `DEFAULT_EDIT = "flux3_klein_edit"`. Leave `TRY_HARDER_EDIT` unchanged. Verify: `./gradlew :app:compileDebugKotlin`.
- [ ] 1.2 `HomeViewModelTest.kt`:
  - change `submit_customPromptUsesDefaultWorkflowWhenTryHarderIsOff` to expect `flux3_klein_edit`;
  - add a test that saving a prompt from the composer calls `createPrompt` with `workflow = "flux3_klein_edit"`;
  - add a test that submitting with a `FakeJobRepository` saved prompt (bound to `flux2_klein_edit`) sets `lastPromptId` to that id and sends no `prompt_text`. Do not assert anything about `lastWorkflow` on this path (design Decision 2).

  Verify: `./gradlew testDebugUnitTest`.
- [ ] 1.3 Extend `JobEntityTest` so that an entity and DTO with `workflow = "flux2_klein_edit"` round-trip unchanged through the mapping that the gallery and photo viewer use. Verify: `./gradlew testDebugUnitTest`.
  - Status 1.1-1.3: code and tests are committed in `882a940`, but their stated Gradle checks are unconfirmed (see 2.2: the toolchain is not available here without a large download). Check these boxes once `./gradlew lint testDebugUnitTest compileDebugKotlin` is reproduced and passes.
- [x] 1.4 Check `docs/architecture.md` and `README.md` for workflow-name mentions. None are expected, so no change. Verify: `grep -rn "klein_edit" docs README.md` returns nothing that needs updating.

## 2. Integration

- [ ] 2.1 **[operator]** Run a debug build against the dev server, using the server tag that ships `add-flux3-klein-edit`:
  - Home: submit one custom-prompt edit;
  - Home: submit one edit with a pre-existing FLUX 2 saved prompt;
  - Home: save one new prompt;
  - Result screen: regenerate a custom-prompt result;
  - Result screen: regenerate a saved-prompt result;
  - Result screen: save a new prompt;
  - regenerate an older FLUX 2 custom job;
  - open an older FLUX 2 job in the gallery.

  Verify: the operator reports the workflow name shown in the photo viewer or prompt list for each path:
  - `flux3_klein_edit` for the custom submit, the custom regenerate, both new saved prompts and the old-job regenerate;
  - `flux2_klein_edit` for both saved-prompt paths;
  - the old job opens normally.
- [ ] 2.2 Run the CI gates locally: `./gradlew lint testDebugUnitTest`, plus `connectedDebugAndroidTest` on the API 36 emulator. Verify: all pass.
  - Status: **unverified.** The local gates (`lint`, `testDebugUnitTest`, `:app:compileDebugKotlin`) have no reproducible passing result. The agent environment has no Gradle 9.7.1 wrapper distribution (cached: 8.2 and 8.11.1), no AGP 9.3.1 in the dependency cache, and no JDK 21 (only JDK 17 and 25); fetching them would be a large download, so they were not run. In the agent sandbox `~/.gradle` passes `test -w`, but creating files under it is denied, so the wrapper cannot create its lock; such an environment needs `GRADLE_USER_HOME=<writable dir>` and `-Porg.gradle.java.installations.paths=<JDK 21 home>`.
  - TODO(operator): on a machine with that toolchain, run `./gradlew lint testDebugUnitTest compileDebugKotlin` and report pass/fail and test counts. Fix any failure before checking 1.1-1.3.
  - TODO(operator): on an API 36 emulator, run `./gradlew connectedDebugAndroidTest`. Required result: `BUILD SUCCESSFUL` with 0 failed instrumented tests. Report pass/fail and the test count only. This box stays unchecked until that pass is reported.

## Workflow follow-up

- Release only after the zun-rust-server change is deployed. Name its concrete tag in the release notes as the minimum server version.
- Archive with `openspec archive add-flux3-klein-edit` after review and release.
