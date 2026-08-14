#!/data/data/com.termux/files/usr/bin/bash
# Supervisor for Termux: keeps the watcher running across crashes and holds a wake lock.
# Start it from a Termux session, or from ~/.termux/boot/ with Termux:Boot installed.

set -u
cd "$(dirname "$0")"

termux-wake-lock 2>/dev/null || true
trap 'termux-wake-unlock 2>/dev/null || true' EXIT

while true; do
  python -m max_alert "$@"
  echo "max-alert exited with $?, restarting in 5s" >&2
  sleep 5
done
