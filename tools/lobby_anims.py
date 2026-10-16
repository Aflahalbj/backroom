"""Generator animasi pose lobby (format Emotecraft v3 untuk Player Animator).

    python tools/lobby_anims.py          -> tulis ke src/main/resources
    python tools/lobby_anims.py --dev    -> juga ke build/resources (pratinjau langsung, lihat AnimDev)

Animasi dibangun secara prosedural lalu disampel tiap STEP tick:
  - pose dasar memegang senjata diambil dari animasi orang ketiga TACZ
    (tools/tacz_rifle.json, format Bedrock/GeckoLib), diinterpolasi
    Catmull-Rom seperti di Blockbench;
  - di atasnya ditumpuk lapisan gerak: napas, goyang pinggul, lutut santai,
    kepala miring, hentakan recoil, kebablasan (overshoot), dst.

Satuan: rotasi dalam derajat (pitch/yaw/roll = xRot/yRot/zRot ModelPart),
posisi sebagai selisih dari posisi bawaan bagian tubuh (piksel model; y ke
bawah, -z ke depan, +x ke kiri karakter). Bagian "body" (seluruh badan)
memakai satuan blok. "bend": siku menekuk ke depan dengan nilai NEGATIF,
lutut ke belakang dengan nilai POSITIF, badan membungkuk dengan nilai positif.
"""
import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIRS = [os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'backrooms', 'player_animation')]
if '--dev' in sys.argv:
    OUT_DIRS.append(os.path.join(ROOT, 'build', 'resources', 'main', 'assets', 'backrooms', 'player_animation'))
TACZ = os.path.join(ROOT, 'tools', 'tacz_rifle.json')

STEP = 2
PARTS = ('head', 'torso', 'body', 'rightArm', 'leftArm', 'rightLeg', 'leftLeg', 'rightItem', 'leftItem')
AXES = ('pitch', 'yaw', 'roll', 'x', 'y', 'z', 'bend', 'axis')
DEFAULTS = {
    'head': (0.0, 0.0, 0.0), 'torso': (0.0, 0.0, 0.0), 'body': (0.0, 0.0, 0.0),
    'rightArm': (-5.0, 2.0, 0.0), 'leftArm': (5.0, 2.0, 0.0),
    'rightLeg': (-1.9, 12.0, 0.1), 'leftLeg': (1.9, 12.0, 0.1),
    'rightItem': (0.0, 0.0, 0.0), 'leftItem': (0.0, 0.0, 0.0),
}


# ------------------------------------------------------------ util waktu

def smooth(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def ease_out_back(x, s=1.70158):
    x = max(0.0, min(1.0, x)) - 1
    return 1 + (s + 1) * x ** 3 + s * x ** 2


def window(t, start, end, ramp):
    """1 di dalam [start, end], naik/turun halus selebar ramp tick."""
    if t < start - ramp or t > end + ramp:
        return 0.0
    if t < start:
        return smooth((t - (start - ramp)) / ramp)
    if t > end:
        return 1.0 - smooth((t - end) / ramp)
    return 1.0


def wave(t, period, phase=0.0):
    return math.sin(2 * math.pi * (t / period + phase))


# ------------------------------------------------------------ pose

class Pose(dict):
    """{part: {axis: value}}; penjumlahan untuk menumpuk lapisan."""

    def add(self, part, **values):
        slot = self.setdefault(part, {})
        for axis, v in values.items():
            slot[axis] = slot.get(axis, 0.0) + v
        return self

    def merged(self, other, weight=1.0):
        out = Pose({p: dict(a) for p, a in self.items()})
        for part, axes in other.items():
            for axis, v in axes.items():
                out.setdefault(part, {})[axis] = out.get(part, {}).get(axis, 0.0) + v * weight
        return out


def lerp_pose(a, b, w):
    out = Pose()
    for part in set(a) | set(b):
        for axis in set(a.get(part, {})) | set(b.get(part, {})):
            va = a.get(part, {}).get(axis, 0.0)
            vb = b.get(part, {}).get(axis, 0.0)
            out.setdefault(part, {})[axis] = va + (vb - va) * w
    return out


# ------------------------------------------------------------ TACZ (Bedrock)

BEDROCK_PARTS = {'head': 'head', 'torso': 'torso', 'right_arm': 'rightArm', 'left_arm': 'leftArm',
                 'right_leg': 'rightLeg', 'left_leg': 'leftLeg', 'body': 'body'}


def _vec(v):
    if isinstance(v, list):
        return v
    if 'post' in v:
        return _vec(v['post'])
    return v['vector']


def _channel(ch):
    if isinstance(ch, list) or 'vector' in ch:
        return [(0.0, _vec(ch))]
    return sorted((float(k), _vec(v)) for k, v in ch.items() if k != 'easing')


def _catmull(keys, t):
    if t <= keys[0][0]:
        return keys[0][1]
    if t >= keys[-1][0]:
        return keys[-1][1]
    for i in range(len(keys) - 1):
        t0, v0 = keys[i]
        t1, v1 = keys[i + 1]
        if t0 <= t <= t1:
            u = (t - t0) / (t1 - t0) if t1 > t0 else 0.0
            vp = keys[i - 1][1] if i > 0 else v0
            vn = keys[i + 2][1] if i + 2 < len(keys) else v1
            return [0.5 * (2 * b + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u * u + (-a + 3 * b - 3 * c + d) * u ** 3)
                    for a, b, c, d in zip(vp, v0, v1, vn)]
    return keys[-1][1]


class Tacz:
    def __init__(self, path):
        self.anims = json.load(open(path, encoding='utf8'))['animations']

    def length(self, name):
        return float(self.anims[name].get('animation_length') or 0.05)

    def pose(self, name, seconds):
        """Pose TACZ pada detik tertentu, dikonversi ke konvensi Emotecraft."""
        out = Pose()
        for bone, chans in self.anims[name]['bones'].items():
            part = BEDROCK_PARTS.get(bone)
            if part is None:
                continue
            if 'rotation' in chans:
                rx, ry, rz = _catmull(_channel(chans['rotation']), seconds)
                if part == 'body':
                    rx, ry = -rx, -ry
                out.add(part, pitch=rx, yaw=ry, roll=rz)
            if 'position' in chans:
                px, py, pz = _catmull(_channel(chans['position']), seconds)
                if part == 'body':
                    out.add(part, x=-px / 16.0, y=py / 16.0, z=pz / 16.0)
                else:
                    out.add(part, x=px, y=-py, z=pz)
        return out


# ------------------------------------------------------------ keluaran

ANGLE_AXES = ('pitch', 'yaw', 'roll')


def compile_anim(name, length, fn, loop=True, intro=0, intro_fn=None, shots=(), sounds=(), step=STEP):
    """Sampel pose tiap STEP tick jadi keyframe LINEAR.

    Kalau ada intro_fn, tick 0..intro dimainkan sekali (intro_fn(t)), lalu
    loop fn(t) berulang dari returnTick = intro. Setiap sumbu yang pernah
    dipakai dikeluarkan di setiap sampel (supaya tidak ada interpolasi
    panjang yang tak sengaja), dan lompatan sudut > 180 derajat antar sampel
    ditandai dengan "turn" (Player Animator memutar penuh, bukan balik arah).
    """
    total = intro + length
    ticks = list(range(0, total + 1, step))
    if ticks[-1] != total:
        ticks.append(total)
    if intro and intro not in ticks:
        ticks.append(intro)
        ticks.sort()
    samples = []
    for t in ticks:
        if t < intro:
            samples.append(intro_fn(t))
        else:
            lt = t - intro
            samples.append(fn(lt if lt < length else 0))
    used = {}
    for pose in samples:
        for part, axes in pose.items():
            used.setdefault(part, set()).update(axes)
    moves = []
    for i, (t, pose) in enumerate(zip(ticks, samples)):
        nxt = samples[i + 1] if i + 1 < len(samples) else None
        for part in PARTS:
            axes = used.get(part)
            if not axes:
                continue
            values = {}
            turns = {}
            for axis in sorted(axes):
                v = pose.get(part, {}).get(axis, 0.0)
                if nxt is not None and axis in ANGLE_AXES:
                    jump = nxt.get(part, {}).get(axis, 0.0) - v
                    if abs(jump) > 180:
                        turns[axis] = int(round(jump / 360.0))
                if axis in ('x', 'y', 'z'):
                    v = DEFAULTS[part]['xyz'.index(axis)] + v
                values[axis] = round(v, 3)
            plain = {a: v for a, v in values.items() if a not in turns}
            if plain:
                moves.append({'tick': t, 'easing': 'LINEAR', 'turn': 0, part: plain})
            for axis, turn in turns.items():
                moves.append({'tick': t, 'easing': 'LINEAR', 'turn': turn, part: {axis: values[axis]}})
    data = {
        'version': 3, 'name': name, 'author': 'Backrooms',
        # Tick tembakan (dibaca LobbyFx untuk api moncong dan suara).
        'shots': ','.join(str(x) for x in shots),
        # Suara reload bertahap, "jenis:tick" (magout/magin/end).
        'sounds': ','.join('%s:%d' % (k, x) for k, x in sounds),
        'emote': {'isLoop': loop, 'returnTick': intro, 'beginTick': 0, 'endTick': total, 'stopTick': total + 1,
                  'degrees': True, 'easeBeforeKeyframe': True, 'moves': moves},
    }
    text = json.dumps(data, separators=(',', ':'))
    for d in OUT_DIRS:
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, name + '.json'), 'w', encoding='utf8') as f:
            f.write(text)
    print('%-16s %3d+%3d tick  %5d moves  %6.1f KB' % (name, intro, length, len(moves), len(text) / 1024))


