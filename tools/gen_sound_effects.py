#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成「童音音效」素材：app/src/main/res/raw/snd_*.wav

为什么需要这个脚本
------------------
原本 res/raw 里的 snd_*.wav 是纯电子音（正弦/方波合成），听感生硬、不像给小朋友用的产品。
本脚本把音效换成**小朋友的语气声**：

  1) 用系统 TTS（Windows SAPI 中文语音 / eSpeak NG）朗读一句很短的中文（如「哇！」「好棒！」）；
  2) 对音频做**升调 + 加速**（resample），把成年女声压成稚嫩童音（音高上去、语速变快，正是小孩说话的特征）；
  3) 叠一层**清脆铃音 / 上行小琶音**，做出游戏里「叮～」的爽感，让音效更卡通、更抓耳；
  4) 归一化 + 首尾淡入淡出，避免爆音。

产出的 wav 为 44100Hz / 16bit / 单声道，直接被 SoundPlayer 通过 MediaPlayer 播放。
若合成后端不可用，脚本会明确报错而不是静默产出空文件（避免再出现「文件在但内容不对」的坑）。

用法：
    py -3 tools/gen_sound_effects.py            # 增量生成（已存在则跳过）
    py -3 tools/gen_sound_effects.py --force    # 强制全部重生成
