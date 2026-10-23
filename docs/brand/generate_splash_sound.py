"""Synthesizes the short download sound played with the splash animation (res/raw/splash_download.wav).

Pure Python, no dependencies. Run: python generate_splash_sound.py <output.wav>
The sound is original, so it carries no licence. Timing follows the splash: the badge drops for about 0.65 s and
lands with a soft pop, then a tiny two-note chime confirms the download.
"""
import math
import struct
import sys
import wave

RATE = 22050
DURATION = 1.05
samples = [0.0] * int(RATE * DURATION)


def add(start, length, fn):
    first = int(start * RATE)
    for i in range(int(length * RATE)):
        if first + i < len(samples):
            samples[first + i] += fn(i / RATE, length)


def envelope(t, length, attack=0.01):
    return min(1.0, t / attack) * max(0.0, 1.0 - t / length) ** 2


# 1) a soft falling whoosh while the badge drops: a sine gliding from 780 Hz down to 260 Hz
def whoosh(t, length):
    progress = t / length
    freq = 780 - 520 * progress
    phase = 2 * math.pi * (780 * t - 520 * t * t / (2 * length))
    return 0.22 * envelope(t, length, 0.05) * math.sin(phase) * (0.35 + 0.65 * (1 - progress))


add(0.0, 0.5, whoosh)


# 2) the landing: a short low pop
def pop(t, length):
    return 0.55 * envelope(t, length, 0.003) * math.sin(2 * math.pi * (170 - 60 * t / length) * t)


add(0.5, 0.16, pop)


# 3) a tiny rising two-note chime: "done"
def chime(freq):
    def tone(t, length):
        return 0.20 * envelope(t, length, 0.006) * (math.sin(2 * math.pi * freq * t) + 0.35 * math.sin(2 * math.pi * freq * 2 * t))
    return tone


add(0.62, 0.32, chime(880))
add(0.72, 0.33, chime(1318.5))

peak = max(abs(s) for s in samples) or 1.0
scale = 0.85 / peak
fade = int(0.02 * RATE)
with wave.open(sys.argv[1], "wb") as out:
    out.setnchannels(1)
    out.setsampwidth(2)
    out.setframerate(RATE)
    for i, s in enumerate(samples):
        gain = min(1.0, (len(samples) - i) / fade)  # avoid a click at the very end
        out.writeframes(struct.pack("<h", int(max(-1.0, min(1.0, s * scale * gain)) * 32767)))
print("wrote", sys.argv[1])
