package com.backrooms.entity;

import com.backrooms.block.ModBlocks;
import com.backrooms.entity.custom.SkinWalkerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Port dari {@code com.sp.init.ModEntities} (hanya Skinwalker). ID sama: {@code spb-revamped:skin_walker}. */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ModBlocks.NAMESPACE);

    public static final RegistryObject<EntityType<SkinWalkerEntity>> SKIN_WALKER = ENTITY_TYPES.register("skin_walker",
            () -> EntityType.Builder.<SkinWalkerEntity>of(SkinWalkerEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 2.0F)
                    .build(ModBlocks.NAMESPACE + ":skin_walker"));

    private ModEntities() {}

    /** {@code FabricDefaultAttributeRegistry.register(SKIN_WALKER_ENTITY, createSkinWalkerAttributes())}. */
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SKIN_WALKER.get(), SkinWalkerEntity.createSkinWalkerAttributes().build());
    }
}
