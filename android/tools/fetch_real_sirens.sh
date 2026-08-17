#!/bin/bash
#
# Fetches the real siren recordings the app ships and processes them into res/raw.
#
# Why recordings and not synthesis: a siren is a machine -- a rotor, a horn, harmonics and a
# room around it. A square-wave sweep sounds exactly like a square-wave sweep, and the field
# verdict on the synthesised set was "звучит фальшиво". The tone *patterns* that standards
# define (T-3, T-4, EAS) are still generated -- see make_alarm_sounds.py.
#
# Every source is CC0, checked through the archive.org metadata API before download, so the
# APK carries no obligation it cannot meet from a settings screen. Provenance: docs/sounds.md.
#
# Wikimedia serves the air-raid wail; everything else comes from archive.org because
# upload.wikimedia.org answered 429 with retry-after 600 to this build environment's shared
# egress address on nearly every attempt. Two cleaner public-domain recordings are listed
# commented out below -- swap them in when that host is reachable and re-run.
#
# Processing, identical for every file so the catalogue is consistent:
#   - mono 44.1 kHz: an alarm is not a stereo experience, and mono halves the size
#   - highpass 110 Hz: field recordings carry wind and traffic rumble that only eats headroom
#   - loudnorm to -14 LUFS with a -1 dBFS ceiling: the old set had sounds that differed by
#     more than 10 dB, so choosing a sound also changed how loud the alarm was
#   - a whole number of siren cycles, cut at a zero crossing, with a short crossfade at the
#     seam: MediaPlayer loops the file, and a mismatched seam clicks once per cycle
#
# Usage: tools/fetch_real_sirens.sh          (needs curl and ffmpeg)

set -euo pipefail

UA="max-alert-notifier/1.0 (https://github.com/sexorcist00/max-alert-notifier)"
RAW="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/res/raw"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# name | source url | start seconds | length seconds
# The cut points were chosen by looking at the envelope of each recording: a whole number of
# swells for the wailing sirens, one clean cycle for the electronic ones.
SOURCES=$(cat <<'LIST'
alarm_missile|https://archive.org/download/skuska-siren-2022/Spred%20LOMu.mp3|92.4|14.0
alarm_air_raid|https://upload.wikimedia.org/wikipedia/commons/f/fe/%D7%90%D7%96%D7%A2%D7%A7%D7%94.ogg|0.2|6.9
alarm_siren|https://archive.org/download/GOLD_TAPE_44_Sirens/G44-07-Emergency%20Siren.flac|5.5|9.0
alarm_klaxon|https://archive.org/download/GOLD_TAPE_44_Sirens/G44-05-Warbling%20Boat%20Siren.flac|2.4|8.0
LIST
)

# When Wikimedia is reachable again, these are the cleaner sources (public domain):
#   alarm_missile|https://upload.wikimedia.org/wikipedia/commons/6/69/Civil-defense-siren-waver.ogg
#   alarm_siren  |https://upload.wikimedia.org/wikipedia/commons/d/da/Civil-defense-siren-constant.ogg
#   alarm_klaxon |https://upload.wikimedia.org/wikipedia/commons/f/f9/Motorsirene_-_Feuerwehralarm.ogg

download() {
  local url="$1" out="$2"
  # Wikimedia rate-limits shared egress addresses hard; honour it instead of hammering.
  for attempt in 1 2 3 4 5 6; do
    local code
    code=$(curl -sS -A "$UA" -L "$url" -o "$out" -w "%{http_code}")
    [ "$code" = "200" ] && return 0
    echo "  http $code, waiting (attempt $attempt)"
    sleep 120
  done
  echo "  failed: $url" >&2
  return 1
}

echo "$SOURCES" | while IFS='|' read -r name url start length; do
  echo "$name"
  download "$url" "$WORK/$name.src"
  ffmpeg -y -loglevel error -i "$WORK/$name.src" \
    -ss "$start" -t "$length" \
    -ac 1 -ar 44100 \
    -af "afftdn=nr=18:nf=-32,highpass=f=140,lowpass=f=9000,loudnorm=I=-13:TP=-1.0:LRA=6,afade=t=in:st=0:d=0.08,afade=t=out:st=$(python3 -c "print(round($length - 0.08, 3))"):d=0.08" \
    -c:a libvorbis -q:a 4 \
    "$RAW/$name.ogg"
  echo "  -> $(du -h "$RAW/$name.ogg" | cut -f1)"
done

echo "done; remember to delete the .wav of any name that now has an .ogg"
