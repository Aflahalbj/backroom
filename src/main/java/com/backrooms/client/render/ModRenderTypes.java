package com.backrooms.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.api.client.render.VeilRenderBridge;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Port dari {@code com.sp.render.RenderLayers}: layer PBR, layer langit
 * Poolrooms, layer kotak cahaya lampu neon, dan layer kepala Skinwalker yang
 * terdistorsi, semuanya memakai shader Veil milik mod aslinya.
 *
 * <p>Layer PBR dan langit adalah layer chunk tambahan (seperti
 * {@code RenderLayerMixin} di aslinya); di Forge keduanya didaftarkan ke
 * {@link RenderType#chunkBufferLayers()} oleh {@code RenderTypeMixin}.
 */
public final class ModRenderTypes extends RenderType {

    public static final ResourceLocation NORMAL_ATLAS_TEXTURE = new ResourceLocation("spb-revamped", "textures/atlas/normal.png");
    public static final ResourceLocation HEIGHT_ATLAS_TEXTURE = new ResourceLocation("spb-revamped", "textures/atlas/height.png");

    private static final ShaderStateShard LIGHT_SHADER = VeilRenderBridge.shaderState(new ResourceLocation("spb-revamped", "light/fluorescent_light"));
    private static final ShaderStateShard POOLROOMS_SKY_SHADER = VeilRenderBridge.shaderState(new ResourceLocation("spb-revamped", "sky"));
    private static final ShaderStateShard DISTORTED_ENTITY_SHADER = VeilRenderBridge.shaderState(new ResourceLocation("spb-revamped", "distorted_entity"));
    private static final ShaderStateShard PBR_SHADER = VeilRenderBridge.shaderState(new ResourceLocation("spb-revamped", "pbr/pbr"));

    private static final RenderType PBR_LAYER = create("pbr", ModVertexFormats.PBR, VertexFormat.Mode.QUADS, 2097152, false, false,
            CompositeState.builder()
                    .setLightmapState(LIGHTMAP)
                    .setShaderState(PBR_SHADER)
                    .setTextureState(MultiTextureStateShard.builder()
                            .add(TextureAtlas.LOCATION_BLOCKS, false, true)
                            .add(NORMAL_ATLAS_TEXTURE, false, true)
                            .add(HEIGHT_ATLAS_TEXTURE, false, false)
                            .add(HEIGHT_ATLAS_TEXTURE, false, true)
                            .build())
                    .createCompositeState(true));

    public static final RenderType FLUORESCENT_LIGHT = create("fluorescent_light", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 256, false, false,
            CompositeState.builder().setShaderState(LIGHT_SHADER).createCompositeState(false));

    private static final RenderType POOLROOMS_SKY = create("poolrooms_sky", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 256, false, false,
            CompositeState.builder().setShaderState(POOLROOMS_SKY_SHADER).createCompositeState(true));

    /** Kepala Skinwalker wujud asli. */
    private static final Function<ResourceLocation, RenderType> DISTORTED_ENTITY = Util.memoize(texture -> {
        CompositeState state = CompositeState.builder()
                .setShaderState(DISTORTED_ENTITY_SHADER)
                .setTextureState(new TextureStateShard(texture, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
        return create("distorted_entity", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false, state);
    });

    private ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                           boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    public static RenderType getDistortedEntity(ResourceLocation texture) {
        return DISTORTED_ENTITY.apply(texture);
    }

    public static RenderType getPbrLayer() {
        return PBR_LAYER;
    }

    public static RenderType getPoolroomsSky() {
        return POOLROOMS_SKY;
    }
}
