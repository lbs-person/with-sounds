"""Generate a small, fully synthetic demo library for screenshots and tests.

All audio is generated from sine waves; no copyrighted recording is used.
"""
import io
import math
import os

import lameenc
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from mutagen.id3 import APIC, ID3, TALB, TIT2, TPE1, TRCK

OUT = r"I:\mini project\tongpin\demo-music"
os.makedirs(OUT, exist_ok=True)
SR = 44100

TRACKS = [
    ("晚风", "橘子海岸", "夏日留声", 0.94, 0.52, 0.38, "#FF8A66", "#33202A"),
    ("午夜电台", "北纬三十度", "城市失眠", 0.35, 0.46, 0.86, "#A8A6FF", "#151725"),
    ("雨停之前", "小岛工作室", "潮湿的下午", 0.42, 0.78, 0.66, "#91E8C9", "#112220"),
    ("第七首歌", "慢半拍", "耳机分你", 0.96, 0.72, 0.48, "#F6C56B", "#241D12"),
    ("日落大道", "鹿野", "公路电影", 0.88, 0.34, 0.46, "#FF9EC4", "#25131D"),
    ("银河便利店", "宇宙订单", "凌晨两点", 0.48, 0.54, 0.95, "#79C7FF", "#111B25"),
]

CHORDS = [
    [220.00, 261.63, 329.63, 392.00],  # Am7
    [174.61, 220.00, 261.63, 349.23],  # Fmaj7
    [130.81, 164.81, 196.00, 261.63],  # Cmaj
    [196.00, 246.94, 293.66, 392.00],  # G
]


def font(size):
    for path in (r"C:\Windows\Fonts\msyhbd.ttc", r"C:\Windows\Fonts\msyh.ttc"):
        try:
            return ImageFont.truetype(path, size)
        except Exception:
            pass
    return ImageFont.load_default()


def make_cover(title, artist, album, hue, seed):
    rng = np.random.default_rng(seed)
    w = h = 900
    y = np.linspace(0, 1, h)[:, None]
    x = np.linspace(0, 1, w)[None, :]
    base = np.zeros((h, w, 3), dtype=np.float32)
    for i in range(3):
        base[..., i] = (hue[i] * (1 - y) + 0.06 * y)
    # Grain / gradient folds.
    base += (np.sin(x * 8 + y * 3) * 0.035)[..., None]
    base += (np.cos(y * 14 - x * 2) * 0.025)[..., None]
    base = np.clip(base, 0, 1)
    img = Image.fromarray((base * 255).astype(np.uint8), "RGB").filter(ImageFilter.GaussianBlur(1.2))
    draw = ImageDraw.Draw(img, "RGBA")

    # Abstract record / orbit motif.
    cx, cy, r = 620, 320, 250
    for i in range(16):
        rr = r - i * 13
        draw.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], outline=(255, 255, 255, 18), width=2)
    draw.ellipse([cx - 72, cy - 72, cx + 72, cy + 72], fill=(16, 12, 15, 235))
    draw.ellipse([cx - 16, cy - 16, cx + 16, cy + 16], fill=(255, 255, 255, 50))
    draw.ellipse([cx - 7, cy - 7, cx + 7, cy + 7], fill=tuple(int(c * 255) for c in hue) + (255,))

    # Soft light streak.
    draw.polygon([(0, 700), (900, 420), (900, 540), (0, 840)], fill=(255, 255, 255, 12))
    draw.text((72, 560), title, font=font(84), fill=(255, 248, 242, 245))
    draw.text((76, 682), artist, font=font(34), fill=(255, 248, 242, 180))
    draw.text((76, 744), album.upper(), font=font(22), fill=(255, 248, 242, 110))
    draw.text((688, 800), "同频 DEMO", font=font(24), fill=(255, 248, 242, 150))

    out = io.BytesIO()
    img.save(out, "JPEG", quality=90)
    return out.getvalue()


