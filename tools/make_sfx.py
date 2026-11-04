"""わちゃパの効果音をプログラムで合成する（録音・素材サイトの音は使っていないので、権利の心配がない）。

できるもの: assets/sfx/<名前>.wav（元）→ Android 用 app/src/main/res/raw/sfx_<名前>.ogg、ウェブ用 pc/src/sfx/<名前>.mp3
使い方: python3 tools/make_sfx.py   （ffmpeg が必要）
"""
import os, subprocess, wave
import numpy as np
from scipy import signal

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'raw')
WEB = os.path.join(ROOT, 'pc', 'src', 'sfx')
WAV = os.path.join(ROOT, 'tools', 'sfx_wav')
rng = np.random.default_rng(7)


def t(sec):
    return np.arange(int(SR * sec)) / SR


def env(n, a=0.005, d=0.1, curve=4.0):
    """立ち上がり a 秒、そのあと指数で消える"""
    x = np.arange(n) / SR
    e = np.minimum(1.0, x / max(a, 1e-4))
    e *= np.exp(-np.maximum(0, x - a) * curve / max(d, 1e-4))
    return e


def adsr(n, a, d, s, r):
    x = np.arange(n) / SR; total = n / SR
    e = np.where(x < a, x / a, np.where(x < a + d, 1 - (1 - s) * (x - a) / d, s))
    rel = np.clip((total - x) / r, 0, 1)
    return e * rel


def osc(freq, sec, kind='sine', phase=0.0):
    """freq は数か、長さのそろった配列（音程が動く音）"""
    n = int(SR * sec)
    f = np.full(n, freq, dtype=float) if np.isscalar(freq) else np.asarray(freq, dtype=float)[:n]
    ph = 2 * np.pi * np.cumsum(f) / SR + phase
    if kind == 'sine': return np.sin(ph)
    if kind == 'tri': return 2 / np.pi * np.arcsin(np.sin(ph))
    if kind == 'square': return np.tanh(3 * np.sin(ph))
    if kind == 'saw': return 2 * ((ph / (2 * np.pi)) % 1.0) - 1
    raise ValueError(kind)


def glide(f0, f1, sec, shape=1.0):
    x = np.linspace(0, 1, int(SR * sec)) ** shape
    return f0 * (f1 / f0) ** x


def noise(sec):
    return rng.uniform(-1, 1, int(SR * sec))


def bp(x, lo, hi, order=2):
    b, a = signal.butter(order, [lo / (SR / 2), hi / (SR / 2)], 'band'); return signal.lfilter(b, a, x)


def lp(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), 'low'); return signal.lfilter(b, a, x)


def hp(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), 'high'); return signal.lfilter(b, a, x)


