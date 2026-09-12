# -*- coding: utf-8 -*-
"""生成 10 个新增内置铃声到 res/raw/。
  horn_tone.wav        🎺 喇叭号角（上升方波+锯齿，嘹亮）
  drum_tone.wav        🥁 鼓点（低频正弦打击+噪声瞬态，2 连击）
  gong_tone.wav        🔔 锣声（低频金属泛音+悠长衰减）
  piano_tone.wav       🎹 钢琴音（三角波+谐波+自然衰减，C大调和弦）
  heartbeat_tone.wav   💓 心跳（双低频脉冲，lub-dub 节奏）
  whistle_tone.wav     🎯 哨声（高频正弦+频率上扫+颤音）
  electronic_tone.wav  ⚡ 电子提示音（方波琶音+快衰减）
  bell_tower_tone.wav  🔔 钟声（大钟+非谐泛音+超长余音）
  bird_tone.wav        🐦 鸟鸣（高频颤音+啁啾）
  buzzer_tone.wav      📣 蜂鸣器（方波+断续节奏）
用法：python gen_more_tones.py
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


def env_adsr(n, attack=0.005, decay=0.05, sustain=0.7, release=0.1, sr=SR):
    """简单 ADSR 包络。"""
    buf = []
    a = int(attack * sr); d = int(decay * sr); r = int(release * sr)
    s_start = a + d
    s_end = n - r
    for i in range(n):
        if i < a:
            e = i / max(a, 1)
        elif i < s_start:
            e = 1.0 - (1.0 - sustain) * (i - a) / max(d, 1)
        elif i < s_end:
            e = sustain
        else:
            e = sustain * max(0, 1.0 - (i - s_end) / max(r, 1))
        buf.append(e)
    return buf


def gen_horn():
    """🎺 喇叭号角：方波+锯齿混合，三音上升琶音（C-E-G），嘹亮。"""
    def note(freq, dur):
        n = int(SR * dur)
        buf = []
        env = env_adsr(n, attack=0.02, decay=0.08, sustain=0.75, release=0.12)
        for i in range(n):
            t = i / SR
            # 方波+锯齿混合，加一点低通感（通过泛音比例）
            s = 0.6 * math.copysign(1, math.sin(2*math.pi*freq*t)) \
                + 0.4 * (2 * ((freq*t) % 1) - 1)
            # 加一点 2 次泛音提亮
            s += 0.15 * math.sin(2*math.pi*freq*2*t)
            buf.append(s * env[i])
        return buf
    loop = note(261.63, 0.22) + note(329.63, 0.22) + note(392.0, 0.45) + silence(0.25)
    write_wav("horn_tone.wav", normalize(loop, 0.75))


def gen_drum():
    """🥁 鼓点：低频正弦打击+噪声瞬态，2 连击（咚-咚）。"""
    def kick(dur=0.18, freq_start=150, freq_end=50):
        n = int(SR * dur)
        buf = []
        rnd = random.Random(3)
        for i in range(n):
            t = i / SR
            # 频率从高到低快速下降（鼓的特征）
            freq = freq_start + (freq_end - freq_start) * (t / dur)
            body = math.sin(2*math.pi*freq*t) * math.exp(-t / 0.05)
            # 敲击噪声瞬态
            click = (rnd.random()*2-1) * math.exp(-t / 0.004) * 0.3 if t < 0.015 else 0.0
            buf.append((body + click) * 0.9)
        return buf
    loop = kick(0.20, 160, 55) + silence(0.12) + kick(0.18, 140, 50) + silence(0.40)
    write_wav("drum_tone.wav", normalize(loop, 0.85))


def gen_gong():
    """🔔 锣声：低频金属（基频 80Hz）+ 非谐泛音列 + 悠长衰减。"""
    n = int(SR * 2.2)
    buf = []
    partials = [(1.0, 1.0), (1.45, 0.5), (2.1, 0.35), (2.9, 0.2), (4.0, 0.12), (5.5, 0.08)]
    base = 85.0
    for i in range(n):
        t = i / SR
        env = math.exp(-t / 0.9) * min(1.0, i / (0.005*SR))
        s = sum(p * math.sin(2*math.pi*base*m*t) for m, p in partials)
        buf.append(s * env * 0.7)
    # 首尾淡入淡出
    fade = int(0.05*SR)
    for i in range(fade):
        buf[i] *= i/fade
        buf[-1-i] *= i/fade
    write_wav("gong_tone.wav", normalize(buf, 0.8))


def gen_piano():
    """🎹 钢琴音：三角波+谐波，C大调和弦（C-E-G-C），自然衰减。"""
    def pnote(freq, dur, amp=0.6):
        n = int(SR * dur)
        buf = []
        for i in range(n):
            t = i / SR
            env = math.exp(-t / 0.6) * min(1.0, i / (0.003*SR))
            # 三角波近似 + 2/3 次泛音
            s = 0.7 * (2/math.pi) * math.asin(math.sin(2*math.pi*freq*t)) \
                + 0.2 * math.sin(2*math.pi*freq*2*t) \
                + 0.1 * math.sin(2*math.pi*freq*3*t)
            buf.append(s * env * amp)
        return buf
    # C4 E4 G4 C5 和弦，同时发声
    dur = 1.6
    n = int(SR * dur)
    buf = [0.0] * n
    for f, a in [(261.63, 0.5), (329.63, 0.45), (392.0, 0.4), (523.25, 0.3)]:
        note = pnote(f, dur, a)
        for i in range(n):
            buf[i] += note[i]
    buf += silence(0.30)
    write_wav("piano_tone.wav", normalize(buf, 0.78))


def gen_heartbeat():
    """💓 心跳：lub-dub 双脉冲，低频正弦+快速衰减。"""
    def beat(dur=0.14, freq=60, amp=0.9):
        n = int(SR * dur)
        buf = []
        for i in range(n):
            t = i / SR
            s = math.sin(2*math.pi*freq*t) * math.exp(-t / 0.04)
            buf.append(s * amp)
        return buf
    # lub (强) - 短间隙 - dub (弱) - 长间隙
    loop = beat(0.16, 55, 1.0) + silence(0.10) + beat(0.12, 65, 0.7) + silence(0.55)
    write_wav("heartbeat_tone.wav", normalize(loop, 0.85))


def gen_whistle():
    """🎯 哨声：高频正弦+频率上扫（1500→3000Hz）+ 轻微颤音。"""
    n = int(SR * 0.6)
    buf = []
    f0, f1 = 1500.0, 3000.0
    vibrato = 8.0  # 颤音频率
    vibrato_depth = 15.0
    for i in range(n):
        t = i / SR
        # 频率上扫
        freq = f0 + (f1 - f0) * (t / (n/SR))
        # 颤音
        freq += vibrato_depth * math.sin(2*math.pi*vibrato*t)
        env = min(1.0, i/(0.02*SR)) * min(1.0, (n-i)/(0.05*SR))
        s = math.sin(2*math.pi*freq*t)
        buf.append(s * env * 0.6)
    loop = buf + silence(0.35)
    write_wav("whistle_tone.wav", normalize(loop, 0.75))


def gen_electronic():
    """⚡ 电子提示音：方波琶音（C5-E5-G5-C6），快起快落，科技感。"""
    def enote(freq, dur=0.09):
        n = int(SR * dur)
        buf = []
        for i in range(n):
            t = i / SR
            env = min(1.0, i/(0.003*SR)) * math.exp(-t / 0.06)
            s = math.copysign(1, math.sin(2*math.pi*freq*t)) * 0.7 \
                + 0.3 * math.sin(2*math.pi*freq*2*t)
            buf.append(s * env)
        return buf
    loop = enote(523.25) + enote(659.25) + enote(783.99) + enote(1046.5) + silence(0.30)
    write_wav("electronic_tone.wav", normalize(loop, 0.72))


def gen_bell_tower():
    """🔔 钟声：大钟（基频 220Hz A3）+ 非谐泛音 + 超长余音（3秒）。"""
    n = int(SR * 3.0)
    buf = []
    # 钟声非谐泛音列
    partials = [(1.0, 1.0), (2.0, 0.55), (2.4, 0.4), (3.0, 0.28), (4.2, 0.18), (5.4, 0.1)]
    base = 220.0
    for i in range(n):
        t = i / SR
        env = math.exp(-t / 1.4) * min(1.0, i / (0.004*SR))
        s = sum(p * math.sin(2*math.pi*base*m*t) for m, p in partials)
        buf.append(s * env * 0.65)
    fade = int(0.08*SR)
    for i in range(fade):
        buf[i] *= i/fade
    write_wav("bell_tower_tone.wav", normalize(buf, 0.8))


def gen_bird():
    """🐦 鸟鸣：高频颤音+啁啾（频率快速上下波动），2 声。"""
    def chirp(dur=0.18, f_center=2800, f_swing=600):
        n = int(SR * dur)
        buf = []
        for i in range(n):
            t = i / SR
            # 频率快速波动（啁啾）
            freq = f_center + f_swing * math.sin(2*math.pi*12*t)
            # 颤音
            freq += 40 * math.sin(2*math.pi*25*t)
            env = min(1.0, i/(0.01*SR)) * min(1.0, (n-i)/(0.02*SR))
            s = math.sin(2*math.pi*freq*t)
            buf.append(s * env * 0.5)
        return buf
    loop = chirp(0.20, 2800, 700) + silence(0.08) + chirp(0.15, 3200, 500) + silence(0.40)
    write_wav("bird_tone.wav", normalize(loop, 0.7))


def gen_buzzer():
    """📣 蜂鸣器：方波（800Hz）+ 断续节奏（哔-哔-哔），刺耳。"""
    def beep(dur=0.12, freq=800):
        n = int(SR * dur)
        buf = []
        for i in range(n):
            t = i / SR
            env = min(1.0, i/(0.003*SR)) * min(1.0, (n-i)/(0.005*SR))
            s = math.copysign(1, math.sin(2*math.pi*freq*t))
            buf.append(s * env * 0.55)
        return buf
    loop = beep(0.12, 800) + silence(0.06) + beep(0.12, 800) + silence(0.06) + beep(0.12, 800) + silence(0.35)
    write_wav("buzzer_tone.wav", normalize(loop, 0.72))


if __name__ == "__main__":
    gen_horn()
    gen_drum()
    gen_gong()
    gen_piano()
    gen_heartbeat()
    gen_whistle()
    gen_electronic()
    gen_bell_tower()
    gen_bird()
    gen_buzzer()
    print("\n✅ 10 个新铃声生成完毕")
