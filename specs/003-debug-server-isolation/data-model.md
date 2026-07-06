# Phase 1 Data Model: Debug Build Server Isolation

No new persisted entities, tables, or storage schemas are introduced. This feature adds validation rules and a build-type-scoped default to an existing conceptual entity.

## Server Connection Configuration (existing, extended)

Represents the single server connection a given app install (debug or release) is configured to use. Already persisted by `SettingsManager` (`serverUrl` in plain `SharedPreferences`, `apiToken` in `KeystoreSecureStore`) — unchanged by this feature.

| Attribute | Type | Notes |
|---|---|---|
| `serverUrl` | string, nullable | Existing. Normalized/validated by `ServerUrls.normalizeOptionalServerUrl` before persistence. |
| `apiToken` | string, nullable, encrypted | Existing. Unrelated to this feature. |
| *(new)* default value per build type | derived, not persisted | Debug builds start with a local development address pre-filled (not blank) before the developer has saved anything; release builds start blank as today. Sourced at build time, not stored as app data. |
| *(new)* rejected value: production hostname | validation rule, not a stored field | Debug builds must reject the exact known production hostname (`zun.h.doremysweet.com`, any scheme/port/case) as a value for `serverUrl`. Release builds have no such restriction. |

### Validation rules (delta)

- **Existing**: scheme must be `http`/`https`; `http` only allowed when `allowHttp` (release requires `https`); must include a host; must not include query/fragment.
- **New**: when a `blockHost` is supplied (debug builds only), reject if the candidate URL's host equals that value case-insensitively, regardless of scheme or port.

### State transitions

None — this remains a simple "set/replace the current value" configuration, not a stateful entity. A rejected save leaves the previously persisted `serverUrl` unchanged (FR-003), matching existing behavior for other validation failures (e.g. invalid scheme).
