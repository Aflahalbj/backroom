package com.backrooms.client;

import com.backrooms.client.render.FlashlightRenderer;
import com.backrooms.client.render.PoolroomsDayCycle;
import com.backrooms.client.render.PreviousUniforms;
import com.backrooms.client.render.SSAOSamples;
import com.backrooms.client.render.ShadowMapRenderer;
import com.backrooms.client.render.camera.CameraShake;
import com.backrooms.util.TickTimer;
import com.backrooms.world.BackroomsLevel;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.deferred.light.renderer.LightRenderer;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import foundry.veil.api.client.render.shader.definition.ShaderPreDefinitions;
import foundry.veil.api.client.render.shader.program.MutableUniformAccess;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Port dari {@code com.sp.SPBRevampedClient} (bagian render): efek kamera
 * VHS asli mod SP-Backrooms Revamped.
 *
 * <ul>
 *   <li>Pipeline post Veil {@code spb-revamped:vhs} (SSAO, cahaya, genangan,
 *       kabut, bloom, kerucut cahaya, distorsi VHS, motion blur, glitch)
 *       dipasang/dilepas tiap frame sesuai {@link #shouldRenderCameraEffect()}.</li>
 *   <li>Uniform dan define shader diisi persis seperti aslinya sebelum
 *       pipeline berjalan.</li>
 *   <li>Shadow map Level 0 dan Poolrooms digambar setelah langit.</li>
 *   <li>Di dalam backrooms dengan efek menyala, cahaya vanilla (Level 0,
 *       Level 1) dan ambient occlusion dimatikan: ruangan diterangi lampu Veil
 *       dari tiap lampu neon.</li>
 * </ul>
 *
 * Fitur dari level yang tidak ada di port ini (warp Level 2) memakai nilai
 * "diam" seperti saat fitur itu tidak aktif di mod aslinya. Jumpscare
 * Skinwalker dan glitch Smiler ikut berjalan seperti aslinya.
 */
public final class CameraEffect {

    private static final ResourceLocation VHS_POST = new ResourceLocation("spb-revamped", "vhs");
    private static final ResourceLocation SSAO = new ResourceLocation("spb-revamped", "vhs/ssao");
    private static final ResourceLocation EVERYTHING_SHADER = new ResourceLocation("spb-revamped", "vhs/everything");
    private static final ResourceLocation POST_VHS = new ResourceLocation("spb-revamped", "vhs/vhs_post");
    private static final ResourceLocation MIXED_SHADER = new ResourceLocation("spb-revamped", "vhs/mixed");
    private static final ResourceLocation GLITCH_SHADER = new ResourceLocation("spb-revamped", "vhs/glitch");

    /** {@code ConfigDefinitions.definitions} asli. */
    private static final Map<String, BooleanSupplier> CONFIG_DEFINITIONS = new LinkedHashMap<>();
    /** {@code BackroomsLevels.definitions} asli (hanya level yang ada di port ini). */
    private static final Map<String, BackroomsLevel> LEVEL_DEFINITIONS = new LinkedHashMap<>();

    static {
        CONFIG_DEFINITIONS.put("SHADOWS", ClientConfig::enableShadows);
        CONFIG_DEFINITIONS.put("VOLUMETRIC_LIGHT", ClientConfig::enableVolumetricLight);
        CONFIG_DEFINITIONS.put("PUDDLES", ClientConfig::enablePuddles);
        CONFIG_DEFINITIONS.put("LEVEL1_FOG", ClientConfig::enableLevel1Fog);
        CONFIG_DEFINITIONS.put("WATER_REFLECTIONS", ClientConfig::renderWaterReflections);
        CONFIG_DEFINITIONS.put("BLOCK_REFLECTIONS", ClientConfig::renderBlockReflections);
        CONFIG_DEFINITIONS.put("MOTION_BLUR", ClientConfig::motionBlur);
        LEVEL_DEFINITIONS.put("LEVEL0", BackroomsLevel.LEVEL0);
        LEVEL_DEFINITIONS.put("LEVEL1", BackroomsLevel.LEVEL1);
        LEVEL_DEFINITIONS.put("POOLROOMS", BackroomsLevel.POOLROOMS);
    }

    private static final CameraShake CAMERA_SHAKE = new CameraShake();
    private static final FlashlightRenderer FLASHLIGHT_RENDERER = new FlashlightRenderer();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final RandomSource RANDOM2 = RandomSource.create(34563264L);

    static boolean inBackrooms = false;
    public static boolean isLightning = false;
    public static Camera camera;
    public static Vector3f cameraBobOffset;
    public static TickTimer tickTimer = new TickTimer();
    public static boolean blackScreen;
    public static boolean youCantEscape;
    /** True selama entity digambar (lihat {@code LevelRendererMixin}). */
    public static boolean shouldRenderWarp = false;

    /** {@code HelpfulHintManager.turnedOnFlashlight}. */
    private static boolean turnedOnFlashlight;

    private CameraEffect() {}

    public static void init() {
        VeilEventPlatform.INSTANCE.onVeilRenderTypeStageRender(CameraEffect::onRenderStage);
        VeilEventPlatform.INSTANCE.preVeilPostProcessing(CameraEffect::preVeilPostProcessing);
        MinecraftForge.EVENT_BUS.addListener(CameraEffect::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(CameraEffect::onLevelTick);
        MinecraftForge.EVENT_BUS.addListener(CameraEffect::onJoin);
        MinecraftForge.EVENT_BUS.addListener(CameraEffect::onDisconnect);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.level.LevelEvent.Unload event) -> {
            if (event.getLevel().isClientSide()) com.backrooms.client.light.LightTicks.clearAll();
        });
        com.backrooms.client.lobby.LobbyClient.init();
        com.backrooms.client.skinwalker.SkinWalkerClient.init();
        LevelClient.init();
        com.backrooms.client.lobby.AnimDev.init();
        ClientSelfTest.init();
        DevWorld.init();
    }

    // ------------------------------------------------------------- stages

    private static void onRenderStage(VeilRenderLevelStageEvent.Stage stage, net.minecraft.client.renderer.LevelRenderer levelRenderer,
                                      net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource,
                                      com.mojang.blaze3d.vertex.PoseStack poseStack, Matrix4f projectionMatrix, int renderTick,
                                      float partialTicks, Camera cam, net.minecraft.client.renderer.culling.Frustum frustum) {
        if (cam != null) {
            camera = cam;
        }
        Minecraft client = Minecraft.getInstance();
        Level clientWorld = client.level;
        if (clientWorld != null) {
            if (isInLevel(BackroomsLevel.POOLROOMS) && stage == VeilRenderLevelStageEvent.Stage.AFTER_SKY && cam != null) {
                ShadowMapRenderer.renderShadowMap(cam, partialTicks, clientWorld);
            }
            if (isInLevel(BackroomsLevel.LEVEL0) && stage == VeilRenderLevelStageEvent.Stage.AFTER_SKY && cam != null) {
                ShadowMapRenderer.renderLevel0ShadowMap(cam, clientWorld);
            }
        }

        PostProcessingManager postProcessingManager = VeilRenderSystem.renderer().getPostProcessingManager();
        if (stage == VeilRenderLevelStageEvent.Stage.AFTER_LEVEL) {
            FLASHLIGHT_RENDERER.renderFlashlightForEveryPlayer(partialTicks);
            com.backrooms.client.skinwalker.SkinWalkerClient.afterLevel(frustum);
            PostPipeline pipeline = postProcessingManager.getPipeline(VHS_POST);
            if (pipeline != null) {
                if (shouldRenderCameraEffect()) {
                    if (!postProcessingManager.isActive(VHS_POST)) {
                        postProcessingManager.add(VHS_POST);
                    }
                } else if (postProcessingManager.isActive(VHS_POST)) {
                    postProcessingManager.remove(VHS_POST);
                }
            }
        }
    }

    private static void preVeilPostProcessing(ResourceLocation name, PostPipeline pipeline, PostPipeline.Context context) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        VeilRenderer renderer = VeilRenderSystem.renderer();
        ShaderPreDefinitions definitions = renderer.getShaderDefinitions();
        if (!shouldRenderCameraEffect() && definitions.getDefinition("WARP") != null) {
            definitions.remove("WARP");
        }
        if (player == null || client.level == null) return;

        if (VHS_POST.equals(name)) {
            ShaderProgram shaderProgram = context.getShader(POST_VHS);
            if (shaderProgram != null) {
                shaderProgram.setInt("youCantEscape", youCantEscape ? 1 : 0);
                com.backrooms.component.PlayerComponent playerComponent = com.backrooms.component.PlayerComponent.get(player);
                if (playerComponent.isBeingCaptured()) {
                    com.backrooms.client.skinwalker.SkinwalkerJumpscare.doJumpscare(shaderProgram, client, playerComponent);
                } else {
                    shaderProgram.setInt("Jumpscare", 0);
                    shaderProgram.setInt("CreepyFace1", 0);
                    shaderProgram.setInt("CreepyFace2", 0);
                    shaderProgram.setVector("Rand", 0.0F, 0.0F);
                }
                if (PreviousUniforms.prevModelViewMat != null && PreviousUniforms.prevProjMat != null) {
                    shaderProgram.setMatrix("prevViewMat", PreviousUniforms.prevModelViewMat);
                    shaderProgram.setMatrix("prevProjMat", PreviousUniforms.prevProjMat);
                    shaderProgram.setVector("prevCameraPos", PreviousUniforms.prevCameraPos);
                }
                shaderProgram.setFloat("MotionBlurStrength", com.backrooms.client.lobby.DropCutscene.motionBlur(ClientConfig.motionBlurStrength()));
                shaderProgram.setFloat("DistortionStrength", ClientConfig.vhsDistortionMultiplier() + com.backrooms.client.lobby.DropCutscene.distortionPulse());
            }

            shaderProgram = context.getShader(SSAO);
            if (shaderProgram != null) {
                shaderProgram.setVectors("samples", SSAOSamples.getSSAOSamples());
            }

            shaderProgram = context.getShader(EVERYTHING_SHADER);
            if (shaderProgram != null) {
                boolean level1 = isInLevel(BackroomsLevel.LEVEL1);
                shaderProgram.setInt("FogToggle", level1 ? 1 : 0);
                shaderProgram.setInt("blackScreen", !blackScreen && !player.isInWall()
                        && !com.backrooms.component.PlayerComponent.get(player).isBeingReleased() ? 0 : 1);
                shaderProgram.setInt("TogglePuddles", level1 ? 1 : 0);
                // Kabut cahaya matahari Poolrooms memudar saat jatuh di cutscene perkenalan (jurang tanpa bayangan jadi abu-abu).
                shaderProgram.setVector("shadowColor", PoolroomsDayCycle.getLightColor().mul(com.backrooms.client.lobby.DropCutscene.sunHaze()));
            }

            shaderProgram = context.getShader(MIXED_SHADER);
            if (shaderProgram != null) {
                shaderProgram.setVector("Rand", RANDOM.nextFloat() * 2.0F - 1.0F, RANDOM2.nextFloat() * 2.0F - 1.0F);
                shaderProgram.setVector("shadowColor", PoolroomsDayCycle.getLightColor());
                shaderProgram.setInt("isLightning", isLightning ? 1 : 0);
            }

            shaderProgram = context.getShader(GLITCH_SHADER);
            if (shaderProgram != null) {
                shaderProgram.setFloat("glitchTime", Math.max(com.backrooms.component.PlayerComponent.get(player).getGlitchTimer(), com.backrooms.client.lobby.DropCutscene.glitch()));
            }
        }

        CONFIG_DEFINITIONS.forEach((define, enabled) -> {
            if (enabled.getAsBoolean()) {
                definitions.define(define);
            } else {
                definitions.remove(define);
            }
        });
        BackroomsLevel current = currentLevel();
        LEVEL_DEFINITIONS.forEach((define, level) -> {
            if (current == level) {
                definitions.define(define);
            } else {
                definitions.remove(define);
            }
        });
        PreviousUniforms.update();
    }

    // -------------------------------------------------------------- ticks

    private static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.level.isClientSide) return;
        for (TickTimer timer : TickTimer.getAllInstances()) {
            timer.addCurrentTick();
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        Player playerClient = client.player;
        if (playerClient != null) {
            setInBackrooms(currentLevel() != null);
            if (client.level != null) {
                LightRenderer lightRenderer = VeilRenderSystem.renderer().getDeferredRenderer().getLightRenderer();
                if (shouldRenderCameraEffect() && isInBackrooms()) {
                    // Hanya Poolrooms yang hasVanillaLighting() di aslinya.
                    if (!isInLevel(BackroomsLevel.POOLROOMS)) {
                        lightRenderer.disableVanillaLight();
                    } else {
                        lightRenderer.enableVanillaLight();
                    }
                    lightRenderer.disableAmbientOcclusion();
                } else {
                    lightRenderer.enableVanillaLight();
                    lightRenderer.enableAmbientOcclusion();
                }
            }
            Flashlight.tick(client);
        }
    }

    private static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        VeilRenderSystem.renderer().getDeferredRenderer().reset();
        PoolroomsDayCycle.dayTime = 0.0F;
        if (ClientConfig.enableHint() && !turnedOnFlashlight) {
            event.getPlayer().sendSystemMessage(Component.translatable("flashlight.hint",
                    ModKeyBinds.TOGGLE_FLASHLIGHT.getTranslatedKeyMessage().plainCopy()
                            .withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE)));
        }
    }

    private static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        if (event.getPlayer() != null) {
            Flashlight.setOn(event.getPlayer().getUUID(), false);
            FLASHLIGHT_RENDERER.tryToRemoveFlashlight(event.getPlayer());
        }
        FLASHLIGHT_RENDERER.clearFlashlights();
        Flashlight.clear();
    }

    // ------------------------------------------------------------ helpers

    /** Dipanggil oleh mixin {@code MutableUniformAccess} untuk setiap shader Veil. */
    public static void setShadowUniforms(MutableUniformAccess access, Level world) {
        Matrix4f level0ViewMat = ShadowMapRenderer.createShadowModelView(camera.getPosition().x, camera.getPosition().y, camera.getPosition().z, true).last().pose();
        Matrix4f viewMat = ShadowMapRenderer.createShadowModelView(camera.getPosition().x, camera.getPosition().y, camera.getPosition().z, world, true).last().pose();
        access.setMatrix("level0ViewMatrix", level0ViewMat);
        access.setMatrix("viewMatrix", viewMat);
        access.setMatrix("IShadowViewMatrix", viewMat.invert());
        access.setMatrix("orthographMatrix", ShadowMapRenderer.createProjMat());
    }

    /** Warp hanya ada di Level 2 (tidak di-port), jadi selalu 0 seperti di level lain. */
    public static float getWarpTimer(Level world) {
        return 0.0F;
    }

    public static boolean isInBackrooms() {
        return inBackrooms;
    }

    public static void setInBackrooms(boolean value) {
        inBackrooms = value;
    }

    public static boolean shouldRenderCameraEffect() {
        if (com.backrooms.client.lobby.LobbyClient.isActive()) {
            // Lobby selalu memakai efek kamera VHS (tidak bisa diatur).
            return true;
        }
        return !isInBackrooms() && ClientConfig.enableVhsEffect() || isInBackrooms() && ClientConfig.enableVhsEffectInTheBackrooms();
    }

    public static CameraShake getCameraShake() {
        return CAMERA_SHAKE;
    }

    static void disableFlashlightHint() {
        turnedOnFlashlight = true;
    }

    public static boolean isInLevel(BackroomsLevel level) {
        return currentLevel() == level;
    }

    /**
     * Level yang suasananya sedang berlaku untuk pemain ini: dimensinya, atau
     * di dimensi lobby set latar yang ditempati (lihat {@link BackroomsLevel#at}).
     */
    public static BackroomsLevel currentLevel() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;
        double x = client.player != null ? client.player.getX() : 0.0D;
        return BackroomsLevel.at(client.level.dimension(), x);
    }
}
