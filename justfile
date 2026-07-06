# Local dev-environment orchestration for zun-android-app debug builds.
# Run `just` to list recipes.
#
# Debug builds default to a local server address (per
# specs/003-debug-server-isolation) instead of production. This justfile
# brings that local backend up: zun-rust-server (job orchestration/API,
# always required) and optionally zun-flux-pipeline/ComfyUI (GPU inference,
# only needed to actually generate images — auth, prompts, and edit-history
# testing all work fine without it).
#
# Quick start:
#   just dev-up          # zun-rust-server + adb reverse (fast, no GPU)
#   just dev-up-full      # ^ plus the ComfyUI pipeline (slower, needed to generate)
#   just dev-status
#   just dev-down
#
# Sibling-repo paths default to ../zun-rust-server and ../zun-flux-pipeline.
# If your checkout layout differs, `cp dev.toml.example dev.toml` and edit
# it — dev.toml (gitignored, machine-specific) always wins over the default.

run_dir := ".dev"

default:
    @just --list

# Bring up zun-rust-server and wire adb reverse. Idempotent — safe to
# re-run if it's already up.
dev-up: _up-server dev-reverse
    @echo "Ready. Use http://127.0.0.1:8080 in Setup/Settings (via adb reverse)."

# Same as dev-up, plus the ComfyUI pipeline — needed only when you actually
# want a submitted job to finish, not just to reach Setup/test lineage
# detection. Loads model weights: expect this to take a while on first run.
dev-up-full: _up-server _up-pipeline dev-reverse
    @echo "Ready. Use http://127.0.0.1:8080 in Setup/Settings (via adb reverse)."

# Reads `key = "value"` out of dev.toml if it exists, else prints [default].
# Explicit, no env vars: `cp dev.toml.example dev.toml` to override.
_toml_get key default:
    #!/usr/bin/env bash
    set -euo pipefail
    if [[ -f dev.toml ]]; then
        value=$(grep -E "^{{key}}\s*=" dev.toml | sed -E 's/^[^=]*=\s*"(.*)"\s*$/\1/' || true)
        if [[ -n "$value" ]]; then
            echo "$value"
            exit 0
        fi
    fi
    echo "{{default}}"

_up-server:
    #!/usr/bin/env bash
    set -euo pipefail
    rust_server_dir=$(just _toml_get rust_server_dir ../zun-rust-server)
    mkdir -p {{run_dir}}
    if curl -sf -m 2 http://127.0.0.1:8080/api/v1/health >/dev/null 2>&1; then
        echo "zun-rust-server: already running"
        exit 0
    fi
    if [[ ! -f "$rust_server_dir/config.toml" ]]; then
        echo "zun-rust-server: no config.toml found in $rust_server_dir" >&2
        echo "  run: just -f \"$rust_server_dir/justfile\" setup" >&2
        exit 1
    fi
    echo "zun-rust-server: starting..."
    (cd "$rust_server_dir" && nohup just run) > {{run_dir}}/zun-rust-server.log 2>&1 &
    echo $! > {{run_dir}}/zun-rust-server.pid
    for _ in $(seq 1 30); do
        if curl -sf -m 1 http://127.0.0.1:8080/api/v1/health >/dev/null 2>&1; then
            echo "zun-rust-server: up"
            exit 0
        fi
        sleep 1
    done
    echo "zun-rust-server: didn't become healthy in time — check {{run_dir}}/zun-rust-server.log" >&2
    exit 1

