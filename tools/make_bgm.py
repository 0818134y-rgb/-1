"""わちゃパの場面別BGMを、楽譜（tools/bgm_scores.py）からプログラムで合成する。

作曲も音づくりもこのリポジトリの中だけで完結していて、録音・素材サイトの音・他社AIの曲は入っていない
（だから広告で収益化しても権利の心配がない）。
できるもの: Android 用 app/src/main/res/raw/bgm_<id>.ogg、ウェブ用 pc/src/bgm/<id>.mp3
曲の終わりの響き（リバーブ・ディレイ）は曲の頭に回してあるので、くり返し再生すると切れ目なくつながる。
使い方: python3 tools/make_bgm.py [id ...]   （ffmpeg が必要）
"""
import os, re, subprocess, sys, wave
import numpy as np
from scipy import signal

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bgm_scores import TRACKS  # noqa: E402

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'raw')
WEB = os.path.join(ROOT, 'pc', 'src', 'bgm')
WAV = os.path.join(ROOT, 'tools', 'bgm_wav')

NAMES = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
ACC = {'#': 1, 'b': -1, '': 0}


# ================= 楽譜を読む =================

def midi(name):
    m = re.fullmatch(r'([A-G])([#b]?)(-?\d)', name.strip())
    if not m: raise ValueError('音名がおかしい: ' + name)
    return NAMES[m.group(1)] + ACC[m.group(2)] + (int(m.group(3)) + 1) * 12


def hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def parse_line(s):
    """"E5:0.5 R:1 ..." → [(midi か None, 開始拍, 長さ拍)], 合計拍"""
    out = []; at = 0.0
    for tok in s.split():
        name, _, dur = tok.partition(':')
        d = float(dur or 1)
        out.append((None if name.upper() == 'R' else midi(name), at, d)); at += d
    return out, at


QUAL = {
    'maj7': [0, 4, 7, 11], 'm7b5': [0, 3, 6, 10], 'm7': [0, 3, 7, 10], 'm9': [0, 3, 7, 10, 14], 'madd9': [0, 3, 7, 14],
    'm6': [0, 3, 7, 9], 'm': [0, 3, 7], 'dim7': [0, 3, 6, 9], 'dim': [0, 3, 6], 'aug': [0, 4, 8],
    '7sus4': [0, 5, 7, 10], 'sus4': [0, 5, 7], 'sus2': [0, 2, 7], 'add9': [0, 4, 7, 14], 'maj9': [0, 4, 7, 11, 14],
    '9': [0, 4, 7, 10, 14], '7': [0, 4, 7, 10], '6': [0, 4, 7, 9], '': [0, 4, 7],
}


def pc(s):
    m = re.match(r'([A-G])([#b]?)', s)
    return (NAMES[m.group(1)] + ACC[m.group(2)]) % 12, len(m.group(0))


def chord(sym):
    """"Am7" "D/F#" → (根音 0-11, 構成音のずれ, ベース音 0-11)"""
    main, _, slash = sym.strip().partition('/')
    root, k = pc(main); rest = main[k:]
    ivs = None
    for q in sorted(QUAL, key=len, reverse=True):
        if rest == q: ivs = QUAL[q]; break
    if ivs is None: raise ValueError('コード名がおかしい: ' + sym)
    bass = pc(slash)[0] if slash else root
    return root, ivs, bass


SCALES = {'major': [0, 2, 4, 5, 7, 9, 11], 'minor': [0, 2, 3, 5, 7, 8, 10], 'dorian': [0, 2, 3, 5, 7, 9, 10]}


def third_below(m, key, mode):
    """調の中で3度下の音（ハモり）"""
    sc = [(key + s) % 12 for s in SCALES[mode]]
    if m % 12 not in sc: return m - 3
    cand = [x for x in range(m - 5, m - 2) if x % 12 in sc]
    # 音階を2つ下がった音
    below = [x for x in range(m - 1, m - 6, -1) if x % 12 in sc]
    return below[1] if len(below) > 1 else (cand[0] if cand else m - 3)


# ================= 音のもと =================

rng = np.random.default_rng(11)


def ph_of(f, n):
    f = np.full(n, float(f)) if np.isscalar(f) else f
    return 2 * np.pi * np.cumsum(f) / SR


def env_ahr(n, a, hold, r, s=1.0, d=0.1):
    """立ち上がり a → (d 秒で s まで下がる) → hold 秒で離して r 秒で消える"""
    x = np.arange(n) / SR
    att = np.clip(x / max(a, 1e-4), 0, 1)
    dec = s + (1 - s) * np.exp(-np.maximum(0, x - a) / max(d, 1e-4))
    rel = np.clip(1 - (x - hold) / max(r, 1e-4), 0, 1)
    return att * dec * np.where(x > hold, rel, 1.0)


