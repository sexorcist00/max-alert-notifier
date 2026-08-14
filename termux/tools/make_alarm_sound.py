"""Regenerates assets/alarm.wav: a two-tone siren, deliberately unpleasant.

Only needed if you want a different tone; the generated file is committed.
Usage: python tools/make_alarm_sound.py
"""

from __future__ import annotations

import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 22050
SECONDS = 3.0
TONES_HZ = (880.0, 660.0)
SWITCH_SECONDS = 0.4
AMPLITUDE = 0.6


def main() -> None:
    frames = bytearray()
    total = int(SAMPLE_RATE * SECONDS)
    for index in range(total):
        seconds = index / SAMPLE_RATE
        frequency = TONES_HZ[int(seconds / SWITCH_SECONDS) % len(TONES_HZ)]
        # Square wave: carries much further through a pocket than a sine.
        value = AMPLITUDE if math.sin(2 * math.pi * frequency * seconds) >= 0 else -AMPLITUDE
        value *= _envelope(index, total)
        frames += struct.pack("<h", int(value * 32767))

    out = Path(__file__).resolve().parent.parent / "assets" / "alarm.wav"
    out.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(out), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(SAMPLE_RATE)
        handle.writeframes(bytes(frames))
    print(f"written {out} ({out.stat().st_size} bytes)")


def _envelope(index: int, total: int) -> float:
    """Short fade in/out so the loop does not click."""
    fade = int(SAMPLE_RATE * 0.01)
    if index < fade:
        return index / fade
    if index > total - fade:
        return max(0.0, (total - index) / fade)
    return 1.0


if __name__ == "__main__":
    main()