# ------------------------------------------------------------ toolkit animator

def clamp01(x):
    return max(0.0, min(1.0, x))


def ease_out(x):
    return 1 - (1 - clamp01(x)) ** 3


def ease_in(x):
    return clamp01(x) ** 3


def snap(x):
    """Cepat lalu kebablasan sedikit dan kembali (overshoot)."""
    return ease_out_back(x, 2.4)


def keyed(t, keys):
    """Kurva dari key [(tick, nilai[, easing ke key ini])]; easing bawaan smooth."""
    if t <= keys[0][0]:
        return keys[0][1]
    for i in range(1, len(keys)):
        k0, k1 = keys[i - 1], keys[i]
        if t <= k1[0]:
            ease = k1[2] if len(k1) > 2 else smooth
            u = (t - k0[0]) / (k1[0] - k0[0]) if k1[0] > k0[0] else 1.0
            return k0[1] + (k1[1] - k0[1]) * ease(u)
    return keys[-1][1]


def kick(dt, attack=1.0, decay=4.0):
    """Impuls: naik cepat dalam `attack` tick lalu meluruh eksponensial."""
    if dt < 0:
        return 0.0
    if dt < attack:
        return dt / attack
    return math.exp(-(dt - attack) / decay)


def settle(dt, period=8.0, decay=5.0):
    """Getaran teredam setelah hentakan (follow-through), mulai dari 1."""
    if dt < 0:
        return 0.0
    return math.exp(-dt / decay) * math.cos(2 * math.pi * dt / period)


def noise(t, seed, period=23.0):
    """Gerak halus pseudo-acak (jumlah sinus tak sebanding), kira-kira -1..1."""
    return (0.6 * math.sin(2 * math.pi * (t / period + seed * 0.37))
            + 0.3 * math.sin(2 * math.pi * (t / (period * 0.53) + seed * 1.13))
            + 0.1 * math.sin(2 * math.pi * (t / (period * 0.29) + seed * 2.71)))


def blend(p, part, w, **target):
    """Geser sumbu-sumbu `part` menuju nilai mutlak `target` dengan bobot w."""
    if w <= 0:
        return p
    slot = p.setdefault(part, {})
    for axis, v in target.items():
        cur = slot.get(axis, 0.0)
        slot[axis] = cur + (v - cur) * w
    return p


def all_parts_ready(p):
    for part in PARTS:
        p.setdefault(part, {})
    return p


# ------------------------------------------------------------ rig tempur

tacz = Tacz(TACZ)

# Di atas pose bidik TACZ: siku menekuk (lengan atas turun, lengan bawah
# mendatar), siku kanan keluar, senjata dikoreksi supaya tetap lurus, badan
# condong ke depan dengan lutut menekuk, pipi menempel di popor.
AIM_RIG = Pose({
    'rightArm': {'pitch': 25, 'bend': -50, 'z': -1, 'roll': 12},
    'leftArm': {'pitch': 22, 'bend': -44, 'z': -1},
    'rightItem': {'pitch': -25},
    'torso': {'bend': 12}, 'body': {'y': -0.09},
    'rightLeg': {'bend': 22}, 'leftLeg': {'bend': 26},
    'head': {'pitch': 6},
})
if os.environ.get('RIG_AIM'):
    AIM_RIG = Pose(json.loads(os.environ['RIG_AIM']))

# Low ready: popor tetap di bahu, moncong turun, kepala tegak di atas senjata.
LOW_DELTA = Pose({
    'rightArm': {'pitch': 24}, 'leftArm': {'pitch': 24, 'y': 0.6},
    'torso': {'bend': -5}, 'head': {'pitch': -10}, 'body': {'y': 0.03},
})


# Arah hadap dasar: karakter menyerong 3/4 dari kamera lobby supaya senjata
# terlihat utuh, tidak mengarah lurus ke layar.
FACING = 28


def combat(t, aim=1.0, facing=FACING):
    """Pose dasar memegang senapan: aim=1 bidik di bahu .. aim=0 low ready."""
    s = (t / 20.0) % tacz.length('aim_upper')
    p = tacz.pose('aim_upper', s).merged(tacz.pose('hold_lower', s)).merged(AIM_RIG)
    p.add('body', yaw=facing)
    return p.merged(LOW_DELTA, 1 - aim)


def breathing(t, period=64, depth=1.0):
    """Dada naik-turun: badan, lengan, dan kepala ikut sedikit."""
    b = wave(t, period) * depth
    p = Pose()
    p.add('torso', pitch=-0.8 * b, y=-0.2 * b)
    p.add('head', y=-0.22 * b, pitch=0.5 * b)
    p.add('rightArm', y=-0.22 * b)
    p.add('leftArm', y=-0.22 * b)
    return p


