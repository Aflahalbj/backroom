package com.backrooms.command;

import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.events.AbstractEvent;
import com.backrooms.world.events.WorldEvents;
import com.backrooms.world.events.generic.lights.LightLevelBlackout;
import com.backrooms.world.events.generic.lights.LightLevelFlicker;
import com.backrooms.world.events.level0.Level0IntercomBasic;
import com.backrooms.world.events.level0.Level0Music;
import com.backrooms.world.events.level1.Level1Ambience;
import com.backrooms.world.events.level1.Level1Blackout;
import com.backrooms.world.events.poolrooms.PoolroomsAmbience;
import com.backrooms.world.events.poolrooms.PoolroomsSunset;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Port dari {@code com.sp.command.EventCommand} ({@code /backroomsevent}, di sini
 * {@code /backroom event <nama>}, level OP 2): memulai event langsung di salinan level
 * tempat command dijalankan. Event level yang tidak ada di port ini (warp Level 2,
 * Level 324, Infinite Field) tidak ikut; pesan errornya tetap sama dengan aslinya.
 */
public class EventCommand {
    private static final SimpleCommandExceptionType FLICKER_BLACKOUT_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Event only occurs in Level 0 and Level 1"));
    private static final SimpleCommandExceptionType ONLY_LEVEL0_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Event only occurs in Level 0"));
    private static final SimpleCommandExceptionType AMBIENCE_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Event only occurs in Level 1, Level 2, The Poolrooms, or the Infinite Field"));
    private static final SimpleCommandExceptionType SUNSET_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Event only occurs in The Poolrooms"));

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("event").requires(source -> source.hasPermission(2))
                .then(Commands.literal("flicker").executes(context -> doFlicker(context.getSource())))
                .then(Commands.literal("blackout").executes(context -> doBlackout(context.getSource())))
                .then(Commands.literal("intercom").executes(context -> doIntercom(context.getSource())))
                .then(Commands.literal("music").executes(context -> doMusic(context.getSource())))
                .then(Commands.literal("ambience").executes(context -> doAmbience(context.getSource())))
                .then(Commands.literal("sunset").executes(context -> doSunset(context.getSource()))));
    }

    private static BackroomsLevel levelOf(CommandSourceStack source) {
        return BackroomsLevel.byDimension(source.getLevel().dimension());
    }

    private static int doFlicker(CommandSourceStack source) throws CommandSyntaxException {
        BackroomsLevel level = levelOf(source);
        if (level == BackroomsLevel.LEVEL0 || level == BackroomsLevel.LEVEL1) {
            setEvent(source, new LightLevelFlicker());
            return 1;
        }
        throw FLICKER_BLACKOUT_EXCEPTION.create();
    }

    private static int doBlackout(CommandSourceStack source) throws CommandSyntaxException {
        BackroomsLevel level = levelOf(source);
        if (level == BackroomsLevel.LEVEL0) {
            setEvent(source, new LightLevelBlackout());
            return 1;
        } else if (level == BackroomsLevel.LEVEL1) {
            setEvent(source, new Level1Blackout());
            return 1;
        }
        throw FLICKER_BLACKOUT_EXCEPTION.create();
    }

    private static int doIntercom(CommandSourceStack source) throws CommandSyntaxException {
        if (levelOf(source) == BackroomsLevel.LEVEL0) {
            setEvent(source, new Level0IntercomBasic());
            return 1;
        }
        throw ONLY_LEVEL0_EXCEPTION.create();
    }

    private static int doMusic(CommandSourceStack source) throws CommandSyntaxException {
        if (levelOf(source) == BackroomsLevel.LEVEL0) {
            setEvent(source, new Level0Music());
            return 1;
        }
        throw ONLY_LEVEL0_EXCEPTION.create();
    }

    private static int doAmbience(CommandSourceStack source) throws CommandSyntaxException {
        BackroomsLevel level = levelOf(source);
        if (level == BackroomsLevel.POOLROOMS) {
            setEvent(source, new PoolroomsAmbience());
            return 1;
        } else if (level == BackroomsLevel.LEVEL1) {
            setEvent(source, new Level1Ambience());
            return 1;
        }
        throw AMBIENCE_EXCEPTION.create();
    }

    private static int doSunset(CommandSourceStack source) throws CommandSyntaxException {
        if (levelOf(source) == BackroomsLevel.POOLROOMS) {
            setEvent(source, new PoolroomsSunset());
            return 1;
        }
        throw SUNSET_EXCEPTION.create();
    }

    /** Salinan level di posisi command (aslinya satu dimensi = satu level). */
    private static void setEvent(CommandSourceStack source, AbstractEvent activeEvent) {
        WorldEvents.get(source.getLevel(), source.getPosition().x).startEvent(activeEvent);
    }
}
