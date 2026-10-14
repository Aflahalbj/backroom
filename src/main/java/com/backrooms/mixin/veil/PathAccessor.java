package com.backrooms.mixin.veil;

import foundry.veil.api.client.anim.Frame;
import foundry.veil.api.client.anim.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Port dari {@code com.sp.mixin.cutscene.PathAccessor}. */
@Mixin(value = Path.class, remap = false)
public interface PathAccessor {

    @Accessor("frames")
    List<Frame> getFrames();
}