def sway(p, t, amount=1.0):
    """Goyang berat badan kecil yang tak pernah benar-benar diam."""
    p.add('body', x=0.012 * amount * noise(t, 4, 61), yaw=0.8 * amount * noise(t, 5, 47))
    p.add('head', yaw=1.2 * amount * noise(t, 6, 37), roll=0.8 * amount * noise(t, 7, 43))
    return p


def tremor(p, t, amount=1.0):
    """Getar kecil tangan saat menahan bidikan."""
    p.add('rightArm', pitch=0.45 * amount * noise(t, 1, 13), yaw=0.35 * amount * noise(t, 2, 17))
    p.add('leftArm', pitch=0.45 * amount * noise(t, 1, 13), yaw=0.35 * amount * noise(t, 2, 17))
    return p


def track(p, t, f, lead=3, lag=3, amount=1.0):
    """Badan berputar ke arah f(t); kepala mendahului, lengan/senjata tertinggal
    (overlap), kaki menahan sebagian supaya telapak tidak meluncur, badan
    sedikit miring ke arah putaran."""
    y = f(t) * amount
    p.add('body', yaw=y)
    p.add('head', yaw=0.55 * (f(t + lead) - f(t)) * amount)
    drag = (f(t) - f(t - lag)) * amount
    p.add('rightArm', yaw=-0.3 * drag)
    p.add('leftArm', yaw=-0.3 * drag)
    p.add('rightLeg', yaw=-0.45 * y)
    p.add('leftLeg', yaw=-0.45 * y)
    vel = (f(t + 1) - f(t - 1)) * 0.5 * amount
    p.add('torso', roll=-0.35 * vel)
    return p


def fire(p, t, shots, power=1.0, climb=1.0):
    """Recoil per tembakan: hentakan cepat ke belakang (bahu terdorong, kepala
    ikut), moncong naik yang menumpuk selama rentetan lalu pulih, dan sedikit
    getar acak supaya tiap tembakan beda."""
    for i, shot in enumerate(shots):
        dt = t - shot
        if dt < 0 or dt > 45:
            continue
        k = kick(dt, 0.6, 2.2) * power
        c = math.exp(-dt / 7.0) * climb * power
        jitter = ((i * 7919) % 13 - 6) / 6.0
        p.add('rightArm', z=1.8 * k, pitch=-3.0 * k - 2.4 * c, roll=0.8 * jitter * k)
        p.add('leftArm', z=1.5 * k, pitch=-2.4 * k - 2.4 * c)
        p.add('rightItem', pitch=2.0 * k)
        p.add('torso', pitch=-2.5 * k, yaw=1.2 * jitter * k)
        p.add('head', pitch=-1.8 * k, z=0.4 * k, roll=0.6 * jitter * k)
        p.add('body', z=0.03 * k)
    return p


def burst(start, count=3, gap=2):
    return [start + i * gap for i in range(count)]


# ------------------------------------------------------------ pose-pose

# Tick tembakan tiap animasi (relatif ke awal loop; intro ditambahkan saat kompilasi).
SHOTS = {}
# Suara reload (jenis, tick) per animasi.
SOUNDS = {}


def siaga(t):
    """Jaga sektor: low ready memindai kiri-kanan (kepala mendahului, senjata
    menyusul), mendadak ada ancaman -> naik ke bidikan secepat kilat sambil
    merendah, melacak sasaran, turun lagi, lalu isyarat "tahan" ke rekan di
    belakang dengan kepalan tangan kiri."""
    aim = keyed(t, [(0, 0), (84, 0), (89, 1, snap), (136, 1), (158, 0), (240, 0)])
    p = combat(t, aim)

    def scan(x):
        return keyed(x % 240, [(0, 0), (24, 30), (54, 30), (62, -14, ease_out), (84, -14), (89, -4, snap),
                               (136, -4), (158, 0), (240, 0)]) \
            + 2.5 * noise(x, 1, 31) * window(x % 240, 92, 132, 4)
    track(p, t, scan)
    # Kaget: kepala tersentak, badan merendah dan maju saat naik ke bidikan.
    p.add('head', pitch=-5 * kick(t - 80, 1, 3))
    p.add('body', y=-0.05 * aim - 0.03 * kick(t - 85, 2, 6), z=-0.03 * aim)
    p.add('torso', bend=4 * aim)
    tremor(p, t, aim)
    # Isyarat "tahan": lengan atas mendatar ke samping kiri, lengan bawah tegak,
    # kepalan setinggi kepala; senjata ditahan satu tangan, kepala menoleh ke belakang kiri.
    sig = keyed(t, [(0, 0), (168, 0), (176, 1, snap), (204, 1), (214, 0), (240, 0)])
    if sig > 0:
        blend(p, 'leftArm', sig, pitch=-88, yaw=-40, roll=0, x=0, y=0, z=0, bend=-105)
        p.add('leftArm', pitch=-5 * kick(t - 186, 1, 3))
        p.add('rightArm', pitch=10 * sig)
        p.add('rightItem', pitch=-8 * sig)
        p.add('head', yaw=-50 * sig, pitch=-4 * sig)
        p.add('torso', yaw=-8 * sig)
    sway(p, t, 1 - aim)
    return all_parts_ready(p.merged(breathing(t, 60, 1.0 - 0.6 * aim)))


SHOTS['lobby_bidik'] = burst(14) + burst(54, 4) + [84, 87] + burst(116, 9)


def bidik(t):
    """Tembak terkendali: rentetan 3, pindah sasaran kanan -> 4, pindah kiri ->
    double tap, kembali ke tengah -> rentetan panjang dengan moncong naik dan
    badan bergetar, lalu kepala terangkat dari popor menilai hasil, dan bidik lagi."""
    def target(x):
        return keyed(x % 180, [(0, 0), (44, 0), (49, 16, snap), (76, 16), (80, -12, snap), (104, -12),
                               (109, 0, snap), (180, 0)])
    assess = keyed(t, [(0, 0), (140, 0), (148, 1), (164, 1), (170, 0, snap), (180, 0)])
    p = combat(t, 1 - 0.5 * assess)
    track(p, t, target)
    p.add('head', pitch=-9 * assess, y=-1.0 * assess, z=0.8 * assess, yaw=-5 * assess)
    tremor(p, t, 1 - assess)
    fire(p, t, SHOTS['lobby_bidik'])
    auto = window(t, 116, 134, 2)
    p.add('body', x=0.012 * math.sin(t * 2.7) * auto, y=-0.015 * auto)
    p.add('torso', bend=3 * auto)
    return all_parts_ready(p.merged(breathing(t, 50, 0.4)))


# reload_upper TACZ dimainkan 1,3x lebih lambat mulai tick R0.
R0 = 18
RS = 1.3


def reload_tick(seconds):
    return int(round(R0 + seconds * 20 * RS))


