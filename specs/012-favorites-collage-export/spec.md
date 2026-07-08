# Feature Specification: Favorites Collage Export

**Feature Branch**: `012-favorites-collage-export`

**Created**: 2026-07-07

**Status**: Draft

**Input**: User description: "收藏合集导出——挑几张收藏,本地拼成一张四宫格/拼贴图导出分享，让分享出去的东西比单张图更有'这是我的作品集'的感觉，纯本地拼图，不涉及服务端。" (Favorites collage export — pick a few favorites, locally compose them into a four-panel grid / collage image, export and share, so what's shared feels more like "this is my portfolio" than a single image. Purely local composition, no server involvement.)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Turn a handful of favorites into one shareable collage (Priority: P1)

A user has favorited a few of their best AI edits and wants to share them together as one combined image — a small "portfolio" moment — rather than sharing one photo at a time. They select a few images in the gallery, choose to create a collage, and get back a single combined image arranged in a clean grid, ready to save or share.

**Why this priority**: This is the entire feature — without it there's no collage capability at all.

**Independent Test**: Select 4 favorited images in the gallery, choose the collage action, and confirm a single combined image is produced showing all 4 arranged in a grid, which can then be saved to the device or shared like any other image.

**Acceptance Scenarios**:

1. **Given** the user has selected 4 images in the gallery, **When** they choose to create a collage, **Then** a single combined image is produced showing all 4 arranged in a clean four-panel grid.
2. **Given** the user has selected 2 or 3 images instead of 4, **When** they create a collage, **Then** a single combined image is produced with all selected images filling the frame in a sensible arrangement — no empty or blank panels.
3. **Given** a collage has been produced, **When** the user chooses to save or share it, **Then** it behaves like any other exported image — saved to the device's photo storage, or handed to the system share sheet as one file — not as separate individual images.
4. **Given** the device has no network connection, **When** the user creates and exports a collage from images already available on the device, **Then** the whole flow (composing, saving, sharing) completes successfully with no server involvement.

### Edge Cases

- What happens if the user selects only 1 image? The collage action isn't available — at least 2 images are required to make a collage meaningful.
- What happens if the user selects more than 4 images? The collage action isn't available until the selection is narrowed back down to 4 or fewer, avoiding any ambiguity about which images would have been picked.
- What happens if one of the selected images isn't available offline (not yet cached and the device has no connection)? The user gets a clear, explicit message that the collage can't be composed until that image is available — the same "offline, unavailable" treatment already used elsewhere in the app — rather than silently substituting a lower-quality placeholder or dropping that image from the collage.
- What happens when the selected images have different aspect ratios (mixing portrait and landscape)? Each image is cropped to fit its panel uniformly, so the final collage has a clean, consistent grid regardless of the originals' shapes.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to select between 2 and 4 images in the gallery and combine them into a single collage image.
- **FR-002**: The collage's layout MUST adapt to the number of images selected (2, 3, or 4), always filling the whole frame with no empty panels.
- **FR-003**: Composing the collage MUST happen entirely on-device, with no server request involved.
- **FR-004**: Each source image MUST be uniformly cropped to fit its panel, regardless of its original aspect ratio, so the collage reads as one clean grid.
- **FR-005**: The finished collage MUST be exportable the same way a single image already is in this app — savable to the device's photo storage and shareable via the system share sheet — as one combined file, not as separate images.
- **FR-006**: The collage action MUST only be available when between 2 and 4 images are selected; outside that range, it's unavailable rather than silently truncating or erroring on the selection.
- **FR-007**: If a selected image isn't available offline, the user MUST be told clearly rather than having the collage silently composed with a placeholder or missing panel.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can go from selecting their favorite images to holding a single shareable collage image in 2 taps or fewer after selection.
- **SC-002**: 100% of exported collages are a single combined image file, never multiple separate files.
- **SC-003**: A collage can be composed, saved, and shared entirely without network connectivity, provided the selected images are already available on the device.
- **SC-004**: 100% of collages produced from mixed-aspect-ratio source images show a uniform, gap-free grid with no distorted or empty panels.

## Assumptions

- The collage action is reached through the gallery's existing multi-select mode (already used for batch save/share/delete) rather than a new, separate selection flow — it becomes available there once the selection count is within range (FR-006). It is not restricted to only favorited images technically, even though favorites are the primary expected source, since gating it to one specific filter would add complexity without changing what the feature is for.
- The collage uses the same higher-quality image tier already used for full-screen viewing and sharing (not the small grid thumbnail), so the exported result looks good when viewed or shared at full size.
- No collage layout customization (reordering panels, choosing a non-grid style, adding captions/borders) is in scope for this version — one sensible, gap-free grid per photo count (2, 3, or 4) is the full extent of the "layout," keeping this a small, focused feature rather than a mini design tool.
- The produced collage is not saved back into the gallery's own job/generation history — it's a one-off exported artifact (saved to the device's general photo storage or shared out), not a new item that appears in this app's own gallery grid.
