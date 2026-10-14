package com.backrooms.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

/**
 * Mode uji development ({@code -Panimtest} / {@code -Pclienttest}): begitu
 * title screen muncul, langsung masuk world {@value #NAME}. Kalau world itu
 * belum ada (mis. terhapus), dibuat baru dulu (creative, damai).
 */
public final class DevWorld {

    public static final String NAME = "BackroomsTest";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean opened;

    private DevWorld() {}

    public static void init() {
        if (!Boolean.getBoolean("backrooms.animtest") && !Boolean.getBoolean("backrooms.clienttest")) return;
        MinecraftForge.EVENT_BUS.addListener(DevWorld::onScreen);
    }

    private static void onScreen(ScreenEvent.Init.Post event) {
        if (opened || !(event.getScreen() instanceof TitleScreen title)) return;
        opened = true;
        Minecraft client = Minecraft.getInstance();
        if (client.getLevelSource().levelExists(NAME)) {
            LOGGER.info("[devworld] membuka world {}", NAME);
            client.createWorldOpenFlows().loadLevel(title, NAME);
            return;
        }
        LOGGER.info("[devworld] world {} belum ada, membuat baru", NAME);
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(NAME, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(WorldOptions.randomSeed(), false, false);
        client.createWorldOpenFlows().createFreshLevel(NAME, settings, options, WorldPresets::createNormalWorldDimensions);
    }
}
