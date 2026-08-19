# Phase 1 Data Model: Consolidate Duplicated UI Boilerplate

No new entities, persisted fields, or storage schemas are introduced. This is a
structural extract-and-replace refactor across Compose UI files
(`ProgressViewModel.kt`, `ProgressScreen.kt`, `BatchProgressScreen.kt`,
`ui/common/Polish.kt`, `EditHistoryScreen.kt`, `GalleryScreen.kt`,
`SettingsScreen.kt`, `ResultScreen.kt`, `PhotoViewerScreen.kt`) — no `JobEntity`,
Room, DataStore, or `SharedPreferences` shape changes, no new interface
contract, and no server-side involvement.