# Magasin dicabut ~0,42 dtk, masuk 1,29 dtk, kokang 1,75 dtk (waktu TACZ).
SOUNDS['lobby_reload'] = [('magout', reload_tick(0.42)), ('magin', reload_tick(1.29)), ('end', reload_tick(1.75))]


def reload(t):
    """Reload taktis: turun dari bidikan, senjata dimiringkan, magasin dicabut
    dan dilempar, magasin baru dihantam masuk, kokang dengan hentakan, naik
    lagi ke bidikan dengan overshoot, lalu memindai sektor."""
    rl = tacz.length('reload_upper')
    end = reload_tick(rl)
    sec = (t - R0) / (20.0 * RS)
    w = keyed(t, [(0, 0), (R0 - 6, 0), (R0, 1, ease_out), (end - 2, 1), (end + 6, 0), (170, 0)])
    aim = keyed(t, [(0, 1), (R0 - 8, 1), (R0, 0.3), (end, 0.3), (end + 8, 1, snap), (170, 1)])

    def look(x):
        return keyed(x % 170, [(0, 0), (end + 14, 0), (end + 26, -16), (end + 42, -16), (end + 52, 12),
                               (end + 66, 12), (170, 0)])
    p = combat(t, aim)
    if w > 0:
        r = tacz.pose('reload_upper', min(max(sec, 0.0), rl)).merged(tacz.pose('hold_lower', 0))
        # Senjata tidak diangkat setinggi aslinya.
        r['rightArm']['pitch'] = -90.5 + (r['rightArm']['pitch'] + 90.5) * 0.8
        r = r.merged(Pose({k: v for k, v in AIM_RIG.items() if k in ('torso', 'body', 'rightLeg', 'leftLeg')}))
        p = lerp_pose(p, r, w)
        # Magasin kosong dikibas lepas ke bawah samping.
        flick = window(sec, 0.5, 0.56, 0.05)
        p.add('leftArm', yaw=-30 * flick, pitch=25 * flick, roll=-10 * flick)
        # Magasin baru diambil dari kantong di sabuk kiri depan, ditarik keluar.
        pouch = window(sec, 0.68, 0.9, 0.1)
        if pouch > 0:
            blend(p, 'leftArm', pouch, pitch=-22, yaw=12, roll=-4, x=0, y=0, z=0, bend=-78)
            p.add('leftArm', pitch=8 * kick(sec - 0.82, 0.03, 0.06))
            p.add('head', yaw=-14 * pouch, pitch=8 * pouch)
        # Magasin masuk: senjata tersentak, badan sedikit turun.
        k1 = kick(sec - 1.29, 0.02, 0.08)
        p.add('rightItem', pitch=-6 * k1)
        p.add('body', y=-0.02 * k1)
        p.add('head', pitch=3 * k1)
        # Kokang: hentakan lebih keras dengan getar susulan.
        k2 = settle(sec - 1.75, 0.18, 0.12)
        p.add('rightItem', roll=6 * k2, pitch=-4 * k2)
        p.add('torso', yaw=3 * k2)
        p.add('body', y=-0.025 * kick(sec - 1.75, 0.02, 0.1))
    track(p, t, look)
    tremor(p, t, aim * (1 - w))
    return all_parts_ready(p.merged(breathing(t, 52, 0.7)))


def kneel(p, rise=0.0):
    """Lutut kanan di tanah, kaki kiri menapak di depan; rise 0..1 = setengah berdiri."""
    p['rightLeg'] = {}
    p['leftLeg'] = {}
    p.add('body', y=-0.375 + 0.2 * rise, z=0.02)
    p.add('rightLeg', pitch=-8 - 20 * rise, bend=96 - 50 * rise, yaw=10, roll=4)
    p.add('leftLeg', pitch=-84 + 40 * rise, bend=86 - 40 * rise, yaw=-8, roll=-3)
    p.add('torso', bend=8)
    return p


SHOTS['lobby_berlutut'] = burst(42) + burst(54) + burst(124, 4)


def berlutut(t):
    """Berlindung berlutut: napas memburu di balik perlindungan, condong keluar
    ke kanan dengan cepat, dua rentetan, kembali berlindung; lalu bangkit dari
    atas perlindungan, rentetan, dan turun lagi."""
    lean = keyed(t, [(0, 0), (30, 0), (37, 1, snap), (64, 1), (70, 0, ease_out), (210, 0)])
    rise = keyed(t, [(0, 0), (108, 0), (117, 1, snap), (142, 1), (149, 0, ease_out), (210, 0)])
    expose = max(lean, rise)
    p = combat(t, 0.25 + 0.75 * expose)
    kneel(p, rise)
    cover = 1 - expose
    p.add('head', pitch=10 * cover, yaw=-14 * cover)
    p.add('body', roll=3 * cover)
    p.add('body', roll=-13 * lean, x=-0.2 * lean)
    p.add('head', roll=8 * lean)
    tremor(p, t, expose)
    fire(p, t, SHOTS['lobby_berlutut'], 0.9)
    # Di balik perlindungan: menoleh mengecek kiri.
    p.add('head', yaw=-26 * window(t, 160, 190, 6))
    return all_parts_ready(p.merged(breathing(t, 30, 0.6 + 0.8 * cover)))


SHOTS['lobby_guling'] = burst(64) + burst(76) + burst(160, 4)


def guling(t):
    """Berguling tempur ke samping: bidik, merendah dan condong ke arah guling
    (antisipasi), meringkuk berguling (bagian atas badan mendahului ke arah
    gerak), keluar dari guling langsung berlutut dan menembak, berguling balik,
    bangkit berdiri dan menembak."""
    side = 0.95  # jarak guling (blok, ke kiri karakter = kanan layar)

    def kneeling(x):
        q = combat(x, 1.0)
        kneel(q)
        return q

    def rolling(x, u, direction, start, end):
        """Pose selama guling; u 0..1. direction +1 = bergerak ke kiri karakter.
        Badan diputar di sumbu depan; pusat putar (0,7 blok di atas kaki)
        direndahkan saat meringkuk dan diangkat saat terbalik supaya badan
        tidak menembus lantai. Awal/akhir dibaurkan ke pose sebelum/sesudahnya."""
        q = combat(x, 1.0)
        tuck = math.sin(math.pi * clamp01(u))
        ang = -direction * 360 * smooth(u)
        inv = max(0.0, -math.cos(math.radians(ang)))
        wrapped = (ang + 180) % 360 - 180
        q.add('body', roll=wrapped, y=-0.3 * tuck + 0.5 * inv)
        q.add('rightLeg', bend=120 * tuck, pitch=-85 * tuck)
        q.add('leftLeg', bend=120 * tuck, pitch=-85 * tuck)
        q.add('rightArm', pitch=45 * tuck, z=-2 * tuck)
        q.add('leftArm', pitch=45 * tuck, z=-2 * tuck)
        q.add('torso', bend=55 * tuck)
        q.add('head', pitch=40 * tuck)
        if start is not None and u < 0.25:
            q = lerp_pose(start, q, smooth(u / 0.25))
        if end is not None and u > 0.75:
            q = lerp_pose(q, end, smooth((u - 0.75) / 0.25))
        return q

    x = 0.0
    if t < 20:
        p = combat(t, 1.0)
    elif t < 28:  # antisipasi: merendah dan condong ke arah guling
        p = combat(t, 1.0)
        a = ease_in((t - 20) / 8)
        p.add('body', y=-0.12 * a, roll=-6 * a)
        p.add('rightLeg', bend=30 * a)
        p.add('leftLeg', bend=30 * a)
    elif t < 48:
        u = (t - 28) / 20
        x = side * smooth(u)
        start = combat(t, 1.0).add('body', y=-0.12, roll=-6).add('rightLeg', bend=30).add('leftLeg', bend=30)
        p = rolling(t, u, 1, start, kneeling(t))
    elif t < 56:  # mendarat berlutut dengan hentakan
        x = side
        p = kneeling(t)
        p.add('body', y=-0.04 * settle(t - 48, 8, 3))
    elif t < 116:
        x = side
        p = kneeling(t)
    elif t < 124:  # antisipasi balik
        x = side
        p = kneeling(t)
        p.add('body', roll=6 * ease_in((t - 116) / 8))
    elif t < 144:
        u = (t - 124) / 20
        x = side * (1 - smooth(u))
        start = kneeling(t).add('body', roll=6)
        end = combat(t, 1.0).add('body', y=-0.14).add('rightLeg', bend=40).add('leftLeg', bend=40)
        p = rolling(t, u, -1, start, end)
    elif t < 152:
        p = combat(t, 1.0)
        a = snap((t - 144) / 8)
        p.add('body', y=-0.14 * (1 - a))
        p.add('rightLeg', bend=40 * (1 - a))
        p.add('leftLeg', bend=40 * (1 - a))
    else:
        p = combat(t, 1.0)
    p.add('body', x=x)
    fire(p, t, SHOTS['lobby_guling'], 0.9)
    tremor(p, t, 0.6)
    return all_parts_ready(p.merged(breathing(t, 36, 0.7)))


