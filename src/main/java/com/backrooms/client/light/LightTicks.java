package com.backrooms.client.light;

import com.backrooms.block.EmergencyLightBlock;
import com.backrooms.block.Lights;
import com.backrooms.block.entity.EmergencyLightBlockEntity;
import com.backrooms.block.entity.FluorescentLightBlockEntity;
import com.backrooms.block.entity.ThinFluorescentLightBlockEntity;
import com.backrooms.client.CameraEffect;
import com.backrooms.client.ClientConfig;
import com.backrooms.sound.ModSounds;
import com.backrooms.world.BackroomsLevel;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.deferred.light.AreaLight;
import foundry.veil.api.client.render.deferred.light.Light;
import foundry.veil.api.client.render.deferred.light.PointLight;
import foundry.veil.api.client.render.deferred.light.renderer.LightRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Random;
import java.util.Set;

/**
 * Port sisi klien dari {@code com.sp.clientWrapper.ClientWrapper}
 * ({@code doClientSideTick}, {@code doClientSideThinFluorescentsTick},
 * {@code tickEmergencyLight}): lampu Veil per lampu blok, bunyi kedip, dan
 * suara dengung/alarm. Nilai warna, terang, dan radiusnya sama dengan aslinya.
 */
public final class LightTicks {

    /** Field Veil yang di aslinya ditaruh langsung di block entity. */
    static final class State {
        PointLight pointLight;
        AreaLight areaLight1;
        AreaLight areaLight2;
        boolean initEmergencyLights;
        boolean initNormalLights;
        EmergencyAlarmSoundInstance emergencyAlarmSoundInstance;
    }

    private LightTicks() {}

    private static LightRenderer lights() {
        return VeilRenderSystem.renderer().getDeferredRenderer().getLightRenderer();
    }

    private static State state(FluorescentLightBlockEntity be) {
        if (!(be.clientLight instanceof State)) be.clientLight = new State();
        return (State) be.clientLight;
    }

    private static State state(ThinFluorescentLightBlockEntity be) {
        if (!(be.clientLight instanceof State)) be.clientLight = new State();
        return (State) be.clientLight;
    }

    private static State state(EmergencyLightBlockEntity be) {
        if (!(be.clientLight instanceof State)) be.clientLight = new State();
        return (State) be.clientLight;
    }

    /** Semua lampu Veil yang dipasang di sini, supaya bisa dibersihkan saat dunianya diganti. */
    private static final Set<Light> ADDED = Collections.newSetFromMap(new IdentityHashMap<>());

    public static void addLight(Light light) {
        ADDED.add(light);
        lights().addLight(light);
    }

    public static void removeLight(Light light) {
        if (light != null) {
            ADDED.remove(light);
            lights().removeLight(light);
        }
    }

    /**
     * Dunia klien diganti (pindah dimensi, keluar server): block entity dunia
     * lama tidak dihapus satu per satu, jadi lampunya dilepas di sini. Tanpa
     * ini lampu dari dimensi sebelumnya menumpuk dan penerangan rusak.
     */
    public static void clearAll() {
        if (ADDED.isEmpty()) return;
        LightRenderer renderer = lights();
        for (Light light : ADDED) {
            renderer.removeLight(light);
        }
        ADDED.clear();
    }

    // ------------------------------------------------------- fluorescent