def decay(n, a, tau):
    x = np.arange(n) / SR
    return np.clip(x / max(a, 1e-4), 0, 1) * np.exp(-x / tau)


def lp(x, f, order=2):
    b, a = signal.butter(order, min(f, SR / 2 - 200) / (SR / 2), 'low'); return signal.lfilter(b, a, x)


def hp(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), 'high'); return signal.lfilter(b, a, x)


def bp(x, lo, hi, order=2):
    b, a = signal.butter(order, [lo / (SR / 2), min(hi, SR / 2 - 200) / (SR / 2)], 'band'); return signal.lfilter(b, a, x)


def vib(n, f, depth=0.004, rate=5.2, delay=0.22):
    x = np.arange(n) / SR
    return f * (1 + depth * np.sin(2 * np.pi * rate * x) * np.clip((x - delay) / 0.3, 0, 1))


def saw(ph):
    return 2 * ((ph / (2 * np.pi)) % 1.0) - 1


def pulse(ph, width=0.25):
    return np.where((ph / (2 * np.pi)) % 1.0 < width, 1.0, -1.0)


def filt_env(y, f_lo, f_hi, tau, n):
    """フィルターが開いてから閉じる（明るい立ち上がり→落ち着いた音）を、2本の低域通過の混ぜ合わせで近似"""
    lo = lp(y, f_lo); hi = lp(y, f_hi)
    e = np.exp(-np.arange(n) / SR / tau)
    return lo + (hi - lo) * e