SHOTS['lobby_ancam'] = [112, 136, 158]


def ancam(t):
    """Mengancam: kepala menunduk diam, perlahan terangkat menatap kamera,
    senjata dibidikkan sekilat langsung ke penonton, merayap maju, tiga
    tembakan tunggal yang berat, lalu turun pelan dengan kepala dimiringkan."""
    aim = keyed(t, [(0, 0), (50, 0), (57, 1, snap), (172, 1), (196, 0), (220, 0)])
    p = combat(t, aim, facing=8)
    # Menunduk lalu menatap: kepala naik pelan, sedikit tersentak di akhir.
    stare = keyed(t, [(0, 0), (22, 0), (46, 1), (196, 1), (214, 0), (220, 0)])
    p.add('head', pitch=22 * (1 - stare), roll=-6 * (1 - stare))
    p.add('head', pitch=-3 * kick(t - 46, 1, 4))
    # Merayap maju selama membidik, badan makin rendah.
    creep = keyed(t, [(0, 0), (58, 0), (104, 1), (172, 1), (200, 0), (220, 0)])
    p.add('body', z=-0.14 * creep, y=-0.035 * creep)
    p.add('torso', bend=5 * creep)
    p.add('rightLeg', bend=8 * creep)
    p.add('leftLeg', bend=10 * creep)
    # Dingin: hampir tanpa getar, napas pelan dan dalam.
    tremor(p, t, 0.3 * aim)
    fire(p, t, SHOTS['lobby_ancam'], power=1.8, climb=0.6)
    # Setelah tembakan terakhir: kepala dimiringkan perlahan.
    tilt = keyed(t, [(0, 0), (170, 0), (186, 1), (206, 1), (218, 0)])
    p.add('head', roll=14 * tilt, yaw=4 * tilt)
    sway(p, t, 0.4 * (1 - aim))
    return all_parts_ready(p.merged(breathing(t, 84, 1.2)))


# ------------------------------------------------------------ intro

INTRO = 14


def ready_intro(target):
    """Saat pose dipilih: senjata dinaikkan dari bawah ke posisi, dengan hentakan."""
    def fn(t):
        end = target(0)
        low = end.merged(Pose().add('rightArm', pitch=28).add('leftArm', pitch=24, y=1.5)
                         .add('rightItem', pitch=-30).add('body', y=-0.05)
                         .add('rightLeg', bend=12).add('leftLeg', bend=12))
        return all_parts_ready(lerp_pose(low, end, ease_out_back(t / INTRO, 1.3)))
    return fn


# ------------------------------------------------------------ cutscene jatuh
#
# Cutscene perkenalan room (client/lobby/DropCutscene): lantai lobby bolong,
# semua anggota jatuh, lalu satu per satu diperkenalkan ke kamera dalam
# gerak lambat. drop_jatuh diputar semua anggota sejak cutscene mulai;
# drop_<pose> mulai saat giliran anggota itu (tick 0 = kamera mulai menyambar
# ke dia, puncak aksinya sekitar tick 9..16 saat gerak lambat); drop_selam
# untuk bagian akhir (semua menyelam ke kegelapan). Tiap animasi: bagian aksi
# sekali jalan (intro), lalu menahan pose akhirnya sambil diterpa angin (loop).
#
# Seluruh badan diputar lewat "body" (poros 0,7 blok di atas kaki); angka
# "y" mengangkat pusat putar supaya putaran terlihat di sekitar pinggang.
# Pitch "body" NEGATIF = condong ke depan (positif = terjengkang ke belakang).

DROP_HOLD = 40


def flutter(p, t, amount=1.0, period=DROP_HOLD):
    """Angin menerpa selama jatuh: anggota badan bergetar halus. Semua
    gelombangnya kelipatan `period`, jadi loop tidak patah di sambungan."""
    def w(k, ph):
        return wave(t, period / k, ph)
    p.add('rightLeg', pitch=5 * amount * w(2, 0.1), roll=2 * amount * w(3, 0.3), bend=4 * amount * w(2, 0.35))
    p.add('leftLeg', pitch=5 * amount * w(2, 0.6), roll=-2 * amount * w(3, 0.8), bend=4 * amount * w(2, 0.85))
    p.add('rightArm', pitch=1.6 * amount * w(4, 0.2), yaw=1.2 * amount * w(5, 0.5))
    p.add('leftArm', pitch=1.6 * amount * w(4, 0.2), yaw=1.2 * amount * w(5, 0.5))
    p.add('head', yaw=2.5 * amount * w(1, 0.4), roll=1.5 * amount * w(2, 0.9))
    p.add('body', roll=3 * amount * w(1, 0.25), pitch=2 * amount * w(1, 0.7))
    return p


def sky_legs(p, w=1.0, asym=1.0):
    """Kaki penerjun: lutut menekuk ke belakang, tidak simetris supaya hidup."""
    p.add('rightLeg', pitch=14 * w, bend=58 * w, yaw=6 * w, roll=5 * w)
    p.add('leftLeg', pitch=(-8 + 4 * (1 - asym)) * w, bend=(34 + 24 * (1 - asym)) * w, yaw=-4 * w, roll=-4 * w)
    return p


