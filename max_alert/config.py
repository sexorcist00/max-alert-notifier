"""Loading and validation of config.toml.

Everything that can be wrong with the configuration has to fail here, at startup,
with a message naming the key -- not at 3 a.m. when the alarm is supposed to ring.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

try:  # Python 3.11+
    import tomllib
except ModuleNotFoundError:  # pragma: no cover - Python 3.10
    import tomli as tomllib  # type: ignore[no-redef]


class ConfigError(Exception):
    """Raised when config.toml is missing, malformed or self-contradictory."""


@dataclass(frozen=True)
class MaxConfig:
    phone: str
    work_dir: Path
    session_name: str


@dataclass(frozen=True)
class WatchConfig:
    chat_ids: tuple[int, ...]
    chat_title_contains: tuple[str, ...]
    keywords: tuple[str, ...]
    regex: re.Pattern[str] | None
    case_sensitive: bool
    from_senders: tuple[int, ...]
    ignore_own_messages: bool


@dataclass(frozen=True)
class AlarmConfig:
    sound: Path
    volume: int
    loop_seconds: int
    vibrate_ms: int
    cooldown_seconds: int


@dataclass(frozen=True)
class Config:
    path: Path
    max: MaxConfig
    watch: WatchConfig
    alarm: AlarmConfig


def load_config(path: Path) -> Config:
    if not path.is_file():
        raise ConfigError(f"config not found: {path} (copy config.example.toml to config.toml)")

    try:
        raw = tomllib.loads(path.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError as exc:
        raise ConfigError(f"{path}: invalid TOML: {exc}") from exc

    base_dir = path.parent
    return Config(
        path=path,
        max=_max_config(_section(raw, "max"), base_dir),
        watch=_watch_config(_section(raw, "watch")),
        alarm=_alarm_config(_section(raw, "alarm"), base_dir),
    )


def _section(raw: dict[str, Any], name: str) -> dict[str, Any]:
    section = raw.get(name)
    if section is None:
        raise ConfigError(f"missing [{name}] section")
    if not isinstance(section, dict):
        raise ConfigError(f"[{name}] must be a table")
    return section


def _max_config(section: dict[str, Any], base_dir: Path) -> MaxConfig:
    phone = _str(section, "max.phone", "phone", default="").strip()
    if not phone.startswith("+") or not phone[1:].isdigit():
        raise ConfigError("max.phone must be an international number, e.g. \"+79990000000\"")

    session_name = _str(section, "max.session_name", "session_name", default="main.db").strip()
    if not session_name:
        raise ConfigError("max.session_name must not be empty")

    work_dir = Path(_str(section, "max.work_dir", "work_dir", default="cache"))
    if not work_dir.is_absolute():
        work_dir = base_dir / work_dir

    return MaxConfig(phone=phone, work_dir=work_dir, session_name=session_name)


def _watch_config(section: dict[str, Any]) -> WatchConfig:
    keywords = tuple(k for k in _str_list(section, "watch.keywords", "keywords") if k)
    pattern_text = _str(section, "watch.regex", "regex", default="").strip()
    case_sensitive = _bool(section, "watch.case_sensitive", "case_sensitive", default=False)

    if not keywords and not pattern_text:
        raise ConfigError("watch needs at least one of keywords or regex")

    pattern: re.Pattern[str] | None = None
    if pattern_text:
        flags = 0 if case_sensitive else re.IGNORECASE
        try:
            pattern = re.compile(pattern_text, flags)
        except re.error as exc:
            raise ConfigError(f"watch.regex is not a valid regular expression: {exc}") from exc

    return WatchConfig(
        chat_ids=_int_list(section, "watch.chat_ids", "chat_ids"),
        chat_title_contains=tuple(
            t for t in _str_list(section, "watch.chat_title_contains", "chat_title_contains") if t
        ),
        keywords=keywords,
        regex=pattern,
        case_sensitive=case_sensitive,
        from_senders=_int_list(section, "watch.from_senders", "from_senders"),
        ignore_own_messages=_bool(
            section, "watch.ignore_own_messages", "ignore_own_messages", default=True
        ),
    )


def _alarm_config(section: dict[str, Any], base_dir: Path) -> AlarmConfig:
    sound = Path(_str(section, "alarm.sound", "sound", default="assets/alarm.wav"))
    if not sound.is_absolute():
        sound = base_dir / sound
    if not sound.is_file():
        raise ConfigError(
            f"alarm.sound does not exist: {sound}"
            " (run `python tools/make_alarm_sound.py`, or point alarm.sound at a system sound"
            " such as /system/media/audio/alarms/Argon.ogg)"
        )

    volume = _int(section, "alarm.volume", "volume", default=15)
    if not 0 <= volume <= 15:
        raise ConfigError("alarm.volume must be between 0 and 15")

    loop_seconds = _int(section, "alarm.loop_seconds", "loop_seconds", default=300)
    if loop_seconds <= 0:
        raise ConfigError("alarm.loop_seconds must be positive")

    vibrate_ms = _int(section, "alarm.vibrate_ms", "vibrate_ms", default=1200)
    if vibrate_ms < 0:
        raise ConfigError("alarm.vibrate_ms must not be negative")

    cooldown_seconds = _int(section, "alarm.cooldown_seconds", "cooldown_seconds", default=30)
    if cooldown_seconds < 0:
        raise ConfigError("alarm.cooldown_seconds must not be negative")

    return AlarmConfig(
        sound=sound,
        volume=volume,
        loop_seconds=loop_seconds,
        vibrate_ms=vibrate_ms,
        cooldown_seconds=cooldown_seconds,
    )


def _str(section: dict[str, Any], key: str, name: str, *, default: str) -> str:
    value = section.get(name, default)
    if not isinstance(value, str):
        raise ConfigError(f"{key} must be a string")
    return value


def _bool(section: dict[str, Any], key: str, name: str, *, default: bool) -> bool:
    value = section.get(name, default)
    if not isinstance(value, bool):
        raise ConfigError(f"{key} must be true or false")
    return value


def _int(section: dict[str, Any], key: str, name: str, *, default: int) -> int:
    value = section.get(name, default)
    if isinstance(value, bool) or not isinstance(value, int):
        raise ConfigError(f"{key} must be an integer")
    return value


def _str_list(section: dict[str, Any], key: str, name: str) -> tuple[str, ...]:
    value = section.get(name, [])
    if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
        raise ConfigError(f"{key} must be a list of strings")
    return tuple(value)


def _int_list(section: dict[str, Any], key: str, name: str) -> tuple[int, ...]:
    value = section.get(name, [])
    if not isinstance(value, list) or not all(
        isinstance(item, int) and not isinstance(item, bool) for item in value
    ):
        raise ConfigError(f"{key} must be a list of integers")
    return tuple(value)