def inst(kind, f, dur, vel=1.0):
    """1音つくる（モノラル）。dur は押さえている秒数。そのあとの響きも含めて返す"""
    if kind == 'square':  # 明るいチップ系リード
        n = int(SR * (dur + 0.12)); fv = vib(n, f, 0.0035)
        y = 0.6 * pulse(ph_of(fv, n), 0.25) + 0.4 * pulse(ph_of(fv * 1.003, n) + 1.0, 0.5)
        y = filt_env(y, min(2600, f * 4), min(9000, f * 14), 0.09, n)
        y *= env_ahr(n, 0.003, dur, 0.09, 0.7, 0.12) * 0.26
    elif kind == 'triangle':
        n = int(SR * (dur + 0.1)); ph = ph_of(vib(n, f, 0.003), n)
        y = 2 / np.pi * np.arcsin(np.sin(ph)) + 0.08 * np.sin(2 * ph)
        y *= env_ahr(n, 0.005, dur, 0.08, 0.8, 0.1) * 0.55
    elif kind == 'brass':  # 金管アンサンブル風（のこぎり波2本＋フィルターの開き）
        n = int(SR * (dur + 0.18)); fv = vib(n, f, 0.004, 5.4, 0.25)
        y = saw(ph_of(fv, n)) + saw(ph_of(fv * 1.0045, n) + 2.0) + 0.5 * saw(ph_of(fv * 0.997, n) + 4.0)
        fe = np.clip(np.arange(n) / SR / 0.06, 0, 1)  # ふわっと開く
        lo = lp(y, min(900, f * 2)); hi = lp(y, min(7000, f * 8))
        y = lo + (hi - lo) * fe * (0.6 + 0.4 * np.exp(-np.arange(n) / SR / 0.25))
        y *= env_ahr(n, 0.025, dur, 0.12, 0.85, 0.2) * 0.13
    elif kind == 'stacc':  # 弦のスタッカート
        n = int(SR * (dur + 0.12)); ph = ph_of(f, n)
        y = saw(ph) + saw(ph_of(f * 1.006, n) + 1) + saw(ph_of(f * 0.995, n) + 2)
        y = filt_env(y, min(1400, f * 3), min(6500, f * 9), 0.05, n)
        y *= env_ahr(n, 0.004, max(0.04, dur * 0.5), 0.09, 0.4, 0.06) * 0.16
    elif kind == 'flute':
        n = int(SR * (dur + 0.14)); fv = vib(n, f, 0.0055, 5.0, 0.15); ph = ph_of(fv, n)
        y = np.sin(ph) + 0.15 * np.sin(2 * ph) + 0.05 * np.sin(3 * ph)
        br = bp(rng.uniform(-1, 1, n), f * 1.2, min(12000, f * 6)) * (0.08 + 0.25 * np.exp(-np.arange(n) / SR / 0.05))
        y = (y + br) * env_ahr(n, 0.045, dur, 0.12, 0.85, 0.15) * 0.42
    elif kind == 'marimba':
        n = int(SR * max(0.45, min(dur + 0.35, 1.1))); ph = ph_of(f, n)
        y = np.sin(ph) * decay(n, 0.0015, 0.32) + 0.32 * np.sin(ph * 3.93) * decay(n, 0.001, 0.045)
        y += 0.09 * np.sin(ph * 9.2) * decay(n, 0.0005, 0.012)
        y += 0.05 * lp(rng.uniform(-1, 1, n), 3000) * decay(n, 0.0005, 0.004)  # ばちの当たる音
        y *= 0.62
    elif kind == 'bell':  # グロッケン
        n = int(SR * max(0.9, min(dur + 1.0, 2.2))); ph = ph_of(f, n)
        y = np.sin(ph) * decay(n, 0.0015, 0.7) + 0.4 * np.sin(2 * ph) * decay(n, 0.001, 0.35)
        y += 0.22 * np.sin(ph * 3.0) * decay(n, 0.001, 0.18) + 0.14 * np.sin(ph * 5.43) * decay(n, 0.0008, 0.07)
        y += 0.06 * np.sin(ph * 8.1) * decay(n, 0.0005, 0.03)
        y *= 0.36
    elif kind == 'pluck':  # 弦をはじく（カープラス・ストロング）
        n = int(SR * max(0.45, min(dur + 0.4, 1.6))); p = max(2, int(round(SR / f)))
        buf = lp(rng.uniform(-1, 1, p * 2), min(9000, f * 14), 1)[-p:]
        a = np.zeros(p + 2); a[0] = 1; a[p] = -0.4985; a[p + 1] = -0.4985
        y = signal.lfilter([1.0], a, np.concatenate([buf, np.zeros(n - p)]))
        y = y / (np.max(np.abs(y)) or 1) * decay(n, 0.001, 0.55 + 0.15 * min(dur, 1))
        y = hp(y, 90) * 0.5
    elif kind == 'epiano':  # FM のエレピ
        n = int(SR * max(0.7, min(dur + 0.7, 2.6))); ph = ph_of(f, n)
        idx = 1.6 * decay(n, 0.001, 0.22) + 0.25
        y = np.sin(ph + idx * np.sin(ph)) * decay(n, 0.003, 1.1)
        y += 0.18 * np.sin(ph * 14.0) * decay(n, 0.0005, 0.02)  # 打鍵のキラッ
        y *= env_ahr(n, 0.001, dur + 0.2, 0.25) * 0.42
    elif kind == 'organ':
        n = int(SR * (dur + 0.08)); ph = ph_of(vib(n, f, 0.002, 6.5, 0.0), n)
        y = np.sin(ph) + 0.6 * np.sin(2 * ph) + 0.3 * np.sin(3 * ph) + 0.2 * np.sin(4 * ph) + 0.1 * np.sin(6 * ph)
        y *= env_ahr(n, 0.008, dur, 0.07) * 0.16
    elif kind == 'pad':  # ストリングスパッド（のこぎり波5本をずらして重ねる）
        n = int(SR * (dur + 0.7)); y = np.zeros(n)
        for det in (-0.009, -0.004, 0.0, 0.0045, 0.0085):
            y += saw(ph_of(vib(n, f * (1 + det), 0.002, 4.6 + det * 100, 0.0), n) + rng.uniform(0, 6.28))
        y = lp(y, min(2800, f * 5.5), 2) * env_ahr(n, 0.4, dur, 0.6, 0.85, 0.5) * 0.06
    elif kind == 'warm':  # やわらかいパッド（三角波系）
        n = int(SR * (dur + 0.8)); y = np.zeros(n)
        for det in (-0.004, 0.0, 0.0042):
            ph = ph_of(f * (1 + det), n) + rng.uniform(0, 6.28)
            y += 2 / np.pi * np.arcsin(np.sin(ph)) + 0.15 * saw(ph)
        y = lp(y, min(2000, f * 4)) * env_ahr(n, 0.6, dur, 0.7, 0.9, 0.5) * 0.14
    elif kind == 'bass':  # はずむシンセベース
        n = int(SR * (dur + 0.06)); ph = ph_of(f, n)
        y = np.sin(ph) + 0.5 * saw(ph_of(f, n) + 0.3) * 0.6
        y = filt_env(y, 380, 2200, 0.05, n) * env_ahr(n, 0.003, dur, 0.05, 0.6, 0.15) * 0.55
    elif kind == 'sub':  # 丸いベース
        n = int(SR * (dur + 0.08)); ph = ph_of(f, n)
        y = np.sin(ph) + 0.18 * np.sin(2 * ph) + 0.05 * np.sin(3 * ph)
        y *= env_ahr(n, 0.008, dur, 0.07, 0.75, 0.25) * 0.62
    elif kind == 'finger':  # 指弾きベース
        n = int(SR * (dur + 0.08)); ph = ph_of(f, n)
        y = np.sin(ph) + 0.3 * np.sin(2 * ph) + 0.12 * np.sin(3 * ph)
        y += 0.15 * lp(rng.uniform(-1, 1, n), 1500) * decay(n, 0.0005, 0.008)
        y *= env_ahr(n, 0.004, dur, 0.06, 0.55, 0.3) * 0.6
    else:
        raise ValueError(kind)
    return y * vel * CAL.get(kind, 1.0)


