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
PARTS = ('head', 'torso', 'body', 'rightArm', 'leftArm', 'rightLeg', 'leftLeg', 'rightItem')
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


def compile_anim(name, length, fn, loop=True, intro=0, intro_fn=None, shots=(), sounds=()):
    """Sampel pose tiap STEP tick jadi keyframe LINEAR.

    Kalau ada intro_fn, tick 0..intro dimainkan sekali (intro_fn(t)), lalu
    loop fn(t) berulang dari returnTick = intro. Setiap sumbu yang pernah
    dipakai dikeluarkan di setiap sampel (supaya tidak ada interpolasi
    panjang yang tak sengaja), dan lompatan sudut > 180 derajat antar sampel
    ditandai dengan "turn" (Player Animator memutar penuh, bukan balik arah).
    """
    total = intro + length
    ticks = list(range(0, total + 1, STEP))
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


ANIMS = [
    ('lobby_siaga', 240, siaga, True),
    ('lobby_bidik', 180, bidik, True),
    ('lobby_reload', 170, reload, False),
    ('lobby_berlutut', 210, berlutut, True),
    ('lobby_guling', 220, guling, False),
    ('lobby_ancam', 220, ancam, True),
]

OBSOLETE = ('lobby_santai', 'lobby_melambai', 'lobby_pundak', 'lobby_tegap')

if __name__ == '__main__':
    only = [a for a in sys.argv[1:] if not a.startswith('--')]
    for d in OUT_DIRS:
        for name in OBSOLETE:
            path = os.path.join(d, name + '.json')
            if os.path.exists(path):
                os.remove(path)
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
