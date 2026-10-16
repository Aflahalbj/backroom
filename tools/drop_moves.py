"""Gerakan aksi cutscene perkenalan room (dipakai tools/lobby_anims.py).

Pose kuncinya diambil dari riset referensi (kartu operator Valorant/R6/CoD/
Apex, poster film action, posisi senapan militer: port arms, low ready,
press check, compressed ready; posisi terjun bebas dari panduan skydiving:
sit-fly, mantis, back-fly) lalu dikalibrasi di AnimDev dari sudut kamera
cutscene. Aturan pose yang dipakai: tidak simetris (kaki/lengan beda
>= 15 derajat), kepala selalu sedikit miring dan dagu turun, badan 3/4 ke
kamera, senapan tidak menunjuk lurus ke lensa, kaki terjun bebas rileks
(angin dari bawah, tidak ada kaki lurus menapak).

Tiap gerakan punya empat bagian (waktu ADEGAN, tick):
  0..4    antisipasi kecil dari pose jatuh
  4..17   masuk ke pose kunci bertahap: badan, lalu kepala, lengan, kaki,
          senapan paling akhir (overlap; filter pegas menambah follow-through)
  ~15..24 satu aksi di bullet time (tembakan, cek kamar, kokang, menoleh)
  24..35  moving hold: napas, ayunan pelan, mata ke kamera paling akhir

Tiap gerakan: dict name, pose(s) -> Pose tanpa recoil, shots (waktu
adegan), power/climb recoil, events [(jenis, waktu adegan)].
"""
import math

from lobby_anims import Pose, all_parts_ready, combat, fall_hold, clamp01, lerp_pose

# ------------------------------------------------------------ kaki terjun bebas
# FA rileks setengah duduk, SIT sit-fly.
FA = {'rightLeg': {'pitch': -20, 'bend': 30, 'roll': 10}, 'leftLeg': {'pitch': -35, 'bend': 55, 'roll': -10}}


SIT = {'rightLeg': {'pitch': -78, 'bend': 80, 'roll': 12, 'yaw': 8}, 'leftLeg': {'pitch': -88, 'bend': 95, 'roll': -14, 'yaw': -8}}


def key(aim=None, legs=None, **parts):
    """Pose kunci. aim=None: semua nilai mutlak; aim=angka: di atas pose
    memegang senapan TACZ (combat) dengan nilai sebagai tambahan."""
    if aim is None:
        p = Pose()
        for part, axes in parts.items():
            p[part] = dict(axes)
    else:
        p = combat(0, aim, facing=0)
        for part, axes in parts.items():
            p.add(part, **axes)
    if legs is not None:
        p['rightLeg'] = dict(legs['rightLeg'])
        p['leftLeg'] = dict(legs['leftLeg'])
    return all_parts_ready(p)


def smoother(x):
    x = clamp01(x)
    return x * x * x * (x * (x * 6 - 15) + 10)


def ramp(s, a, b):
    return smoother((s - a) / (b - a))


def pulse(s, a, b):
    """0 -> 1 -> 0 dengan halus antara a dan b."""
    if s <= a or s >= b:
        return 0.0
    return 0.5 - 0.5 * math.cos(2 * math.pi * (s - a) / (b - a))


# Urutan masuk ke pose (overlap): badan memimpin, senapan paling akhir.
ENTRY = {'body': (4, 13), 'torso': (5, 14), 'head': (6, 15), 'rightArm': (7, 16), 'leftArm': (8, 17),
         'rightLeg': (8, 18), 'leftLeg': (9, 19), 'rightItem': (9, 17), 'leftItem': (9, 17)}


def enter(s, target, entry=ENTRY):
    """Dari pose jatuh ke pose kunci, tiap bagian dengan waktunya sendiri."""
    start = all_parts_ready(fall_hold(s))
    out = Pose()
    for part in set(start) | set(target):
        a, b = entry.get(part, (6, 16))
        w = ramp(s, a, b)
        sa, ta = start.get(part, {}), target.get(part, {})
        for axis in set(sa) | set(ta):
            va, vb = sa.get(axis, 0.0), ta.get(axis, 0.0)
            out.setdefault(part, {})[axis] = va + (vb - va) * w
    # Antisipasi: sebelum bergerak, badan sedikit condong ke arah sebaliknya.
    anti = pulse(s, 0, 7)
    out.add('body', roll=-4 * anti, pitch=3 * anti)
    out.add('rightArm', pitch=6 * anti)
    out.add('leftArm', pitch=6 * anti)
    return out


