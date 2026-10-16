package com.backrooms.client.lobby;

import com.backrooms.block.Lights;
import com.backrooms.lobby.DropTiming;
import com.backrooms.lobby.LobbyStage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pemandangan cutscene jatuh ({@link DropCutscene}):
 * <ul>
 *   <li>lantai lobby di bawah barisan dilubangi (hanya di klien ini, dan
 *       dikembalikan sesudahnya); balok-baloknya jadi puing yang ikut jatuh,
 *       mulai dari tengah lalu merambat ke tepi seperti retakan;</li>
 *   <li>pecahan dunia Backrooms (dinding, plafon, karpet dari set lobby itu
 *       sendiri) melayang di kegelapan sepanjang jalur jatuh;</li>
 *   <li>garis angin yang memanjang sesuai kecepatan jatuh (memendek saat
 *       waktu melambat).</li>
 * </ul>
 */
final class DropScenery {

    private static final double DEBRIS_GRAVITY = 21.0D;
    private static final double DEBRIS_TERMINAL = 21.0D;
    private static final int HOLE_DEPTH = 6;
    /** Garis angin dibuat per lapisan setebal ini (blok). */
    private static final int STREAK_LAYER = 3;

    private record Removed(BlockPos pos, BlockState state) {}

    private record Debris(BlockState state, Vec3 start, double delay, double vx, double vz, float spinX, float spinZ, int light) {}

    private static ClientLevel level;
    private static final List<Removed> REMOVED = new ArrayList<>();
    private static final List<Debris> DEBRIS = new ArrayList<>();
    private static List<BlockState> palette = List.of();
    private static int minX, maxX, minZ, maxZ, floorY;
    private static Vec3 axis = Vec3.ZERO;
    private static boolean broken;
    /** Lampu neon plafon di atas barisan, urut dari yang terdekat; meledak satu per satu saat lantai runtuh. */
    private static final List<BlockPos> LIGHTS = new ArrayList<>();
    private static int lightsBlown;
    /** Keadaan asli lampu (dikembalikan sesudah cutscene; lampu berkedip saat gempa). */
    private static final java.util.Map<BlockPos, BlockState> LIGHT_STATES = new java.util.HashMap<>();
    /** Balok lantai yang retak saat gempa (dari tengah ke tepi) dan tahap retaknya sekarang. */
    private static final List<BlockPos> CRACKS = new ArrayList<>();
    private static int[] crackStage = new int[0];


    private DropScenery() {}