def fall_hold(t, aim=0.45, lean=14):
    """Pose jatuh stabil memegang senapan: condong ke depan, kaki penerjun."""
    p = combat(t, aim, facing=10)
    p['rightLeg'] = {}
    p['leftLeg'] = {}
    sky_legs(p)
    p.add('body', pitch=-lean, y=0.05)
    p.add('torso', bend=-6)
    p.add('head', pitch=-8 - 0.4 * lean)
    return p


# drop_jatuh, tick sejak cutscene mulai (sama dengan DropTiming): gemuruh
# 0..28, lantai runtuh di 28, menggantung (hang time) 28..35, jatuh dari 35.
# Tick penting (sama dengan DropTiming): getaran pertama 12, lantai runtuh 52, mulai jatuh 60.
QUAKE_TREMOR = 12
QUAKE_BREAK = 52
QUAKE_SHIFT = QUAKE_BREAK - 28
JATUH_INTRO = 80 + QUAKE_SHIFT


def jatuh(t):
    """Gempa lalu jatuh. Sampai lantai runtuh: getaran pertama mengagetkan,
    lengan kiri terentang menjaga keseimbangan, kaki melebar dan menekuk,
    badan terhuyung mengikuti guncangan, menunduk ke retakan, mendongak ke
    debu yang jatuh dari plafon, lalu merendah. Sesudahnya: jatuh_runtuh."""
    if t >= QUAKE_BREAK:
        return jatuh_runtuh(t - QUAKE_SHIFT)
    # Bagian gemuruh lama diregangkan sepanjang gempa (berakhir sama persis di saat runtuh).
    p = jatuh_runtuh(t * 28.0 / QUAKE_BREAK)
    q = window(t, QUAKE_TREMOR + 2, QUAKE_BREAK - 4, 4)
    jolt = kick(t - QUAKE_TREMOR, 1.0, 3.0)
    k = clamp01((t - QUAKE_TREMOR) / float(QUAKE_BREAK - QUAKE_TREMOR))
    # Kaget di getaran pertama: badan tersentak, kepala menoleh cepat.
    p.add('body', y=0.03 * jolt, roll=4 * jolt)
    p.add('head', pitch=-8 * jolt, yaw=-14 * jolt)
    # Menjaga keseimbangan: lengan kiri lepas dari senapan, terentang ke samping.
    blend(p, 'leftArm', 0.85 * q, pitch=-40, yaw=0, roll=-48, x=0, y=0, z=0, bend=-18)
    p.add('rightArm', pitch=10 * q)
    # Kaki melebar, terhuyung bergantian (melangkah menahan guncangan).
    p.add('rightLeg', roll=8 * q, pitch=10 * q * math.sin(t * 0.7))
    p.add('leftLeg', roll=-8 * q, pitch=-10 * q * math.sin(t * 0.7 + 1.3))
    p.add('body', roll=(2.0 + 4.0 * k) * q * math.sin(t * 0.55), x=0.03 * q * math.sin(t * 0.43))
    # Mata: ke retakan di lantai, ke plafon (debu), lalu ke bawah lagi.
    p.add('head', pitch=q * keyed(t, [(0, 0), (16, 20), (26, 24), (31, -30), (37, -26), (43, 28), (52, 30)]))
    return all_parts_ready(p)


def jatuh_runtuh(t):
    """Gemuruh: menunduk, lutut menekuk (antisipasi). Lantai hilang: badan
    kaku menggantung sesaat sambil menatap ke bawah. Jatuh: lengan dan
    senapan tertarik ke atas oleh angin, kaki menendang, kepala mendongak ke
    lubang; lalu menguasai diri ke pose penerjun memegang senapan."""
    if t >= JATUH_INTRO - QUAKE_SHIFT:
        return all_parts_ready(flutter(fall_hold(t + QUAKE_SHIFT), t + QUAKE_SHIFT))
    aim = keyed(t, [(0, 0.1), (28, 0.1), (31, 0.5, snap), (40, 0.2), (62, 0.2), (74, 0.45), (80, 0.45)])
    stand = combat(t, aim, facing=10)
    hold = fall_hold(t)
    # 0 = berdiri di lantai, 1 = pose jatuh stabil.
    st = keyed(t, [(0, 0), (35, 0), (80, 1)])
    p = lerp_pose(stand, hold, st)
    # Gemuruh makin kuat; badan berguncang.
    shake = window(t, 3, 27, 3) * (0.35 + 0.65 * smooth(t / 28))
    p.add('body', x=0.014 * shake * math.sin(t * 2.9), z=0.008 * shake * math.sin(t * 3.7),
          roll=1.4 * shake * math.sin(t * 2.3))
    # Antisipasi: merendah, lalu lantai hilang -> kaki lurus kaku (menggantung).
    crouch = keyed(t, [(0, 0), (8, 0.3), (26, 1), (29, 0.15, ease_out), (35, 0)])
    p.add('rightLeg', bend=28 * crouch)
    p.add('leftLeg', bend=32 * crouch)
    p.add('body', y=-0.07 * crouch)
    p.add('torso', bend=7 * crouch)
    # Kepala: melirik kiri-kanan, menunduk ke lantai yang retak, lalu mendongak saat jatuh.
    p.add('head', yaw=keyed(t, [(0, 0), (6, -26), (14, 22), (22, 0)]) * window(t, 0, 24, 3))
    p.add('head', pitch=keyed(t, [(0, 0), (18, 0), (25, 26), (33, 42), (40, -24, ease_out), (58, -10), (80, 0)]))
    # Kaget saat lantai lenyap: bahu terangkat, lengan sedikit naik (hang).
    hang = keyed(t, [(0, 0), (28, 0), (30, 1, snap), (35, 1), (38, 0)])
    p.add('rightArm', pitch=-14 * hang)
    p.add('leftArm', pitch=-18 * hang, roll=-8 * hang)
    p.add('body', y=0.03 * hang)
    # Jatuh: angin menyeret lengan/senapan ke atas (drag), kaki menendang tak beraturan.
    drag = keyed(t, [(0, 0), (35, 0), (40, 1, ease_out), (56, 0.8), (72, 0)])
    p.add('rightArm', pitch=-42 * drag, roll=10 * drag)
    p.add('leftArm', pitch=-58 * drag, roll=-22 * drag, yaw=-10 * drag)
    p.add('rightItem', pitch=14 * drag)
    kickw = window(t, 37, 64, 5)
    p.add('rightLeg', pitch=26 * kickw * math.sin(t * 0.9), bend=30 * kickw * (0.6 + 0.4 * math.sin(t * 1.3)))
    p.add('leftLeg', pitch=-26 * kickw * math.sin(t * 0.9 + 1.1), bend=30 * kickw * (0.6 + 0.4 * math.sin(t * 1.1 + 2)))
    # Badan terjengkang ke belakang sesaat lalu kembali condong.
    p.add('body', pitch=keyed(t, [(0, 0), (35, 0), (42, 22, ease_out), (60, 8), (80, 0)]))
    p.add('body', roll=keyed(t, [(0, 0), (36, 0), (46, 9), (58, -5), (72, 0)]))
    p.add('torso', bend=keyed(t, [(0, 0), (35, 0), (42, -10), (66, 0)]))
    return all_parts_ready(flutter(p, t + QUAKE_SHIFT, smooth((t - 50) / 30)))


