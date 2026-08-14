from __future__ import annotations

from pathlib import Path

import pytest

from max_alert.config import ConfigError, load_config

VALID = """
[max]
phone = "+79990000000"
work_dir = "cache"
session_name = "main.db"

[watch]
chat_ids = [100]
keywords = ["тревога"]

[alarm]
sound = "alarm.wav"
"""


def write_config(tmp_path: Path, body: str, *, with_sound: bool = True) -> Path:
    if with_sound:
        (tmp_path / "alarm.wav").write_bytes(b"RIFF")
    path = tmp_path / "config.toml"
    path.write_text(body, encoding="utf-8")
    return path


class TestLoadConfig:
    class TestNegativeCases:
        def test_rejects_a_missing_file(self, tmp_path: Path) -> None:
            with pytest.raises(ConfigError, match="config not found"):
                load_config(tmp_path / "config.toml")

        def test_rejects_invalid_toml(self, tmp_path: Path) -> None:
            with pytest.raises(ConfigError, match="invalid TOML"):
                load_config(write_config(tmp_path, "[max"))

        def test_rejects_a_missing_section(self, tmp_path: Path) -> None:
            with pytest.raises(ConfigError, match=r"missing \[watch\]"):
                load_config(write_config(tmp_path, '[max]\nphone = "+79990000000"\n'))

        def test_rejects_a_phone_without_country_code(self, tmp_path: Path) -> None:
            body = VALID.replace('"+79990000000"', '"89990000000"')
            with pytest.raises(ConfigError, match="max.phone"):
                load_config(write_config(tmp_path, body))

        def test_rejects_a_watch_section_without_keywords_or_regex(self, tmp_path: Path) -> None:
            body = VALID.replace('keywords = ["тревога"]', "keywords = []")
            with pytest.raises(ConfigError, match="keywords or regex"):
                load_config(write_config(tmp_path, body))

        def test_rejects_a_broken_regex(self, tmp_path: Path) -> None:
            body = VALID.replace('keywords = ["тревога"]', 'regex = "код ("')
            with pytest.raises(ConfigError, match="not a valid regular expression"):
                load_config(write_config(tmp_path, body))

        def test_rejects_a_sound_file_that_does_not_exist(self, tmp_path: Path) -> None:
            with pytest.raises(ConfigError, match="alarm.sound does not exist"):
                load_config(write_config(tmp_path, VALID, with_sound=False))

        def test_rejects_a_volume_outside_the_range(self, tmp_path: Path) -> None:
            body = VALID + "volume = 42\n"
            with pytest.raises(ConfigError, match="alarm.volume"):
                load_config(write_config(tmp_path, body))

        def test_rejects_a_chat_id_that_is_not_a_number(self, tmp_path: Path) -> None:
            body = VALID.replace("chat_ids = [100]", 'chat_ids = ["100"]')
            with pytest.raises(ConfigError, match="watch.chat_ids"):
                load_config(write_config(tmp_path, body))

    class TestPositiveCases:
        def test_reads_a_minimal_config(self, tmp_path: Path) -> None:
            config = load_config(write_config(tmp_path, VALID))
            assert config.max.phone == "+79990000000"
            assert config.watch.chat_ids == (100,)
            assert config.watch.keywords == ("тревога",)

        def test_resolves_relative_paths_against_the_config_file(self, tmp_path: Path) -> None:
            config = load_config(write_config(tmp_path, VALID))
            assert config.max.work_dir == tmp_path / "cache"
            assert config.alarm.sound == tmp_path / "alarm.wav"

        def test_compiles_the_regex_case_insensitively_by_default(self, tmp_path: Path) -> None:
            body = VALID.replace('keywords = ["тревога"]', 'regex = "код \\\\d+"')
            config = load_config(write_config(tmp_path, body))
            assert config.watch.regex is not None
            assert config.watch.regex.search("КОД 17") is not None

        def test_applies_defaults_for_omitted_alarm_keys(self, tmp_path: Path) -> None:
            config = load_config(write_config(tmp_path, VALID))
            assert config.alarm.volume == 15
            assert config.alarm.loop_seconds == 300
            assert config.alarm.cooldown_seconds == 30

        def test_example_config_is_valid(self, tmp_path: Path) -> None:
            root = Path(__file__).resolve().parent.parent
            body = (root / "config.example.toml").read_text(encoding="utf-8")
            path = tmp_path / "config.toml"
            path.write_text(body.replace('"assets/alarm.wav"', f'"{root / "assets" / "alarm.wav"}"'))
            config = load_config(path)
            assert config.watch.keywords == ("тревога",)
