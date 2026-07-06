# Phase 1 Data Model: Fix Custom Prompt Field Rendering When Unfolded

No new entities, persisted fields, or storage schemas are introduced. This is a
pure Compose layout fix to `PromptLibraryContent`'s internal structure
(`app/src/main/java/dev/zun/flux/ui/home/PromptSheets.kt`) — no `JobEntity`,
Room, DataStore, or `SharedPreferences` shape changes, no new interface
contract, and no server-side involvement.