def alive(p, s, amount=1.0):
    """Moving hold: napas di dada, kaki dan badan melayang pelan (sinus tak
    sebanding, tidak pernah benar-benar diam)."""
    k = amount * ramp(s, 10, 20)
    br = math.sin(s * 2 * math.pi / 22.0)
    p.add('torso', pitch=-1.2 * br * k, y=-0.15 * br * k)
    p.add('head', pitch=0.8 * br * k)
    p.add('rightArm', y=-0.15 * br * k)
    p.add('leftArm', y=-0.15 * br * k)
    p.add('body', roll=2.0 * k * math.sin(s * 0.29 + 1.0), yaw=2.5 * k * math.sin(s * 0.17 + 2.0))
    p.add('rightLeg', pitch=3.0 * k * math.sin(s * 0.23 + 0.5), bend=3.0 * k * math.sin(s * 0.31))
    p.add('leftLeg', pitch=3.0 * k * math.sin(s * 0.19 + 2.2), bend=3.0 * k * math.sin(s * 0.27 + 1.0))
    return p


def look(p, s, at, yaw, pitch=0.0, roll=0.0, dur=5.0):
    """Mata/kepala ke kamera di akhir (head leads, tapi menatap paling akhir)."""
    w = ramp(s, at, at + dur)
    p.add('head', yaw=yaw * w, pitch=pitch * w, roll=roll * w)
    return p


def done(p):
    return all_parts_ready(p)


# ------------------------------------------------------------ pose kunci
# Sepuluh siluet yang berbeda total (tegak, duduk, terbalik, mendatar ke
# bawah, mendatar ke atas, miring, panah, lengan ke atas, X, bola), semuanya
# posisi terjun bebas sungguhan. Sudut kamera tiap pose (Java DropMoves)
# dipilih di lab supaya bentuknya paling terbaca.

K_PORT = key(head={'yaw': 15, 'pitch': 8, 'roll': 5}, body={'pitch': 8, 'y': 0.05}, torso={'bend': -3},
             rightArm={'pitch': -20, 'yaw': -30, 'roll': 8, 'bend': -90}, rightItem={'pitch': 10},
             leftArm={'pitch': -55, 'yaw': 22, 'bend': -70}, legs=FA)
K_DUDUK = key(aim=1.0, body={'pitch': 6, 'y': 0.12}, head={'roll': 7, 'pitch': 2}, torso={'bend': 4}, legs=SIT)
K_KEPALA = key(head={'pitch': 6, 'roll': -8}, body={'roll': 152, 'y': 0.4},
               rightArm={'pitch': -170, 'yaw': -6, 'roll': 6, 'bend': -20}, rightItem={'pitch': -25},
               leftArm={'pitch': -110, 'roll': -85, 'bend': -20},
               rightLeg={'pitch': -60, 'bend': 85, 'roll': 6}, leftLeg={'pitch': 30, 'bend': 80, 'roll': -8})
K_MANTIS = key(aim=1.0, body={'pitch': -70, 'y': 0.24}, head={'pitch': -40, 'roll': 6}, torso={'bend': -8},
               legs={'rightLeg': {'pitch': 22, 'bend': 70, 'roll': 12}, 'leftLeg': {'pitch': 16, 'bend': 84, 'roll': -12}})
K_TELENTANG = key(aim=1.0, body={'pitch': 76, 'y': 0.24}, head={'pitch': -8, 'roll': 4}, torso={'bend': 6},
                  rightArm={'yaw': 38}, leftArm={'yaw': 38},
                  legs={'rightLeg': {'pitch': -85, 'bend': 85, 'roll': 10}, 'leftLeg': {'pitch': -95, 'bend': 100, 'roll': -10}})
K_MIRING = key(aim=0.2, body={'roll': 55, 'y': 0.12}, head={'roll': -38, 'pitch': 6},
               legs={'rightLeg': {'pitch': -30, 'bend': 60, 'roll': 8}, 'leftLeg': {'pitch': 5, 'bend': 15, 'roll': -25}})
