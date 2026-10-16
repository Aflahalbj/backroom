package com.backrooms.client;

import com.backrooms.BackroomsMod;
import com.backrooms.block.ModBlocks;
import com.backrooms.block.entity.ModBlockEntities;
import com.backrooms.client.light.FluorescentLightRenderer;
import com.backrooms.client.light.ThinFluorescentLightRenderer;
import com.backrooms.client.render.ModRenderTypes;
import com.backrooms.client.render.pbr.BlockIdMap;
import com.backrooms.client.render.pbr.PbrRegistry;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * Pendaftaran klien dari {@code SPBRevampedClient.onInitializeClient} asli:
 * render layer blok, layer PBR + langit Poolrooms, ID material blok, tombol
 * senter, dan renderer lampu.
 *
 * <p>Daftar cutout sama persis dengan aslinya. Blok bertekstur transparan
 * lain (slope kolam, pipa) tetap di layer solid -- shader solid aslinya
 * ({@code spbrevamped:warp_solid}) membuang piksel transparan -- dan pagar
 * kawat digambar di layer PBR.
 */
@Mod.EventBusSubscriber(modid = BackroomsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {}

    @SubscribeEvent
    @SuppressWarnings("removal")
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.POOL_SKY.get(), ModRenderTypes.getPoolroomsSky());
            List<RegistryObject<Block>> cutout = List.of(
                    ModBlocks.BOTTOM_TRIM,
                    ModBlocks.WALL_TEXT_1, ModBlocks.WALL_TEXT_2, ModBlocks.WALL_TEXT_3, ModBlocks.WALL_TEXT_4,
                    ModBlocks.WALL_TEXT_5, ModBlocks.WALL_TEXT_6, ModBlocks.WALL_TEXT_7,
                    ModBlocks.ARROW_1, ModBlocks.ARROW_2, ModBlocks.ARROW_3, ModBlocks.ARROW_4,
                    ModBlocks.WALL_SMALL_1, ModBlocks.WALL_SMALL_2,
                    ModBlocks.WALL_DRAWING_DOOR, ModBlocks.WALL_DRAWING_WINDOW,
                    ModBlocks.RUG_1, ModBlocks.RUG_2);
            for (RegistryObject<Block> block : cutout) {
                ItemBlockRenderTypes.setRenderLayer(block.get(), RenderType.cutout());
            }

            BlockIdMap.registerBlockID(blockIdMap -> {
                blockIdMap.put(ModBlocks.POOL_TILES.get(), 18);
                blockIdMap.put(ModBlocks.EMERGENCY_LIGHT.get(), 19);
            });
            PbrRegistry.registerPBR(ModBlocks.CARPET_BLOCK.get(), new PbrRegistry.PbrMaterial(false, 0.0F, 1.25F, 1024));
            PbrRegistry.registerPBR(ModBlocks.CEILING_TILE.get(), new PbrRegistry.PbrMaterial(false, 0.0F, 1.0F, 512));
            PbrRegistry.registerPBR(ModBlocks.GHOST_CEILING_TILE.get(), new PbrRegistry.PbrMaterial(false, 0.0F, 1.0F, 512));
            PbrRegistry.registerPBR(ModBlocks.CONCRETE_11.get(), new PbrRegistry.PbrMaterial(true, 0.4F, 8.0F, 1024));
            PbrRegistry.registerPBR(ModBlocks.BRICKS.get(), new PbrRegistry.PbrMaterial(true, 0.45F, 5.0F, 2048));
            PbrRegistry.registerPBR(ModBlocks.CHAIN_FENCE.get(), new PbrRegistry.PbrMaterial(true, 0.21F, 2.8F, 1024));
            PbrRegistry.registerPBR(ModBlocks.WOODEN_CRATE.get(), new PbrRegistry.PbrMaterial(true, 0.9F, 1.0F, 1024));
        });
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBinds.TOGGLE_FLASHLIGHT);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.FLUORESCENT_LIGHT.get(), FluorescentLightRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.THIN_FLUORESCENT_LIGHT.get(), ThinFluorescentLightRenderer::new);
        event.registerEntityRenderer(com.backrooms.entity.ModEntities.SKIN_WALKER.get(), com.backrooms.client.skinwalker.SkinWalkerRenderer::new);
        event.registerEntityRenderer(com.backrooms.entity.ModEntities.SMILER.get(), com.backrooms.client.smiler.SmilerRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(com.backrooms.client.smiler.SmilerModel.LAYER, com.backrooms.client.smiler.SmilerModel::getTexturedModelData);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        // Seperti listener "after_resources" aslinya: ID blok dihitung ulang saat render berikutnya.
        event.registerReloadListener((ResourceManagerReloadListener) manager -> BlockIdMap.init = false);
    }
}
