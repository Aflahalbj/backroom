package com.backrooms.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/** Port dari {@code com.sp.init.ModDamageTypes} (data di {@code data/spb-revamped/damage_type}). */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> ACID_WATER = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("spb-revamped", "acid_water"));
    public static final ResourceKey<DamageType> SMILER = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("spb-revamped", "smiler"));

    private ModDamageTypes() {}

    public static DamageSource of(Level world, ResourceKey<DamageType> key) {
        return new DamageSource(world.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key));
    }
}
