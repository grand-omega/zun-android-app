# Phase 1 Data Model: Fix Setup Screen Keyboard Layout Squish

No new entities, persisted fields, or storage schemas are introduced. This is a pure
Compose layout/presentation fix — it changes how two existing screens (`SetupScreen.kt`,
`SettingsScreen.kt`) compute window-inset padding around their existing server-URL/token
fields. No `JobEntity`/Room/DataStore/SharedPreferences shape changes, no new interface
contract, and no server-side involvement.