def fall_style(t, style):
    """Pose jatuh stabil per kepribadian (loop 40 tick, mulus di sambungan):
    'pro' penerjun melengkung, 'duduk' duduk santai senapan di bahu,
    'perut' belly-to-earth: menelungkup melengkung, senapan di depan dada."""
    if style == 'pro':
        return fall_hold(t)
    if style == 'duduk':
        p = combat(t, 0.0, facing=14)
        p['rightLeg'] = {}
        p['leftLeg'] = {}
        p.add('rightLeg', pitch=-88, bend=92, yaw=8)
        p.add('leftLeg', pitch=-80, bend=70, yaw=-18, roll=-6)
        blend(p, 'rightArm', 1.0, pitch=-150, yaw=-10, roll=8, x=0, y=0, z=0, bend=-110)
        blend(p, 'rightItem', 1.0, pitch=60, yaw=0, roll=0)
        blend(p, 'leftArm', 1.0, pitch=-25, yaw=0, roll=-30, x=0, y=0, z=0, bend=-80)
        p.add('torso', bend=10)
        p.add('head', roll=8, yaw=14 + 10 * wave(t, DROP_HOLD, 0.2))
        p.add('body', pitch=6, y=0.1)
        # Kaki kiri mengetuk-ngetuk (aksi sekunder).
        p.add('leftLeg', bend=10 * wave(t, DROP_HOLD / 4.0))
        return p
    # perut: posisi dasar terjun bebas (belly-to-earth), punggung melengkung.
    p = combat(t, 0.15, facing=0)
    p['rightLeg'] = {}
    p['leftLeg'] = {}
    p.add('body', pitch=-62, y=0.22)
    p.add('torso', bend=-10)
    p.add('head', pitch=-34)
    p.add('rightLeg', pitch=22, bend=62, roll=10)
    p.add('leftLeg', pitch=18, bend=70, roll=-10)
    return p


def jatuh_style(style):
    """drop_jatuh dengan kepribadian lain: sama sampai menguasai diri (tick 55),
    lalu berbaur ke gaya jatuhnya sendiri."""
    def intro(t):
        base = jatuh(t)
        w = smooth((t - 52 - QUAKE_SHIFT) / 26.0)
        if w <= 0:
            return base
        return all_parts_ready(lerp_pose(base, flutter(fall_style(t, style), t, w), w))

    def loop(t):
        return all_parts_ready(flutter(fall_style(JATUH_INTRO + t, style), JATUH_INTRO + t))
    return intro, loop


SALVO_SHOTS = [13, 16]


def salvo(t):
    """Akhir: semua serempak membidik lurus ke kamera dengan kaki terbuka
    seperti bintang, dua tembakan bersamaan, lalu menahan bidikan."""
    aim = keyed(t, [(0, 0.2), (6, 0.1, ease_in), (10, 1, snap), (DROP_HOLD, 1)])
    p = combat(t, aim, facing=0)
    p['rightLeg'] = {}
    p['leftLeg'] = {}
    spread = keyed(t, [(0, 0.3), (8, 0.1), (11, 1, snap), (DROP_HOLD, 1)])
    p.add('rightLeg', pitch=-10, bend=24, roll=22 * spread)
    p.add('leftLeg', pitch=6, bend=34, roll=-22 * spread)
    p.add('body', pitch=-8 * spread, y=0.05)
    p.add('head', pitch=-4 * kick(t - 9, 1, 3))
    tremor(p, t, 0.5)
    fire(p, t, SALVO_SHOTS, power=1.5)
    return all_parts_ready(from_fall(t, p, 4))


def hold_loop(intro, at):
    """Loop: pose akhir intro (tick `at`) diterpa angin."""
    def fn(t):
        return all_parts_ready(flutter(intro(at, settle_only=True), t))
    return fn


def windy(intro, at):
    """Intro yang angin-nya masuk perlahan menjelang akhir, sama persis dengan
    awal loop di tick `at` (flutter periodik), jadi sambungannya mulus."""
    def fn(t):
        return all_parts_ready(flutter(intro(t), t, smooth((t - (at - 16)) / 16.0)))
    return fn


# ----- pose hero. Masing-masing fn(t, settle_only=False) untuk t = 0..DROP_INTRO.

DROP_INTRO = 40


def from_fall(t, p, ramp=6):
    """Awal aksi dibaurkan dari pose jatuh umum (supaya sambungannya tidak patah)."""
    if t >= ramp:
        return p
    return lerp_pose(fall_hold(t), p, smooth(t / ramp))


def selam(t):
    """Bagian akhir: menyelam kepala lebih dulu ke kegelapan, senapan
    dipeluk di dada, kaki lurus rapat ke atas, lengan siku menempel badan."""
    dive = keyed(t, [(0, 0), (3, -0.08, ease_in), (12, 1, ease_out), (DROP_HOLD, 1)])
    p = combat(t, 0.0, facing=0)
    p['rightLeg'] = {}
    p['leftLeg'] = {}
    p.add('rightLeg', pitch=4 * dive, bend=8 * dive, roll=-3 * dive)
    p.add('leftLeg', pitch=8 * dive, bend=14 * dive, roll=3 * dive)
    p.add('rightArm', pitch=10 * dive)
    p.add('leftArm', pitch=10 * dive)
    p.add('body', pitch=-165 * dive, y=0.45 * smooth(dive))
    p.add('head', pitch=-35 * dive)
    return all_parts_ready(p)


# ------------------------------------------------------------ gerak halus

# Jadwal satu giliran anggota, SAMA dengan DropTiming (Java): kamera berpindah
# 0..800 ms, waktu melambat 500..800, bullet time sampai 2500, kembali normal
# 2500..2900. Animasi aksi "dipanggang" dengan gerak lambat ini: tiap tick
# nyata punya keyframe sendiri, jadi gerak lambatnya mulus (Player Animator
# memutarnya dengan kecepatan normal).
SLOT_MS = 3200
SLOW = 0.3
SLOW_IN = (500, 800)
SLOW_OUT = (2500, 2900)
SLOT_TICKS = SLOT_MS // 50


def speed_at(ms):
    if ms < SLOW_IN[0]:
        return 1.0
    if ms < SLOW_IN[1]:
        return 1.0 + (SLOW - 1.0) * smooth((ms - SLOW_IN[0]) / (SLOW_IN[1] - SLOW_IN[0]))
    if ms < SLOW_OUT[0]:
        return SLOW
    if ms < SLOW_OUT[1]:
        return SLOW + (1.0 - SLOW) * smooth((ms - SLOW_OUT[0]) / (SLOW_OUT[1] - SLOW_OUT[0]))
    return 1.0


def scene_ticks():
    """Waktu adegan (tick) di awal tiap tick nyata 0..SLOT_TICKS."""
    out = [0.0]
    acc = 0.0
    for ms in range(SLOT_MS):
        acc += speed_at(ms) / 50.0
        if (ms + 1) % 50 == 0:
            out.append(acc)
    return out


SCENE = scene_ticks()


