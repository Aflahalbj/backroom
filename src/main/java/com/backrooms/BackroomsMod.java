package com.backrooms;

import com.backrooms.network.ModNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import com.backrooms.block.ModBlocks;
import com.backrooms.block.entity.ModBlockEntities;
import com.backrooms.command.BackroomCommand;
import com.backrooms.config.BackroomsConfig;
import com.backrooms.item.ModItems;
import com.backrooms.sound.ModSounds;
import com.backrooms.world.LevelBuilder;
import com.backrooms.world.SelfTest;
import com.backrooms.world.StructureCheck;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Port Forge dari blok, struktur, dan generator level SP-Backrooms Revamped,
 * dalam bentuk tiga dimensi void yang dibangun saat pertama dikunjungi,
 * plus efek kamera VHS dan senter aslinya (di atas Veil).
 * Masuk lewat {@code /backroom gotolevel0} dst., keluar lewat
 * {@code /backroom gotoworld}.
 */
@Mod(BackroomsMod.MOD_ID)
public class BackroomsMod {

    public static final String MOD_ID = "backrooms";

    public BackroomsMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModSounds.SOUND_EVENTS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BackroomsConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, com.backrooms.client.ClientConfig.SPEC);
        ModNetwork.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.backrooms.client.CameraEffect::init);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BackroomCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            LevelBuilder.tick(event.getServer());
            SelfTest.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        StructureCheck.run(event.getServer());
        if (SelfTest.enabled()) {
            SelfTest.start(event.getServer());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LevelBuilder.clear();
    }
}
