#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
跳一跳 - 资产生成脚本（一次性使用，生成后可删除）。
生成：
  1) res/raw/snd_*.wav   卡通音效（用正弦叠代合成，零素材，可后续替换为真人录音）
  2) res/raw/*.json      Lottie 庆祝 / 星星动画
依赖仅 Python 标准库。运行：python assetgen.py
"""
import wave, math, struct, json, os, random

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "app", "src", "main", "res", "raw")
os.makedirs(RAW, exist_ok=True)
SR = 44100

# ===================== 1) WAV 卡通音效 =====================
def sample(freq, t, gains=(1.0, 0.28, 0.12)):
    """基频 + 少量谐波，做出卡通明亮的‘叮咚’质感。"""
    s = math.sin(2 * math.pi * freq * t) * gains[0]
    if len(gains) > 1:
        s += math.sin(2 * math.pi * 2 * freq * t) * gains[1]
    if len(gains) > 2:
        s += math.sin(2 * math.pi * 3 * freq * t) * gains[2]
    return s

def render(notes, master=0.34):
    """notes: list of (freq, start_s, dur_s[, gains])。返回 16bit PCM 样本列表。"""
    total = max(n[1] + n[2] for n in notes)
    n = int(total * SR)
    buf = [0.0] * n
    for nt in notes:
        f, st, d = nt[0], nt[1], nt[2]
        gains = nt[3] if len(nt) > 3 else (1.0, 0.28, 0.12)
        i0, i1 = int(st * SR), int((st + d) * SR)
        a = max(1, int(0.006 * SR))
        rel = max(1, int(0.05 * SR))
        for i in range(i0, i1):
            local = i - i0
            env = 1.0
            if local < a:
                env = local / a
            elif local > (i1 - i0 - rel):
                env = max(0.0, (i1 - i0 - local) / rel)
            buf[i] += sample(f, local / SR, gains) * env
    peak = max(1e-6, max(abs(x) for x in buf))
    scale = min(1.0, 0.95 / peak)
    return [int(max(-32768, min(32767, x * master * scale * 32767))) for x in buf]

def write_wav(name, notes, master=0.34):
    pcm = render(notes, master)
    path = os.path.join(RAW, name)
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", v) for v in pcm))
    print("wav:", name, "%.1fKB" % (os.path.getsize(path) / 1024))

# 跳得好：上行大三和弦琶音（明亮欢快）
write_wav("snd_reward.wav", [
    (523.25, 0.00, 0.12), (659.25, 0.13, 0.12), (783.99, 0.26, 0.12),
    (1046.50, 0.39, 0.30), (1318.51, 0.39, 0.30),
])
# 小奖励：高音叮咚
write_wav("snd_star.wav", [(987.77, 0.00, 0.09), (1318.51, 0.10, 0.18)])
# 动作不规范：柔和下行双音（像“嗯～”，偏闷）
write_wav("snd_correction.wav", [
    (440.00, 0.00, 0.18, (1.0, 0.45, 0.22)),
    (349.23, 0.20, 0.30, (1.0, 0.45, 0.22)),
])
# 加油鼓励：轻快上行口哨感
write_wav("snd_cheer.wav", [(587.33, 0.00, 0.10), (698.46, 0.11, 0.10), (880.00, 0.22, 0.24)])
# 节奏滴答
write_wav("snd_tick.wav", [(2000.00, 0.00, 0.045)], master=0.22)
# 训练完成：胜利小旋律
write_wav("snd_complete.wav", [
    (523.25, 0.00, 0.14), (659.25, 0.15, 0.14), (783.99, 0.30, 0.14),
    (1046.50, 0.45, 0.14), (783.99, 0.60, 0.14), (1046.50, 0.75, 0.42),
])

# ===================== 2) Lottie JSON 动画 =====================
def ease(i=(0.5,), o=(0.5,)):
    return {"i": {"x": list(i), "y": [1]}, "o": {"x": list(o), "y": [0]}}

def confetti_layer(ind, rgb, x0, y0, x1, y1, rot0, rot1, sf, ef, size=16):
    """一条彩带：从底部中心弹出、旋转、飞散、淡出（控制点循环）。"""
    return {
        "ddd": 0, "ind": ind, "ty": 4, "nm": "c%d" % ind, "sr": 1,
        "ks": {
            "o": {"a": 1, "k": [
                {"t": sf, "s": [0], **ease()},
                {"t": sf + 3, "s": [100]},
                {"t": ef - 8, "s": [100]},
                {"t": ef, "s": [0]},
            ]},
            "r": {"a": 1, "k": [
                {"t": sf, "s": [rot0], **ease()},
                {"t": ef, "s": [rot1]},
            ]},
            "p": {"a": 1, "k": [
                {"t": sf, "s": [x0, y0, 0], **ease((0.3,), (0,))},
                {"t": ef, "s": [x1, y1, 0]},
            ]},
            "a": {"a": 0, "k": [0, 0, 0]},
            "s": {"a": 0, "k": [100, 100, 100]},
        },
        "ao": 0,
        "shapes": [{
            "ty": "gr", "it": [
                {"ty": "rc", "d": 1, "s": {"a": 0, "k": [size, int(size * 2.4)]},
                 "p": {"a": 0, "k": [0, 0]}, "r": {"a": 0, "k": 3}, "nm": "r"},
                {"ty": "fl", "c": {"a": 0, "k": [rgb[0], rgb[1], rgb[2], 1]},
                 "o": {"a": 0, "k": 100}, "nm": "f"},
                {"ty": "tr", "p": {"a": 0, "k": [0, 0]}, "a": {"a": 0, "k": [0, 0]},
                 "s": {"a": 0, "k": [100, 100]}, "r": {"a": 0, "k": 0}, "o": {"a": 0, "k": 100}},
            ], "nm": "g"},
        ],
        "ip": 0, "op": 90, "st": 0, "bm": 0,
    }

def build_celebration():
    W = H = 240
    cx, cy = W // 2, H - 20
    palette = [
        (1.0, 0.42, 0.62), (1.0, 0.80, 0.30), (0.42, 0.86, 0.70),
        (0.45, 0.72, 1.0), (0.78, 0.55, 1.0), (1.0, 0.55, 0.40),
    ]
    layers = []
    random.seed(7)
    for i in range(14):
        ang = (-150 + i * (300 / 13)) + random.uniform(-12, 12)
        rad = random.uniform(70, 110)
        x1 = cx + rad * math.cos(math.radians(ang))
        y1 = cy - abs(rad * math.sin(math.radians(ang))) - random.uniform(0, 30)
        rgb = palette[i % len(palette)]
        sf = random.randint(0, 14)
        ef = 60 + random.randint(0, 20)
        layers.append(confetti_layer(i + 1, rgb, cx, cy, x1, y1,
                                      random.uniform(0, 180), random.uniform(-360, 360), sf, ef))
    comp = {"v": "5.7.4", "fr": 30, "ip": 0, "op": 90, "w": W, "h": H,
            "nm": "celebration", "ddd": 0, "assets": [], "layers": layers}
    return comp

def build_star_spin():
    """一个黄色五角星：弹入 + 持续旋转 + 闪烁，用于启动页。"""
    W = H = 220
    star = {
        "ddd": 0, "ind": 1, "ty": 4, "nm": "star", "sr": 1,
        "ks": {
            "o": {"a": 1, "k": [
                {"t": 0, "s": [60], **ease()},
                {"t": 12, "s": [100]},
                {"t": 45, "s": [100]},
                {"t": 60, "s": [70]},
                {"t": 90, "s": [100]},
            ]},
            "r": {"a": 1, "k": [
                {"t": 0, "s": [0], **ease()},
                {"t": 90, "s": [360]},
            ]},
            "p": {"a": 0, "k": [W // 2, H // 2, 0]},
            "a": {"a": 0, "k": [0, 0, 0]},
            "s": {"a": 1, "k": [
                {"t": 0, "s": [0, 0], **ease()},
                {"t": 16, "s": [120, 120]},
                {"t": 26, "s": [100, 100]},
                {"t": 90, "s": [100, 100]},
            ]},
        },
        "ao": 0,
        "shapes": [{
            "ty": "gr", "it": [
                {"ty": "sr", "sy": 1, "pt": {"a": 0, "k": 5},
                 "p": {"a": 0, "k": [0, 0]}, "r": {"a": 0, "k": 0},
                 "ir": {"a": 0, "k": 20}, "is": {"a": 0, "k": 0},
                 "or": {"a": 0, "k": 46}, "os": {"a": 0, "k": 0}, "nm": "star"},
                {"ty": "fl", "c": {"a": 0, "k": [1.0, 0.82, 0.30, 1]},
                 "o": {"a": 0, "k": 100}, "nm": "f"},
                {"ty": "tr", "p": {"a": 0, "k": [0, 0]}, "a": {"a": 0, "k": [0, 0]},
                 "s": {"a": 0, "k": [100, 100]}, "r": {"a": 0, "k": 0}, "o": {"a": 0, "k": 100}},
            ], "nm": "g"},
        ],
        "ip": 0, "op": 90, "st": 0, "bm": 0,
    }
    return {"v": "5.7.4", "fr": 30, "ip": 0, "op": 90, "w": W, "h": H,
            "nm": "star_spin", "ddd": 0, "assets": [], "layers": [star]}

def write_json(name, comp):
    path = os.path.join(RAW, name)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(comp, f, ensure_ascii=False, separators=(",", ":"))
    print("json:", name, "%.1fKB" % (os.path.getsize(path) / 1024))

write_json("celebration.json", build_celebration())
write_json("star_spin.json", build_star_spin())
print("ALL ASSETS GENERATED ->", RAW)