def make_audio(index, seed, duration=36.0):
    rng = np.random.default_rng(seed)
    n = int(SR * duration)
    t = np.arange(n) / SR
    left = np.zeros(n, dtype=np.float64)
    right = np.zeros(n, dtype=np.float64)

    bpm = 78 + index * 3
    beat = 60.0 / bpm
    bar = beat * 4
    # Chord pads with a soft attack, one chord per bar.
    for b in range(int(duration / bar) + 1):
        chord = CHORDS[(b + index) % len(CHORDS)]
        start = int(b * bar * SR)
        end = min(n, int((b + 1) * bar * SR))
        if start >= n:
            break
        tt = np.arange(end - start) / SR
        env = np.minimum(1, tt / 0.7) * np.minimum(1, (tt[-1] - tt) / 0.5 + 0.15)
        for j, f in enumerate(chord):
            wave = (
                0.6 * np.sin(2 * math.pi * f * tt + j * 0.4)
                + 0.2 * np.sin(2 * math.pi * f * 2 * tt)
                + 0.1 * np.sin(2 * math.pi * f * 3.01 * tt)
            )
            pad = wave * env * (0.055 + 0.008 * j)
            left[start:end] += pad * (0.9 + 0.1 * math.sin(j))
            right[start:end] += pad * (0.9 - 0.1 * math.sin(j))

    # Sparse bell melody on a pentatonic scale.
    scale = [0, 2, 4, 7, 9, 12, 14, 16]
    root = [220, 174.61, 261.63, 196][index % 4]
    step = beat * 1.5
    pos = beat * 0.8
    while pos < duration - 2:
        degree = scale[int(rng.integers(0, len(scale)))]
        f = root * (2 ** (degree / 12))
        start = int(pos * SR)
        length = min(n - start, int(2.6 * SR))
        if length <= 0:
            break
        tt = np.arange(length) / SR
        env = np.exp(-tt * 1.9) * np.minimum(1, tt / 0.012)
        bell = (np.sin(2 * math.pi * f * tt) + 0.35 * np.sin(2 * math.pi * f * 2.01 * tt)) * env * 0.075
        pan = 0.35 + 0.3 * rng.random()
        left[start:start + length] += bell * (1 - pan)
        right[start:start + length] += bell * pan
        pos += step * (1 if rng.random() > 0.22 else 1.5)

    # Airy percussion: filtered noise + low kick.
    for b in range(int(duration / beat)):
        start = int(b * beat * SR)
        length = min(n - start, int(0.13 * SR))
        if length <= 0:
            break
        tt = np.arange(length) / SR
        noise = rng.normal(0, 1, length).astype(np.float64)
        # Cheap smoothing = a softer hi-hat.
        for _ in range(3):
            noise = np.convolve(noise, [0.5, 0.5], mode="same")
        env = np.exp(-tt * 32)
        left[start:start + length] += noise * env * 0.022
        right[start:start + length] += noise * env * 0.026
        if b % 2 == 0:
            tt2 = np.arange(length) / SR
            kick = np.sin(2 * math.pi * (55 + 70 * np.exp(-tt2 * 28)) * tt2) * np.exp(-tt2 * 16) * 0.17
            left[start:start + length] += kick
            right[start:start + length] += kick

    # Fade in / out and gentle stereo widening.
    fade = np.minimum(1, t / 1.2) * np.minimum(1, (duration - t) / 2.5)
    left *= fade
    right *= fade
    stereo = np.stack([left, right], axis=1)
    peak = np.max(np.abs(stereo)) or 1
    stereo = stereo / peak * 0.82
    pcm = (stereo * 32767).astype("<i2").tobytes()
    return pcm, duration


def encode_mp3(pcm, bitrate=192):
    enc = lameenc.Encoder()
    enc.set_bit_rate(bitrate)
    enc.set_in_sample_rate(SR)
    enc.set_channels(2)
    enc.set_quality(2)
    return bytes(enc.encode(pcm) + enc.flush())


def main():
    for i, (title, artist, album, r, g, b, color, bg) in enumerate(TRACKS):
        pcm, duration = make_audio(i, seed=100 + i)
        mp3 = encode_mp3(pcm)
        path = os.path.join(OUT, f"{i + 1:02d} - {artist} - {title}.mp3")
        with open(path, "wb") as f:
            f.write(mp3)
        cover = make_cover(title, artist, album, (r, g, b), seed=200 + i)
        tags = ID3()
        tags.add(TIT2(encoding=3, text=title))
        tags.add(TPE1(encoding=3, text=artist))
        tags.add(TALB(encoding=3, text=album))
        tags.add(TRCK(encoding=3, text=str(i + 1)))
        tags.add(APIC(encoding=3, mime="image/jpeg", type=3, desc="Cover", data=cover))
        tags.save(path, v2_version=4)
        print(f"{path}  {len(mp3) / 1024 / 1024:.1f} MB  {duration:.0f}s")


if __name__ == "__main__":
    main()
