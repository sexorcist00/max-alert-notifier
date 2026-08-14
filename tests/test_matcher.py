from __future__ import annotations

import re

from max_alert.config import WatchConfig
from max_alert.matcher import (
    IncomingMessage,
    TriggerGate,
    matches,
    resolve_watched_chats,
)

WATCHED_CHAT = 100
OTHER_CHAT = 200
ME = 1
SOMEONE = 2


def watch(**overrides: object) -> WatchConfig:
    defaults: dict[str, object] = {
        "chat_ids": (WATCHED_CHAT,),
        "chat_title_contains": (),
        "keywords": ("тревога",),
        "regex": None,
        "case_sensitive": False,
        "from_senders": (),
        "ignore_own_messages": True,
    }
    defaults.update(overrides)
    return WatchConfig(**defaults)  # type: ignore[arg-type]


def message(text: str, *, chat_id: int = WATCHED_CHAT, sender: int = SOMEONE, id: int = 1) -> IncomingMessage:
    return IncomingMessage(id=id, chat_id=chat_id, sender=sender, text=text)


def check(msg: IncomingMessage, rules: WatchConfig, *, own: int | None = ME) -> bool:
    return matches(
        msg,
        rules,
        watched_chat_ids=frozenset(rules.chat_ids),
        own_user_id=own,
    ).matched


class TestMatches:
    class TestNegativeCases:
        def test_ignores_message_from_another_chat(self) -> None:
            assert check(message("тревога", chat_id=OTHER_CHAT), watch()) is False

        def test_ignores_own_message(self) -> None:
            assert check(message("тревога", sender=ME), watch()) is False

        def test_ignores_sender_outside_the_allowed_list(self) -> None:
            assert check(message("тревога", sender=SOMEONE), watch(from_senders=(42,))) is False

        def test_ignores_empty_text(self) -> None:
            assert check(message(""), watch()) is False

        def test_ignores_text_without_the_keyword(self) -> None:
            assert check(message("всё спокойно"), watch()) is False

        def test_respects_case_when_case_sensitive_is_on(self) -> None:
            assert check(message("ТРЕВОГА"), watch(case_sensitive=True)) is False

        def test_ignores_text_that_the_regex_does_not_match(self) -> None:
            rules = watch(regex=re.compile(r"код\s+\d+", re.IGNORECASE))
            assert check(message("код неизвестен"), rules) is False

        def test_regex_wins_over_keywords(self) -> None:
            rules = watch(keywords=("тревога",), regex=re.compile(r"^выезд", re.IGNORECASE))
            assert check(message("тревога в третьем секторе"), rules) is False

    class TestPositiveCases:
        def test_matches_keyword_regardless_of_case(self) -> None:
            assert check(message("ТРЕВОГА в третьем секторе"), watch()) is True

        def test_matches_keyword_as_substring(self) -> None:
            assert check(message("объявлена тревога!"), watch()) is True

        def test_matches_exact_case_when_case_sensitive_is_on(self) -> None:
            assert check(message("тревога"), watch(case_sensitive=True)) is True

        def test_matches_any_of_several_keywords(self) -> None:
            assert check(message("общий выезд"), watch(keywords=("тревога", "выезд"))) is True

        def test_matches_regex(self) -> None:
            rules = watch(regex=re.compile(r"код\s+\d+", re.IGNORECASE))
            assert check(message("Код 17, всем постам"), rules) is True

        def test_matches_in_any_chat_when_no_chat_is_configured(self) -> None:
            rules = watch(chat_ids=())
            assert check(message("тревога", chat_id=OTHER_CHAT), rules) is True

        def test_matches_own_message_when_the_filter_is_off(self) -> None:
            rules = watch(ignore_own_messages=False)
            assert check(message("тревога", sender=ME), rules) is True

        def test_matches_allowed_sender(self) -> None:
            rules = watch(from_senders=(SOMEONE,))
            assert check(message("тревога", sender=SOMEONE), rules) is True


class TestTriggerGate:
    class TestNegativeCases:
        def test_blocks_the_same_message_twice(self) -> None:
            gate = TriggerGate(0, clock=lambda: 0.0)
            assert gate.allow(1) is True
            assert gate.allow(1) is False

        def test_blocks_a_new_message_inside_the_cooldown(self) -> None:
            now = 0.0
            gate = TriggerGate(30, clock=lambda: now)
            assert gate.allow(1) is True
            now = 29.0
            assert gate.allow(2) is False

    class TestPositiveCases:
        def test_allows_the_first_message(self) -> None:
            assert TriggerGate(30, clock=lambda: 0.0).allow(1) is True

        def test_allows_a_new_message_after_the_cooldown(self) -> None:
            now = 0.0
            gate = TriggerGate(30, clock=lambda: now)
            assert gate.allow(1) is True
            now = 30.0
            assert gate.allow(2) is True

        def test_forgets_old_ids_beyond_the_memory_limit(self) -> None:
            gate = TriggerGate(0, clock=lambda: 0.0, remember=2)
            assert gate.allow(1) is True
            assert gate.allow(2) is True
            assert gate.allow(3) is True
            assert gate.allow(1) is True


class TestResolveWatchedChats:
    class TestNegativeCases:
        def test_keeps_only_explicit_ids_when_no_title_matches(self) -> None:
            rules = watch(chat_ids=(7,), chat_title_contains=("диспетчер",))
            assert resolve_watched_chats(rules, [(1, "Курилка"), (2, None)]) == frozenset({7})

        def test_returns_nothing_when_nothing_is_configured(self) -> None:
            rules = watch(chat_ids=(), chat_title_contains=())
            assert resolve_watched_chats(rules, [(1, "Диспетчерская")]) == frozenset()

    class TestPositiveCases:
        def test_resolves_a_title_fragment_ignoring_case(self) -> None:
            rules = watch(chat_ids=(), chat_title_contains=("ДИСПЕТЧЕР",))
            chats = [(1, "Диспетчерская смена"), (2, "Курилка")]
            assert resolve_watched_chats(rules, chats) == frozenset({1})

        def test_merges_ids_and_title_matches(self) -> None:
            rules = watch(chat_ids=(9,), chat_title_contains=("курилка",))
            chats = [(1, "Диспетчерская"), (2, "Курилка")]
            assert resolve_watched_chats(rules, chats) == frozenset({9, 2})
