package com.backrooms.mixin;

import com.google.common.collect.Multimap;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@code SoundSystem.sounds} (dibuka access widener aslinya) untuk jumpscare Skinwalker. */
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {
    @Accessor("instanceBySource")
    Multimap<SoundSource, SoundInstance> getInstanceBySource();
}