K_MIRING['leftArm'] = {'pitch': -30, 'yaw': 0, 'roll': -95, 'bend': -10, 'x': 0, 'y': 0, 'z': 0}
K_TUKIK = key(head={'pitch': -68, 'roll': 4}, body={'pitch': -132, 'y': 0.3}, torso={'bend': -10},
              rightArm={'pitch': -168, 'yaw': -4, 'roll': 6, 'bend': -8}, rightItem={'pitch': -25},
              leftArm={'pitch': -158, 'yaw': 10, 'roll': -8, 'bend': -22},
              rightLeg={'pitch': 14, 'bend': 10, 'roll': 4}, leftLeg={'pitch': 8, 'bend': 24, 'roll': -5})
K_ANGKAT = key(head={'pitch': -22, 'roll': 4}, body={'pitch': 8, 'roll': 6, 'y': 0.05}, torso={'bend': -4},
               rightArm={'pitch': -135, 'yaw': 8, 'roll': 10, 'bend': -5}, rightItem={'pitch': -25},
               leftArm={'pitch': -25, 'roll': -62, 'bend': -35},
               rightLeg={'pitch': -55, 'bend': 70, 'roll': 6}, leftLeg={'pitch': 30, 'bend': 40, 'roll': -6})
K_BINTANG = key(head={'pitch': -10, 'roll': 7}, body={'pitch': -6, 'y': 0.05},
                rightArm={'pitch': -15, 'roll': 85, 'bend': -12}, rightItem={'pitch': -25},
                leftArm={'pitch': -10, 'roll': -80, 'bend': -22},
                rightLeg={'pitch': -8, 'bend': 20, 'roll': 26}, leftLeg={'pitch': -18, 'bend': 35, 'roll': -22})
K_RINGKUK = key(head={'pitch': 18, 'roll': 6}, body={'pitch': -12, 'y': 0.25}, torso={'bend': 22},
                rightArm={'pitch': -40, 'roll': 8, 'bend': -100}, rightItem={'pitch': 70},
                leftArm={'pitch': -65, 'yaw': 25, 'bend': -95},
                rightLeg={'pitch': -105, 'bend': 120, 'roll': 8}, leftLeg={'pitch': -95, 'bend': 125, 'roll': -10})

# Perubahan arah badan besar (terbalik, mendatar) dimulai lebih awal, saat masih jatuh masuk bingkai.
FLIP = dict(ENTRY, body=(1, 13), torso=(2, 14), head=(4, 15))


# ------------------------------------------------------------ gerakan

def port(s):
    """Port arms (kartu operator, poster lineup): senapan diagonal di dada,
    mengendap, lalu dagu turun dan menatap kamera."""
    p = enter(s, K_PORT)
    p.add('rightItem', pitch=-6 * pulse(s, 15, 22))
    p.add('leftArm', pitch=-4 * pulse(s, 15, 22))
    look(p, s, 20, 10, 4, 3)
    return done(alive(p, s))


def duduk(s):
    """Sit-fly membidik: duduk di udara, senapan dibahukan menyamping, dua
    tembakan terkendali, lalu pipi terangkat dari popor menatap kamera."""
    p = enter(s, K_DUDUK)
    lift = ramp(s, 24, 29)
    p.add('head', pitch=-6 * lift, roll=-3 * lift)
    look(p, s, 25, 16, -2, 0)
    return done(alive(p, s))


def kepala(s):
    """Head-down (freefly daffy): masuk bingkai sudah terbalik dengan badan
    miring, senapan diulurkan ke jurang, dua tembakan ke bawah."""
    # Badan sudah terbalik sejak awal giliran (tidak berputar di depan kamera);
    # lengan dan kaki tetap bergerak masuk ke pose seperti gerakan lain.
    p = enter(s, K_KEPALA, dict(ENTRY, body=(-1, 0), torso=(-1, 0), head=(-1, 0)))
    # Menjaga keseimbangan terbalik: kaki mengayuh pelan, lengan kiri mengoreksi arah.
    k = ramp(s, 12, 19)
    p.add('rightLeg', pitch=12 * k * math.sin(s * 0.55), bend=8 * k * math.sin(s * 0.55 + 1.2))
    p.add('leftLeg', pitch=-12 * k * math.sin(s * 0.55 + 0.4), bend=8 * k * math.sin(s * 0.55 + 2.0))
    p.add('leftArm', pitch=14 * k * math.sin(s * 0.42 + 0.7), roll=-10 * k * math.sin(s * 0.33 + 1.9))
    p.add('body', roll=4 * k * math.sin(s * 0.27))
    return done(alive(p, s, 0.7))


def mantis(s):
    """Mantis (belly paling cocok untuk senapan): menelungkup, dada sedikit
    terangkat, membidik ke bawah, satu tembakan."""
    p = enter(s, K_MANTIS, FLIP)
    look(p, s, 25, 10, 10, 2)
    return done(alive(p, s))


