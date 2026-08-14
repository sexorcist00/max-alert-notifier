"""The alarm itself: sound in a loop, vibration, and a notification with a STOP button.

Everything here goes through Termux:API commands. A failing command is logged and
swallowed on purpose -- a missing `termux-vibrate` must not take down the watcher.
"""

from __future__ import annotations

import asyncio
import json
import logging
import shutil
from pathlib import Path

from .config import AlarmConfig

log = logging.getLogger(__name__)

NOTIFICATION_ID = "max-alert"
_POLL_SECONDS = 1.0


def termux_available() -> bool:
    return shutil.which("termux-notification") is not None


async def run_command(*args: str, timeout: float = 15.0) -> tuple[int, str]:
    """Run a Termux:API command. Returns (exit code, stdout); (-1, "") if it could not run."""
    try:
        process = await asyncio.create_subprocess_exec(
            *args,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
    except (OSError, ValueError) as exc:
        log.warning("cannot run %s: %s", args[0], exc)
        return -1, ""

    try:
        stdout, stderr = await asyncio.wait_for(process.communicate(), timeout=timeout)
    except asyncio.TimeoutError:
        process.kill()
        log.warning("%s timed out after %.0fs", args[0], timeout)
        return -1, ""

    if process.returncode:
        log.warning(
            "%s exited with %s: %s",
            args[0],
            process.returncode,
            stderr.decode(errors="replace").strip(),
        )
    return process.returncode or 0, stdout.decode(errors="replace")


async def _read_stream_volume(stream: str) -> tuple[int, int] | None:
    """Current and maximum volume of an Android audio stream, via `termux-volume`."""
    code, out = await run_command("termux-volume")
    if code != 0 or not out.strip():
        return None
    try:
        streams = json.loads(out)
    except json.JSONDecodeError:
        log.warning("termux-volume returned non-JSON output")
        return None
    for entry in streams:
        if entry.get("stream") == stream:
            return int(entry.get("volume", 0)), int(entry.get("max_volume", 15))
    return None


class Alarm:
    """Raises and stops the alarm. One at a time -- a second trigger is ignored while ringing."""

    def __init__(self, config: AlarmConfig, stop_file: Path) -> None:
        self._config = config
        self._stop_file = stop_file
        self._lock = asyncio.Lock()

    @property
    def ringing(self) -> bool:
        return self._lock.locked()

    async def fire(self, title: str, body: str) -> None:
        if self._lock.locked():
            log.info("alarm already ringing, ignoring new trigger")
            return
        async with self._lock:
            await self._ring(title, body)

    async def _ring(self, title: str, body: str) -> None:
        self._clear_stop_file()
        previous_volume = await self._raise_volume()
        await self._post_notification(title, body)
        log.warning("ALARM: %s | %s", title, body)

        loop = asyncio.get_running_loop()
        deadline = loop.time() + self._config.loop_seconds
        next_vibration = 0.0
        try:
            while loop.time() < deadline:
                if self._stop_file.exists():
                    log.info("alarm stopped by user")
                    break
                await self._keep_playing()
                if self._config.vibrate_ms and loop.time() >= next_vibration:
                    await run_command("termux-vibrate", "-d", str(self._config.vibrate_ms), "-f")
                    next_vibration = loop.time() + self._config.vibrate_ms / 1000 + 0.5
                await asyncio.sleep(_POLL_SECONDS)
            else:
                log.info("alarm stopped after %ss", self._config.loop_seconds)
        finally:
            await self._silence(previous_volume)

    async def _keep_playing(self) -> None:
        code, out = await run_command("termux-media-player", "info")
        if code == 0 and "Playing" in out:
            return
        await run_command("termux-media-player", "play", str(self._config.sound))

    async def _raise_volume(self) -> int | None:
        current = await _read_stream_volume("music")
        if current is None:
            await run_command("termux-volume", "music", str(self._config.volume))
            return None
        previous, maximum = current
        await run_command("termux-volume", "music", str(min(self._config.volume, maximum)))
        return previous

    async def _post_notification(self, title: str, body: str) -> None:
        stop_action = f"touch {_shell_quote(str(self._stop_file))}"
        await run_command(
            "termux-notification",
            "--id",
            NOTIFICATION_ID,
            "--priority",
            "max",
            "--ongoing",
            "--title",
            title,
            "--content",
            body,
            "--action",
            stop_action,
            "--button1",
            "СТОП",
            "--button1-action",
            stop_action,
        )

    async def _silence(self, previous_volume: int | None) -> None:
        await run_command("termux-media-player", "stop")
        await run_command("termux-notification-remove", NOTIFICATION_ID)
        if previous_volume is not None:
            await run_command("termux-volume", "music", str(previous_volume))
        self._clear_stop_file()

    def _clear_stop_file(self) -> None:
        try:
            self._stop_file.unlink(missing_ok=True)
        except OSError as exc:
            log.warning("cannot remove stop file %s: %s", self._stop_file, exc)


def _shell_quote(value: str) -> str:
    return "'" + value.replace("'", "'\\''") + "'"