# 楽器ごとの音量合わせ（同じ高さ・長さで鳴らしたときの大きさをそろえる）
CAL = {'square': 1.1, 'triangle': 0.66, 'brass': 2.5, 'stacc': 2.2, 'flute': 0.68, 'marimba': 0.82, 'bell': 0.9,
       'pluck': 2.3, 'epiano': 0.9, 'organ': 1.0, 'pad': 1.0, 'warm': 0.8, 'bass': 0.6, 'sub': 0.42, 'finger': 0.5}


# ---- 打楽器 ----

def kick(v=1.0):  # noqa
    n = int(SR * 0.35); x = np.arange(n) / SR
    f = 46 + 120 * np.exp(-x * 30)
    y = np.sin(ph_of(f, n)) * decay(n, 0.0008, 0.16)
    y += 0.25 * hp(rng.uniform(-1, 1, n), 2500) * decay(n, 0.0003, 0.004)
    return np.tanh(y * 1.4) * 0.6 * v


def snare(v=1.0):
    n = int(SR * 0.25)
    y = 0.6 * bp(rng.uniform(-1, 1, n), 1800, 10000) * decay(n, 0.0008, 0.07)
    y += 0.45 * np.sin(ph_of(200 * (1 + 0.3 * np.exp(-np.arange(n) / SR * 60)), n)) * decay(n, 0.0008, 0.045)
    return y * 0.62 * v


def clap(v=1.0):
    n = int(SR * 0.3); nz = bp(rng.uniform(-1, 1, n), 1000, 6000)
    e = np.zeros(n)
    for k, off in enumerate((0, 0.010, 0.020, 0.029)):
        s = int(off * SR); e[s:] += decay(n - s, 0.0004, 0.01 if k < 3 else 0.1)
    return nz * e * 0.42 * v


def hat(v=1.0):
    n = int(SR * 0.07)
    y = hp(rng.uniform(-1, 1, n), 7500) * decay(n, 0.0004, 0.016)
    return y * 0.4 * v


def ohat(v=1.0):
    n = int(SR * 0.35)
    y = hp(rng.uniform(-1, 1, n), 7000) * decay(n, 0.0005, 0.12)
    return y * 0.22 * v


def shaker(v=1.0):
    n = int(SR * 0.09)
    y = bp(rng.uniform(-1, 1, n), 4500, 13000) * env_ahr(n, 0.025, 0.03, 0.04)
    return y * 0.22 * v


def rim(v=1.0):
    n = int(SR * 0.06); ph = ph_of(1750, n)
    y = np.sin(ph) * decay(n, 0.0004, 0.01) + 0.4 * bp(rng.uniform(-1, 1, n), 2500, 7000) * decay(n, 0.0004, 0.008)
    return y * 0.3 * v


def crash(v=1.0):
    n = int(SR * 2.2)
    y = hp(rng.uniform(-1, 1, n), 4500) * decay(n, 0.002, 0.7)
    y += 0.3 * bp(rng.uniform(-1, 1, n), 3000, 8000) * decay(n, 0.002, 0.25)
    return y * 0.14 * v


def tom(v=1.0, f=140):
    n = int(SR * 0.4); x = np.arange(n) / SR
    y = np.sin(ph_of(f * (1 + 0.5 * np.exp(-x * 25)), n)) * decay(n, 0.001, 0.15)
    return y * 0.55 * v


def tick_wood(v=1.0):  # 時計のチクタク
    n = int(SR * 0.05); ph = ph_of(2600, n)
    return (np.sin(ph) * decay(n, 0.0003, 0.006)) * 0.22 * v


DRUM_FN = {'k': kick, 's': snare, 'c': clap, 'h': hat, 'o': ohat, 'z': shaker, 'r': rim, 't': tick_wood}

# 1小節 = 16分音符×16。'x' 強い '-' 弱い '.' なし
GROOVES = {
    'four': {'k': 'x...x...x...x...', 'c': '....x.......x...', 'h': '..x...x...x...x.', 'z': 'x-x-x-x-x-x-x-x-'},
    'pop': {'k': 'x.....x...x.....', 's': '....x.......x...', 'h': 'x-x-x-x-x-x-x-x-'},
    'drive': {'k': 'x.....x.x.....x.', 's': '....x.......x...', 'h': 'x-x-x-x-x-x-x-x-', 'o': '..............x.'},
    'halftime': {'k': 'x.........x.....', 's': '........x.......', 'h': 'x...x-..x...x-..'},
    'shuffle': {'k': 'x.....x...x.....', 's': '....x.......x...', 'h': 'x..-x..-x..-x..-'},
    'lofi': {'k': 'x......x..x.....', 's': '....x.......x...', 'h': 'x.-.x.-.x.-.x.-.'},
    'soft': {'k': 'x.......x.......', 'r': '....x.......x...', 'z': '..-...-...-...-.'},
    'clock': {'t': 'x...-...x...-...'},
    'tension': {'k': 'x..x..x.x..x..x.', 'h': 'x-x-x-x-x-x-x-x-', 's': '............x...'},
    'build': {'k': 'x...x...x...x...', 's': '..............xx', 'h': 'x-x-x-x-x-x-x-x-'},
    'none': {},
}


