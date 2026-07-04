# Phase 0 Research: Return to Running Batch

No `[NEEDS CLARIFICATION]` markers remained in the Technical Context — this
feature is additive within a well-understood existing architecture, so
research focused on confirming the right integration points rather than
resolving unknowns.

## Decision: Source "active jobs" from the existing Room `jobs` table

**Decision**: Add one query — jobs whose `status` is not `done`/`failed`/`cancelled`
and whose id is not in `pending_deletes` — exposed as a new
`JobRepository.activeJobIds(): Flow<List<String>>`.

**Rationale**: `JobEntity`/`JobDao` already stores every job's live `status`
(`data/local/JobEntity.kt`, `data/local/JobDao.kt`). Any job shown in
`BatchProgressScreen` already gets written to Room via `RealJobRepository.getJob()`
the moment its `ProgressViewModel` starts polling (`ui/progress/ProgressViewModel.kt:56-67,87`),
and `JobWatchWorker` (`data/worker/JobWatchWorker.kt`) keeps polling and writing
even with the UI gone. So Room is already an accurate, up-to-date, offline-safe
record of "what's still processing" — no new tracking mechanism is needed.

**Alternatives considered**:
- *Track the active batch in an in-memory/app-singleton flag*: rejected — would
  not survive process death, failing FR-005 (return after a full app close),
  and would duplicate state Room already has correctly.
- *Overload the existing `getJobsFlow()`*: rejected — it hardcodes
  `status == "done"` (`RealJobRepository.kt:280-282`) and backs gallery
  behavior; changing its filter would risk regressing the gallery. A new,
  narrowly-named method keeps the change surgical (Constitution II).

## Decision: Reuse `Routes.batch(jobIds)` instead of a new "resume" route

**Decision**: Home's entry point calls the existing `nav.navigate(Routes.batch(activeIds))`
(`ui/nav/AppNavHost.kt:114-116`) with the current active job ids read from Room,
rather than introducing a persisted/resumable nav argument or a second screen.

**Rationale**: `Routes.BATCH_PROGRESS = "batch/{jobIds}"` already accepts an
arbitrary comma-joined id list (`ui/nav/Routes.kt:11,17`) and
`BatchProgressScreen` already handles 1..N jobs, per-tile deletion, and an
empty-list auto-pop back to Home (`ui/nav/AppNavHost.kt:200-245`). Reconstructing
the route from Room's current state at tap time is simpler than trying to keep
the original back-stack entry alive, and it's the only approach that also
satisfies FR-005 (the original entry is provably gone after process death;
Room is not).

**Alternatives considered**:
- *Keep the `batch/{jobIds}` back-stack entry alive instead of popping it on
  back*: rejected — changes existing back-button semantics app-wide (user
  expects back to leave the screen) and still wouldn't survive a full app
  close, so FR-005 would need a second mechanism anyway. Simpler to have one
  mechanism (Room) cover both cases.

## Decision: Aggregate all active jobs across concurrent batches into one view

**Decision**: The entry point represents *all* currently non-terminal jobs,
regardless of which submission created them (FR-006), and tapping it opens
one `BatchProgressScreen` over that full set.

**Rationale**: The screen and route were already designed around "a list of
job ids to watch," not "one specific batch" — there is no batch identifier
anywhere in the data model (`JobEntity` has no batch/group column). Building
per-batch grouping would require new schema and tracking with no functional
requirement asking for it; a single combined view is both simpler and matches
FR-006 directly.

**Alternatives considered**:
- *Only show/return to the most recently submitted batch*: rejected as the
  spec explicitly requires all active jobs to stay reachable (FR-006), and
  older still-processing jobs would otherwise become unreachable again.

## Decision: Entry point placement and form

**Decision**: A small dismissible-by-completion (not user-dismissible) banner
in `HomeScreen.kt`, shown/hidden purely by whether `activeJobIds` is non-empty,
labeled with the count (e.g. "3 photos processing") and tapping it navigates
via the callback above.

**Rationale**: Home is where the user already lands after leaving the live
view (`onBack = { nav.popBackStack(Routes.HOME, inclusive = false) }`), so
it's the natural place to look for a way back in. A banner matches this
codebase's existing pattern for transient, status-driven affordances (e.g.
`HomeHealthUi.kt`) rather than introducing a new UI primitive.

**Alternatives considered**:
- *Persistent system notification instead of/in addition to an in-app
  banner*: rejected as unnecessary — `JobNotifications`/`JobWatchWorker`
  already notify per-job on completion; adding a second, longer-lived
  "still running" notification is scope beyond what FR-001..FR-008 ask for
  and risks notification fatigue.
