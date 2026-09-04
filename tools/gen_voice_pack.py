#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
离线生成「语音包」：把鼓励语语料合成为音频，打包进 App 后任意手机音色完全一致，
不依赖厂商 TTS 引擎。对应 app 端 VoiceSpeaker 的 assets/voice/<sha1>.<ext> 离线路径。

用法（在仓库根目录执行）：
    python tools/gen_voice_pack.py

合成后端优先级（自动探测，离线优先）：
    1. espeak-ng  —— 免费开源、离线、支持中文，首选
    2. espeak     —— espeak-ng 的旧版
    3. pyttsx3    —— Windows 自带 SAPI 中文语音（如 Microsoft Huihui），离线
    4. gTTS       —— Python 库（需联网到 Google，国内可能不通）
    5. 都不可用    —— 打印安装提示后退出，装好任一个再跑一次即可

生成内容（三类）：
    A. 静态句（不含 {n}）：<sha1(utf8)>.wav   —— 直接整句播放
    B. 动态句（含 {n}，如里程碑/完成语）：拆成「前缀」「后缀」两段，各生成
       <sha1(前缀)>.wav / <sha1(后缀)>.wav；运行时再与数字音频拼接
    C. 中文数字音频：num_<字>.wav（零 一 二 ... 九 十 百 千 万），运行时按计数拼接

文件名与 App 端约定：
    - 整句/前缀/后缀：UTF-8 做 SHA-1 十六进制（与 VoiceSpeaker.sha1 一致）
    - 数字：字面字符，如 num_十.wav
"""

import hashlib
import os
import re
import shutil
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO, "app", "src", "main", "java", "com", "jumpdaily", "jump", "audio", "Encouragements.kt")
OUT_DIR = os.path.join(REPO, "app", "src", "main", "assets", "voice")

# eSpeak 童音参数（SAPI 不支持音调，仅作说明；espeak 下生效）
PITCH = 72
SPEED = 130

# 中文数字音频需要的字符（与 VoiceSpeaker.toChineseNum 生成的一一对应）
NUM_CHARS = ["零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "百", "千", "万"]


def sha1(text: str) -> str:
    return hashlib.sha1(text.encode("utf-8")).hexdigest()


def unescape_kotlin(s: str) -> str:
    """把 Kotlin 字符串字面量里的转义还原成真字符。

    注意：绝不能用 str.encode('utf-8').decode('unicode_escape') —— 那会把已经是正确的
    UTF-8 中文（多字节）按 latin-1 逐字节拆开，造成乱码，进而让生成的 SHA-1 文件名与
    App 端 VoiceSpeaker.sha1(正确中文) 对不上，离线语音包永远命中不了（只能回退系统 TTS）。
    这里只处理常见转义，非 ASCII 原样保留。
    """
    out: list[str] = []
    i = 0
    mapping = {"n": "\n", "t": "\t", "r": "\r", '"': '"', "\\": "\\", "'": "'", "/": "/"}
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            nxt = s[i + 1]
            if nxt in mapping:
                out.append(mapping[nxt]); i += 2; continue
            if nxt == "u" and i + 6 <= len(s):  # \uXXXX
                try:
                    out.append(chr(int(s[i + 2:i + 6], 16))); i += 6; continue
                except ValueError:
                    pass
        out.append(c); i += 1
    return "".join(out)


def load_phrases() -> list[str]:
    """解析 Encouragements.kt，提取所有语料（含 {n} 的动态句）。"""
    with open(SRC, "r", encoding="utf-8") as f:
        src = f.read()
    phrases: list[str] = []
    for m in re.finditer(r"val\s+\w*Pool\s*=\s*listOf\((.*?)\)", src, re.DOTALL):
        block = m.group(1)
        for s in re.findall(r'"((?:[^"\\]|\\.)*)"', block):
            phrases.append(unescape_kotlin(s))
    seen = set()
    uniq = []
    for p in phrases:
        if p not in seen:
            seen.add(p)
            uniq.append(p)
    return uniq


def which(cmd: str) -> bool:
    return shutil.which(cmd) is not None


def synth(text: str, out_path: str) -> bool:
    """用任一可用后端把 text 合成到 out_path。成功返回 True。"""
    if which("espeak-ng"):
        return synth_espeak(text, out_path, "espeak-ng")
    if which("espeak"):
        return synth_espeak(text, out_path, "espeak")
    try:
        import pyttsx3  # noqa: F401
        return synth_pyttsx3(text, out_path)
    except Exception:
        pass
    try:
        import gtts  # noqa: F401
        return synth_gtts(text, out_path)
    except Exception:
        pass
    return False


def synth_espeak(text: str, out_path: str, cmd: str) -> bool:
    try:
        subprocess.run(
            [cmd, "-v", "zh", f"--pit={PITCH}", f"-s={SPEED}", "-a", "100",
             text, "-w", out_path],
            check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        return os.path.exists(out_path) and os.path.getsize(out_path) > 0
    except Exception as e:
        print(f"  [espeak 失败] {e}")
        return False


def synth_gtts(text: str, out_path: str) -> bool:
    try:
        from gtts import gTTS
        gTTS(text=text, lang="zh-CN", slow=False).save(out_path)
        return os.path.exists(out_path) and os.path.getsize(out_path) > 0
    except Exception as e:
        print(f"  [gTTS 失败] {e}")
        return False


def synth_pyttsx3(text: str, out_path: str) -> bool:
    try:
        import pyttsx3
        e = pyttsx3.init()
        zh = None
        for v in e.getProperty("voices"):
            langs = [str(l).lower() for l in (v.languages or [])]
            if "zh" in "|".join(langs) or "chinese" in v.name.lower():
                zh = v.id
                break
        if zh:
            e.setProperty("voice", zh)
        e.setProperty("rate", 150)
        e.setProperty("pitch", 1.6)
        e.save_to_file(text, out_path)
        e.runAndWait()
        return os.path.exists(out_path) and os.path.getsize(out_path) > 0
    except Exception as ex:
        print(f"  [pyttsx3 失败] {ex}")
        return False


def gen(out_name: str, text: str) -> bool:
    """生成单个音频文件（按 sha1 或 num_ 命名）。返回是否新生成。"""
    out_path = os.path.join(OUT_DIR, out_name)
    if os.path.exists(out_path):
        return False
    print(f"  [合成中] {out_name}  {text}")
    return synth(text, out_path)


def main() -> int:
    if not os.path.isfile(SRC):
        print(f"找不到语料源文件: {SRC}")
        return 2
    os.makedirs(OUT_DIR, exist_ok=True)

    phrases = load_phrases()
    static = [p for p in phrases if "{n}" not in p]
    dynamic = [p for p in phrases if "{n}" in p]
    print(f"语料总数 {len(phrases)}：静态 {len(static)}，动态 {len(dynamic)}")

    ok = 0
    # A. 静态句
    for p in static:
        if gen(f"{sha1(p)}.wav", p):
            ok += 1
    # B. 动态句的前缀/后缀
    for p in dynamic:
        pre, suf = p.split("{n}", 1)
        for seg in (pre, suf):
            if seg and gen(f"{sha1(seg)}.wav", seg):
                ok += 1
    # C. 中文数字音频
    for ch in NUM_CHARS:
        if gen(f"num_{ch}.wav", ch):
            ok += 1

    print(f"\n完成：本次新增 {ok} 个音频到 {OUT_DIR}（含静态句 + 动态句前后缀 + 数字音频）")
    print("重新打包安装 App 后：静态句直接播放；含 {n} 的句子由『前缀+数字+后缀』拼接播放，任意手机音色一致。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
