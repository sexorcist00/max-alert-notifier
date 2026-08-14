"""Entry point: wires the MAX client to the matcher and the alarm, and keeps it alive."""

from __future__ import annotations

import argparse
import asyncio
import logging
import logging.handlers
import sys
from dataclasses import dataclass, field
from pathlib import Path

from pymax import Chat, Client, ExtraConfig, Message

from .alarm import Alarm, run_command, termux_available
from .config import Config, ConfigError, load_config
from .matcher import IncomingMessage, TriggerGate, matches, resolve_watched_chats

log = logging.getLogger("max_alert")

_MAX_BACKOFF_SECONDS = 300


@dataclass
class Runtime:
    """What the handlers need to know, filled in once the client is logged in."""

    watched_chat_ids: frozenset[int] = frozenset()
    chat_titles: dict[int, str] = field(default_factory=dict)
    own_user_id: int | None = None


def cli() -> int:
    args = _parse_args()
    try:
        config = load_config(args.config.expanduser().resolve())
    except ConfigError as exc:
        print(f"config error: {exc}", file=sys.stderr)
        return 2

    _setup_logging(config.path.parent / "logs" / "max-alert.log", verbose=args.verbose)

    if args.check_config:
        _print_config(config)
        return 0

    if not termux_available():
        if args.test_alarm or args.simulate is not None:
            print(
                "Termux:API not found - there is nothing to ring here. "
                "Run this on the phone, with the Termux:API app and `pkg install termux-api`.",
                file=sys.stderr,
            )
            return 3
        log.warning("Termux:API not found - the alarm will be silent. Install the Termux:API app.")

    if args.test_alarm:
        asyncio.run(_test_alarm(config))
        return 0

    if args.simulate is not None:
        return asyncio.run(_simulate(config, args.simulate))

    if args.list_chats:
        return asyncio.run(_list_chats(config))

    asyncio.run(_run_forever(config, discover=args.discover))
    return 0


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="max-alert",
        description="Watch a MAX chat and raise a loud alarm on a matching message.",
    )
    parser.add_argument("--config", type=Path, default=Path("config.toml"), help="path to config.toml")
    parser.add_argument("--check-config", action="store_true", help="validate the config and exit")
    parser.add_argument("--list-chats", action="store_true", help="print chat ids and titles, then exit")
    parser.add_argument(
        "--discover",
        action="store_true",
        help="log every incoming message (chat id, sender, text) and never ring",
    )
    parser.add_argument("--simulate", metavar="TEXT", help="run TEXT through the matcher, ring if it matches")
    parser.add_argument("--test-alarm", action="store_true", help="ring the alarm now, no MAX connection")
    parser.add_argument("-v", "--verbose", action="store_true", help="debug logging")
    return parser.parse_args()


def _setup_logging(log_file: Path, *, verbose: bool) -> None:
    log_file.parent.mkdir(parents=True, exist_ok=True)
    level = logging.DEBUG if verbose else logging.INFO
    formatter = logging.Formatter("%(asctime)s %(levelname)-7s %(name)s: %(message)s")

    file_handler = logging.handlers.RotatingFileHandler(
        log_file, maxBytes=1_000_000, backupCount=3, encoding="utf-8"
    )
    file_handler.setFormatter(formatter)

    console = logging.StreamHandler()
    console.setFormatter(formatter)

    root = logging.getLogger()
    root.setLevel(level)
    root.addHandler(file_handler)
    root.addHandler(console)
    logging.getLogger("pymax").setLevel(logging.WARNING if not verbose else logging.INFO)


def _print_config(config: Config) -> None:
    watch = config.watch
    print(f"config:      {config.path}")
    print(f"phone:       {config.max.phone}")
    print(f"session:     {config.max.work_dir / config.max.session_name}")
    print(f"chat ids:    {list(watch.chat_ids) or '(any)'}")
    print(f"chat titles: {list(watch.chat_title_contains) or '(none)'}")
    print(f"keywords:    {list(watch.keywords) or '(none)'}")
    print(f"regex:       {watch.regex.pattern if watch.regex else '(none)'}")
    print(f"senders:     {list(watch.from_senders) or '(anyone)'}")
    print(f"sound:       {config.alarm.sound}")
    print(f"loop/cool:   {config.alarm.loop_seconds}s / {config.alarm.cooldown_seconds}s")


def _build_client(config: Config) -> Client:
    config.max.work_dir.mkdir(parents=True, exist_ok=True)
    return Client(
        phone=config.max.phone,
        work_dir=str(config.max.work_dir),
        session_name=config.max.session_name,
        extra_config=ExtraConfig(reconnect=True),
    )


def _make_alarm(config: Config) -> Alarm:
    return Alarm(config.alarm, config.max.work_dir / "alarm.stop")


async def _test_alarm(config: Config) -> None:
    await _make_alarm(config).fire("MAX: тест тревоги", "Проверка звука, вибрации и кнопки СТОП")