def sweep_bp(x, f0, f1, width=0.6, steps=24):
    """帯域を動かしながら通す（シュッという音）"""
    out = np.zeros_like(x); n = len(x); seg = n // steps + 1
    win = np.hanning(seg * 2)
    for i in range(steps):
        fc = f0 * (f1 / f0) ** (i / max(1, steps - 1))
        y = bp(x, fc * (1 - width / 2), min(fc * (1 + width / 2), SR / 2 - 100))
        s = max(0, i * seg - seg // 2); e = min(n, s + seg * 2)
        out[s:e] += y[s:e] * win[:e - s]
    return out


def mix(*parts):
    """(信号, 開始秒) を重ねる"""
    n = max(int(st * SR) + len(sig) for sig, st in parts)
    out = np.zeros(n)
    for sig, st in parts:
        s = int(st * SR); out[s:s + len(sig)] += sig
    return out


def reverb(x, wet=0.18, room=0.35):
    """小さな部屋の響き（くし形フィルターを4本）"""
    out = np.concatenate([x, np.zeros(int(SR * room * 1.5))])
    acc = np.zeros_like(out)
    for dly, g in ((0.0297, 0.72), (0.0371, 0.7), (0.0411, 0.68), (0.0437, 0.66)):
        d = int(dly * SR); y = out.copy()
        for i in range(d, len(y)): y[i] += g * y[i - d]
        acc += y
    acc = lp(acc, 6000)
    return out * (1 - wet) + acc / 4 * wet


def bell(freq, sec, bright=1.0):
    """木琴・鈴のような音（倍音が早く消える）"""
    n = int(SR * sec)
    y = osc(freq, sec) * env(n, 0.002, sec * 0.5, 5)
    y += 0.5 * bright * osc(freq * 2.0, sec) * env(n, 0.001, sec * 0.25, 6)
    y += 0.25 * bright * osc(freq * 3.98, sec) * env(n, 0.001, sec * 0.12, 7)
    return y


def marimba(freq, sec):
    n = int(SR * sec)
    y = osc(freq, sec) * env(n, 0.002, sec * 0.6, 6)
    y += 0.35 * osc(freq * 3.93, sec) * env(n, 0.001, 0.05, 6)
    y += 0.15 * osc(freq * 9.2, sec) * env(n, 0.0005, 0.015, 6)
    return y


def note(name):
    names = {'C': -9, 'D': -7, 'E': -5, 'F': -4, 'G': -2, 'A': 0, 'B': 2}
    base = names[name[0]]; i = 1
    if name[i] == '#': base += 1; i += 1
    elif name[i] == 'b': base -= 1; i += 1
    octave = int(name[i:])
    return 440.0 * 2 ** ((base + (octave - 4) * 12) / 12)


def fade_tail(x, sec=0.01):
    n = min(len(x), int(SR * sec)); x = x.copy(); x[-n:] *= np.linspace(1, 0, n); return x


def norm(x, peak=0.89):
    x = x - np.mean(x)
    m = np.max(np.abs(x)) or 1.0
    # 最後の、ほとんど聞こえない部分を切る
    loud = np.nonzero(np.abs(x) > m * 0.004)[0]
    if len(loud): x = x[:min(len(x), loud[-1] + int(SR * 0.02))]
    return fade_tail(x / m * peak)


# ================= 音づくり =================

def tap():
    s = 0.09; n = int(SR * s)
    y = osc(glide(950, 520, s, 0.6), s) * env(n, 0.001, 0.045, 5)
    y += 0.25 * hp(noise(s), 3000) * env(n, 0.0005, 0.006, 6)
    return y


def card_play():
    sw = sweep_bp(noise(0.16), 1500, 6500, 0.8) * adsr(int(SR * 0.16), 0.03, 0.06, 0.6, 0.06)
    thock = mix((osc(glide(240, 120, 0.09), 0.09) * env(int(SR * 0.09), 0.001, 0.05, 5), 0),
                (0.3 * bp(noise(0.03), 800, 2500) * env(int(SR * 0.03), 0.0005, 0.01, 6), 0))
    return reverb(mix((sw * 0.7, 0), (thock, 0.13)), 0.12, 0.2)


def card_draw():
    s = 0.14
    return sweep_bp(noise(s), 2500, 5000, 0.7) * adsr(int(SR * s), 0.02, 0.05, 0.5, 0.07)


def your_turn():
    return reverb(mix((marimba(note('E5'), 0.5), 0), (marimba(note('A5'), 0.7), 0.11)), 0.2, 0.35)


def tick():
    s = 0.06; n = int(SR * s)
    return (osc(1150, s) + 0.5 * osc(2650, s)) * env(n, 0.0005, 0.025, 6)


def tick_last():
    s = 0.08; n = int(SR * s)
    return (osc(1600, s) + 0.5 * osc(3300, s)) * env(n, 0.0005, 0.035, 6)


def buzzer():
    s = 0.55; n = int(SR * s)
    y = osc(196, s, 'square') + 0.8 * osc(207.6, s, 'square')
    y = lp(y, 1800) * adsr(n, 0.01, 0.05, 0.85, 0.12)
    return y


def win():
    seq = [('C5', 0.0), ('E5', 0.09), ('G5', 0.18), ('C6', 0.27)]
    parts = []
    for nm, st in seq:
        f = note(nm); s = 0.22
        tone = (osc(f, s, 'tri') + 0.3 * osc(2 * f, s)) * adsr(int(SR * s), 0.005, 0.08, 0.5, 0.08)
        parts.append((tone * 0.6, st))
    s = 1.0; n = int(SR * s)
    vib = 1 + 0.004 * np.sin(2 * np.pi * 5.5 * t(s))
    chord = sum(osc(note(nm) * vib, s, 'tri') for nm in ('C5', 'E5', 'G5', 'C6')) * adsr(n, 0.01, 0.2, 0.6, 0.5)
    parts.append((chord * 0.35, 0.38))
    for i, nm in enumerate(('G6', 'C7', 'E7', 'G7')):
        parts.append((bell(note(nm), 0.4) * 0.2, 0.42 + i * 0.07))
    return reverb(mix(*parts), 0.22, 0.45)


def lose():
    parts = []
    for i, (nm, d) in enumerate((('G4', 0.26), ('F#4', 0.26), ('F4', 0.26), ('E4', 0.75))):
        f0 = note(nm); n = int(SR * d)
        wob = 1 + (0.012 * np.sin(2 * np.pi * 6 * t(d)) if i == 3 else 0)
        f = np.full(n, f0) * wob
        if i == 3: f = f * glide(1.0, 0.97, d)
        y = osc(f, d, 'saw') + 0.4 * osc(f * 0.5, d, 'saw')
        # ワウ（明るさを開いて閉じる）
        y = lp(y, 1400) * adsr(n, 0.02, 0.08, 0.75, 0.08 if i < 3 else 0.35)
        parts.append((y * 0.6, sum(x for _, x in (('G4', 0.26), ('F#4', 0.26), ('F4', 0.26))[:i])))
    return reverb(mix(*parts), 0.15, 0.3)


def reveal():
    s = 0.45
    sw = sweep_bp(noise(s), 400, 5000, 0.9) * adsr(int(SR * s), 0.3, 0.05, 0.6, 0.1) * 0.5
    ta = bell(note('C6'), 0.18) * 0.5
    da = (bell(note('G6'), 0.6) + bell(note('C6'), 0.6) * 0.7) * 0.6
    return reverb(mix((sw, 0), (ta, 0.42), (da, 0.55)), 0.25, 0.45)


def page():
    s = 0.28; n = int(SR * s)
    flutter = 0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 28 * t(s)))
    y = sweep_bp(noise(s), 1200, 4500, 0.9) * flutter * adsr(n, 0.04, 0.08, 0.6, 0.12)
    return y


