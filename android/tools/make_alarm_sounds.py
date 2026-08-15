"""Generates the bundled alarm tones into app/src/main/res/raw.

Deliberately synthetic: no licensing questions, a few hundred KB total, and each tone
is unpleasant in a different way so one of them cuts through wherever the phone is.

Usage: python tools/make_alarm_sounds.py
"""

from __future__ import annotations

import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 22050
AMPLITUDE = 0.62


def square(seconds: float, frequency: float) -> list[float]:
    return [
        AMPLITUDE if math.sin(2 * math.pi * frequency * (index / SAMPLE_RATE)) >= 0 else -AMPLITUDE
        for index in range(int(SAMPLE_RATE * seconds))
    ]


def silence(seconds: float) -> list[float]:
    return [0.0] * int(SAMPLE_RATE * seconds)


def sweep(seconds: float, start_hz: float, end_hz: float) -> list[float]:
    total = int(SAMPLE_RATE * seconds)
    samples = []
    phase = 0.0
    for index in range(total):
        frequency = start_hz + (end_hz - start_hz) * (index / total)
        phase += 2 * math.pi * frequency / SAMPLE_RATE
        samples.append(AMPLITUDE if math.sin(phase) >= 0 else -AMPLITUDE)
    return samples


def two_tone() -> list[float]:
    """Classic alternating alarm: 880/660 Hz."""
    out: list[float] = []
    for _ in range(4):
        out += square(0.4, 880) + square(0.4, 660)
    return out


def siren() -> list[float]:
    """Rising and falling sweep, like a car alarm."""
    out: list[float] = []
    for _ in range(3):
        out += sweep(0.5, 500, 1400) + sweep(0.5, 1400, 500)
    return out


def pulse() -> list[float]:
    """Short high beeps with gaps -- the most piercing of the four."""
    out: list[float] = []
    for _ in range(10):
        out += square(0.12, 2200) + silence(0.18)
    return out


def klaxon() -> list[float]:
    """Low, slow, ship-horn style; carries through a pocket or a blanket."""
    out: list[float] = []
    for _ in range(4):
        out += square(0.6, 220) + silence(0.25)
    return out


TONES = {
    "alarm_two_tone": two_tone,
    "alarm_siren": siren,
    "alarm_pulse": pulse,
    "alarm_klaxon": klaxon,
}


def envelope(samples: list[float]) -> list[float]:
    fade = int(SAMPLE_RATE * 0.008)
    total = len(samples)
    for index in range(fade):
        samples[index] *= index / fade
        samples[total - 1 - index] *= index / fade
    return samples


def main() -> None:
    out_dir = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw"
    out_dir.mkdir(parents=True, exist_ok=True)
    for name, build in TONES.items():
        samples = envelope(build())
        frames = b"".join(struct.pack("<h", int(max(-1.0, min(1.0, value)) * 32767)) for value in samples)
        path = out_dir / f"{name}.wav"
        with wave.open(str(path), "wb") as handle:
            handle.setnchannels(1)
            handle.setsampwidth(2)
            handle.setframerate(SAMPLE_RATE)
            handle.writeframes(frames)
        print(f"{path.name}: {path.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