async def _simulate(config: Config, text: str) -> int:
    message = IncomingMessage(id=-1, chat_id=None, sender=None, text=text)
    result = matches(
        message,
        config.watch,
        watched_chat_ids=frozenset(),  # chat filter cannot be checked offline
        own_user_id=None,
    )
    print(f"match: {result.matched} ({result.reason})")
    if not result.matched:
        return 1
    await _make_alarm(config).fire("MAX: тревога (симуляция)", text)
    return 0


async def _list_chats(config: Config) -> int:
    client = _build_client(config)

    @client.on_start()
    async def _on_start(started: Client) -> None:
        try:
            for chat in await started.fetch_chats():
                title = chat.title or "(без названия)"
                print(f"{chat.id}\t{chat.type}\t{title}")
        finally:
            await started.stop()

    await client.start()
    return 0


async def _run_forever(config: Config, *, discover: bool) -> None:
    await run_command("termux-wake-lock")
    backoff = 5
    try:
        while True:
            try:
                await _run_once(config, discover=discover)
                log.warning("connection closed, reconnecting in %ss", backoff)
            except asyncio.CancelledError:
                raise
            except Exception:  # noqa: BLE001 - the watcher must survive anything
                log.exception("client failed, reconnecting in %ss", backoff)
            await asyncio.sleep(backoff)
            backoff = min(backoff * 2, _MAX_BACKOFF_SECONDS)
    except (KeyboardInterrupt, asyncio.CancelledError):
        log.info("stopped")
    finally:
        await run_command("termux-wake-unlock")


async def _run_once(config: Config, *, discover: bool) -> None:
    client = _build_client(config)
    alarm = _make_alarm(config)
    gate = TriggerGate(config.alarm.cooldown_seconds)
    runtime = Runtime()

    @client.on_start()
    async def _on_start(started: Client) -> None:
        runtime.own_user_id = _own_user_id(started)
        chats = await started.fetch_chats()
        runtime.chat_titles = {chat.id: chat.title or "" for chat in chats}
        runtime.watched_chat_ids = resolve_watched_chats(
            config.watch, [(chat.id, chat.title) for chat in chats]
        )
        _log_startup(config, runtime, chats, discover=discover)

    @client.on_message()
    async def _on_message(message: Message, _started: Client) -> None:
        try:
            await _handle_message(message, config, runtime, gate, alarm, discover=discover)
        except Exception:  # noqa: BLE001 - a bad message must not kill the dispatcher
            log.exception("failed to handle message %s", message.id)

    @client.on_disconnect()
    async def _on_disconnect() -> None:
        log.warning("disconnected from MAX, the client will try to reconnect")

    await client.start()


async def _handle_message(
    message: Message,
    config: Config,
    runtime: Runtime,
    gate: TriggerGate,
    alarm: Alarm,
    *,
    discover: bool,
) -> None:
    incoming = IncomingMessage(
        id=message.id,
        chat_id=message.chat_id,
        sender=message.sender,
        text=message.text or "",
    )
    title = runtime.chat_titles.get(incoming.chat_id or 0, "")

    if discover:
        log.info(
            "chat_id=%s title=%r sender=%s text=%r",
            incoming.chat_id,
            title,
            incoming.sender,
            incoming.text,
        )
        return

    result = matches(
        incoming,
        config.watch,
        watched_chat_ids=runtime.watched_chat_ids,
        own_user_id=runtime.own_user_id,
    )
    log.debug("message %s: %s (%s)", incoming.id, result.matched, result.reason)
    if not result.matched:
        return

    if not gate.allow(incoming.id):
        log.info("match ignored (cooldown or already seen): message %s", incoming.id)
        return

    log.warning("match in chat %s (%s): %s", incoming.chat_id, result.reason, incoming.text)
    await alarm.fire(f"MAX: {title or incoming.chat_id}", incoming.text[:200])


def _own_user_id(client: Client) -> int | None:
    try:
        return client.me.contact.id
    except AttributeError:
        log.warning("cannot read own user id, ignore_own_messages will not work")
        return None


def _log_startup(config: Config, runtime: Runtime, chats: list[Chat], *, discover: bool) -> None:
    log.info("logged in as user %s, %s chats known", runtime.own_user_id, len(chats))
    if discover:
        log.info("discover mode: logging every message, the alarm stays silent")
        return
    if not runtime.watched_chat_ids:
        if config.watch.chat_title_contains:
            log.error(
                "chat_title_contains %s matched no chat - EVERY chat can raise the alarm now",
                list(config.watch.chat_title_contains),
            )
        else:
            log.warning("no chat filter configured - EVERY chat can raise the alarm")
        return
    for chat_id in sorted(runtime.watched_chat_ids):
        log.info("watching chat %s (%r)", chat_id, runtime.chat_titles.get(chat_id, "?"))
    unknown = runtime.watched_chat_ids - runtime.chat_titles.keys()
    if unknown:
        log.warning("configured chat ids not found in the chat list: %s", sorted(unknown))


if __name__ == "__main__":
    raise SystemExit(cli())
