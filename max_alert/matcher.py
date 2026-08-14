"""Does this message deserve an alarm?

Pure logic, no I/O and no clock of its own -- this is the part that can be tested
offline, which is why chat resolution and time both arrive as arguments.
"""

from __future__ import annotations

import time
from collections import deque
from collections.abc import Callable
from dataclasses import dataclass

from .config import WatchConfig


@dataclass(frozen=True)
class IncomingMessage:
    id: int
    chat_id: int | None
    sender: int | None
    text: str


@dataclass(frozen=True)
class MatchResult:
    matched: bool
    reason: str


def matches(
    message: IncomingMessage,
    watch: WatchConfig,
    *,
    watched_chat_ids: frozenset[int],
    own_user_id: int | None,
) -> MatchResult:
    """Decide whether `message` is the message we are waiting for.

    `watched_chat_ids` is resolved at startup from watch.chat_ids plus any chat whose
    title matched watch.chat_title_contains. Empty means "every chat".
    """
    if watched_chat_ids and message.chat_id not in watched_chat_ids:
        return MatchResult(False, "chat not watched")

    if watch.ignore_own_messages and own_user_id is not None and message.sender == own_user_id:
        return MatchResult(False, "own message")

    if watch.from_senders and message.sender not in watch.from_senders:
        return MatchResult(False, "sender not watched")

    text = message.text
    if not text:
        return MatchResult(False, "empty text")

    if watch.regex is not None:
        found = watch.regex.search(text)
        if found is None:
            return MatchResult(False, "regex did not match")
        return MatchResult(True, f"regex matched {found.group(0)!r}")

    haystack = text if watch.case_sensitive else text.lower()
    for keyword in watch.keywords:
        needle = keyword if watch.case_sensitive else keyword.lower()
        if needle in haystack:
            return MatchResult(True, f"keyword {keyword!r}")

    return MatchResult(False, "no keyword matched")


class TriggerGate:
    """Stops one incident from ringing twice.

    Two separate guards: a message id is never acted on twice (MAX can redeliver an
    update after a reconnect), and a successful trigger silences the next
    `cooldown_seconds` regardless of the message.
    """

    def __init__(
        self,
        cooldown_seconds: int,
        *,
        clock: Callable[[], float] = time.monotonic,
        remember: int = 512,
    ) -> None:
        self._cooldown = cooldown_seconds
        self._clock = clock
        self._seen: deque[int] = deque(maxlen=remember)
        self._seen_set: set[int] = set()
        self._last_fired: float | None = None

    def allow(self, message_id: int) -> bool:
        if message_id in self._seen_set:
            return False
        self._remember(message_id)

        now = self._clock()
        if self._last_fired is not None and now - self._last_fired < self._cooldown:
            return False

        self._last_fired = now
        return True

    def _remember(self, message_id: int) -> None:
        if len(self._seen) == self._seen.maxlen:
            self._seen_set.discard(self._seen[0])
        self._seen.append(message_id)
        self._seen_set.add(message_id)


def resolve_watched_chats(
    watch: WatchConfig, chats: list[tuple[int, str | None]]
) -> frozenset[int]:
    """Turn configured ids + title fragments into the set of chat ids to watch.

    `chats` is (id, title) as returned by the MAX client at startup.
    """
    resolved = set(watch.chat_ids)
    if watch.chat_title_contains:
        fragments = [fragment.lower() for fragment in watch.chat_title_contains]
        for chat_id, title in chats:
            if title and any(fragment in title.lower() for fragment in fragments):
                resolved.add(chat_id)
    return frozenset(resolved)
