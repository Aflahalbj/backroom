package com.backrooms.mixin;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Port dari {@code com.sp.mixin.customatlas.BakedModelManagerMixin}: atlas
 * normal map dan height map untuk layer PBR, dan atlas blok yang dimuat dari
 * {@code spb-revamped:atlases/blocks.json} (tanpa tekstur _normal/_height),
 * supaya ketiga atlas punya susunan sprite yang sama persis.
 */
@Mixin(ModelManager.class)
public abstract class ModelManagerMixin {

    @Shadow
    @Final
    @Mutable
    private static Map<ResourceLocation, ResourceLocation> VANILLA_ATLASES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void backrooms$addPbrAtlas(CallbackInfo ci) {
        VANILLA_ATLASES = new HashMap<>(VANILLA_ATLASES);
        VANILLA_ATLASES.put(new ResourceLocation("spb-revamped", "textures/atlas/normal.png"), new ResourceLocation("spb-revamped", "normal"));
        VANILLA_ATLASES.put(new ResourceLocation("spb-revamped", "textures/atlas/height.png"), new ResourceLocation("spb-revamped", "height"));
        VANILLA_ATLASES.put(TextureAtlas.LOCATION_BLOCKS, new ResourceLocation("spb-revamped", "blocks"));
    }
}