    /** Catat area lubang (di bawah semua anggota) dan bahan pecahan dari set lobby di sekitarnya. */
    static void prepare(ClientLevel lvl, List<Vec3> feet, LobbyStage stage) {
        restore();
        level = lvl;
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (Vec3 f : feet) {
            x0 = Math.min(x0, f.x);
            x1 = Math.max(x1, f.x);
            z0 = Math.min(z0, f.z);
            z1 = Math.max(z1, f.z);
        }
        minX = Mth.floor(x0 - 1.3D);
        maxX = Mth.floor(x1 + 1.3D);
        minZ = Mth.floor(z0 - 1.3D);
        maxZ = Mth.floor(z1 + 1.3D);
        floorY = stage.pos().getY() - 1;
        axis = new Vec3((x0 + x1) / 2.0D, floorY, (z0 + z1) / 2.0D);
        broken = false;

        // Bahan pecahan: balok padat yang paling banyak di sekitar panggung (dinding, plafon, lantai).
        Set<BlockState> found = new LinkedHashSet<>();
        BlockPos center = stage.pos();
        for (int dy = 6; dy >= -2 && found.size() < 6; dy--) {
            for (int dx = -7; dx <= 7 && found.size() < 6; dx++) {
                for (int dz = -7; dz <= 7 && found.size() < 6; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    BlockState state = lvl.getBlockState(p);
                    if (!state.isAir() && state.getRenderShape() == RenderShape.MODEL && state.isSolidRender(lvl, p)) found.add(state);
                }
            }
        }
        palette = found.isEmpty() ? List.of(Blocks.YELLOW_WOOL.defaultBlockState()) : List.copyOf(found);

        LIGHTS.clear();
        lightsBlown = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-9, 1, -9), center.offset(9, 8, 9))) {
            BlockState state = lvl.getBlockState(p);
            if (state.hasProperty(Lights.ON) && state.getValue(Lights.ON)) LIGHTS.add(p.immutable());
        }
        LIGHTS.sort(java.util.Comparator.comparingDouble(p -> p.distToCenterSqr(axis.x, center.getY() + 3, axis.z)));
        LIGHT_STATES.clear();
        for (BlockPos p : LIGHTS) LIGHT_STATES.put(p, lvl.getBlockState(p));

        // Retakan hanya di balok yang nanti runtuh (lubangnya), merambat dari tengah.
        CRACKS.clear();
        RandomSource pick = RandomSource.create(minX * 7919L + minZ * 31L);
        double halfX = (maxX - minX) / 2.0D + 0.5D;
        double halfZ = (maxZ - minZ) / 2.0D + 0.5D;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos p = new BlockPos(x, floorY, z);
                if (lvl.getBlockState(p).isAir()) continue;
                double edge = Math.max(Math.abs(x + 0.5D - axis.x) / halfX, Math.abs(z + 0.5D - axis.z) / halfZ);
                if (pick.nextFloat() < 0.9F - 0.4F * edge) CRACKS.add(p);
            }
        }
        CRACKS.sort(java.util.Comparator.comparingDouble(p -> p.distToCenterSqr(axis.x, floorY, axis.z)));
        crackStage = new int[CRACKS.size()];
        java.util.Arrays.fill(crackStage, -1);
    }

    /**
     * Gempa sebelum lantai runtuh: retakan (tekstur retak blok bawaan)
     * merambat dari tengah ke tepi dan makin dalam, lampu plafon berkedip
     * makin sering. {@code t} ms sejak cutscene mulai.
     */
    static void quake(long t) {
        if (level == null || Minecraft.getInstance().level != level) return;
        long start = DropTiming.TREMOR;
        long end = DropTiming.BREAK;
        if (t < end) {
            for (int i = 0; i < CRACKS.size(); i++) {
                BlockPos p = CRACKS.get(i);
                double dist = Math.sqrt(p.distToCenterSqr(axis.x, floorY, axis.z));
                // Mulai dari tengah, merambat ~0,8 balok per 100 ms.
                double u = (t - start - dist * 125.0D) / (double) (end - start - 300L);
                crackStage[i] = u <= 0.0D ? -1 : Math.min(7, (int) (u * 8.0D));
            }
        }
        // Lampu berkedip: makin lama makin sering mati.
        if (t >= start && t < DropTiming.DROP) {
            float intensity = Mth.clamp((t - start) / (float) (end - start), 0.0F, 1.0F);
            long slot = t / 70L;
            for (int i = 0; i < LIGHTS.size(); i++) {
                BlockPos p = LIGHTS.get(i);
                BlockState original = LIGHT_STATES.get(p);
                BlockState now = level.getBlockState(p);
                if (original == null || !now.hasProperty(Lights.ON)) continue;
                RandomSource r = RandomSource.create(slot * 31L + i * 977L);
                boolean off = r.nextFloat() < 0.08F + 0.45F * intensity;
                if (now.getValue(Lights.ON) == off) level.setBlock(p, now.setValue(Lights.ON, !off), 3);
            }
        }
    }

    private static final ResourceLocation[] CRACK_TEXTURES = new ResourceLocation[10];

    static {
        for (int i = 0; i < 10; i++) CRACK_TEXTURES[i] = new ResourceLocation("minecraft", "textures/block/destroy_stage_" + i + ".png");
    }

    /**
     * Retakan di permukaan lantai: tekstur retak blok bawaan digambar sebagai
     * tempelan gelap di sisi atas tiap balok (retakan bawaan Minecraft tidak
     * tergambar di renderer Veil).
     */
    private static void renderCracks(PoseStack pose, MultiBufferSource buffers, Vec3 cam) {
        if (broken && DropCutscene.time() > DropTiming.BREAK + 900L) return;
        for (int i = 0; i < CRACKS.size(); i++) {
            int stage = crackStage.length > i ? crackStage[i] : -1;
            if (stage < 0) continue;
            BlockPos p = CRACKS.get(i);
            if (broken && level.getBlockState(p).isAir()) continue;
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(CRACK_TEXTURES[stage]));
            Matrix4f m = pose.last().pose();
            org.joml.Matrix3f n = pose.last().normal();
            float x0 = (float) (p.getX() - cam.x), z0 = (float) (p.getZ() - cam.z);
            float y = (float) (p.getY() + 1.003D - cam.y);
            int light = LevelRenderer.getLightColor(level, p.above());
            // Pola tiap balok diputar 0/90/180/270 derajat supaya tidak tampak berulang.
            int rot = Math.floorMod(p.getX() * 3 + p.getZ() * 7, 4);
            float[][] uv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
            float[][] corner = {{x0, z0}, {x0, z0 + 1}, {x0 + 1, z0 + 1}, {x0 + 1, z0}};
            for (int k = 0; k < 4; k++) {
                float[] t = uv[(k + rot) % 4];
                crackVertex(vc, m, n, corner[k][0], y, corner[k][1], t[0], t[1], light);
            }
        }
    }

    private static void crackVertex(VertexConsumer vc, Matrix4f m, org.joml.Matrix3f n, float x, float y, float z, float u, float v, int light) {
        vc.vertex(m, x, y, z).color(48, 40, 30, 190).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    /** Lantai runtuh: lubangi lantai (hanya di klien) dan jadikan balok-baloknya puing. */
    static void breakFloor() {
        if (level == null || broken || Minecraft.getInstance().level != level) return;
        broken = true;
        // Retakan di balok yang runtuh tidak perlu lagi (baloknya hilang); sisanya tetap terlihat retak.
        // Gelombang kejut melingkar di lantai dan semburan debu dari lubang.
        Vec3 floor = new Vec3(axis.x, floorY + 1.05D, axis.z);
        DropFx.shockwave(floor, new Vec3(0.0D, 1.0D, 0.0D), 1.8F);
        DropFx.sparks(floor, 40, 210, 190, 140);
        RandomSource random = RandomSource.create(minX * 31L + minZ);
        double cx = (minX + maxX + 1) / 2.0D;
        double cz = (minZ + maxZ + 1) / 2.0D;
        for (int y = floorY; y > floorY - HOLE_DEPTH; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (y == floorY && state.getRenderShape() == RenderShape.MODEL) {
                        int light = LevelRenderer.getLightColor(level, pos.above(floorY - y + 1));
                        double dist = Math.sqrt((x + 0.5D - cx) * (x + 0.5D - cx) + (z + 0.5D - cz) * (z + 0.5D - cz));
                        // Retakan merambat dari tengah ke tepi; lapisan bawah menyusul.
                        double delay = 0.12D + dist * 0.07D + random.nextDouble() * 0.05D;
                        DEBRIS.add(new Debris(state, new Vec3(x + 0.5D, y + 0.5D, z + 0.5D), delay,
                                outward(x + 0.5D - cx, random), outward(z + 0.5D - cz, random),
                                (float) random.nextGaussian() * 160.0F, (float) random.nextGaussian() * 160.0F, light));
                    }
                    REMOVED.add(new Removed(pos, state));
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    /** Kecepatan menyamping puing: menjauh dari tengah (lantai pecah terbuka), minimal sekian blok/detik. */
    private static double outward(double offset, RandomSource random) {
        double dir = offset == 0.0D ? (random.nextBoolean() ? 1.0D : -1.0D) : Math.signum(offset);
        return dir * (0.35D + Math.abs(offset) * 0.25D + random.nextDouble() * 0.35D);
    }

    /**
     * Lampu plafon meledak bergiliran setelah lantai runtuh (40 ms per lampu,
     * dari yang terdekat): mati, percikan dan pecahan kaca, bunyi kaca pecah.
     */
    private static void blowLights(double scene) {
        if (!broken || level == null) return;
        double since = scene - DropTiming.DROP / 1000.0D;
        while (lightsBlown < LIGHTS.size() && since >= lightsBlown * 0.06D) {
            BlockPos pos = LIGHTS.get(lightsBlown++);
            BlockState state = level.getBlockState(pos);
            if (!state.hasProperty(Lights.ON)) continue;
            REMOVED.add(new Removed(pos, state));
            level.setBlock(pos, state.setValue(Lights.ON, false), 3);
            Vec3 at = Vec3.atCenterOf(pos);
            DropFx.sparks(at, 22, 255, 235, 190);
            DropFx.sparks(at, 8, 220, 240, 255);
            if (lightsBlown <= 6) {
                float pitch = 0.6F + 0.2F * (lightsBlown % 3);
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.GLASS_BREAK, pitch, 0.7F));
            }
        }
    }

    /** Kembalikan lantai (kalau masih di set lobby yang sama) dan buang puingnya. */
    static void restore() {
        Minecraft client = Minecraft.getInstance();
        if (level != null && client.level == level) {
            for (Removed r : REMOVED) level.setBlock(r.pos, r.state, 3);
            for (java.util.Map.Entry<BlockPos, BlockState> e : LIGHT_STATES.entrySet()) level.setBlock(e.getKey(), e.getValue(), 3);
        }
        REMOVED.clear();
        DEBRIS.clear();
        LIGHTS.clear();
        LIGHT_STATES.clear();
        CRACKS.clear();
        broken = false;
        level = null;
    }

    // ============================================================ gambar

    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !DropCutscene.isRunning()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level != level || level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        double scene = DropCutscene.scene();
        quake(DropCutscene.time());
        blowLights(scene);
        renderDebris(client.getBlockRenderer(), pose, buffers, cam, scene);
        if (DropCutscene.inAbyss()) renderNearDebris(client.getBlockRenderer(), pose, buffers, cam, scene);
        renderCracks(pose, buffers, cam);
        buffers.endBatch();
        renderStreaks(pose, buffers, cam);
        if (DropCutscene.inAbyss()) renderAbyss(pose, buffers, cam, scene);
        DropFx.render(pose, buffers, cam);
        buffers.endBatch(RenderType.lightning());
    }

    private static void renderDebris(BlockRenderDispatcher blocks, PoseStack pose, MultiBufferSource buffers, Vec3 cam, double scene) {
        double brk = DropTiming.BREAK / 1000.0D;
        for (Debris d : DEBRIS) {
            double x = scene - brk - d.delay;
            double drop = 0.0D;
            if (x > 0.0D) {
                double reach = DEBRIS_TERMINAL / DEBRIS_GRAVITY;
                drop = x < reach ? 0.5D * DEBRIS_GRAVITY * x * x
                        : 0.5D * DEBRIS_GRAVITY * reach * reach + DEBRIS_TERMINAL * (x - reach);
            }
            double t = Math.max(0.0D, x);
            Vec3 at = d.start.add(d.vx * t, -drop, d.vz * t);
            // Terlalu jauh, atau menempel di lensa saat kamera turun lewat lubang.
            double dist = at.distanceToSqr(cam);
            if (dist > 70.0D * 70.0D || dist < 1.6D * 1.6D) continue;
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            if (x < 0.0D) {
                // Bergetar makin kuat sebelum lepas.
                double j = 0.035D * Math.max(0.0D, 1.0D + x * 3.0D);
                pose.translate(j * Math.sin(scene * 90.0D + d.start.x), 0.0D, j * Math.cos(scene * 80.0D + d.start.z));
            }
            pose.mulPose(Axis.XP.rotationDegrees((float) (d.spinX * t)));
            pose.mulPose(Axis.ZP.rotationDegrees((float) (d.spinZ * t)));
            // Pecah: sedikit mengecil supaya celah antar puing terlihat.
            float s = x > 0.0D ? 0.92F : 1.0F;
            pose.scale(s, s, s);
            pose.translate(-0.5D, -0.5D, -0.5D);
            blocks.renderSingleBlock(d.state, pose, buffers, d.light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
        }
    }

    /**
     * Puing yang ikut jatuh di sekitar yang sedang disorot: potongan lantai,
     * dinding, dan plafon yang melayang pelan ke atas (tertinggal sedikit)
     * sambil berputar, memberi kedalaman di jurang.
     */
    private static void renderNearDebris(BlockRenderDispatcher blocks, PoseStack pose, MultiBufferSource buffers, Vec3 cam, double scene) {
        Vec3 o = DropCutscene.origin();
        int light = LightTexture.pack(11, 11);
        for (int i = 0; i < 16; i++) {
            RandomSource r = RandomSource.create(i * 6151L + 3L);
            double angle = r.nextDouble() * Math.PI * 2.0D;
            double radius = 2.6D + r.nextDouble() * 7.0D;
            // Melayang ke atas relatif dan kembali dari bawah (siklus 16 blok).
            double rise = r.nextDouble() * 16.0D + scene * (0.25D + r.nextDouble() * 0.5D);
            double y = (rise % 16.0D) - 8.0D;
            Vec3 at = o.add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            if (at.distanceToSqr(cam) < 1.5D * 1.5D) continue;
            float size = 0.35F + r.nextFloat() * 0.45F;
            BlockState state = palette.get(r.nextInt(palette.size()));
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            pose.mulPose(Axis.YP.rotationDegrees((float) (r.nextFloat() * 360.0F + scene * (r.nextFloat() - 0.5F) * 60.0F)));
            pose.mulPose(Axis.XP.rotationDegrees((float) (r.nextFloat() * 360.0F + scene * (r.nextFloat() - 0.5F) * 50.0F)));
            pose.scale(size, size * (0.25F + r.nextFloat() * 0.5F), size);
            pose.translate(-0.5D, -0.5D, -0.5D);
            blocks.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
        }
    }

    /**
     * Isi jurang: debu yang melayang di cahaya, berkas cahaya kuning dari
     * lubang jauh di atas, dan sesekali lampu neon yang ikut jatuh berputar.
     */
    private static void renderAbyss(PoseStack pose, MultiBufferSource buffers, Vec3 cam, double scene) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        DropFx.Quads q = new DropFx.Quads(vc, m, cam);
        Vec3 o = DropCutscene.origin();
        // Debu: titik diam di dunia (jadi melesat ke atas saat jatuh, melayang saat waktu melambat).
        int from = (int) Math.floor((cam.y - 10.0D) / 2.0D);
        int to = (int) Math.floor((cam.y + 10.0D) / 2.0D);
        for (int layer = from; layer <= to; layer++) {
            RandomSource r = RandomSource.create(layer * 92821L + 7L);
            for (int k = 0; k < 9; k++) {
                double angle = r.nextDouble() * Math.PI * 2.0D;
                double radius = 0.8D + r.nextDouble() * 9.0D;
                Vec3 at = new Vec3(axis.x + Math.cos(angle) * radius, layer * 2.0D + r.nextDouble() * 2.0D,
                        axis.z + Math.sin(angle) * radius);
                double dist = at.distanceTo(cam);
                if (dist < 0.6D || dist > 12.0D) continue;
                int a = (int) (110 * (1.0D - dist / 12.0D));
                float size = 0.012F + r.nextFloat() * 0.02F;
                q.star(at, size * 2.5F, size, 230, 210, 170, a);
            }
        }
        // Lampu neon jatuh berputar melewati bingkai (satu tiap ~3 detik adegan).
        double cycle = 3.2D;
        int n = (int) Math.floor(scene / cycle);
        double u = (scene - n * cycle) / cycle;
        RandomSource r = RandomSource.create(n * 4421L + 9L);
        double angle = r.nextDouble() * Math.PI * 2.0D;
        double radius = 3.5D + r.nextDouble() * 4.0D;
        Vec3 at = o.add(Math.cos(angle) * radius, 9.0D - 18.0D * u, Math.sin(angle) * radius);
        double spin = scene * (1.5D + r.nextDouble());
        Vec3 dir = new Vec3(Math.cos(spin), Math.sin(spin * 0.7D) * 0.6D, Math.sin(spin)).normalize().scale(0.6D);
        q.line(at.subtract(dir), at.add(dir), 0.05F, 255, 252, 235, 230, 230);
        q.line(at.subtract(dir), at.add(dir), 0.16F, 255, 240, 200, 40, 40);
    }

    /** Garis angin tegak di sekitar jalur jatuh; panjangnya mengikuti kecepatan yang terlihat. */
    private static void renderStreaks(PoseStack pose, MultiBufferSource buffers, Vec3 cam) {
        double v = DropCutscene.fallSpeed() * DropCutscene.speed();
        if (v < 2.0D || cam.y > floorY - 1) return;
        float length = (float) Mth.clamp(v * 0.11D, 0.3D, 3.2D);
        float alpha = (float) Mth.clamp((v - 2.0D) / 10.0D, 0.0D, 1.0D);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        int from = (int) Math.floor((cam.y - 22.0D) / STREAK_LAYER);
        int to = (int) Math.floor((cam.y + 22.0D) / STREAK_LAYER);
        for (int layer = from; layer <= to; layer++) {
            RandomSource random = RandomSource.create(layer * 4813L + 5L);
            for (int k = 0; k < 7; k++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = 1.2D + random.nextDouble() * 11.0D;
                double y = layer * STREAK_LAYER + random.nextDouble() * STREAK_LAYER;
                double px = axis.x + Math.cos(angle) * radius - cam.x;
                double pz = axis.z + Math.sin(angle) * radius - cam.z;
                double py = y - cam.y;
                double dist = Math.sqrt(px * px + py * py + pz * pz);
                if (dist < 0.8D) continue;
                // Lebar menghadap kamera; makin jauh makin redup.
                double flat = Math.sqrt(px * px + pz * pz);
                float sx = flat < 1.0E-3D ? 0.02F : (float) (pz / flat) * 0.022F;
                float sz = flat < 1.0E-3D ? 0.0F : (float) (-px / flat) * 0.022F;
                int a = (int) (alpha * 150.0F * Mth.clamp(1.0F - (float) dist / 24.0F, 0.0F, 1.0F));
                if (a <= 2) continue;
                float x0 = (float) px, y0 = (float) py, z0 = (float) pz;
                vc.vertex(m, x0 - sx, y0, z0 - sz).color(255, 246, 214, 0).endVertex();
                vc.vertex(m, x0 + sx, y0, z0 + sz).color(255, 246, 214, 0).endVertex();
                vc.vertex(m, x0 + sx, y0 + length * 0.35F, z0 + sz).color(255, 246, 214, a).endVertex();
                vc.vertex(m, x0 - sx, y0 + length * 0.35F, z0 - sz).color(255, 246, 214, a).endVertex();
                vc.vertex(m, x0 - sx, y0 + length * 0.35F, z0 - sz).color(255, 246, 214, a).endVertex();
                vc.vertex(m, x0 + sx, y0 + length * 0.35F, z0 + sz).color(255, 246, 214, a).endVertex();
                vc.vertex(m, x0 + sx, y0 + length, z0 + sz).color(255, 246, 214, 0).endVertex();
                vc.vertex(m, x0 - sx, y0 + length, z0 - sz).color(255, 246, 214, 0).endVertex();
            }
        }
    }
}
