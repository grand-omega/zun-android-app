# Feature Specification: Debug Build Server Isolation

**Feature Branch**: `003-debug-server-isolation`

**Created**: 2026-07-05

**Status**: Draft

**Input**: User description: "isolate production and development properly. Currently we do have two version of the app. but both use the same production server (i.e., htts://zun.h.doremysweet.com). For development build (default), restrict to a dedicated local servers, make sure host it locally. The server repo is in /home/doremy/Desktop/grand-omega/zun-rust-server (reference this in consititution). But if you need to touch code in server, it has to be in dev branch of course."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Debug build defaults to the local development server (Priority: P1)

As a developer, when I install the debug build fresh, I want it already pointed at my local development server instead of starting blank or defaulting to production, so that from the very first launch none of my test traffic can reach the shared production server by mistake.

**Why this priority**: This is the core of "isolate dev and prod" — without a local-first default, every fresh debug install starts one typo away from talking to production. It delivers value on its own even before any restriction exists.

**Independent Test**: Install the debug build on a clean device/emulator with no prior configuration and confirm the server address is pre-set to a local development address rather than blank or the production address.

**Acceptance Scenarios**:

1. **Given** a fresh debug-build install with no server previously configured, **When** the developer completes first-run setup, **Then** the app's server address is pre-set to a local development address rather than being blank or the production address.
2. **Given** the debug build's default local address is unreachable (e.g., the local server isn't running yet), **When** the developer opens the app, **Then** the app clearly indicates it cannot reach the local development server — distinctly from other network errors — and lets the developer edit the address with no fallback to production.

---

### User Story 2 - Debug build refuses the production server address (Priority: P2)

As a developer, if I (or a future version of myself) try to enter the production server's address into a debug build, I want the app to refuse it outright, so a moment of carelessness can never send debug testing traffic into the real production job queue.

**Why this priority**: This is the enforcement half of isolation — the default from Story 1 only helps if it can't be silently overwritten with the production address later. Depends on Story 1 existing but is independently verifiable.

**Independent Test**: In a debug build, attempt to save the exact known production server address in Settings → Connection (and during first-run setup) and confirm it is rejected with a clear explanation, while any other address is accepted normally.

**Acceptance Scenarios**:

1. **Given** the debug build's connection settings, **When** the developer enters the exact known production server address and attempts to save it, **Then** the app rejects the save, explains why, and leaves the previously saved valid address (if any) unchanged.
2. **Given** the same screen, **When** the developer enters any other address (their local server, a different LAN host, etc.), **Then** the save proceeds normally with no additional restriction beyond what exists today.

---

### User Story 3 - Release build behavior is unchanged (Priority: P3)

As a developer distributing or using the release build, I want its ability to connect to the production server to keep working exactly as it does today, so this developer-focused isolation feature introduces zero regression for real usage.

**Why this priority**: Protects existing production usage from any side effect of the debug-only changes above. Lowest priority only because it's a "must not change" guarantee rather than new capability.

**Independent Test**: In a release build, confirm entering the production server address (or any other address) in Settings → Connection is accepted and behaves exactly as before this feature.

**Acceptance Scenarios**:

1. **Given** the release build, **When** a developer or user enters the production server address in Settings → Connection, **Then** it is accepted and works exactly as before.
2. **Given** the release build, **When** any address is entered, **Then** no debug-only restriction applies.

---

### Edge Cases

- A developer working away from their usual network (e.g., traveling) needs a debug build to reach some other non-local, non-production address (a friend's test server, a different remote host) — this MUST continue to work, since the restriction only targets the exact known production hostname, not "non-local" addresses in general.
- A developer wants to intentionally test a debug build against production (e.g., to reproduce a production-only bug) — not supported by this feature; the documented path is to use the release build for that scenario instead.
- A debug build that was installed and configured with the production address before this feature shipped — not automatically migrated or detected; the developer reinstalls the debug build to pick up the new default (see Assumptions).
- Debug and release builds installed side-by-side on the same device must keep their stored server addresses and credentials fully independent, so the debug-only restriction never leaks into or affects the release build's stored configuration.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The debug build MUST come pre-configured with a default server address pointing at a local development server, rather than starting blank or pointing at the production address.
- **FR-002**: The debug build MUST reject any attempt to save the exact known production server address (`https://zun.h.doremysweet.com`) as its server address, whether during first-run setup or later in Settings → Connection.
- **FR-003**: When rejecting a production address, the debug build MUST clearly explain to the developer why the address was rejected and MUST leave the previously saved valid address (if any) unchanged.
- **FR-004**: The debug build's restriction MUST apply only to the exact known production hostname; any other address (local, LAN, or other remote hosts) MUST be accepted exactly as it is today, with no additional validation.
- **FR-005**: The release build's behavior for entering and using any server address, including the production address, MUST remain unchanged.
- **FR-006**: The debug build and release build MUST continue to store their server addresses and credentials completely independently of one another, so this restriction never affects the release build.
- **FR-007**: This feature MUST NOT include any automatic migration, detection, or prompting for debug installs that already have the production address saved from before this change; existing debug installs are handled by the developer manually reinstalling the debug build.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A freshly installed debug build has a local development address configured before the developer performs any manual server setup step.
- **SC-002**: 100% of attempts to save the exact production address into a debug build are rejected, verified both during first-run setup and later settings edits.
- **SC-003**: A developer can tell why a save attempt was rejected from the on-screen explanation alone, without needing to check logs or ask anyone.
- **SC-004**: Every release-build server-configuration scenario that worked before this change continues to work identically after — zero regressions.

## Assumptions

- "Local development server" refers to the developer's own `zun-rust-server` instance together with the `zun-flux-pipeline` (ComfyUI/FLUX) inference process it depends on; both are run on the developer's Ubuntu Linux GPU workstation, which is used for the large majority of development work.
- Any change required in `zun-rust-server` to support this feature (e.g., exposing an environment identifier) will be made on that repository's `dev` branch, per its existing branching practice.
- The exact default local address pre-filled in the debug build (e.g., a LAN hostname or IP for the developer's workstation) is expected to remain editable in Settings → Connection for cases where the developer's network setup differs from the built-in default.
- No override mechanism is provided for pointing a debug build at production; a developer who needs to validate the production-only path uses the release build instead.
- Existing debug-build installs that already have the production address saved are not automatically migrated; the developer will uninstall and reinstall the debug build to pick up the new default.