# ================= 置く・まとめる =================

class Bus:
    def __init__(self, n): self.x = np.zeros((2, n))

    def put(self, sig, t_sec, pan=0.0, gain=1.0):
        """t_sec の位置に置く。はみ出した分は頭に回す（切れ目のないループのため）"""
        n = self.x.shape[1]; s = int(round(t_sec * SR)) % n
        l = gain * np.cos((pan + 1) * np.pi / 4) * 1.4142; r = gain * np.sin((pan + 1) * np.pi / 4) * 1.4142
        k = len(sig); pos = 0
        while pos < k:
            take = min(k - pos, n - s)
            self.x[0, s:s + take] += sig[pos:pos + take] * l; self.x[1, s:s + take] += sig[pos:pos + take] * r
            pos += take; s = 0


def circ(fn, x):
    """フィルターを輪として通す（2周流して後半を使う）＝ループの継ぎ目に段差ができない"""
    n = x.shape[-1]
    return np.stack([fn(np.concatenate([ch, ch]))[n:] for ch in x]) if x.ndim == 2 else fn(np.concatenate([x, x]))[n:]


def pingpong(x, d_sec, fb=0.35, taps=5, tone=3500):
    """左右に跳ねるディレイ（輪なので np.roll でずらせば切れ目なし）"""
    d = int(d_sec * SR); out = np.zeros_like(x); mono = (x[0] + x[1]) / 2
    src = circ(lambda v: lp(hp(v, 300), tone), mono)
    for k in range(1, taps + 1):
        out[(k + 1) % 2] += np.roll(src, d * k) * fb ** k
    return out


def reverb(x, size=1.0, damp=5000, pre=0.012):
    """フリーバーブ風の響き。2周ぶん流して後半を使う＝ループの頭にも前の周の響きが乗る"""
    n = x.shape[1]; out = np.zeros_like(x)
    combs = (0.0253, 0.0269, 0.0290, 0.0307, 0.0322, 0.0338)
    for ch in range(2):
        src = np.concatenate([x[ch], x[ch]])
        src = np.concatenate([np.zeros(int(pre * SR)), src])[:len(src)]
        acc = np.zeros_like(src)
        for i, dly in enumerate(combs):
            d = int(dly * size * SR) + ch * 23
            g = 0.84 * min(1.0, 0.92 + 0.08 * size)
            a = np.zeros(d + 1); a[0] = 1; a[d] = -g
            acc += signal.lfilter([1.0], a, lp(src, damp, 1))
        for dly in (0.0051, 0.0126, 0.0100 + ch * 0.0007):
            d = int(dly * SR); g = 0.6
            b = np.zeros(d + 1); b[0] = -g; b[d] = 1; a = np.zeros(d + 1); a[0] = 1; a[d] = -g
            acc = signal.lfilter(b, a, acc)
        out[ch] = hp(acc, 180)[n:] / len(combs)
    return out


def duck_env(n, kick_times, depth, rel=0.16):
    """キックに合わせて伴奏を少し下げる（ポップスらしいノリ）"""
    e = np.ones(n)
    if depth <= 0: return e
    L = int(rel * 4 * SR); shape = 1 - depth * np.exp(-np.arange(L) / SR / rel)
    for t in kick_times:
        s = int(round(t * SR)) % n
        seg = min(L, n - s)
        e[s:s + seg] = np.minimum(e[s:s + seg], shape[:seg])
        if seg < L: e[:L - seg] = np.minimum(e[:L - seg], shape[seg:])
    return e


def voicing(ivs, root, prev, lo=52, hi=70):
    """前の和音から一番近い並べ方を選ぶ（なめらかに聞こえる）"""
    pcs = sorted(set((root + iv) % 12 for iv in ivs[:4]))
    best = None
    for base in range(lo - 12, hi):
        notes = []
        for p in pcs:
            m = base + ((p - base) % 12)
            notes.append(m)
        notes.sort()
        if notes[0] < lo or notes[-1] > hi: continue
        cost = 0 if prev is None else sum(min(abs(a - b) for b in prev) for a in notes)
        if prev is None: cost = abs(np.mean(notes) - 60)
        if best is None or cost < best[0]: best = (cost, notes)
    return best[1] if best else [lo + (p - lo) % 12 for p in pcs]


