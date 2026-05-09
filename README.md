# FluxEdit

A privacy-first Android client for your self-hosted Flux / Stable Diffusion server.

FluxEdit pairs an Android phone with a [Zun Flux server](../zun-rust-server) on your LAN or Tailscale network. Pick a source image, choose a prompt, and generate. Results sync to a local gallery that stays usable when the server is offline. API tokens are encrypted with an Android Keystore-backed AES key; the app is biometric-locked by default.

<!-- TODO: screenshots — add files under docs/img/ and reference them here -->

## Features

- **First-time setup with LAN discovery.** Type your server's IP or hostname; the app probes common ports (`5000`, `5001`, `7860`, `8000`, `8188`) over both `http` and `https` and picks the one that responds.
- **LAN ↔ Tailscale failover.** Configure a primary and an optional fallback URL. The app picks whichever route is currently reachable, or you can pin it manually.
- **Offline gallery.** Recently viewed thumbnails, previews, and result images are cached on disk. The gallery still loads when the server is down; uncached items get an "unavailable offline" badge.
- **Biometric lock.** Configurable lockout (always, 30s, 1m, 5m, 10m, 30m). Tokens are stored in the app's Keystore-backed secure store.
- **Optional certificate pinning.** Capture and pin server certs from Settings → Connection. Re-pin after renewal.
- **Batch generation.** Submit multiple source images with the same prompt; per-job progress is tracked.
- **Before/after viewer.** Compare source and result side-by-side with a zoomable image view.
- **Soft delete with 30-day undo.** Deletions queue locally and sync to the server in the background.

## Requirements

- Android 16 (API 36) or newer. Built and tested only on Samsung Galaxy Z Fold 7 (arm64-v8a).
- A running [Zun Flux server](../zun-rust-server) reachable over LAN or Tailscale.
- An API token issued by that server.

## Install

Sideload the latest signed APK from the [project Releases page](https://github.com/grand-omega/zun-android-app/releases).

## First run

1. Launch FluxEdit. The Setup screen opens automatically until a server is configured.
2. Either type your server's IP/hostname and tap **Search**, or tap **Enter URL manually** to paste a full URL. Optionally add a fallback URL (e.g. your Tailscale hostname).
3. Paste your API token and tap **Connect**. The app verifies the connection before saving.

You can change any of these later from **Settings → Connection**.

## Privacy & security

- The APK ships with no server URL or token baked in. URLs and routing preferences are stored in plain app-private preferences; API tokens are encrypted with an Android Keystore-backed AES key.
- Plain HTTP is permitted for user-picked self-hosted LAN servers. Use HTTPS and optional certificate pinning for stricter deployments.
- No analytics, no crash reporting, no third-party trackers.
- Biometric/device unlock is required after the configured lockout window.
- Backups (`allowBackup`) and Auto Backup are disabled so secrets don't leave the device.

## Building from source

See [docs/build.md](docs/build.md) for the full toolchain and commands. Short version:

```bash
cp local.properties.example local.properties   # set sdk.dir
./gradlew assembleDebug
./gradlew installDebug
```

## Project layout

Single-module Android app, Kotlin + Jetpack Compose, no DI framework. Top-level packages under `dev.zun.flux`:

- `data/` — API client, Room database, repositories, networking, WorkManager workers, diagnostics
- `ui/` — Compose screens grouped by feature (`home`, `gallery`, `progress`, `result`, `settings`, `auth`, `capture`, `nav`, `theme`, `common`)
- `util/` — small helpers (image decoding, error mapping, URL normalization, MediaStore saving)

For details and data-flow walkthroughs, see [docs/architecture.md](docs/architecture.md).

## Server

This client expects the Zun Flux Rust server. The wire contract — endpoints, payloads, error codes — lives in the server repo:

- Repo: `../zun-rust-server`
- Contract: [`../zun-rust-server/API_CONTRACT.md`](../zun-rust-server/API_CONTRACT.md)

## Documentation

- [docs/architecture.md](docs/architecture.md) — package map, data flow, wiring
- [docs/build.md](docs/build.md) — toolchain, build commands, signing, versioning

Working principles for contributors live in [`CLAUDE.md`](CLAUDE.md).

## License

[MIT](LICENSE) © Yanwen Xu
