package com.backrooms.client.render;

import com.backrooms.client.Flashlight;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.deferred.light.AreaLight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Port dari {@code com.sp.render.FlashlightRenderer}: senter tiap pemain =
 * dua {@link AreaLight} Veil (terang 1, jarak 25, ukuran 0; yang kedua sudut
 * 0.25) di posisi mata, arahnya mengikuti pandangan dengan slerp
 * {@code 0.7 × durasi frame} supaya terasa seperti senter yang dipegang.
 */
public class FlashlightRenderer {

    private final Minecraft client = Minecraft.getInstance();
    private final Map<AbstractClientPlayer, List<AreaLight>> flashLightList2 = new HashMap<>();

    public void renderFlashlightForEveryPlayer(float partialTicks) {
        if (this.client.level == null) return;
        // Pemain dari dunia sebelumnya (pindah dimensi/respawn) tidak lagi ada
        // di daftar dunia ini; lampunya dilepas supaya tidak tertinggal.
        Iterator<Map.Entry<AbstractClientPlayer, List<AreaLight>>> stale = this.flashLightList2.entrySet().iterator();
        while (stale.hasNext()) {
            Map.Entry<AbstractClientPlayer, List<AreaLight>> entry = stale.next();
            if (entry.getKey().level() != this.client.level || entry.getKey().isRemoved()) {
                entry.getValue().forEach(light -> VeilRenderSystem.renderer().getDeferredRenderer().getLightRenderer().removeLight(light));
                stale.remove();
            }
        }

        for (AbstractClientPlayer player : this.client.level.players()) {
            if (player == null) continue;
            if (player.isSpectator() && !player.equals(this.client.player)) {
                this.tryToRemoveFlashlight(player);
            } else if (Flashlight.isOn(player)) {
                Vec3 playerPos = player.getEyePosition(partialTicks);
                if (!this.flashLightList2.containsKey(player)) {
                    AreaLight areaLight = new AreaLight();
                    AreaLight areaLight2 = new AreaLight();
                    Quaternionf orientation = new Quaternionf().rotateXYZ((float) -Math.toRadians(player.getViewXRot(partialTicks)), (float) Math.toRadians(player.getViewYRot(partialTicks)), 0.0F);
                    VeilRenderSystem.renderer().getDeferredRenderer().getLightRenderer().addLight(areaLight.setBrightness(1.0F).setDistance(25.0F).setSize(0.0D, 0.0D).setPosition(playerPos.x(), playerPos.y(), playerPos.z()).setOrientation(orientation));
                    VeilRenderSystem.renderer().getDeferredRenderer().getLightRenderer().addLight(areaLight2.setBrightness(1.0F).setAngle(0.25F).setDistance(25.0F).setSize(0.0D, 0.0D).setPosition(playerPos.x(), playerPos.y(), playerPos.z()).setOrientation(orientation));
                    List<AreaLight> list = new ArrayList<>();
                    list.add(areaLight);
                    list.add(areaLight2);
                    this.flashLightList2.put(player, list);
                } else {
                    for (AreaLight areaLights : this.flashLightList2.get(player)) {
                        Quaternionf currentRot = new Quaternionf().rotateXYZ((float) -Math.toRadians(player.getViewXRot(partialTicks)), (float) Math.toRadians(player.getViewYRot(partialTicks)), 0.0F);
                        float alpha = this.client.player != null && this.client.player.isSpectator() ? 1.0F : 0.7F * this.client.getDeltaFrameTime();
                        areaLights.getOrientation().slerp(currentRot, alpha);
                        areaLights.setPosition(playerPos.x(), playerPos.y(), playerPos.z());
                    }
                }
            } else {
                this.tryToRemoveFlashlight(player);
            }
        }
    }

    public void tryToRemoveFlashlight(AbstractClientPlayer player) {
        List<AreaLight> lights = this.flashLightList2.remove(player);
        if (lights != null) {
            for (AreaLight areaLight : lights) {
                VeilRenderSystem.renderer().getDeferredRenderer().getLightRenderer().removeLight(areaLight);
            }
        }
    }

    public void clearFlashlights() {
        this.flashLightList2.clear();
    }
}