_up-pipeline:
    #!/usr/bin/env bash
    set -euo pipefail
    flux_pipeline_dir=$(just _toml_get flux_pipeline_dir ../zun-flux-pipeline)
    mkdir -p {{run_dir}}
    if curl -sf -m 2 http://127.0.0.1:8188/ >/dev/null 2>&1; then
        echo "zun-flux-pipeline (ComfyUI): already running"
        exit 0
    fi
    if [[ ! -f "$flux_pipeline_dir/ComfyUI/main.py" ]]; then
        echo "zun-flux-pipeline: not bootstrapped in $flux_pipeline_dir" >&2
        echo "  run: just -f \"$flux_pipeline_dir/justfile\" bootstrap && just -f \"$flux_pipeline_dir/justfile\" fetch-models" >&2
        exit 1
    fi
    echo "zun-flux-pipeline: starting (loading model weights, this can take a minute)..."
    (cd "$flux_pipeline_dir" && nohup just serve) > {{run_dir}}/zun-flux-pipeline.log 2>&1 &
    echo $! > {{run_dir}}/zun-flux-pipeline.pid
    for _ in $(seq 1 120); do
        if curl -sf -m 1 http://127.0.0.1:8188/ >/dev/null 2>&1; then
            echo "zun-flux-pipeline: up"
            exit 0
        fi
        sleep 1
    done
    echo "zun-flux-pipeline: didn't become healthy in time — check {{run_dir}}/zun-flux-pipeline.log" >&2
    exit 1

# Forward each connected physical device's 127.0.0.1:8080 to this machine's
# 127.0.0.1:8080 over USB, so the debug build's local default just works —
# no LAN IP/firewall fuss. Skips emulators (10.0.2.2 already reaches the
# host without adb reverse).
dev-reverse:
    #!/usr/bin/env bash
    set -euo pipefail
    if ! command -v adb >/dev/null 2>&1; then
        echo "adb not found on PATH — skipping (LAN IP still works instead)" >&2
        exit 0
    fi
    devices=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
    if [[ -z "$devices" ]]; then
        echo "no connected devices — skipping adb reverse"
        exit 0
    fi
    for d in $devices; do
        case "$d" in
            emulator-*) continue ;;
        esac
        adb -s "$d" reverse tcp:8080 tcp:8080
        echo "adb reverse tcp:8080 set up for $d"
    done

# Stop both backends. Matches by the actual binary/script, not the
# `just run`/`just serve` wrapper PID — `cargo run` and `uv run` fork a
# chain of children, so killing only the top wrapper leaves the real
# process running underneath (reparented, still bound to the port).
dev-down:
    #!/usr/bin/env bash
    set -euo pipefail
    if pkill -f "target/release/zun-rust-server" 2>/dev/null; then
        echo "zun-rust-server: stopped"
    else
        echo "zun-rust-server: not running"
    fi
    if pkill -f "ComfyUI/main.py" 2>/dev/null; then
        echo "zun-flux-pipeline: stopped"
    else
        echo "zun-flux-pipeline: not running"
    fi
    rm -f {{run_dir}}/zun-rust-server.pid {{run_dir}}/zun-flux-pipeline.pid

# Health of both backends plus current adb reverse mappings.
dev-status:
    #!/usr/bin/env bash
    set -euo pipefail
    if curl -sf -m 2 http://127.0.0.1:8080/api/v1/health >/dev/null 2>&1; then
        echo "zun-rust-server:   up   (http://127.0.0.1:8080)"
    else
        echo "zun-rust-server:   down"
    fi
    if curl -sf -m 2 http://127.0.0.1:8188/ >/dev/null 2>&1; then
        echo "zun-flux-pipeline: up   (http://127.0.0.1:8188)"
    else
        echo "zun-flux-pipeline: down (only needed to actually generate images)"
    fi
    if command -v adb >/dev/null 2>&1; then
        echo "adb reverse:"
        for d in $(adb devices | awk 'NR>1 && $2=="device" {print $1}'); do
            case "$d" in emulator-*) continue ;; esac
            echo "  $d: $(adb -s "$d" reverse --list 2>/dev/null | grep 8080 || echo 'not set up')"
        done
    fi

# Tail a running backend's log. NAME is "server" or "pipeline".
dev-logs NAME:
    tail -f {{run_dir}}/zun-{{ if NAME == "server" { "rust-server" } else { "flux-pipeline" } }}.log
