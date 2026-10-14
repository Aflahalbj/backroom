package com.backrooms.client;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Pengaturan klien, port dari {@code com.sp.compat.modmenu.ConfigStuff}
 * (kategori "video" dan "shaders"). Nama dan nilai bawaannya sama dengan
 * aslinya. File-nya {@code config/backrooms-client.toml}; efek kamera juga
 * bisa dinyalakan/dimatikan lewat {@code /backroom camera ...}.
 */
public final class ClientConfig {

    public static final ForgeConfigSpec SPEC;

    // video
    public static final ForgeConfigSpec.BooleanValue ENABLE_REAL_CAMERA;
    public static final ForgeConfigSpec.DoubleValue LOOK_ROLL_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue STRAFE_ROLL_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SMOOTH_CAMERA;
    public static final ForgeConfigSpec.DoubleValue CAMERA_SMOOTHING;
    public static final ForgeConfigSpec.BooleanValue SHOW_HANDS;
    public static final ForgeConfigSpec.BooleanValue USE_DEFAULT_GUI;
    public static final ForgeConfigSpec.BooleanValue ENABLE_HINT;

    // shaders
    public static final ForgeConfigSpec.BooleanValue ENABLE_VHS_EFFECT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VHS_EFFECT_IN_THE_BACKROOMS;
    public static final ForgeConfigSpec.DoubleValue VHS_DISTORTION_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue LIGHT_RENDER_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SHADOWS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VOLUMETRIC_LIGHT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PUDDLES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_LEVEL1_FOG;
    public static final ForgeConfigSpec.BooleanValue RENDER_WATER_REFLECTIONS;
    public static final ForgeConfigSpec.BooleanValue RENDER_BLOCK_REFLECTIONS;
    public static final ForgeConfigSpec.BooleanValue MOTION_BLUR;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_STRENGTH;

    // lobby
    public static final ForgeConfigSpec.IntValue LOBBY_SOUND;

    /** Diset intro Level 0 selama kamera jatuh (aslinya mengubah {@code ConfigStuff.lightRenderDistance}); -1 = tidak. */
    public static int lightRenderDistanceOverride = -1;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("video");
        ENABLE_REAL_CAMERA = b.comment("Roll kamera saat menoleh/geser dan guncangan kamera.")
                .define("enableRealCamera", true);
        LOOK_ROLL_MULTIPLIER = b.defineInRange("lookRollMultiplier", 7.0D, 0.0D, 10.0D);
        STRAFE_ROLL_MULTIPLIER = b.defineInRange("strafeRollMultiplier", 7.0D, 0.0D, 10.0D);
        ENABLE_SMOOTH_CAMERA = b.define("enableSmoothCamera", true);
        CAMERA_SMOOTHING = b.defineInRange("cameraSmoothing", 0.7D, 0.0D, 1.0D);
        SHOW_HANDS = b.define("showHands", false);
        USE_DEFAULT_GUI = b.define("useDefaultGUI", false);
        ENABLE_HINT = b.comment("Tampilkan petunjuk senter saat masuk world.")
                .define("enableHint", true);
        b.pop();

        b.push("shaders");
        ENABLE_VHS_EFFECT = b.comment("Efek kamera VHS di luar backrooms.")
                .define("enableVhsEffect", true);
        ENABLE_VHS_EFFECT_IN_THE_BACKROOMS = b.comment("Efek kamera VHS di dalam backrooms.")
                .define("enableVhsEffectInTheBackrooms", true);
        VHS_DISTORTION_MULTIPLIER = b.defineInRange("VHSDistortionMultiplier", 1.0D, 0.0D, 1.0D);
        LIGHT_RENDER_DISTANCE = b.defineInRange("lightRenderDistance", 100, 10, Integer.MAX_VALUE);
        ENABLE_SHADOWS = b.define("enableShadows", true);
        ENABLE_VOLUMETRIC_LIGHT = b.define("enableVolumetricLight", true);
        ENABLE_PUDDLES = b.define("enablePuddles", true);
        ENABLE_LEVEL1_FOG = b.define("enableLevel1Fog", true);
        RENDER_WATER_REFLECTIONS = b.define("renderWaterReflections", true);
        RENDER_BLOCK_REFLECTIONS = b.define("renderBlockReflections", true);
        MOTION_BLUR = b.define("motionBlur", true);
        MOTION_BLUR_STRENGTH = b.defineInRange("motionBlurStrength", 0.5D, 0.1D, 1.0D);
        b.pop();

        b.push("lobby");
        LOBBY_SOUND = b.comment("Suara lobby: 0 = suasana level, 1 = Creepy 1, 2 = Creepy 2, 3 = No Escape,",
                        "4 = Senja Poolrooms, 5 = acak, 6 = hening.")
                .defineInRange("lobbySound", 0, 0, 6);
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {}

    private static boolean get(ForgeConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static float get(ForgeConfigSpec.DoubleValue value) {
        return (float) (SPEC.isLoaded() ? value.get() : value.getDefault()).doubleValue();
    }

    public static boolean enableRealCamera() { return get(ENABLE_REAL_CAMERA); }
    public static float lookRollMultiplier() { return get(LOOK_ROLL_MULTIPLIER); }
    public static float strafeRollMultiplier() { return get(STRAFE_ROLL_MULTIPLIER); }
    public static boolean enableSmoothCamera() { return get(ENABLE_SMOOTH_CAMERA); }
    public static float cameraSmoothing() { return get(CAMERA_SMOOTHING); }
    public static boolean showHands() { return get(SHOW_HANDS); }
    public static boolean useDefaultGUI() { return get(USE_DEFAULT_GUI); }
    public static boolean enableHint() { return get(ENABLE_HINT); }
    public static boolean enableVhsEffect() { return get(ENABLE_VHS_EFFECT); }
    public static boolean enableVhsEffectInTheBackrooms() { return get(ENABLE_VHS_EFFECT_IN_THE_BACKROOMS); }
    public static float vhsDistortionMultiplier() { return get(VHS_DISTORTION_MULTIPLIER); }
    public static int lightRenderDistance() {
        if (lightRenderDistanceOverride >= 0) return lightRenderDistanceOverride;
        return SPEC.isLoaded() ? LIGHT_RENDER_DISTANCE.get() : LIGHT_RENDER_DISTANCE.getDefault();
    }
    public static boolean enableShadows() { return get(ENABLE_SHADOWS); }
    public static boolean enableVolumetricLight() { return get(ENABLE_VOLUMETRIC_LIGHT); }
    public static boolean enablePuddles() { return get(ENABLE_PUDDLES); }
    public static boolean enableLevel1Fog() { return get(ENABLE_LEVEL1_FOG); }
    public static boolean renderWaterReflections() { return get(RENDER_WATER_REFLECTIONS); }
    public static boolean renderBlockReflections() { return get(RENDER_BLOCK_REFLECTIONS); }
    public static boolean motionBlur() { return get(MOTION_BLUR); }
    public static float motionBlurStrength() { return get(MOTION_BLUR_STRENGTH); }
    public static int lobbySound() { return SPEC.isLoaded() ? LOBBY_SOUND.get() : LOBBY_SOUND.getDefault(); }

    public static void set(ForgeConfigSpec.IntValue value, int newValue) {
        value.set(newValue);
        value.save();
    }

    /** Simpan perubahan dari command ke file config. */
    public static void set(ForgeConfigSpec.BooleanValue value, boolean on) {
        value.set(on);
        value.save();
    }
}
