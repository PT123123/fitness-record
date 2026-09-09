# -*- coding: utf-8 -*-
"""生成 3 个内置铃声：
  beep_tone.wav   『滴滴滴滴滴滴』6 声柔和短滴
  knock_tone.wav  『咚咚咚』3 声敲门
  chime_tone.wav  『风铃声』高低错落金属管 + 悠长余音
用法：python gen_extra_tones.py
"""
import wave, math, struct, random

SR = 44100
OUT_DIR = r"app\src\main\res\raw"


def silence(dur):
    return [0.0] * int(SR * dur)


def normalize(buf, peak=0.8):
    m = max(abs(x) for x in buf) or 1.0
    scale = peak * 32767 / m
    return [x * scale for x in buf]


def write_wav(name, buf):
    pcm = b"".join(struct.pack("<h", int(max(-32768, min(32767, round(x))))) for x in buf)
    out = OUT_DIR + "\\" + name
    with wave.open(out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm)
    print(f"written {out}: {len(pcm)} bytes, {len(buf)/SR:.2f}s")


def soft_beep(freq, dur, amp):
    """柔和短促的『滴』：正弦 + 快起快落包络，带一点高次泛音提亮，避免刺耳。"""
    n = int(SR * dur)
    buf = []
    for i in range(n):
        t = i / SR
        att = min(1.0, i / (0.006 * SR))            # 6ms 起音
        rel = math.exp(-t / 0.05)                   # 快速衰减
        tail = min(1.0, max(0.0, (dur - t) / 0.006))  # 收尾线性归零防爆音
        env = att * rel * tail
        s = math.sin(2 * math.pi * freq * t) + 0.25 * math.sin(2 * math.pi * freq * 2 * t)
        buf.append(s * env * amp)
    return buf


def gen_beep():
    """『滴滴滴滴滴滴』：6 声柔和短滴。"""
    loop = []
    for _ in range(6):
        loop += soft_beep(1000.0, 0.11, 0.72)
        loop += silence(0.07)
    loop += silence(0.30)                           # 循环呼吸间隔
    write_wav("beep_tone.wav", normalize(loop, 0.8))


def knock(freq, dur, amp):
    """一声『咚』：低频撞击 + 快速衰减 + 敲击噪声瞬态。"""
    n = int(SR * dur)
    buf = []
    rnd = random.Random(7)
    for i in range(n):
        t = i / SR
        body = math.sin(2 * math.pi * freq * t) * math.exp(-t / 0.045)
        thump = math.sin(2 * math.pi * freq * 0.55 * t) * math.exp(-t / 0.07) * 0.5
        harm = math.sin(2 * math.pi * freq * 2.0 * t) * math.exp(-t / 0.03) * 0.30
        click = (rnd.random() * 2 - 1) * math.exp(-t / 0.006) * 0.18 if t < 0.02 else 0.0
        buf.append((body + thump + harm + click) * amp)
    return buf


def gen_knock():
    """『咚咚咚』：3 声敲门。"""
    loop = []
    for _ in range(3):
        loop += knock(150.0, 0.20, 0.9)
        loop += silence(0.30)
    loop += silence(0.35)
    write_wav("knock_tone.wav", normalize(loop, 0.85))


def chime_partials(freq):
    # 金属管风铃的非整数倍泛音列（基频 + 非谐分音）
    return [(1.0, 1.0), (2.76, 0.30), (5.40, 0.15), (8.93, 0.06)]


def chime_note(freq, dur, amp, decay):
    n = int(SR * dur)
    buf = []
    psum = sum(p for _, p in chime_partials(freq))
    for i in range(n):
        t = i / SR
        env = math.exp(-t / decay)
        att = min(1.0, i / (0.003 * SR))
        s = sum(p * math.sin(2 * math.pi * freq * m * t) for m, p in chime_partials(freq))
        buf.append(s * env * att * amp / psum)
    return buf


def gen_chime():
    """『风铃声』：多根高低错落的金属管随机敲击，余音悠长。"""
    random.seed(42)
    pitches = [1046.5, 1174.7, 1318.5, 1568.0, 1760.0, 2093.0]
    total = 4.2
    buf = [0.0] * int(SR * total)
    for _ in range(7):
        start = 0.2 + random.random() * 2.4            # 敲击时间分散
        freq = random.choice(pitches) * (1 + random.uniform(-0.003, 0.003))
        amp = 0.5 + random.random() * 0.4
        decay = 0.9 + random.random() * 0.5
        note = chime_note(freq, total - start, amp, decay)
        off = int(start * SR)
        for i, v in enumerate(note):
            if off + i < len(buf):
                buf[off + i] += v
    # 首尾淡入淡出，避免循环爆音
    fade_in = int(0.08 * SR)
    fade_out = int(0.6 * SR)
    for i in range(fade_in):
        buf[i] *= i / fade_in
    for i in range(fade_out):
        buf[len(buf) - 1 - i] *= i / fade_out
    write_wav("chime_tone.wav", normalize(buf, 0.8))


if __name__ == "__main__":
    gen_beep()
    gen_knock()
    gen_chime()