# ================= 曲をつくる =================

def render(tr):
    bpm = tr['bpm']; beat = 60.0 / bpm
    secs = tr['sections']
    total_bars = sum(len(s['chords']) for s in secs)
    L = total_bars * 4 * beat; n = int(round(L * SR))
    lead = Bus(n); music = Bus(n); bassb = Bus(n); drums = Bus(n)
    kick_times = []
    key = tr.get('key', 0); mode = tr.get('mode', 'major')
    swing = tr.get('swing', 0.0)  # 0 = まっすぐ、0.17 くらいでハネる
    hum = np.random.default_rng(hash(tr['id']) % 2 ** 32)

    def tt(b):  # 拍 → 秒（8分の裏を少し遅らせてハネさせる）
        whole = np.floor(b + 1e-9); frac = b - whole
        if swing and abs(frac - 0.5) < 1e-6: frac = 0.5 + swing
        elif swing and abs(frac - 0.25) < 1e-6: frac = 0.25 + swing / 2
        elif swing and abs(frac - 0.75) < 1e-6: frac = 0.75 + swing / 2
        return (whole + frac) * beat

    bar0 = 0; prev_voicing = None
    for si, s in enumerate(secs):
        chords = [chord(c) for c in s['chords']]
        nb = len(chords); b0 = bar0 * 4  # 拍の位置
        # ---- メロディ ----
        for key_line, default_inst, gain, pan in (('melody', 'square', 0.85, 0.08), ('counter', 'triangle', 0.4, -0.3)):
            line = s.get(key_line, '')
            if not line: continue
            notes, got = parse_line(line)
            if abs(got - nb * 4) > 0.01: raise ValueError(f'{tr["id"]} {si}番目 {key_line}: {got}拍（{nb * 4}拍のはず）')
            kinds = s.get('lead' if key_line == 'melody' else 'counter_inst', default_inst)
            kinds = kinds if isinstance(kinds, (list, tuple)) else [kinds]
            g = gain * s.get('lead_gain' if key_line == 'melody' else 'counter_gain', 1.0)
            legato = s.get('legato', 0.92)
            for m, at, d in notes:
                if m is None: continue
                v = 0.92 + 0.08 * hum.random()
                for j, kind in enumerate(kinds):
                    oc = 0
                    if ':' in kind: kind, oc = kind.split(':'); oc = int(oc)
                    sig = inst(kind, hz(m + 12 * oc), d * beat * legato, v)
                    lead.put(sig, tt(b0 + at), pan * (1 if j == 0 else -1), g * (1.0 if j == 0 else s.get('double_gain', 0.45)))
                if key_line == 'melody' and s.get('harmony'):
                    hk = s['harmony']
                    sig = inst(hk, hz(third_below(m, key, mode)), d * beat * legato, v)
                    lead.put(sig, tt(b0 + at), -0.35, g * s.get('harmony_gain', 0.4))
        # ---- 和音・アルペジオ・ベース ----
        pad = s.get('pad', 'pad'); arp = s.get('arp'); bstyle = s.get('bass', 'root')
        for i, (root, ivs, bass) in enumerate(chords):
            t0 = (b0 + i * 4) * beat
            vo = voicing(ivs, root, prev_voicing); prev_voicing = vo
            if pad in ('pad', 'warm', 'organ'):
                for m in vo:
                    for side in (-0.55, 0.55):
                        music.put(inst(pad, hz(m), 4 * beat * 0.98), t0, side, s.get('pad_gain', 1.0) * 0.75)
            elif pad == 'epiano':
                hits = s.get('comp', [(0, 1.5), (1.5, 1.0), (2.5, 1.5)])
                for h, dur in hits:
                    for m in vo:
                        music.put(inst('epiano', hz(m), dur * beat, 0.8 + 0.2 * hum.random()), tt(b0 + i * 4 + h), 0.0, s.get('pad_gain', 1.0) * 0.32)
            elif pad == 'stabs':  # ブラスのジャン！
                for h, dur in s.get('comp', [(0, 0.5), (1.5, 0.5), (3, 0.5)]):
                    for m in vo:
                        music.put(inst('brass', hz(m + 12), dur * beat), tt(b0 + i * 4 + h), 0.0, s.get('pad_gain', 1.0) * 0.5)
            if arp:
                ak, pattern, rate = arp  # 楽器, 並び, 1拍に何音
                tones = vo + [vo[0] + 12, vo[1] + 12]
                seqs = {'up': [0, 1, 2, 3], 'updown': [0, 1, 2, 3, 2, 1], 'broken': [0, 2, 1, 3], 'pulse': [0, 2, 0, 3],
                        'wide': [0, 3, 1, 4, 2, 4]}
                seq = seqs[pattern]
                for k in range(int(4 * rate)):
                    m = tones[seq[k % len(seq)] % len(tones)] + 12 * s.get('arp_oct', 1)
                    v = (1.0 if k % rate == 0 else 0.72) * (0.9 + 0.1 * hum.random())
                    music.put(inst(ak, hz(m), beat / rate * 0.8, v), tt(b0 + i * 4 + k / rate), 0.3 * np.sin(k * 0.9), s.get('arp_gain', 1.0) * 0.35)
            # ベース
            nroot = 36 + bass
            while nroot > 45: nroot -= 12
            fifth = nroot + 7; octv = nroot + 12
            nxt = chords[(i + 1) % nb][2] if i + 1 < nb else chord(secs[(si + 1) % len(secs)]['chords'][0])[2]
            tgt = 36 + nxt
            while tgt > 45: tgt -= 12
            third = nroot + (ivs[1] if len(ivs) > 1 else 4)
            pats = {
                'bounce': [(0, nroot, 0.45), (0.75, nroot, 0.2), (1.5, octv, 0.35), (2, nroot, 0.45), (2.75, fifth, 0.2), (3.5, octv, 0.4)],
                'drive': [(k * 0.5, octv if k in (3, 7) else nroot, 0.4) for k in range(8)],
                'pulse': [(k * 0.5, nroot, 0.3) for k in range(8)],
                'walk': [(0, nroot, 0.9), (1, third, 0.9), (2, fifth, 0.9), (3, tgt + (1 if tgt < fifth else -1), 0.9)],
                'root': [(0, nroot, 2.6), (3, fifth, 0.9)],
                'long': [(0, nroot, 3.9)],
                'lofi': [(0, nroot, 1.4), (1.75, nroot, 0.4), (2.5, fifth, 1.2)],
                'none': [],
            }
            bk = s.get('bass_inst', 'bass')
            for at, m, d in pats[bstyle]:
                bassb.put(inst(bk, hz(m), d * beat, 0.9 + 0.1 * hum.random()), tt(b0 + i * 4 + at), 0.0, s.get('bass_gain', 1.0))
        # ---- ドラム ----
        groove = GROOVES[s.get('drums', 'none')]
        fill = s.get('fill', 'snare')
        for i in range(nb):
            last = (i == nb - 1); bt = b0 + i * 4
            for name, seq in groove.items():
                for k, ch in enumerate(seq):
                    if ch == '.': continue
                    if last and fill != 'none' and k >= 12 and name in ('s', 'h', 'c', 'o'): continue
                    v = (1.0 if ch == 'x' else 0.55) * (0.88 + 0.12 * hum.random())
                    t = tt(bt + k / 4)
                    drums.put(DRUM_FN[name](v), t, {'h': 0.3, 'o': 0.3, 'z': -0.3, 'r': 0.15, 't': 0.2}.get(name, 0.0), s.get('drum_gain', 1.0))
                    if name == 'k': kick_times.append(t)
            if last and fill != 'none' and groove:
                if fill == 'snare':
                    for k, v in zip((12, 13, 14, 15), (0.5, 0.6, 0.75, 0.9)):
                        drums.put(snare(v), tt(bt + k / 4), 0.0, s.get('drum_gain', 1.0) * 0.85)
                elif fill == 'tom':
                    for k, f in zip((12, 13, 14, 15), (220, 180, 150, 120)):
                        drums.put(tom(0.9, f), tt(bt + k / 4), (15 - k) * 0.2 - 0.3, s.get('drum_gain', 1.0))
                elif fill == 'build':  # 最後の1小節を16分のスネアで盛り上げる
                    for k in range(16):
                        drums.put(snare(0.25 + 0.045 * k), tt(bt + k / 4), 0.0, s.get('drum_gain', 1.0) * 0.8)
                elif fill == 'soft':
                    drums.put(snare(0.4), tt(bt + 3.5), 0.0, s.get('drum_gain', 1.0)); drums.put(snare(0.55), tt(bt + 3.75), 0.0, s.get('drum_gain', 1.0))
        if s.get('crash', True) and groove:
            drums.put(crash(1.0), tt(b0), 0.35, s.get('drum_gain', 1.0))
        bar0 += nb

    # ---- まとめ ----
    duck = duck_env(n, kick_times, tr.get('duck', 0.0))
    music.x *= duck; bassb.x *= np.sqrt(duck)
    bg = dict(lead=1.0, music=0.85, bass=0.8, drums=0.9); bg.update(tr.get('mix', {}))
    lead.x *= bg['lead']; music.x *= bg['music']; bassb.x *= bg['bass']; drums.x *= bg['drums']
    if os.environ.get('BGM_DEBUG'):
        db = lambda b: round(20 * np.log10(np.sqrt(np.mean(b.x ** 2)) + 1e-9), 1)
        print('   buses lead', db(lead), 'music', db(music), 'bass', db(bassb), 'drums', db(drums))
    rv = tr.get('reverb', 0.25); dl = tr.get('delay', 0.18)
    lead_fx = lead.x + pingpong(lead.x, beat * 0.75, 0.33, 4) * dl
    verb_in = lead_fx * 1.0 + music.x * 0.8 + drums.x * 0.12
    wet = reverb(verb_in, tr.get('room', 1.0))
    mix = lead_fx + music.x + bassb.x + drums.x + wet * rv * 2.2
    if tr.get('lofi'):
        mix = circ(lambda v: lp(v, 7500, 2), mix)
        crack = np.zeros(n); pos = rng.integers(0, n, int(L * 18))
        crack[pos] = rng.uniform(-1, 1, len(pos)) * rng.uniform(0.2, 1, len(pos))
        crack = circ(lambda v: lp(hp(v, 1500), 9000), crack) * 0.05 + lp(rng.uniform(-1, 1, n), 2500) * 0.004
        mix += np.stack([crack, np.roll(crack, 311)])
    # 仕上げ: 低すぎる音を切り、全体の大きさをそろえ、ピークをやわらかく抑える
    mix = circ(lambda v: hp(v, 32, 2), mix)
    air = tr.get('air', 0.0 if tr.get('lofi') else 0.35)   # 5kHz より上を少し持ち上げて、こもらないように
    if air: mix = mix + circ(lambda v: hp(v, 5000, 2), mix) * air
    rms = np.sqrt(np.mean(mix ** 2)) or 1
    target = 10 ** (tr.get('loud_db', -17.0) / 20)
    mix = mix * (target / rms)
    mix = np.tanh(mix * 1.15) / np.tanh(1.15)
    peak = np.max(np.abs(mix))
    if peak > 0.95: mix *= 0.95 / peak
    return mix


