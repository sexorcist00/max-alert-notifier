"""Generates the bundled *pattern* signals into app/src/main/res/raw.

Only the signals that standards define as tone patterns are synthesised here -- T-3, T-4,
the EAS attention tone and two plain beep patterns. For those, synthesis is not a shortcut:
the standard specifies the cadence and the frequency, so a recording of somebody's smoke
alarm would be less faithful, not more.

The sirens are the opposite case and are NOT generated: a siren is a machine with a rotor,
harmonics and a room around it, and a square wave sweep sounds exactly like what it is.
Those come from real recordings -- see tools/fetch_real_sirens.sh and docs/sounds.md.

What changed after the field verdict "звучат фальшиво":
  - 44.1 kHz instead of 22.05 kHz. A 3100 Hz square wave at 22 kHz aliases audibly, and the
    aliasing is most of what made the old tones sound cheap.
  - horn timbre instead of a bare square wave: a fundamental with odd harmonics rolling off,
    which is what a piezo/horn sounder actually radiates.
  - an attack and release on every pulse, plus a slight tremolo, so pulses start and stop
    like a mechanical device rather than switching on in one sample.

Usage: python tools/make_alarm_sounds.py     (needs ffmpeg for the ogg encode)
"""

from __future__ import annotations

import math
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

SAMPLE_RATE = 44100
AMPLITUDE = 0.72

# Odd harmonics with a rolloff: the sound of a horn driver, not of a bit flipping.
HARMONICS = ((1.0, 1.0), (3.0, 0.34), (5.0, 0.16), (7.0, 0.07), (9.0, 0.03))


def horn(seconds: float, frequency: float, tremolo_hz: float = 5.5) -> list[float]:
    """A sounder-like tone: odd harmonics, soft attack and release, slight tremolo."""
    total = int(SAMPLE_RATE * seconds)
    attack = min(int(SAMPLE_RATE * 0.012), total // 4)
    release = min(int(SAMPLE_RATE * 0.020), total // 4)
    out: list[float] = []
    norm = sum(weight for _, weight in HARMONICS)
    for index in range(total):
        t = index / SAMPLE_RATE
        value = sum(
            weight * math.sin(2 * math.pi * frequency * multiple * t)
            for multiple, weight in HARMONICS
        ) / norm
        # A real driver is never perfectly steady.
        value *= 1.0 - 0.08 * (1.0 - math.cos(2 * math.pi * tremolo_hz * t)) / 2.0
        if index < attack:
            value *= index / attack
        elif index > total - release:
            value *= (total - index) / release
        out.append(AMPLITUDE * value)
    return out


def silence(seconds: float) -> list[float]:
    return [0.0] * int(SAMPLE_RATE * seconds)


def mix(seconds: float, frequencies: tuple[float, ...]) -> list[float]:
    """Pure sines added together -- this is literally how the EAS tone is specified."""
    total = int(SAMPLE_RATE * seconds)
    attack = int(SAMPLE_RATE * 0.015)
    out: list[float] = []
    for index in range(total):
        t = index / SAMPLE_RATE
        value = sum(math.sin(2 * math.pi * frequency * t) for frequency in frequencies)
        value = AMPLITUDE * value / len(frequencies)
        if index < attack:
            value *= index / attack
        elif index > total - attack:
            value *= (total - index) / attack
        out.append(value)
    return out


def two_tone() -> list[float]:
    """Classic alternating alarm: 880/660 Hz."""
    out: list[float] = []
    for _ in range(4):
        out += horn(0.4, 880) + horn(0.4, 660)
    return out


def pulse() -> list[float]:
    """Short high beeps with gaps -- the most piercing pattern of the set."""
    out: list[float] = []
    for _ in range(6):
        out += horn(0.12, 2400) + silence(0.12)
    out += silence(0.6)
    return out


def temporal_three() -> list[float]:
    """ISO 8201 / ANSI-ASA S3.41 T-3: the international evacuation signal.

    0.5 s on, 0.5 s off, three times, then 1.5 s of silence. 3100 Hz is the frequency
    smoke alarms use -- it cuts through sleep better than a low tone.
    """
    out: list[float] = []
    for _ in range(2):
        for _ in range(3):
            out += horn(0.5, 3100) + silence(0.5)
        out += silence(1.0)  # 0.5 already counted above -> 1.5 s total gap
    return out


def temporal_four() -> list[float]:
    """ANSI-ASA S3.41 T-4: the carbon-monoxide pattern, four pulses then a long pause."""
    out: list[float] = []
    for _ in range(2):
        for _ in range(4):
            out += horn(0.1, 3100) + silence(0.1)
        out += silence(4.8)
    return out


def emergency_attention() -> list[float]:
    """The EAS / Wireless Emergency Alerts attention signal: 853 Hz and 960 Hz together."""
    return mix(4.0, (853.0, 960.0))


TONES = {
    "alarm_two_tone": two_tone,
    "alarm_pulse": pulse,
    "alarm_t3": temporal_three,
    "alarm_t4": temporal_four,
    "alarm_wea": emergency_attention,
}


def encode(samples: list[float], destination: Path) -> None:
    frames = b"".join(
        struct.pack("<h", int(max(-1.0, min(1.0, value)) * 32767)) for value in samples
    )
    with tempfile.NamedTemporaryFile(suffix=".wav") as scratch:
        with wave.open(scratch.name, "wb") as handle:
            handle.setnchannels(1)
            handle.setsampwidth(2)
            handle.setframerate(SAMPLE_RATE)
            handle.writeframes(frames)
        subprocess.run(
            [
                "ffmpeg", "-y", "-loglevel", "error",
                "-i", scratch.name,
                "-ac", "1",
                "-c:a", "libvorbis", "-q:a", "3",
                str(destination),
            ],
            check=True,
        )


def main() -> None:
    out_dir = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw"
    out_dir.mkdir(parents=True, exist_ok=True)
    for name, build in TONES.items():
        path = out_dir / f"{name}.ogg"
        encode(build(), path)
        print(f"{path.name}: {path.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
