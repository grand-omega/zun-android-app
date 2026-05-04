# FluxEdit Server API Contract

This file is the ground truth for the wire contract between the Android client and the Project ZUN server. If this document conflicts with any higher-level design document, this document wins.

## Global Rules

- Base path: all API endpoints are under `/api/v1/`.
- Authentication: every request must include `Authorization: Bearer <apiToken>`.
- JSON encoding: request and response bodies use JSON with snake_case field names.
- Unknown JSON fields should be tolerated by clients.
- Timestamps are Unix epoch seconds unless explicitly documented otherwise.
- Image/file endpoints are loaded directly by the Android client and must accept the same bearer token.
- The Android client treats `source_prompt_id` as canonical and falls back to legacy `prompt_id` when needed.

## Health

### `GET /api/v1/health`

Checks whether the service is reachable.

Response body:

```json
{
  "status": "ok"
}
```

Fields:

- `status` string, required.

## Prompts

Prompts are per-user reusable generation presets.

### Prompt Object

```json
{
  "id": 123,
  "label": "Oil paint",
  "description": "Optional user-facing description",
  "text": "Edit prompt text",
  "workflow": "flux2_klein_edit",
  "timeout_seconds": 120,
  "created_at": 1710000000,
  "updated_at": 1710000300
}
```

Fields:

- `id` integer, required in responses. Clients send `0` when creating a prompt.
- `label` string, required.
- `description` string or null, optional.
- `text` string or null, optional.
- `workflow` string or null, optional.
- `timeout_seconds` integer or null, optional.
- `created_at` integer or null, optional.
- `updated_at` integer or null, optional.

### `GET /api/v1/prompts`

Lists available prompts.

Response body:

```json
{
  "items": [
    {
      "id": 123,
      "label": "Oil paint",
      "description": null,
      "text": "Edit prompt text",
      "workflow": "flux2_klein_edit",
      "timeout_seconds": null,
      "created_at": 1710000000,
      "updated_at": 1710000300
    }
  ]
}
```

### `GET /api/v1/prompts/{id}`

Gets one prompt by numeric id.

Path parameters:

- `id` integer, required.

Response body: a Prompt Object.

### `POST /api/v1/prompts`

Creates a prompt.

Request body: a Prompt Object. The client sends `id: 0`.

Response body: the created Prompt Object with server-assigned fields.

### `PATCH /api/v1/prompts/{id}`

Updates a prompt.

Path parameters:

- `id` integer, required.

Request body: a Prompt Object.

Response body: the updated Prompt Object.

### `DELETE /api/v1/prompts/{id}`

Deletes a prompt.

Path parameters:

- `id` integer, required.

Response body: empty.

## Jobs

Jobs represent image generation/edit requests.

### Submit Invariants

The Android client submits jobs with exactly one prompt mode:

- Preset prompt mode: `prompt_id` is set, `prompt_text` is null.
- Custom prompt mode: `prompt_text` is set, `prompt_id` is null, and `workflow` is required.

Custom workflow names currently used by the Android client:

- Default: `flux2_klein_edit`
- Try harder: `flux2_klein_9b_kv_experimental`

### `POST /api/v1/jobs` JSON Probe

The client always tries this cheap JSON request before uploading image bytes.

Request body:

```json
{
  "input_sha256": "hex-encoded-sha256",
  "input_name": "prepared-image.jpg",
  "prompt_id": 123,
  "prompt_text": null,
  "workflow": null
}
```

Fields:

- `input_sha256` string, required.
- `input_name` string or null, optional.
- `prompt_id` integer or null, optional.
- `prompt_text` string or null, optional.
- `workflow` string or null, required when `prompt_text` is present.

Success response:

- HTTP `200` or `202`.
- Body is Job Created Response.

```json
{
  "job_id": "job_abc123",
  "input_id": 456
}
```

Fields:

- `job_id` string, required.
- `input_id` integer or null, optional.

Need-upload response:

- HTTP `409`.
- Body must identify that the server needs the file upload.

```json
{
  "code": "need_upload",
  "need_upload": true,
  "input_id": null
}
```

The Android client accepts either `need_upload: true` or `code: "need_upload"` as the signal.

### `POST /api/v1/jobs` Multipart Upload

Used only after a `409 need_upload` JSON probe response.

Content type: `multipart/form-data`.

Parts:

- `image` file part, required. Android currently uploads prepared JPEG bytes with media type `image/jpeg`.
- `input_sha256` text part, required.
- `input_name` text part, optional.
- `prompt_id` text part, optional.
- `prompt_text` text part, optional.
- `workflow` text part, optional, required when `prompt_text` is present.

Response body: Job Created Response.

```json
{
  "job_id": "job_abc123",
  "input_id": 456
}
```

### `GET /api/v1/jobs/{id}`

Gets detailed status for one job.

Path parameters:

- `id` string, required.

Response body:

```json
{
  "id": "job_abc123",
  "status": "running",
  "input_id": 456,
  "source_prompt_id": 123,
  "prompt_id": 123,
  "prompt_text": null,
  "workflow": "flux2_klein_edit",
  "seed": 987654321,
  "progress": 0.42,
  "error": null,
  "created_at": 1710000000,
  "started_at": 1710000005,
  "completed_at": null,
  "width": 1024,
  "height": 1024
}
```

Fields:

- `id` string, required.
- `status` string, required.
- `input_id` integer or null, optional.
- `source_prompt_id` integer or null, optional. Canonical prompt id.
- `prompt_id` integer or null, optional. Legacy prompt id; clients fall back to it only when `source_prompt_id` is null.
- `prompt_text` string or null, optional.
- `workflow` string or null, optional.
- `seed` integer or null, optional.
- `progress` number or null, optional. Expected range is `0.0` to `1.0`.
- `error` string or null, optional.
- `created_at` integer, required.
- `started_at` integer or null, optional.
- `completed_at` integer or null, optional.
- `width` integer or null, optional.
- `height` integer or null, optional.

Client-recognized terminal statuses:

- `done`
- `failed`
- `cancelled`

Any other status is treated as queued/running for display and polling.

### `GET /api/v1/jobs`

Lists jobs with cursor pagination.

Query parameters:

- `status` string, optional. Android defaults to `done` for history sync.
- `limit` integer, optional. Android defaults to `50`, and uses `100` for background history sync.
- `cursor` string, optional and opaque.
- `input_id` integer, optional.

Response body:

```json
{
  "items": [
    {
      "id": "job_abc123",
      "status": "done",
      "input_id": 456,
      "source_prompt_id": 123,
      "prompt_id": 123,
      "prompt_text": null,
      "workflow": "flux2_klein_edit",
      "seed": 987654321,
      "created_at": 1710000000,
      "completed_at": 1710000060,
      "duration_seconds": 60
    }
  ],
  "next_cursor": null
}
```

Job summary fields:

- `id` string, required.
- `status` string, optional, defaults client-side to `done` if absent.
- `input_id` integer or null, optional.
- `source_prompt_id` integer or null, optional. Canonical prompt id.
- `prompt_id` integer or null, optional. Legacy prompt id.
- `prompt_text` string or null, optional.
- `workflow` string or null, optional.
- `seed` integer or null, optional.
- `created_at` integer, required.
- `completed_at` integer or null, optional.
- `duration_seconds` integer or null, optional.

Pagination:

- `next_cursor` string or null, optional.
- Clients treat `cursor` as opaque and pass it back unchanged.

### `DELETE /api/v1/jobs/{id}`

Soft-deletes a job on the server.

Path parameters:

- `id` string, required.

Response body: empty.

Client behavior:

- The Android client hides the job locally immediately.
- If a later delete sync receives HTTP `404`, the client treats the delete as already complete.

### `POST /api/v1/jobs/{id}/restore`

Restores a soft-deleted job during the server's restore window.

Path parameters:

- `id` string, required.

Response body: empty.

Client behavior:

- After restore, the Android client attempts to fetch the job again to repopulate Room.

### `POST /api/v1/jobs/{id}/cancel`

Cancels a queued or running job.

Path parameters:

- `id` string, required.

Response body: empty.

Client behavior:

- After cancel, the Android client fetches the job again so local observers see status `cancelled`.

## Inputs

Inputs are addressable uploaded source images.

### `GET /api/v1/inputs/{id}`

Gets metadata for an input.

Path parameters:

- `id` integer, required.

Response body:

```json
{
  "id": 456,
  "sha256": "hex-encoded-sha256",
  "available": true,
  "original_name": "source.jpg",
  "content_type": "image/jpeg",
  "size_bytes": 123456,
  "width": 2048,
  "height": 1536,
  "created_at": 1710000000,
  "last_used_at": 1710000300
}
```

Fields:

- `id` integer, required.
- `sha256` string, required.
- `available` boolean, required.
- `original_name` string or null, optional.
- `content_type` string or null, optional.
- `size_bytes` integer or null, optional.
- `width` integer or null, optional.
- `height` integer or null, optional.
- `created_at` integer, required.
- `last_used_at` integer or null, optional.

### `GET /api/v1/inputs/{id}/file`

Downloads input image bytes.

Path parameters:

- `id` integer, required.

Response body: binary image data.

Client usage:

- Coil loads this URL for input previews.
- The repository downloads this file into private cache when the user reuses a recent/original input.

## Image Result Endpoints

These endpoints are not declared as Retrofit JSON methods, but they are part of the server contract because the Android client builds these URLs and loads them with Coil or file helpers.

### `GET /api/v1/jobs/{id}/thumb`

Returns a thumbnail image for a job.

Path parameters:

- `id` string, required.

Response body: binary image data.

Client usage: gallery thumbnails or compact previews.

### `GET /api/v1/jobs/{id}/preview`

Returns a preview image for a job. The Android client expects roughly viewer-sized output, about 1280 px on the long edge.

Path parameters:

- `id` string, required.

Response body: binary image data.

Client usage: result comparison, batch done tiles, full-screen photo viewer.

### `GET /api/v1/jobs/{id}/result`

Returns the original result image.

Path parameters:

- `id` string, required.

Response body: binary image data.

Client usage: save-to-gallery and share actions. This can be larger than preview and should not be used for ordinary thumbnails.

## Error Handling Expectations

The Android client has explicit behavior for these responses:

- `401` from health/setup prompt validation: show unauthorized or invalid token state.
- `409` from JSON job submit with `need_upload`: retry the same submit as multipart.
- `404` from pending delete sync: treat as already deleted and clear pending delete.

For other non-success job-submit JSON responses, the client reports `Submit failed: HTTP <code>`.

For IO failures during submit, the client retries up to 3 attempts with exponential backoff before surfacing failure.

## Compatibility Notes

- Server responses may include extra fields; clients ignore unknown fields.
- `source_prompt_id` should be used by new server implementations.
- `prompt_id` is legacy and should remain present or tolerated while older clients exist.
- The job list `status` field may be omitted; Android defaults missing summary status to `done`.
- Custom prompt jobs must preserve `prompt_text` and `workflow` in job detail/list responses so regenerate and gallery filtering work.

