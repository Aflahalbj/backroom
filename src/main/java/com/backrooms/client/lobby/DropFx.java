package com.backrooms.client.lobby;

import com.backrooms.lobby.LobbyWeapon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Efek dunia cutscene jatuh: peluru yang terlihat melesat saat waktu
 * melambat (dengan riak udara ala The Matrix), selongsong kuningan yang
 * berputar melayang, gelombang kejut, kilau, dan percikan.
 *
 * <p>Semua posisi disimpan relatif ke titik jatuh bersama
 * ({@link DropCutscene#origin()}) dan dihitung dengan waktu adegan
 * ({@link DropCutscene#scene()}): ikut jatuh bersama para anggota dan ikut
 * melambat saat bullet time. Digambar aditif ({@link RenderType#lightning()})
 * supaya menyala di kegelapan dan tetap tampil di bawah renderer Veil.
 */
final class DropFx {

    /** Kecepatan peluru (blok/detik waktu adegan): saat 0,08x terlihat merayap. */
    private static final double BULLET_SPEED = 80.0D;
    private static final double BULLET_RANGE = 40.0D;
    private static final RandomSource RANDOM = RandomSource.create();

    private abstract static class Fx {
        final double born = DropCutscene.scene();
        Vec3 rel;

        Fx(Vec3 rel) {
            this.rel = rel;
        }

        double age() {
            return DropCutscene.scene() - born;
        }

        /** Gambar; {@code false} kalau sudah habis. */
        abstract boolean draw(Quads q, Vec3 origin);
    }

    private static final List<Fx> FX = new ArrayList<>();

    private DropFx() {}

    static void clear() {
        FX.clear();
    }

    // ============================================================ pemicu

    /** Satu tembakan dari moncong di {@code muzzle} (dunia) ke arah {@code barrel}. */
    static void shot(Vec3 muzzle, Vec3 barrel, Vec3 side, LobbyWeapon weapon) {
        if (!DropCutscene.isRunning()) return;
        Vec3 rel = muzzle.subtract(DropCutscene.origin());
        boolean shotgun = weapon == LobbyWeapon.M870;
        if (shotgun) {
            // Sembilan butir menyebar dalam kerucut.
            for (int i = 0; i < 9; i++) {
                Vec3 spread = barrel.add(RANDOM.nextGaussian() * 0.07D, RANDOM.nextGaussian() * 0.07D, RANDOM.nextGaussian() * 0.07D).normalize();
                FX.add(new Tracer(rel, spread, 0.6F, false));
            }
        } else {
            FX.add(new Tracer(rel, barrel, weapon == LobbyWeapon.AWP ? 1.6F : 1.0F, true));
        }
        // Selongsong dilempar ke kanan-atas senjata, berputar.
        Vec3 port = rel.subtract(barrel.scale(shotgun ? 0.5D : 0.45D));
        Vec3 up = side.cross(barrel).normalize();
        Vec3 velocity = side.scale(1.8D + RANDOM.nextDouble() * 0.8D).add(up.scale(-0.9D - RANDOM.nextDouble() * 0.6D))
                .add(barrel.scale(-0.3D)).add(RANDOM.nextGaussian() * 0.15D, RANDOM.nextGaussian() * 0.15D, RANDOM.nextGaussian() * 0.15D);
        FX.add(new Casing(port, velocity, shotgun));
        // Asap tipis dan percikan di moncong.
        for (int i = 0; i < 5; i++) {
            Vec3 v = barrel.scale(2.0D + RANDOM.nextDouble() * 3.0D).add(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian());
            FX.add(new Spark(rel, v, 0.12D + RANDOM.nextDouble() * 0.1D, 255, 190, 90));
        }
    }

    /** Gelombang kejut melingkar di sekitar {@code center} (dunia), bidangnya tegak lurus {@code normal}. */
    static void shockwave(Vec3 center, Vec3 normal, float size) {
        FX.add(new Ring(center.subtract(DropCutscene.origin()), normal.normalize(), size));
    }

    /** Kilau bintang (lensa/teropong/koin). */
    static void glint(Vec3 at) {
        FX.add(new Glint(at.subtract(DropCutscene.origin())));
    }

    /** Sebutir debu/serpihan plafon yang jatuh dari {@code at}. */
    static void dust(Vec3 at) {
        Vec3 v = new Vec3(RANDOM.nextGaussian() * 0.15D, -0.5D - RANDOM.nextDouble() * 1.5D, RANDOM.nextGaussian() * 0.15D);
        FX.add(new Grain(at.subtract(DropCutscene.origin()), v, 0.9D + RANDOM.nextDouble() * 0.8D));
    }

    /** Ledakan percikan di {@code at}. */
    static void sparks(Vec3 at, int count, int r, int g, int b) {
        Vec3 rel = at.subtract(DropCutscene.origin());
        for (int i = 0; i < count; i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize().scale(2.0D + RANDOM.nextDouble() * 5.0D);
            FX.add(new Spark(rel, v, 0.25D + RANDOM.nextDouble() * 0.35D, r, g, b));
        }
    }

    // ============================================================ gambar

    static void render(PoseStack pose, MultiBufferSource buffers, Vec3 cam) {
        if (FX.isEmpty()) return;
        Quads q = new Quads(buffers.getBuffer(RenderType.lightning()), pose.last().pose(), cam);
        Vec3 origin = DropCutscene.origin();
        Iterator<Fx> it = FX.iterator();
        while (it.hasNext()) {
            if (!it.next().draw(q, origin)) it.remove();
        }
    }

    /** Peluru: kepala putih-kuning menyala, ekor memudar, riak udara melingkar di jalurnya. */
    private static final class Tracer extends Fx {
        final Vec3 dir;
        final float size;
        final boolean ripples;

        Tracer(Vec3 rel, Vec3 dir, float size, boolean ripples) {
            super(rel);
            this.dir = dir;
            this.size = size;
            this.ripples = ripples;
        }

        @Override
        boolean draw(Quads q, Vec3 origin) {
            double travel = age() * BULLET_SPEED;
            if (travel > BULLET_RANGE) return false;
            Vec3 start = origin.add(rel);
            Vec3 head = start.add(dir.scale(travel));
            Vec3 tail = start.add(dir.scale(Math.max(0.0D, travel - 1.6D * size)));
            q.line(tail, head, 0.018F * size, 255, 210, 120, 0, 230);
            q.line(head.subtract(dir.scale(0.25D)), head, 0.035F * size, 255, 250, 230, 200, 255);
            if (ripples) {
                // Riak udara: cincin tipis yang mengembang di belakang peluru.
                for (int k = 1; k <= 7; k++) {
                    double at = k * 0.85D;
                    if (at > travel) break;
                    double ringAge = (travel - at) / BULLET_SPEED;
                    float fade = (float) (1.0D - ringAge / 0.4D);
                    if (fade <= 0.0F) continue;
                    double radius = 0.05D + Math.min(0.32D, ringAge * 1.4D) * size;
                    q.ring(start.add(dir.scale(at)), dir, radius, 0.012D, 24, 200, 225, 255, (int) (120 * fade));
                }
            }
            return true;
        }
    }

    /** Selongsong kuningan (atau selongsong shotgun merah) yang berputar melayang. */
    private static final class Casing extends Fx {
        final Vec3 velocity;
        final Vec3 axis;
        final boolean shell;

        Casing(Vec3 rel, Vec3 velocity, boolean shell) {
            super(rel);
            this.velocity = velocity;
            this.axis = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
            this.shell = shell;
        }

        @Override
        boolean draw(Quads q, Vec3 origin) {
            double age = age();
            if (age > 3.0D) return false;
            // Jatuh bebas bersama semua orang: relatif hanya melayang dengan kecepatan lemparnya (sedikit hambatan udara).
            Vec3 at = origin.add(rel).add(velocity.scale(age / (1.0D + age * 0.6D)));
            double angle = age * 16.0D;
            Vec3 perp = axis.cross(new Vec3(0.3D, 1.0D, 0.2D)).normalize();
            Vec3 along = perp.scale(Math.cos(angle)).add(axis.cross(perp).scale(Math.sin(angle)));
            double half = shell ? 0.07D : 0.05D;
            float width = shell ? 0.034F : 0.02F;
            int a = (int) (255 * Math.min(1.0D, (3.0D - age) / 0.5D));
            if (shell) {
                q.line(at.subtract(along.scale(half)), at.add(along.scale(half * 0.4D)), width, 210, 40, 30, a, a);
                q.line(at.add(along.scale(half * 0.4D)), at.add(along.scale(half)), width, 230, 180, 70, a, a);
            } else {
                q.line(at.subtract(along.scale(half)), at.add(along.scale(half)), width, 240, 180, 70, a, a);
            }
            return true;
        }
    }

    /** Gelombang kejut: cincin yang mengembang cepat lalu memudar. */
    private static final class Ring extends Fx {
        final Vec3 normal;
        final float size;

        Ring(Vec3 rel, Vec3 normal, float size) {
            super(rel);
            this.normal = normal;
            this.size = size;
        }

        @Override
        boolean draw(Quads q, Vec3 origin) {
            double u = age() / 0.55D;
            if (u > 1.0D) return false;
            double eased = 1.0D - Math.pow(1.0D - u, 3.0D);
            double radius = 0.3D + eased * 3.6D * size;
            int a = (int) (220 * (1.0D - u));
            Vec3 c = origin.add(rel);
            q.ring(c, normal, radius, 0.09D * (1.0D - u * 0.6D) * size, 48, 210, 235, 255, a);
            q.ring(c, normal, radius * 0.82D, 0.03D * size, 48, 255, 255, 255, a / 2);
            return true;
        }
    }

    /** Kilau bintang empat sudut yang muncul dan menghilang cepat. */
    private static final class Glint extends Fx {
        Glint(Vec3 rel) {
            super(rel);
        }

        @Override
        boolean draw(Quads q, Vec3 origin) {
            double u = age() / 0.35D;
            if (u > 1.0D) return false;
            float s = (float) Math.sin(Math.PI * u);
            q.star(origin.add(rel), 0.55F * s, 0.02F * s + 0.004F, 255, 250, 220, (int) (255 * s));
            return true;
        }
    }

    /** Debu: butir kecil kekuningan yang jatuh makin cepat (gravitasi) dan memudar. */
    private static final class Grain extends Fx {
        final Vec3 velocity;
        final double life;
        final float size = 0.008F + RANDOM.nextFloat() * 0.012F;

        Grain(Vec3 rel, Vec3 velocity, double life) {
            super(rel);
            this.velocity = velocity;
            this.life = life;
        }

        @Override
        boolean draw(Quads q, Vec3 origin) {
            double age = age();
            if (age > life) return false;
            Vec3 at = origin.add(rel).add(velocity.scale(age)).subtract(0.0D, 4.0D * age * age, 0.0D);
            int a = (int) (110 * Math.min(1.0D, (life - age) / 0.3D) * Math.min(1.0D, age / 0.1D));
            // Garis jatuh pendek (butir kabur karena gerak), warna debu redup.
            Vec3 tail = at.add(0.0D, 0.06D + 0.16D * age, 0.0D);
            q.line(tail, at, size, 150, 135, 110, 0, a);
            return true;
        }
    }

    /** Percikan: garis pendek terang yang terbang dan memudar. */
    private static final class Spark extends Fx {
        final Vec3 velocity;
        final double life;
        final int r, g, b;

        Spark(Vec3 rel, Vec3 velocity, double life, int r, int g, int b) {
            super(rel);
            this.velocity = velocity;
            this.life = life;
            this.r = r;
            this.g = g;
            this.b = b;
        }

        @Override
        boolean draw(Quads q, Vec3 origin) {
            double age = age();
            if (age > life) return false;
            Vec3 at = origin.add(rel).add(velocity.scale(age));
            Vec3 back = at.subtract(velocity.scale(0.03D));
            int a = (int) (255 * (1.0D - age / life));
            q.line(back, at, 0.012F, r, g, b, 0, a);
            return true;
        }
    }

    // ===================================================== bentuk dasar

    /** Penulis segi empat aditif menghadap kamera. */
    static final class Quads {
        private final VertexConsumer vc;
        private final Matrix4f m;
        private final Vec3 cam;

        Quads(VertexConsumer vc, Matrix4f m, Vec3 cam) {
            this.vc = vc;
            this.m = m;
            this.cam = cam;
        }

        /** Garis dari {@code a} ke {@code b}, lebar {@code width}, alfa {@code aA} di a dan {@code aB} di b. */
        void line(Vec3 a, Vec3 b, float width, int r, int g, int bl, int aA, int aB) {
            Vec3 d = b.subtract(a);
            Vec3 toCam = cam.subtract(a);
            Vec3 side = d.cross(toCam);
            if (side.lengthSqr() < 1.0E-10D) return;
            side = side.normalize().scale(width);
            vertex(a.subtract(side), r, g, bl, aA);
            vertex(a.add(side), r, g, bl, aA);
            vertex(b.add(side), r, g, bl, aB);
            vertex(b.subtract(side), r, g, bl, aB);
        }

        /** Cincin di bidang tegak lurus {@code normal}. */
        void ring(Vec3 c, Vec3 normal, double radius, double thick, int segments, int r, int g, int b, int a) {
            if (a <= 0) return;
            Vec3 u = normal.cross(Math.abs(normal.y) < 0.9D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
            Vec3 v = normal.cross(u).normalize();
            for (int i = 0; i < segments; i++) {
                double a0 = Math.PI * 2.0D * i / segments;
                double a1 = Math.PI * 2.0D * (i + 1) / segments;
                Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
                Vec3 d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
                vertex(c.add(d0.scale(radius - thick)), r, g, b, a);
                vertex(c.add(d1.scale(radius - thick)), r, g, b, a);
                vertex(c.add(d1.scale(radius + thick)), r, g, b, 0);
                vertex(c.add(d0.scale(radius + thick)), r, g, b, 0);
            }
        }

        /** Bintang empat sudut menghadap kamera. */
        void star(Vec3 c, float size, float width, int r, int g, int b, int a) {
            Vec3 view = cam.subtract(c).normalize();
            Vec3 up = Math.abs(view.y) < 0.95D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 x = view.cross(up).normalize();
            Vec3 y = x.cross(view).normalize();
            line(c.subtract(x.scale(size)), c, width, r, g, b, 0, a);
            line(c, c.add(x.scale(size)), width, r, g, b, a, 0);
            line(c.subtract(y.scale(size * 0.7D)), c, width, r, g, b, 0, a);
            line(c, c.add(y.scale(size * 0.7D)), width, r, g, b, a, 0);
        }

        private void vertex(Vec3 p, int r, int g, int b, int a) {
            vc.vertex(m, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).color(r, g, b, a).endVertex();
        }
    }
}
