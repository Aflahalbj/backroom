package com.backrooms.client.skinwalker;

import com.backrooms.component.SkinWalkerComponent;
import com.backrooms.entity.custom.SkinWalkerEntity;
import com.backrooms.entity.ik.model.GeckoLib.GeoModelAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * Port dari {@code com.sp.entity.client.model.SkinWalkerModel}: dalam
 * penyamaran memakai model pemain (lebar/slim) dan skin korbannya; wujud
 * asli memakai model {@code skin_walker_final_default}.
 */
public class SkinWalkerModel extends GeoModel<SkinWalkerEntity> {
    private final ResourceLocation SLIM_MODEL = new ResourceLocation("spb-revamped", "geo/entity/skin_walker_slim.geo.json");
    private final ResourceLocation DEFAULT_MODEL = new ResourceLocation("spb-revamped", "geo/entity/skin_walker_default.geo.json");
    private final ResourceLocation FINAL_MODEL = new ResourceLocation("spb-revamped", "geo/entity/skin_walker_final_default.geo.json");
    private final ResourceLocation PLACEHOLDER_TEXTURE = new ResourceLocation("spb-revamped", "textures/entity/skinwalker/placeholder.png");
    private final ResourceLocation STEVE_TEXTURE = new ResourceLocation("textures/entity/player/wide/steve.png");
    private final ResourceLocation ANIMATION = new ResourceLocation("spb-revamped", "animations/entity/skinwalker.animation.json");

    @Override
    public void setCustomAnimations(SkinWalkerEntity animatable, long instanceId, AnimationState<SkinWalkerEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        SkinWalkerComponent component = animatable.component;
        if (component.isInTrueForm()) {
            animatable.tickComponentsClient(animatable, new GeoModelAccessor(this));
        }
    }

    @Override
    public ResourceLocation getModelResource(SkinWalkerEntity animatable) {
        SkinWalkerComponent component = animatable.component;
        if (!component.isInTrueForm()) {
            Minecraft client = Minecraft.getInstance();
            if (client.level != null && component.getTargetPlayerUUID() != null) {
                AbstractClientPlayer player = (AbstractClientPlayer) client.level.getPlayerByUUID(component.getTargetPlayerUUID());
                if (player != null && player.getModelName().equals("slim")) {
                    return this.SLIM_MODEL;
                }
            }
            return this.DEFAULT_MODEL;
        }
        return this.FINAL_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(SkinWalkerEntity animatable) {
        Minecraft client = Minecraft.getInstance();
        SkinWalkerComponent component = animatable.component;
        if (client.level != null && component.getTargetPlayerUUID() != null) {
            AbstractClientPlayer player = (AbstractClientPlayer) client.level.getPlayerByUUID(component.getTargetPlayerUUID());
            if (player != null) {
                return player.getSkinTextureLocation();
            }
        }
        return this.PLACEHOLDER_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(SkinWalkerEntity animatable) {
        return this.ANIMATION;
    }
}
