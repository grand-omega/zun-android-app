# Phase 1 Contracts: Return to Running Batch

This feature has no external/server-facing interface — `FluxApi.kt` and
`../zun-rust-server/docs/API_CONTRACT.md` are unaffected (Constitution V:
N/A). The contracts below are the internal seams between layers that an
implementation must honor so the feature composes cleanly with existing code.

## 1. `JobRepository.activeJobIds()`

```kotlin
/** Ids of jobs not yet in a terminal state (done/failed/cancelled) and not
 *  locally deleted. Backed by local Room state — no network call. */
fun activeJobIds(): Flow<List<String>>
```

- **Owner**: `data/repo/JobRepository.kt` (interface), implemented in
  `data/repo/RealJobRepository.kt` by delegating to a new `JobDao` query.
- **Guarantees**: Emits synchronously from local Room state; never throws;
  reflects deletions the same way `getVisibleJobs()` already does.
- **Non-guarantees**: Does not guarantee ordering; callers that need a stable
  order should sort (e.g. by `createdAt`) after collecting.

## 2. Home → Navigation callback

```kotlin
// HomeRoute.kt / AppNavHost.kt
onResumeBatch: (jobIds: List<String>) -> Unit
```

- **Caller**: `HomeScreen`'s entry point, invoked with the current value of
  `activeJobIds` when the user taps it.
- **Callee**: `AppNavHost.kt`, wired the same way `onBatchSubmitted` already
  is: `onResumeBatch = { jobIds -> nav.navigate(Routes.batch(jobIds)) }`.
- **Precondition**: `jobIds` is non-empty (the entry point is not shown
  otherwise — see FR-004); `Routes.batch()` joins them into the existing
  `batch/{jobIds}` route unchanged.

## 3. `HomeViewModel` exposed state

```kotlin
val activeJobIds: StateFlow<List<String>>
```

- **Owner**: `HomeViewModel`, sourced from `jobRepo.activeJobIds()` via
  `stateIn(...)`, following the same pattern already used for `prompts`
  (`HomeViewModel.kt:110-118`).
- **Consumer**: `HomeScreen` composable — renders the entry point iff this
  list is non-empty, and reflects its `size` as the displayed count.

No other component needs to change. `BatchProgressScreen`,
`ProgressViewModel`, `JobWatchWorker`, and `JobNotifications` are consumed
as-is with no interface changes.
