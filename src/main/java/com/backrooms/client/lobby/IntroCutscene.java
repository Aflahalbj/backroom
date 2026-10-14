package com.backrooms.client.lobby;

import com.backrooms.client.CameraEffect;
import com.backrooms.client.ClientConfig;
import com.backrooms.mixin.CameraAccessor;
import com.backrooms.mixin.veil.PathAccessor;
import com.backrooms.sound.ModSounds;
import com.backrooms.util.MathStuff;
import com.backrooms.world.BackroomsLevel;
import foundry.veil.api.client.anim.Frame;
import foundry.veil.api.client.anim.Keyframe;
import foundry.veil.api.client.anim.Path;
import foundry.veil.api.client.util.Easings.Easing;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Port dari {@code com.sp.render.camera.CutsceneManager}: intro Level 0.
 *
 * <ol>
 *   <li>layar hitam 3 detik, semua suara dijeda;</li>
 *   <li>kamera jatuh dari Y=220 ke Y=27 selama 7 detik dengan suara
 *       {@code falling}, berputar mengikuti keyframe aslinya;</li>
 *   <li>layar hitam, lalu kamera tergeletak di lantai dengan judul
 *       "The Backrooms" selama 5 detik;</li>
 *   <li>layar hitam 2 detik, kontrol kembali ke pemain.</li>
 * </ol>
 *
 * <p>Semua angka, keyframe, dan urutannya sama dengan aslinya. Bedanya:
 * kamera aslinya entity item sementara, di sini posisi/rotasinya langsung
 * dipasang ke {@link Camera} (lihat {@code CameraMixin}); semua titik digeser
 * ke slot Backrooms room ({@code offsetX/offsetZ}); dan pemicunya paket dari
 * server setelah noclip dari lobby, bukan tercekik di dalam dinding overworld.
 */
public final class IntroCutscene {

    private static boolean doingCutscene = false;
    private static boolean started = false;
    private static boolean isPlaying = false;
    private static boolean fall = false;
    private static int prevLightRenderDistance;
    private static boolean backroomsBySP = false;
    private static long startTime;
    private static final int DURATION = 7000;
    private static final int DURATION2 = 5000;
    public static final BlackScreen BLACK_SCREEN = new BlackScreen();
    private static boolean camera = false;
    private static Vec3 cameraPos = Vec3.ZERO;
    private static float cameraYaw;
    private static float cameraPitch;
    private static final Path CAMERA_PATH_POS = new Path(List.of(
            new Keyframe(new Vec3(0.5D, 220.0D, 0.5D), Vec3.ZERO, Vec3.ZERO, MathStuff.millisecToTick(DURATION), Easing.easeInSine),
            new Keyframe(new Vec3(0.5D, 27.0D, 0.5D), Vec3.ZERO, Vec3.ZERO, 0, Easing.linear)), false, false);
    private static final Path CAMERA_PATH_ROT_X = new Path(List.of(
            new Keyframe(Vec3.ZERO, new Vec3(80.0D, 0.0D, 0.0D), Vec3.ZERO, MathStuff.millisecToTick(DURATION) / 2, Easing.easeInOutSine),
            new Keyframe(Vec3.ZERO, new Vec3(60.0D, 0.0D, 0.0D), Vec3.ZERO, MathStuff.millisecToTick(DURATION) / 2, Easing.easeInOutSine),
            new Keyframe(Vec3.ZERO, new Vec3(110.0D, 0.0D, 0.0D), Vec3.ZERO, 0, Easing.easeInOutSine)), false, false);
    private static final Path CAMERA_PATH_ROT_Y = new Path(List.of(
            new Keyframe(Vec3.ZERO, new Vec3(0.0D, 0.0D, 0.0D), Vec3.ZERO, MathStuff.millisecToTick(DURATION), Easing.linear),
            new Keyframe(Vec3.ZERO, new Vec3(0.0D, 120.0D, 0.0D), Vec3.ZERO, 0, Easing.linear)), false, false);
    private static final Path CAMERA_PATH_ROT_Z = new Path(List.of(
            new Keyframe(Vec3.ZERO, new Vec3(0.0D, 0.0D, 20.0D), Vec3.ZERO, MathStuff.millisecToTick(DURATION) / 2, Easing.easeInOutSine),
            new Keyframe(Vec3.ZERO, new Vec3(0.0D, 0.0D, -20.0D), Vec3.ZERO, MathStuff.millisecToTick(DURATION) / 2, Easing.easeInOutSine),
            new Keyframe(Vec3.ZERO, new Vec3(0.0D, 0.0D, 0.0D), Vec3.ZERO, 0, Easing.easeInOutSine)), false, false);
    public static float cameraRotZ = 0.0F;
    private static int offsetX;
    private static int offsetZ;

