# -*- coding: utf-8 -*-
"""校验 Encouragements.kt 里每条语料的离线音频是否都已在 assets/voice 生成。"""
import hashlib, os, re

SRC = r"D:/SmallTools/JumpDaily/app/src/main/java/com/jumpdaily/jump/audio/Encouragements.kt"
OUT = r"D:/SmallTools/JumpDaily/app/src/main/assets/voice"

src = open(SRC, encoding="utf-8").read()

def unescape(s):
    out, i, m = [], 0, {"n": "\n", "t": "\t", "r": "\r", '"': '"', "\\": "\\", "'": "'", "/": "/"}
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            nx = s[i+1]
            if nx in m:
                out.append(m[nx]); i += 2; continue
            if nx == "u" and i + 6 <= len(s):
                try:
                    out.append(chr(int(s[i+2:i+6], 16))); i += 6; continue
                except Exception:
                    pass
        out.append(c); i += 1
    return "".join(out)

pat_pool = re.compile(r'val\s+(\w+Pool)\s*=\s*listOf\((.*?)\)', re.DOTALL)
pat_str = re.compile(r'"((?:[^"\\]|\\.)*)"')

pools = {}
for mm in pat_pool.finditer(src):
    pools[mm.group(1)] = [unescape(s) for s in pat_str.findall(mm.group(2))]

existing = set(os.listdir(OUT))
def sha1(t): return hashlib.sha1(t.encode("utf-8")).hexdigest()

total_missing = 0
for name, phrases in pools.items():
    static = [p for p in phrases if "{n}" not in p]
    dyn = [p for p in phrases if "{n}" in p]
    miss = [p for p in static if ("%s.wav" % sha1(p)) not in existing]
    seg_miss = []
    for p in dyn:
        pre, suf = p.split("{n}", 1)
        if pre and ("%s.wav" % sha1(pre)) not in existing: seg_miss.append("PRE:" + p)
        if suf and ("%s.wav" % sha1(suf)) not in existing: seg_miss.append("SUF:" + p)
    print("%s: static=%d dyn=%d missing_static=%d missing_seg=%d" % (name, len(static), len(dyn), len(miss), len(seg_miss)))
    for p in miss[:3]: print("   MISS:", p)
    for p in seg_miss[:3]: print("   MISS-SEG:", p)
    total_missing += len(miss) + len(seg_miss)

print("TOTAL MISSING:", total_missing)
