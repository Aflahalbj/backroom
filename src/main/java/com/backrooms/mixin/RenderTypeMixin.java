package com.backrooms.mixin;

import com.backrooms.client.render.ModRenderTypes;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port dari {@code com.sp.mixin.renderlayer.RenderLayerMixin}: layer langit
 * Poolrooms dan layer PBR ditambahkan ke daftar layer chunk (setelah tripwire,
 * sama urutannya dengan aslinya).
 *
 * <p>Forge memberi tiap layer chunk nomor urut ({@code chunkLayerId}) saat
 * {@link RenderType} diinisialisasi, jadi daftar dan nomornya diperbarui di
 * akhir static initializer.
 */
@Mixin(RenderType.class)
public abstract class RenderTypeMixin {

    @Shadow
    @Final
    @Mutable
    private static ImmutableList<RenderType> CHUNK_BUFFER_LAYERS;

    @Shadow(remap = false)
    private int chunkLayerId;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void backrooms$addChunkLayers(CallbackInfo ci) {
        ImmutableList<RenderType> layers = ImmutableList.<RenderType>builder()
                .addAll(CHUNK_BUFFER_LAYERS)
                .add(ModRenderTypes.getPoolroomsSky())
                .add(ModRenderTypes.getPbrLayer())
                .build();
        CHUNK_BUFFER_LAYERS = layers;
        for (int i = 0; i < layers.size(); i++) {
            ((RenderTypeMixin) (Object) layers.get(i)).chunkLayerId = i;
        }
    }
}