    private IntroCutscene() {}

    /** Mulai intro untuk Backrooms yang bangunannya digeser sebesar {@code offsetX/offsetZ}. */
    static void start(int x, int z) {
        reset();
        offsetX = x;
        offsetZ = z;
        doingCutscene = true;
    }

    public static boolean isPlaying() {
        return isPlaying;
    }

    /** Intro (atau layar hitam penutupnya) masih berjalan. */
    public static boolean isRunning() {
        return doingCutscene || BLACK_SCREEN.isBlackScreen;
    }

    /** Judul "The Backrooms" sedang tampil ({@code TitleText} asli). */
    public static boolean showTitle() {
        return backroomsBySP && !BLACK_SCREEN.isBlackScreen;
    }

    /** Dipanggil tiap frame sebelum dunia digambar, seperti {@code MinecraftClientMixin.doCameraCutscene} asli. */
    static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.level != null) {
            if (doingCutscene && client.level.dimension() == BackroomsLevel.LEVEL0.dimension()) {
                pause();
                fall();
                backroomsBySP();
            } else if (doingCutscene && !(client.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen)) {
                doingCutscene = false;
            }
            BLACK_SCREEN.tick();
        }
    }

    private static void pause() {
        if (!backroomsBySP && !fall) {
            if (!started) {
                BLACK_SCREEN.showBlackScreen(60, true, false);
                startTime = System.currentTimeMillis();
                started = true;
            }
            float timer = (float) (System.currentTimeMillis() - startTime) / 2900.0F;
            if ((double) timer >= 1.0D) {
                fall = true;
            }
        }
    }

    private static void fall() {
        if (!backroomsBySP && fall) {
            if (!isPlaying) {
                prevLightRenderDistance = ClientConfig.lightRenderDistanceOverride;
                ClientConfig.lightRenderDistanceOverride = 1000;
                startTime = System.currentTimeMillis();
                isPlaying = true;
                if (ModSounds.available("falling")) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.event("falling"), 1.0F));
                }
            }
            float timer = (float) (System.currentTimeMillis() - startTime) / (float) DURATION;
            if (!camera) {
                initCamera();
            }
            if ((double) timer >= 1.0D) {
                BLACK_SCREEN.showBlackScreen(50, true, false);
                backroomsBySP = true;
                startTime = System.currentTimeMillis() + 2500L;
                fall = false;
                setCamera(3.0D, 21.0D, 1.5D, 15.0F, 90.0F);
                ClientConfig.lightRenderDistanceOverride = prevLightRenderDistance;
            } else {
                Vec3 newCameraPos = lerpedCameraPos(timer);
                Vec3 newCameraRot = lerpedCameraRot(timer);
                cameraRotZ = (float) newCameraRot.z;
                setCamera(newCameraPos.x, newCameraPos.y, newCameraPos.z, (float) newCameraRot.y, (float) newCameraRot.x);
            }
        }
    }

    private static void backroomsBySP() {
        if (backroomsBySP) {
            float timer = (float) (System.currentTimeMillis() - startTime) / (float) DURATION2;
            if ((double) timer >= 1.0D) {
                BLACK_SCREEN.showBlackScreen(40, true, false);
                reset();
            } else {
                setCamera(3.0D, 21.0D, 1.5D, 5.0F, 83.0F);
                cameraRotZ = 100.0F;
            }
        }
    }

    private static Vec3 lerpedCameraRot(float timer) {
        double interpolateX = MathStuff.mod(timer * (float) frames(CAMERA_PATH_ROT_X).size(), 1.0F);
        double currentFrameRotX = CAMERA_PATH_ROT_X.frameAtProgress(timer).getRotation().x;
        double prevFrameRotX = previousFrameAtProgress(CAMERA_PATH_ROT_X, timer).getRotation().x;
        double interpolateY = MathStuff.mod(timer * (float) frames(CAMERA_PATH_ROT_Y).size(), 1.0F);
        double currentFrameRotY = CAMERA_PATH_ROT_Y.frameAtProgress(timer).getRotation().y;
        double prevFrameRotY = previousFrameAtProgress(CAMERA_PATH_ROT_Y, timer).getRotation().y;
        double interpolateZ = MathStuff.mod(timer * (float) frames(CAMERA_PATH_ROT_Z).size(), 1.0F);
        double currentFrameRotZ = CAMERA_PATH_ROT_Z.frameAtProgress(timer).getRotation().z;
        double prevFrameRotZ = previousFrameAtProgress(CAMERA_PATH_ROT_Z, timer).getRotation().z;
        return new Vec3(Mth.lerp(interpolateX, prevFrameRotX, currentFrameRotX),
                Mth.lerp(interpolateY, prevFrameRotY, currentFrameRotY),
                Mth.lerp(interpolateZ, prevFrameRotZ, currentFrameRotZ));
    }

    private static Vec3 lerpedCameraPos(float timer) {
        double interpolatePos = MathStuff.mod(timer * (float) frames(CAMERA_PATH_POS).size(), 1.0F);
        Vec3 currentFramePos = CAMERA_PATH_POS.frameAtProgress(timer).getPosition();
        Vec3 prevFramePos = previousFrameAtProgress(CAMERA_PATH_POS, timer).getPosition();
        return new Vec3(Mth.lerp(interpolatePos, prevFramePos.x, currentFramePos.x),
                Mth.lerp(interpolatePos, prevFramePos.y, currentFramePos.y),
                Mth.lerp(interpolatePos, prevFramePos.z, currentFramePos.z));
    }

    static void reset() {
        if (isPlaying) {
            ClientConfig.lightRenderDistanceOverride = prevLightRenderDistance;
        }
        isPlaying = false;
        started = false;
        fall = false;
        backroomsBySP = false;
        camera = false;
        cameraRotZ = 0.0F;
        startTime = 0L;
        doingCutscene = false;
    }

    /** Batalkan semuanya, termasuk layar hitam (keluar server / kembali ke lobby). */
    static void cancel() {
        reset();
        BLACK_SCREEN.clear();
    }

    private static void initCamera() {
        camera = true;
        setCamera(1.5D, 300.0D, 1.5D, 0.0F, 90.0F);
    }

    /** {@code camera.refreshPositionAndAngles} asli, di koordinat asli mod. */
    private static void setCamera(double x, double y, double z, float yaw, float pitch) {
        cameraPos = new Vec3(x + offsetX, y, z + offsetZ);
        cameraYaw = yaw;
        cameraPitch = pitch;
    }

    /** Pasang kamera intro ke kamera game; {@code false} kalau intro sedang tidak memegang kamera. */
    static boolean applyCamera(Camera cam) {
        if (!camera) return false;
        CameraAccessor accessor = (CameraAccessor) cam;
        accessor.backrooms$setPosition(cameraPos);
        accessor.backrooms$setRotation(cameraYaw, cameraPitch);
        accessor.backrooms$setDetached(true);
        return true;
    }

    private static List<Frame> frames(Path path) {
        return ((PathAccessor) path).getFrames();
    }

    private static Frame previousFrameAtProgress(Path path, double progress) {
        List<Frame> frames = frames(path);
        int index = (int) ((double) frames.size() * progress) - 1;
        return index < 0 ? frames.get(0) : frames.get(index);
    }

    /** Port dari {@code CutsceneManager.BlackScreen}. */
    public static final class BlackScreen {
        public boolean isBlackScreen = false;
        public boolean noEscape;
        private long duration = 0L;
        private long startTime = 0L;
        private boolean shouldPauseSounds;

        public void showBlackScreen(int time, boolean shouldPauseSounds, boolean noEscape) {
            this.duration = (long) time * 50L;
            this.isBlackScreen = true;
            this.noEscape = noEscape;
            this.startTime = System.currentTimeMillis();
            this.shouldPauseSounds = shouldPauseSounds;
        }

        void tick() {
            if (this.isBlackScreen) {
                Minecraft client = Minecraft.getInstance();
                float timer = (float) (System.currentTimeMillis() - this.startTime) / (float) this.duration;
                if ((double) timer >= 1.0D) {
                    clear();
                } else {
                    if (this.shouldPauseSounds) {
                        client.getSoundManager().pause();
                    }
                    if (this.noEscape) {
                        CameraEffect.youCantEscape = true;
                    }
                    CameraEffect.blackScreen = true;
                }
            }
        }

        void clear() {
            if (!this.isBlackScreen) return;
            CameraEffect.blackScreen = false;
            CameraEffect.youCantEscape = false;
            this.isBlackScreen = false;
            this.startTime = 0L;
            Minecraft.getInstance().getSoundManager().resume();
        }
    }
}
