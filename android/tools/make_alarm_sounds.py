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


def mix(seconds: float, frequencies: tuple[float, ...]) -> list[float]:
    total = int(SAMPLE_RATE * seconds)
    out = []
    for index in range(total):
        t = index / SAMPLE_RATE
        value = sum(math.sin(2 * math.pi * frequency * t) for frequency in frequencies)
        out.append(AMPLITUDE * value / len(frequencies))
    return out


def temporal_three() -> list[float]:
    """ISO 8201 / ANSI-ASA S3.41 T-3: the international evacuation signal.

    0.5 s on, 0.5 s off, three times, then 1.5 s of silence. 3100 Hz is the frequency
    smoke alarms use -- it cuts through sleep better than a low tone.
    """
    out: list[float] = []
    for _ in range(2):
        for _ in range(3):
            out += square(0.5, 3100) + silence(0.5)
        out += silence(1.0)  # 0.5 already counted above -> 1.5 s total gap
    return out


def temporal_four() -> list[float]:
    """ANSI-ASA S3.41 T-4: the carbon-monoxide pattern, four pulses then a long pause."""
    out: list[float] = []
    for _ in range(2):
        for _ in range(4):
            out += square(0.1, 3100) + silence(0.1)
        out += silence(4.8)
    return out


def emergency_attention() -> list[float]:
    """The EAS / Wireless Emergency Alerts attention signal: 853 Hz and 960 Hz together."""
    return mix(4.0, (853.0, 960.0))


def missile_alert() -> list[float]:
    """Ракетная опасность: the wailing civil-defence siren.

    A slow rise and fall, ~2.5 s each way. This is the "attack warning" wavering tone
    used by civil-defence sirens ("Внимание всем!"), as opposed to the steady tone that
    means attention only -- the wail is the one people are taught means take cover.
    """
    out: list[float] = []
    for _ in range(2):
        out += sweep(2.5, 400, 1000) + sweep(2.5, 1000, 400)
    return out


TONES = {
    "alarm_missile": missile_alert,
    "alarm_two_tone": two_tone,
    "alarm_siren": siren,
    "alarm_pulse": pulse,
    "alarm_klaxon": klaxon,
    "alarm_t3": temporal_three,
    "alarm_t4": temporal_four,
    "alarm_wea": emergency_attention,
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