def telentang(s):
    """Back-fly: berbalik telentang, muka menghadap ke atas, senapan membidik
    serong ke atas di sisi kanan (muka tidak tertutup), rentetan pendek."""
    p = enter(s, K_TELENTANG, FLIP)
    return done(alive(p, s))


def miring(s):
    """Side-fly: badan miring ke samping, lengan kiri kaku menahan angin,
    senapan low ready; kepala tegak sejajar cakrawala lalu menatap kamera."""
    p = enter(s, K_MIRING, FLIP)
    look(p, s, 22, 12, -4, 0)
    return done(alive(p, s))


def tukik(s):
    """Menukik: badan condong jauh kepala duluan, kedua lengan lurus ke
    depan memegang senapan seperti tombak, kaki lurus ke belakang, kepala
    mendongak menatap kamera."""
    p = enter(s, K_TUKIK, FLIP)
    # Kamera di sisi kanan depan: kepala menoleh sedikit supaya muka terlihat.
    look(p, s, 16, 28, -6, 0, dur=7.0)
    return done(alive(p, s, 0.6))


def angkat(s):
    """Angkat senjata (tembakan ke langit): senapan diacungkan serong ke atas
    dengan satu tangan dan ditembakkan tiga kali ke udara, tangan kiri
    membentang menahan keseimbangan, kaki terbuka; lalu menatap kamera."""
    p = enter(s, K_ANGKAT)
    look(p, s, 22, -6, 14, 0)
    return done(alive(p, s))


def bintang(s):
    """Bintang X (posisi bebas klasik): lengan membentang, senapan terulur di
    tangan kanan, kaki mengangkang; lengan berputar seperti kincir karena
    hilang keseimbangan, berputar pelan sambil menatap kamera."""
    p = enter(s, K_BINTANG)
    p.add('body', yaw=10 * math.sin(max(0.0, s - 12) * 0.18) * ramp(s, 12, 18))
    # Hilang keseimbangan: lengan berputar seperti kincir (berlawanan fase,
    # lengan senapan lebih kecil), kaki ikut menendang, badan oleng.
    k = ramp(s, 13, 19)
    w = 2 * math.pi / 7.0
    p.add('leftArm', pitch=42 * k * math.sin(w * s), roll=-26 * k * math.cos(w * s))
    p.add('rightArm', pitch=22 * k * math.sin(w * s + math.pi + 0.6), roll=14 * k * math.cos(w * s + math.pi + 0.6))
    p.add('rightLeg', pitch=10 * k * math.sin(w * 0.8 * s + 1.0))
    p.add('leftLeg', pitch=-10 * k * math.sin(w * 0.8 * s + 1.0))
    p.add('body', roll=5 * k * math.sin(w * 0.5 * s + 0.3), pitch=3 * k * math.sin(w * 0.7 * s))
    look(p, s, 20, 8, -4, 2)
    return done(alive(p, s))


def ringkuk(s):
    """Meringkuk (cannonball): lutut ke dada memeluk senapan tegak, lalu
    sedikit membuka dan menatap kamera dari balik senapan."""
    p = enter(s, K_RINGKUK)
    open_ = ramp(s, 21, 28)
    p.add('rightLeg', pitch=20 * open_, bend=-25 * open_)
    p.add('leftLeg', pitch=12 * open_, bend=-15 * open_)
    p.add('torso', bend=-10 * open_)
    look(p, s, 21, 10, -18, 2)
    return done(alive(p, s))



MOVES = [
    dict(name='drop_port', pose=port, shots=[]),
    dict(name='drop_duduk', pose=duduk, shots=[19, 21], power=1.2),
    dict(name='drop_kepala', pose=kepala, shots=[19, 21.5], power=1.1),
    dict(name='drop_mantis', pose=mantis, shots=[20], power=1.3),
    dict(name='drop_telentang', pose=telentang, shots=[18, 19.2, 20.4], power=0.9),
    dict(name='drop_miring', pose=miring, shots=[]),
    dict(name='drop_tukik', pose=tukik, shots=[]),
    dict(name='drop_angkat', pose=angkat, shots=[17, 18.6, 20.2], power=0.8),
    dict(name='drop_bintang', pose=bintang, shots=[]),
    dict(name='drop_ringkuk', pose=ringkuk, shots=[]),
]