def write_wav(path, x):
    pcm = (np.clip(x.T, -1, 1) * 32767).astype(np.int16)
    with wave.open(path, 'wb') as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())


def encode(tr, x):
    """Android 用は曲そのまま（ogg は頭も終わりもぴったり）。ウェブ用 mp3 は、エンコーダーが頭に無音を足すので、
    曲の頭1秒を後ろにもう一度つけておき、再生側で loopStart=0.1秒・loopEnd=0.1秒+曲の長さ でくり返す。"""
    wav = os.path.join(WAV, tr['id'] + '.wav'); write_wav(wav, x)
    ext = os.path.join(WAV, tr['id'] + '_ext.wav'); write_wav(ext, np.concatenate([x, x[:, :SR]], axis=1))
    ogg = os.path.join(RAW, 'bgm_' + tr['id'] + '.ogg'); mp3 = os.path.join(WEB, tr['id'] + '.mp3')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '3', ogg], check=True)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', ext, '-c:a', 'libmp3lame', '-b:a', '128k', mp3], check=True)
    os.remove(ext)
    return ogg, mp3


def write_loops():
    import json
    loops = {tr['id']: round(sum(len(s['chords']) for s in tr['sections']) * 4 * 60.0 / tr['bpm'], 6) for tr in TRACKS}
    # ファイルを直接開いたとき（PC版）も読めるよう、JSON ではなく script で読む形にする
    open(os.path.join(WEB, 'loops.js'), 'w').write('window.PG_BGM_LOOPS = ' + json.dumps(loops) + ';\n')


def read_wav(path):
    w = wave.open(path)
    return np.frombuffer(w.readframes(w.getnframes()), np.int16).reshape(-1, 2).T / 32768.0


if __name__ == '__main__':
    for d in (RAW, WEB, WAV): os.makedirs(d, exist_ok=True)
    args = sys.argv[1:]
    only_encode = '--encode' in args   # 合成しなおさず、tools/bgm_wav の wav から変換だけする
    want = set(a for a in args if not a.startswith('--'))
    total = 0
    for tr in TRACKS:
        if want and tr['id'] not in want: continue
        x = read_wav(os.path.join(WAV, tr['id'] + '.wav')) if only_encode else render(tr)
        ogg, mp3 = encode(tr, x)
        total += os.path.getsize(ogg) + os.path.getsize(mp3)
        rms = 20 * np.log10(np.sqrt(np.mean(x ** 2)))
        print(f'{tr["id"]:8s} {x.shape[1] / SR:5.1f}s rms {rms:5.1f}dB  ogg {os.path.getsize(ogg) // 1024}KB  mp3 {os.path.getsize(mp3) // 1024}KB')
    write_loops()
    print('total', total // 1024, 'KB')