def sent():
    return reverb(mix((bell(note('E6'), 0.5, 0.6), 0), (0.5 * bell(note('B6'), 0.4, 0.4), 0.0)), 0.2, 0.3)


def join():
    s = 0.14; n = int(SR * s)
    return osc(glide(380, 980, s, 0.5), s) * adsr(n, 0.004, 0.03, 0.6, 0.06)


def bomb():
    s = 1.4; n = int(SR * s)
    boom = lp(noise(s), 900, 4) * env(n, 0.004, 0.55, 4)
    crack = hp(noise(0.25), 1500) * env(int(SR * 0.25), 0.001, 0.08, 5) * 0.5
    sub = osc(glide(90, 38, 0.6), 0.6) * env(int(SR * 0.6), 0.003, 0.4, 4) * 1.3
    rumble = lp(noise(s), 180, 2) * env(n, 0.05, 1.0, 3) * 2.0
    return reverb(mix((boom, 0), (crack, 0), (sub, 0), (rumble, 0.05)), 0.2, 0.5)


def alert():
    parts = []
    for i in range(3):
        s = 0.07; n = int(SR * s)
        parts.append(((osc(1760, s, 'square') * 0.5 + osc(1760, s)) * adsr(n, 0.002, 0.01, 0.8, 0.02), i * 0.1))
    return mix(*parts)


def sync():
    parts = [(bell(note(nm), 0.5, 0.8) * 0.6, i * 0.06) for i, nm in enumerate(('C6', 'E6', 'G6', 'C7', 'E7'))]
    return reverb(mix(*parts), 0.3, 0.45)


def start():
    parts = []
    for nm, st, d in (('G4', 0, 0.12), ('G4', 0.14, 0.12), ('C5', 0.28, 0.5)):
        f = note(nm); n = int(SR * d)
        tone = (osc(f, d, 'square') * 0.35 + osc(f, d, 'tri')) * adsr(n, 0.005, 0.05, 0.6, 0.08 if d < 0.2 else 0.25)
        parts.append((lp(tone, 4000) * 0.7, st))
    parts.append((bell(note('C7'), 0.4) * 0.15, 0.3))
    return reverb(mix(*parts), 0.18, 0.35)


def vote():
    s = 0.1; n = int(SR * s)
    y = osc(glide(700, 600, s), s, 'tri') * env(n, 0.002, 0.05, 5)
    y += 0.4 * bp(noise(s), 1500, 4000) * env(n, 0.0005, 0.008, 6)
    return y


def out():
    """だれかが負けた（ボヨン）"""
    s = 0.45; n = int(SR * s)
    f = glide(420, 140, s, 0.7) * (1 + 0.05 * np.sin(2 * np.pi * 14 * t(s)))
    return reverb(osc(f, s, 'tri') * adsr(n, 0.005, 0.1, 0.7, 0.15), 0.12, 0.25)


SOUNDS = {
    'tap': tap, 'card_play': card_play, 'card_draw': card_draw, 'your_turn': your_turn, 'tick': tick, 'tick_last': tick_last,
    'buzzer': buzzer, 'win': win, 'lose': lose, 'reveal': reveal, 'page': page, 'sent': sent, 'join': join, 'bomb': bomb,
    'alert': alert, 'sync': sync, 'start': start, 'vote': vote, 'out': out,
}
# 小さく鳴らしたい音（ボタンなど、何度も鳴るもの）
GAIN = {'tap': 0.45, 'tick': 0.5, 'tick_last': 0.6, 'card_draw': 0.55, 'vote': 0.55, 'join': 0.6, 'page': 0.7}


def write_wav(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with wave.open(path, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())


if __name__ == '__main__':
    for d in (RAW, WEB, WAV): os.makedirs(d, exist_ok=True)
    total = 0
    for name, fn in SOUNDS.items():
        x = norm(fn(), 0.89 * GAIN.get(name, 1.0))
        wav = os.path.join(WAV, name + '.wav'); write_wav(wav, x)
        ogg = os.path.join(RAW, 'sfx_' + name + '.ogg'); mp3 = os.path.join(WEB, name + '.mp3')
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4', ogg], check=True)
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libmp3lame', '-b:a', '96k', mp3], check=True)
        total += os.path.getsize(ogg) + os.path.getsize(mp3)
        print(f'{name:10s} {len(x) / SR:.2f}s  ogg {os.path.getsize(ogg) // 1024}KB  mp3 {os.path.getsize(mp3) // 1024}KB')
    print('total', total // 1024, 'KB')