    public static void fluorescent(Level world, BlockPos pos, BlockState state, FluorescentLightBlockEntity block) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        Vec3 position = pos.getCenter();
        State s = state(block);
        if (player == null) return;
        if (!state.getValue(Lights.COPY) && pos.closerToCenterThan(player.position(), 20.0D)
                && block.prevOn != world.getBlockState(pos).getValue(Lights.ON)) {
            client.getSoundManager().play(new SimpleSoundInstance(ModSounds.event("light_blink"), SoundSource.AMBIENT, 0.1F,
                    block.random1.nextFloat(0.9F, 1.1F), block.random, pos));
        }
        Vec3 playerPos = player.position();
        boolean withinDistance = pos.closerToCenterThan(playerPos, ClientConfig.lightRenderDistance());
        if (withinDistance) {
            if (!state.getValue(Lights.COPY) && state.getValue(Lights.ON) && !state.getValue(Lights.BLACKOUT)) {
                if (!block.playingSound && pos.closerToCenterThan(playerPos, 16.0D) && !state.getValue(Lights.BLACKOUT) && !CameraEffect.blackScreen) {
                    client.getSoundManager().play(new FluorescentLightSoundInstance(block, player));
                    block.playingSound = true;
                }
                if (s.pointLight == null) {
                    s.pointLight = new PointLight();
                    addLight(s.pointLight.setRadius(13.0F).setColor(1.0F, 0.9411765F, 0.39215687F)
                            .setPosition(position.x, position.y - 1.0D, position.z).setBrightness(1.0F));
                }
            } else {
                removeLight(s.pointLight);
                s.pointLight = null;
                block.playingSound = false;
            }
        } else {
            removeLight(s.pointLight);
            s.pointLight = null;
        }
    }

    // -------------------------------------------------- thin fluorescent

    public static void thinFluorescent(Level world, BlockPos pos, BlockState state, ThinFluorescentLightBlockEntity block) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Random random1 = new Random();
        Vec3 position = pos.getCenter();
        State s = state(block);
        Vec3 playerPos = player.position();
        double distance = ClientConfig.lightRenderDistance();
        boolean withinDistance = pos.closerToCenterThan(playerPos, distance);
        if (withinDistance) {
            if (!state.getValue(Lights.COPY) && pos.closerToCenterThan(playerPos, 15.0D)
                    && block.prevOn != world.getBlockState(pos).getValue(Lights.ON)) {
                Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(ModSounds.event("light_blink"), SoundSource.AMBIENT, 0.2F,
                        random1.nextFloat(0.9F, 1.1F), block.random, pos));
            }
            if (!state.getValue(Lights.COPY) && state.getValue(Lights.ON) && !state.getValue(Lights.BLACKOUT)) {
                if (!block.playingSound && pos.closerToCenterThan(playerPos, 15.0D) && !CameraEffect.blackScreen) {
                    Minecraft.getInstance().getSoundManager().play(new ThinFluorescentLightSoundInstance(block, player));
                    block.playingSound = true;
                }
                if (s.pointLight == null) {
                    s.pointLight = new PointLight();
                    addLight(s.pointLight.setRadius(18.0F).setBrightness(0.0024F));
                    // Di aslinya switch FACE/FACING tanpa break jatuh ke kasus
                    // langit-langit, jadi posisinya selalu pusat blok.
                    s.pointLight.setPosition(position.x, position.y, position.z);
                    BackroomsLevel here = BackroomsLevel.at(world, pos.getX());
                    if (here == BackroomsLevel.POOLROOMS) {
                        s.pointLight.setColor(175.0F, 175.0F, 255.0F).setBrightness(0.0035F);
                    } else if (here == BackroomsLevel.LEVEL0) {
                        s.pointLight.setColor(200.0F, 200.0F, 255.0F).setBrightness(0.005F);
                    } else {
                        s.pointLight.setColor(255.0F, 255.0F, 255.0F);
                    }
                }
            } else {
                removeLight(s.pointLight);
                s.pointLight = null;
            }
        } else {
            removeLight(s.pointLight);
            s.pointLight = null;
        }
    }

    // ---------------------------------------------------------- emergency

    public static void emergency(Level world, BlockPos pos, BlockState state, EmergencyLightBlockEntity block) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null) return;
        State s = state(block);
        if (state.getValue(EmergencyLightBlock.RED_LIGHT)) {
            if (!block.playingEmergencyAlarm) {
                s.emergencyAlarmSoundInstance = new EmergencyAlarmSoundInstance(block, player);
                client.getSoundManager().play(s.emergencyAlarmSoundInstance);
                block.setEmergencyAlarm(true);
            }
            Quaternionf quaternionf = new Quaternionf();
            Vec3 centerPos = pos.getCenter();
            switch (state.getValue(EmergencyLightBlock.FACE)) {
                case WALL -> {
                    switch (state.getValue(EmergencyLightBlock.FACING)) {
                        case EAST -> {
                            quaternionf.rotateXYZ(0.0F, 0.0F, (float) Math.toRadians(90.0D));
                            centerPos = centerPos.add(-0.28125D, 0.0D, 0.0D);
                        }
                        case WEST -> {
                            quaternionf.rotateXYZ(0.0F, 0.0F, (float) Math.toRadians(90.0D));
                            centerPos = centerPos.add(0.28125D, 0.0D, 0.0D);
                        }
                        case NORTH -> {
                            quaternionf.rotateXYZ((float) Math.toRadians(90.0D), 0.0F, 0.0F);
                            centerPos = centerPos.add(0.0D, 0.0D, 0.28125D);
                        }
                        case SOUTH -> {
                            quaternionf.rotateXYZ((float) Math.toRadians(90.0D), 0.0F, 0.0F);
                            centerPos = centerPos.add(0.0D, 0.0D, -0.28125D);
                        }
                        default -> { }
                    }
                }
                case FLOOR -> centerPos = centerPos.add(0.0D, -0.28125D, 0.0D);
                default -> centerPos = centerPos.add(0.0D, 0.28125D, 0.0D);
            }
            removeNormalLights(s);
            if (!s.initEmergencyLights) {
                s.areaLight1 = new AreaLight();
                s.areaLight2 = new AreaLight();
                s.pointLight = new PointLight();
                addLight(s.areaLight1.setBrightness(1.0F).setColor(1.0F, 0.0F, 0.0F).setSize(0.0D, 0.0D)
                        .setAngle((float) Math.toRadians(50.0D)).setOrientation(new Quaternionf().rotateXYZ(0.0F, 0.0F, 0.0F))
                        .setPosition(new Vector3d(centerPos.x, centerPos.y, centerPos.z)).setDistance(15.0F));
                addLight(s.areaLight2.setBrightness(1.0F).setColor(1.0F, 0.0F, 0.0F).setSize(0.0D, 0.0D)
                        .setAngle((float) Math.toRadians(50.0D)).setOrientation(new Quaternionf().rotateXYZ(0.0F, 0.0F, 0.0F))
                        .setPosition(new Vector3d(centerPos.x, centerPos.y, centerPos.z)).setDistance(15.0F));
                addLight(s.pointLight.setBrightness(0.5F).setColor(1.0F, 0.0F, 0.0F)
                        .setPosition(new Vector3d(centerPos.x, centerPos.y, centerPos.z)).setRadius(15.0F));
                s.initEmergencyLights = true;
            }
            Quaternionf quaternionf1 = new Quaternionf(quaternionf);
            s.areaLight1.setOrientation(quaternionf1.rotateLocalY((float) Math.toRadians(block.randomOffset + (float) (world.getGameTime() * 20L))));
            Quaternionf quaternionf2 = new Quaternionf(quaternionf);
            s.areaLight2.setOrientation(quaternionf2.rotateLocalY((float) Math.toRadians(block.randomOffset + 180.0F + (float) (world.getGameTime() * 20L))));
        } else {
            if (block.playingEmergencyAlarm) {
                client.getSoundManager().stop(s.emergencyAlarmSoundInstance);
                s.emergencyAlarmSoundInstance = null;
                block.setEmergencyAlarm(false);
            }
            removeEmergencyLights(s);
            if (!s.initNormalLights) {
                s.pointLight = new PointLight();
                Vec3 centerPos = pos.getCenter().add(0.0D, -0.15625D, 0.0D);
                addLight(s.pointLight.setBrightness(1.0F).setPosition(new Vector3d(centerPos.x, centerPos.y, centerPos.z)).setRadius(15.0F));
                s.initNormalLights = true;
            }
        }
    }

    private static void removeEmergencyLights(State s) {
        if (s.initEmergencyLights) {
            removeLight(s.areaLight1);
            s.areaLight1 = null;
            removeLight(s.areaLight2);
            s.areaLight2 = null;
            removeLight(s.pointLight);
            s.pointLight = null;
            s.initEmergencyLights = false;
        }
    }

    private static void removeNormalLights(State s) {
        if (s.initNormalLights) {
            removeLight(s.pointLight);
            s.pointLight = null;
            s.initNormalLights = false;
        }
    }

    // ------------------------------------------------------------ removal

    public static void remove(FluorescentLightBlockEntity block) {
        if (block.clientLight instanceof State s) {
            removeLight(s.pointLight);
            s.pointLight = null;
        }
    }

    public static void remove(ThinFluorescentLightBlockEntity block) {
        if (block.clientLight instanceof State s) {
            removeLight(s.pointLight);
            s.pointLight = null;
        }
    }

    public static void remove(EmergencyLightBlockEntity block) {
        if (block.clientLight instanceof State s) {
            removeEmergencyLights(s);
            removeNormalLights(s);
        }
    }
}
