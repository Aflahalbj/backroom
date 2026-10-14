package com.backrooms.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {

    @Invoker("setRotation")
    void backrooms$setRotation(float yRot, float xRot);

    @Invoker("setPosition")
    void backrooms$setPosition(Vec3 pos);

    /** Kamera lepas dari pemain: tubuh pemain ikut digambar (kamera lobby dan cutscene). */
    @Accessor("detached")
    void backrooms$setDetached(boolean detached);
}