"""

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import wave

import numpy as np

# ===== 路径 =====
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW_DIR = os.path.join(ROOT, "app", "src", "main", "res", "raw")
OUT_SR = 44100

# ===== 音效配置 =====
# speak   : TTS 朗读的文本（为空 = 纯合成音，不经过 TTS）
# pitch   : 升调系数，>1 表示音高和语速一起上去（1.3 ≈ 童音）
# bells   : 叠加的铃音频率序列 [(频率Hz, 起始秒, 时长秒, 音量)]，做「叮～」的卡通感
# gain    : 整体输出峰值（0~1）
SOUNDS = {
    # 每跳一下的计数音：极短、清脆，用纯铃音（人声在每跳都念会太吵）
    "snd_tick": dict(
        speak="",
        bells=[(1760.0, 0.0, 0.10, 0.55), (2637.0, 0.012, 0.09, 0.28)],
        gain=0.55,
        note="计数「叮」",
    ),
    # 冒小星星 / 小礼物
    "snd_star": dict(
        speak="哇！",
        pitch=1.34,
        bells=[(1318.5, 0.0, 0.16, 0.30), (1975.5, 0.05, 0.18, 0.22)],
        gain=0.9,
        note="哇！（惊喜）",
    ),
    # 里程碑奖励（满 10 个等）
    "snd_reward": dict(
        speak="好棒！",
        pitch=1.30,
        bells=[(659.25, 0.0, 0.14, 0.26), (987.77, 0.07, 0.16, 0.24), (1318.5, 0.14, 0.22, 0.20)],
        gain=0.95,
        note="好棒！（奖励）",
    ),
    # 加油
    "snd_cheer": dict(
        speak="加油！",
        pitch=1.32,
        bells=[(783.99, 0.0, 0.14, 0.24), (1046.5, 0.08, 0.20, 0.20)],
        gain=0.92,
        note="加油！",
    ),
    # 动作不规范：语气要温柔一点、往下走，避免像在批评孩子
    "snd_correction": dict(
        speak="再来！",
        pitch=1.20,
        bells=[(523.25, 0.0, 0.18, 0.20), (392.0, 0.12, 0.26, 0.16)],
        gain=0.85,
        note="再来！（温柔纠错）",
    ),
    # 训练完成：最长的一句，配一小段上行胜利琶音
    "snd_complete": dict(
        speak="你太厉害啦！",
        pitch=1.26,
        bells=[
            (523.25, 0.0, 0.16, 0.22),
            (659.25, 0.10, 0.16, 0.20),
            (783.99, 0.20, 0.16, 0.20),
            (1046.5, 0.30, 0.40, 0.22),
        ],
        gain=0.95,
        note="你太厉害啦！（完成）",
    ),
}


# ============================ 基础音频工具 ============================

def read_wav(path: str):
    """读 wav -> (float32 数组[-1,1], 采样率)。"""
    with wave.open(path, "rb") as w:
        sr = w.getframerate()
        n = w.getnframes()
        raw = w.readframes(n)
    data = np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768.0
    if w.getnchannels() == 2:
        data = data.reshape(-1, 2).mean(axis=1)
    return data, sr


def write_wav(path: str, data: np.ndarray, sr: int = OUT_SR):
    """写 16bit 单声道 wav，自动限幅避免溢出失真。"""
    pcm = np.clip(data, -1.0, 1.0)
    pcm = (pcm * 32767.0).astype("<i2")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm.tobytes())


def resample(data: np.ndarray, src_sr: int, dst_sr: int) -> np.ndarray:
    """线性插值重采样（改变采样率，不改变音高/时长）。"""
    if src_sr == dst_sr or len(data) == 0:
        return data
    n_src = len(data)
    n_dst = max(1, int(round(n_src * dst_sr / src_sr)))
    x_src = np.arange(n_src, dtype=np.float32)
    x_dst = np.linspace(0.0, n_src - 1.0, n_dst, dtype=np.float32)
    return np.interp(x_dst, x_src, data).astype(np.float32)


def pitch_up(data: np.ndarray, factor: float) -> np.ndarray:
    """
    升调 + 加速：把样本按 factor 压缩播放。

    这是最简单的「变童音」手段——同时抬高音高、加快语速，
    正好对应小朋友说话的两个特征（成人 TTS 声音压扁后就有稚嫩感）。
    """
    if factor <= 1.0 or len(data) == 0:
        return data
    n_dst = max(1, int(len(data) / factor))
    x_src = np.arange(len(data), dtype=np.float32)
    x_dst = np.linspace(0.0, len(data) - 1.0, n_dst, dtype=np.float32)
    return np.interp(x_dst, x_src, data).astype(np.float32)


def ola_stretch(data: np.ndarray, factor: float, sr: int, win_ms: float = 45.0, hop_ms: float = 15.0) -> np.ndarray:
    """
    变调不变速（OLA 重叠相加时间拉伸）：把音频拉长 factor 倍，音高不变。

    与 pitch_up 搭配使用：先 pitch_up 升调（同时加速），再用它把时长拉回来，
    最终得到「音调高、语速只略快」的童音，而不是升调后那种花栗鼠式的急促感。
    """
    if factor <= 1.0 or len(data) < 2:
        return data
    win = max(64, int(sr * win_ms / 1000))
    hop_a = max(16, int(sr * hop_ms / 1000))
    hop_s = max(1, int(round(hop_a * factor)))
    n_out = int(len(data) * factor) + win
    out = np.zeros(n_out, dtype=np.float32)
    norm = np.zeros(n_out, dtype=np.float32)
    w = np.hanning(win).astype(np.float32)
    pos_s = 0
    pos_a = 0
    while pos_a + win <= len(data):
        out[pos_s:pos_s + win] += data[pos_a:pos_a + win] * w
        norm[pos_s:pos_s + win] += w
        pos_a += hop_a
        pos_s += hop_s
    nz = norm > 1e-6
    out[nz] /= norm[nz]
    return out[:max(1, pos_s)]


def trim_silence(data: np.ndarray, sr: int, thresh: float = 0.012, pad_ms: int = 30) -> np.ndarray:
    """掐掉首尾静音，让音效起落干脆（留 pad_ms 余量避免削掉字头字尾）。"""
    if len(data) == 0:
        return data
    win = max(1, int(sr * 0.01))
    # 滑动窗能量，避免单个采样点误判
    energy = np.convolve(np.abs(data), np.ones(win) / win, mode="same")
    idx = np.where(energy > thresh)[0]
    if len(idx) == 0:
        return data
    pad = int(sr * pad_ms / 1000)
    a = max(0, int(idx[0]) - pad)
    b = min(len(data), int(idx[-1]) + pad)
    return data[a:b]


def fade(data: np.ndarray, sr: int, ms: int = 8) -> np.ndarray:
    """首尾淡入淡出，防止 click 爆音。"""
    n = int(sr * ms / 1000)
    if len(data) <= n * 2:
        return data
    out = data.copy()
    ramp = np.linspace(0.0, 1.0, n, dtype=np.float32)
    out[:n] *= ramp
    out[-n:] *= ramp[::-1]
    return out


def normalize(data: np.ndarray, peak: float) -> np.ndarray:
    m = float(np.max(np.abs(data))) if len(data) else 0.0
    if m < 1e-6:
        return data
    return (data / m * peak).astype(np.float32)


def bell_tone(freq: float, start: float, dur: float, amp: float, sr: int) -> np.ndarray:
    """
    一个卡通铃音：正弦基频 + 2 倍频泛音，指数衰减。
    比纯正弦更「叮」、更有玩具琴的感觉。
    """
    n = int(sr * dur)
    if n <= 0:
        return np.zeros(0, dtype=np.float32)
    t = np.arange(n, dtype=np.float32) / sr
    env = np.exp(-4.6 * t / dur).astype(np.float32)          # 衰减到 ~1%
    env *= np.minimum(1.0, t / 0.004)                        # 4ms 起振，避免咔哒
    wave = np.sin(2 * np.pi * freq * t) + 0.35 * np.sin(4 * np.pi * freq * t)
    return (wave * env * amp * 0.5).astype(np.float32)


def mix_into(base: np.ndarray, layer: np.ndarray, start: float, sr: int) -> np.ndarray:
    """把 layer 叠加到 base 的 start 秒处，自动加长 base。"""
    off = int(sr * start)
    need = off + len(layer)
    if len(base) < need:
        base = np.pad(base, (0, need - len(base)))
    base = base.copy()
    base[off:off + len(layer)] += layer
    return base


# ============================ TTS 后端 ============================

def tts_to_wav(text: str, out_path: str) -> bool:
    """
    合成一句中文到 wav。优先级：espeak-ng -> espeak -> pyttsx3(Windows SAPI)。
    失败返回 False（绝不静默产出空文件）。
    """
    for cmd in (
        ["espeak-ng", "-v", "cmn", "-s", "150", "-p", "70", "-w", out_path, text],
        ["espeak", "-v", "cmn", "-s", "150", "-p", "70", "-w", out_path, text],
    ):
        if shutil.which(cmd[0]):
            try:
                subprocess.run(cmd, check=True, capture_output=True, timeout=60)
                if os.path.getsize(out_path) > 1000:
                    return True
            except Exception:
                pass

    try:
        import pyttsx3
        engine = pyttsx3.init()
        # 语速略快 + 音量拉满，给后面的升调留余地（升调会同时加快语速）
        engine.setProperty("rate", 165)
        engine.setProperty("volume", 1.0)
        voices = engine.getProperty("voices")
        for v in voices:
            langs = " ".join(str(x) for x in getattr(v, "languages", []) or []).lower()
            if "zh" in langs or "chinese" in v.name.lower() or "huihui" in v.name.lower():
                engine.setProperty("voice", v.id)
                break
        engine.save_to_file(text, out_path)
        engine.runAndWait()
        for _ in range(50):  # SAPI 是异步落盘，轮询等待文件写完
            if os.path.exists(out_path) and os.path.getsize(out_path) > 1000:
                return True
            import time
            time.sleep(0.1)
    except Exception as e:
        print(f"  [warn] pyttsx3 合成失败：{e}")
    return False


# ============================ 主流程 ============================

def build_one(name: str, cfg: dict, force: bool) -> bool:
    dst = os.path.join(RAW_DIR, f"{name}.wav")
    if os.path.exists(dst) and not force:
        print(f"  - {name}.wav 已存在，跳过（--force 可强制重生成）")
        return True

    base = np.zeros(0, dtype=np.float32)

    # 1) 童音人声
    if cfg.get("speak"):
        with tempfile.TemporaryDirectory() as td:
            tmp = os.path.join(td, "tts.wav")
            if not tts_to_wav(cfg["speak"], tmp):
                print(f"  [错误] {name}：TTS 合成失败，未生成该文件")
                return False
            voice, sr = read_wav(tmp)
        voice = trim_silence(voice, sr)
        voice = resample(voice, sr, OUT_SR)
        pitch = cfg.get("pitch", 1.3)
        voice = pitch_up(voice, pitch)
        # 把被压缩的时长拉回来（默认拉回 sqrt(pitch) 倍）：音调仍是童音，语速只快一点点
        voice = ola_stretch(voice, cfg.get("stretch", float(np.sqrt(pitch))), OUT_SR)
        base = mix_into(base, voice, 0.0, OUT_SR)

    # 2) 叠铃音（铃音起点在人声之后一点点，形成「话音刚落叮一下」的层次）
    lead = (len(voice) / OUT_SR * 0.15) if cfg.get("speak") else 0.0
    for freq, start, dur, amp in cfg.get("bells", []):
        base = mix_into(base, bell_tone(freq, start, dur, amp, OUT_SR), lead, OUT_SR)

    if len(base) == 0:
        print(f"  [错误] {name}：没有任何音频内容")
        return False

    base = fade(base, OUT_SR, ms=8)
    base = normalize(base, cfg.get("gain", 0.9))
    write_wav(dst, base)
    dur = len(base) / OUT_SR
    print(f"  ✓ {name}.wav  {dur:.2f}s  （{cfg.get('note','')}）")
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="强制重新生成全部音效")
    args = ap.parse_args()

    print("=== 生成童音音效 ===")
    ok = 0
    for name, cfg in SOUNDS.items():
        if build_one(name, cfg, args.force):
            ok += 1
    print(f"完成：{ok}/{len(SOUNDS)} 个音效 -> {RAW_DIR}")
    if ok != len(SOUNDS):
        sys.exit(1)


if __name__ == "__main__":
    main()