def real_tick(scene):
    """Tick nyata pertama yang waktu adegannya sudah mencapai `scene`."""
    for i, s_ in enumerate(SCENE):
        if s_ >= scene:
            return i
    return len(SCENE) - 1


# Frekuensi alami pegas tiap bagian (rad/detik): badan berat dan lambat,
# tangan dan senjata lebih lincah, kaki paling malas (terseret angin).
SPRING = {'body': 7.0, 'torso': 9.0, 'head': 11.0, 'rightArm': 12.0, 'leftArm': 10.0,
          'rightLeg': 6.5, 'leftLeg': 6.0, 'rightItem': 16.0, 'leftItem': 16.0}


def spring_filter(targets, dt=0.05, substeps=8):
    """Setiap sumbu mengikuti targetnya seperti pegas teredam kritis: tidak ada
    lonjakan atau patahan, dan bagian yang lebih lambat tertinggal sedikit di
    belakang badan (follow-through dan overlapping action dengan sendirinya)."""
    state = {}
    prev = targets[0]
    out = []
    for target in targets:
        res = Pose()
        for part, axes in target.items():
            w = SPRING.get(part, 10.0)
            for axis, goal in axes.items():
                key = (part, axis)
                start = prev.get(part, {}).get(axis, goal)
                if key not in state:
                    state[key] = [goal, 0.0]
                x, v = state[key]
                h = dt / substeps
                for k in range(substeps):
                    g = start + (goal - start) * (k + 1) / substeps
                    acc = w * w * (g - x) - 2.0 * w * v
                    v += acc * h
                    x += v * h
                state[key] = [x, v]
                res.setdefault(part, {})[axis] = x
        out.append(res)
        prev = target
    return out


def bake(pose_fn, ticks, recoil=None):
    """Pose tiap tick nyata: pose_fn(waktu_adegan), dihaluskan pegas, lalu
    hentakan recoil (yang memang harus tajam) ditumpuk sesudahnya."""
    times = SCENE[:ticks + 1]
    raw = [all_parts_ready(pose_fn(s_)) for s_ in times]
    smooth_poses = spring_filter(raw)
    out = []
    for s_, p in zip(times, smooth_poses):
        if recoil is not None:
            p = recoil(p, s_)
        out.append(all_parts_ready(p))
    return out


def hero_moves():
    """Gerakan aksi dari tools/drop_moves.py (lihat docstring modul itu)."""
    import drop_moves
    return drop_moves.MOVES


def compile_hero(move):
    """Satu gerakan aksi: SLOT_TICKS tick nyata gerak lambat yang dipanggang,
    lalu loop menahan pose akhir diterpa angin."""
    shots = move['shots']
    power = move.get('power', 1.0)
    climb = move.get('climb', 1.0)

    def recoil(p, s_):
        return fire(p, s_, shots, power, climb)
    frames = bake(move['pose'], SLOT_TICKS, recoil)
    end = frames[-1]
    # Angin masuk perlahan di 16 tick terakhir, sama persis dengan awal loop.
    intro = [all_parts_ready(flutter(f, t, smooth((t - (SLOT_TICKS - 16)) / 16.0))) for t, f in enumerate(frames)]
    events = [(kind, real_tick(at)) for kind, at in move.get('events', [])]
    compile_anim(move['name'], DROP_HOLD, lambda t: all_parts_ready(flutter(end, t)),
                 intro=SLOT_TICKS, intro_fn=lambda t: intro[min(t, SLOT_TICKS)],
                 shots=[real_tick(x) for x in shots], sounds=events, step=1)


def compile_baked(name, intro_fn, intro_len, loop_fn, **kw):
    """Animasi biasa (waktu nyata) yang bagian intro-nya dihaluskan pegas."""
    times = list(range(intro_len + 1))
    smooth_poses = spring_filter([all_parts_ready(intro_fn(t)) for t in times])
    compile_anim(name, DROP_HOLD, loop_fn, intro=intro_len,
                 intro_fn=lambda t: all_parts_ready(smooth_poses[min(t, intro_len)]), step=1, **kw)


def compile_drops(only):
    def want(name):
        return not only or name in only
    if want('drop_jatuh'):
        compile_baked('drop_jatuh', jatuh, JATUH_INTRO, lambda t: jatuh(JATUH_INTRO + t))
    for style in ('duduk', 'perut'):
        name = 'drop_jatuh_' + style
        if want(name):
            intro, loop = jatuh_style(style)
            compile_baked(name, intro, JATUH_INTRO, loop)
    if want('drop_salvo'):
        compile_baked('drop_salvo', windy(salvo, DROP_HOLD), DROP_HOLD,
                      lambda t: all_parts_ready(flutter(salvo(DROP_HOLD), t)), shots=SALVO_SHOTS)
    if want('drop_selam'):
        compile_baked('drop_selam', windy(selam, DROP_HOLD), DROP_HOLD,
                      lambda t: all_parts_ready(flutter(selam(DROP_HOLD), t)))
    for move in hero_moves():
        if want(move['name']):
            compile_hero(move)


ANIMS = [
    ('lobby_siaga', 240, siaga, True),
    ('lobby_bidik', 180, bidik, True),
    ('lobby_reload', 170, reload, False),
    ('lobby_berlutut', 210, berlutut, True),
    ('lobby_guling', 220, guling, False),
    ('lobby_ancam', 220, ancam, True),
]

OBSOLETE = ('lobby_santai', 'lobby_melambai', 'lobby_pundak', 'lobby_tegap',
            # gerakan cutscene versi lama (diganti tools/drop_moves.py)
            'drop_santai', 'drop_terjun', 'drop_reload', 'drop_terbalik', 'drop_putar', 'drop_ancam',
            'drop_hujan', 'drop_lempar', 'drop_kursi', 'drop_salto', 'drop_kata', 'drop_lengkung',
            'drop_koin', 'drop_rebahan', 'drop_jojo', 'drop_bor', 'drop_mendarat', 'drop_kartwil',
            'drop_jatuh_guling', 'drop_halo', 'drop_jaga', 'drop_tukik', 'drop_awas', 'drop_isi', 'drop_luncur',
            'drop_isyarat', 'drop_gulir', 'drop_siap', 'drop_samping', 'drop_runduk', 'drop_langit', 'drop_toleh',
            'drop_low', 'drop_profil', 'drop_press', 'drop_obor', 'drop_radio', 'drop_pompa', 'drop_bahu', 'drop_rapat')

if __name__ == '__main__':
    only = [a for a in sys.argv[1:] if not a.startswith('--')]
    for d in OUT_DIRS:
        for name in OBSOLETE:
            path = os.path.join(d, name + '.json')
            if os.path.exists(path):
                os.remove(path)
    compile_drops(only)
    for name, length, fn, intro in ANIMS:
        if only and name not in only:
            continue
        shots = SHOTS.get(name, [])
        sounds = SOUNDS.get(name, [])
        if intro:
            compile_anim(name, length, fn, intro=INTRO, intro_fn=ready_intro(fn), shots=[s + INTRO for s in shots],
                         sounds=[(k, x + INTRO) for k, x in sounds])
        else:
            compile_anim(name, length, fn, shots=shots, sounds=sounds)
