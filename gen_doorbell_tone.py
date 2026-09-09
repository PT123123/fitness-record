# -*- coding: utf-8 -*-
"""生成内置门铃音（叮咚双音）res/raw/alarm_tone.wav。
经典门铃：先高后低两个钟声（A5 880Hz -> E5 659Hz），带铃音泛音与自然衰减，可循环播放。
用法：python gen_doorbell_tone.py
"""
import wave, math, struct

SR = 44100


def bell_partials(freq):
    # 钟声泛音列（基频 + 非整数倍分音），模拟真实门铃钟体
    return [(1.0, 1.0), (2.76, 0.38), (5.40, 0.18), (8.93, 0.07)]


def bell_note(freq, dur, amp, decay):
    n = int(SR * dur)
    buf = []
    psum = sum(p for _, p in bell_partials(freq))
    for i in range(n):
        t = i / SR
        env = math.exp(-t / decay)                 # 指数衰减
        att = min(1.0, i / (0.004 * SR))           # 4ms 起音防爆音
        s = sum(p * math.sin(2 * math.pi * freq * m * t) for m, p in bell_partials(freq))
        buf.append(s * env * att * amp / psum)
    return buf


def silence(dur):
    return [0.0] * int(SR * dur)


def main():
    ding = bell_note(880.0, 0.70, 0.72, 0.26)      # 叮：A5
    dong = bell_note(659.25, 1.00, 0.82, 0.38)     # 咚：E5（低四度，更悠长）
    loop = ding + silence(0.10) + dong + silence(0.55)  # 循环间隔，听感不突兀

    peak = max(abs(x) for x in loop) or 1.0
    scale = 0.82 * 32767 / peak
    pcm = b"".join(struct.pack("<h", int(x * scale)) for x in loop)

    out = r"app\src\main\res\raw\alarm_tone.wav"
    with wave.open(out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm)
    print(f"written {out}: {len(pcm)} bytes, {len(loop)/SR:.2f}s per loop")


if __name__ == "__main__":
    main()
