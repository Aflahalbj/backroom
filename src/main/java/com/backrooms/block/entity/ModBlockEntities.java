package com.backrooms.block.entity;

import com.backrooms.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Block entity yang punya logika server di mod aslinya. ID-nya disamakan
 * dengan aslinya -- termasuk {@code poolrooms_window_block_entity}, yang di
 * aslinya memang dipakai untuk drawing marker.
 *
 * <p>Emergency light ikut dibawa karena efek klien Veil-nya (lampu merah
 * berputar + alarm) sekarang di-port.
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ModBlocks.NAMESPACE);

    public static final RegistryObject<BlockEntityType<FluorescentLightBlockEntity>> FLUORESCENT_LIGHT =
            BLOCK_ENTITIES.register("fluorescent_light_block_entity", () -> BlockEntityType.Builder
                    .of(FluorescentLightBlockEntity::new, ModBlocks.FLUORESCENT_LIGHT.get()).build(null));

    public static final RegistryObject<BlockEntityType<ThinFluorescentLightBlockEntity>> THIN_FLUORESCENT_LIGHT =
            BLOCK_ENTITIES.register("thin_fluorescent_light_block_entity", () -> BlockEntityType.Builder
                    .of(ThinFluorescentLightBlockEntity::new, ModBlocks.THIN_FLUORESCENT_LIGHT.get()).build(null));

    public static final RegistryObject<BlockEntityType<DrawingMarkerBlockEntity>> DRAWING_MARKER =
            BLOCK_ENTITIES.register("poolrooms_window_block_entity", () -> BlockEntityType.Builder
                    .of(DrawingMarkerBlockEntity::new, ModBlocks.DRAWING_MARKER.get()).build(null));

    public static final RegistryObject<BlockEntityType<WoodenCrateBlockEntity>> WOODEN_CRATE =
            BLOCK_ENTITIES.register("wooden_crate_block_entity", () -> BlockEntityType.Builder
                    .of(WoodenCrateBlockEntity::new, ModBlocks.WOODEN_CRATE.get()).build(null));

    public static final RegistryObject<BlockEntityType<EmergencyLightBlockEntity>> EMERGENCY_LIGHT =
            BLOCK_ENTITIES.register("emergency_light_block_entity", () -> BlockEntityType.Builder
                    .of(EmergencyLightBlockEntity::new, ModBlocks.EMERGENCY_LIGHT.get()).build(null));

    private ModBlockEntities() {}
}
