package com.backrooms.command;

import com.backrooms.component.PlayerComponent;
import com.backrooms.entity.ModEntities;
import com.backrooms.entity.custom.SkinWalkerEntity;
import com.backrooms.network.ModNetwork;
import com.backrooms.network.PersonalSoundS2C;
import com.backrooms.world.events.WorldEvents;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Port dari {@code com.sp.command.SkinwalkerCommand}: {@code /backroom skinwalker <pemain>}
 * (kulit pemain itu langsung dicuri) dan {@code /backroom release <pemain>}, level OP 2.
 */
public class SkinwalkerCommand {
    private static final SimpleCommandExceptionType TOO_MANY_TARGETS = new SimpleCommandExceptionType(new LiteralMessage("Can only apply to 1 target"));

    /** Didaftarkan di bawah {@code /backroom} (aslinya command tingkat atas {@code /skinwalker} dan {@code /release}). */
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("skinwalker").requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> execute(context.getSource(), EntityArgument.getPlayers(context, "targets")))));
        root.then(Commands.literal("release").requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> release(context.getSource(), EntityArgument.getPlayers(context, "targets")))));
    }

    private static int execute(CommandSourceStack source, Collection<ServerPlayer> targets) throws CommandSyntaxException {
        if (targets.size() > 1) {
            throw TOO_MANY_TARGETS.create();
        }
        for (ServerPlayer target : targets) {
            // Aslinya komponen dunia tempat command dijalankan; di sini salinan level tempat korban berada.
            WorldEvents events = WorldEvents.get(source.getLevel(), target.getX());
            events.setActiveSkinwalkerTarget(target.getUUID());
            SkinWalkerEntity skinWalkerEntity = ModEntities.SKIN_WALKER.get().create(source.getLevel());
            if (skinWalkerEntity != null) {
                PlayerComponent targetComponent = PlayerComponent.get(target);
                skinWalkerEntity.moveTo(target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
                skinWalkerEntity.setDeltaMovement(target.getDeltaMovement());
                source.getLevel().addFreshEntity(skinWalkerEntity);
                events.activeSkinWalkerEntity = skinWalkerEntity;
                targetComponent.setPrevGameMode(target.gameMode.getGameModeForPlayer());
                targetComponent.setBeingCaptured(true);
                targetComponent.setHasBeenCaptured(true);
                targetComponent.setShouldBeMuted(true);
                targetComponent.sync();
                target.setGameMode(GameType.SPECTATOR);
                target.setCamera(skinWalkerEntity);
            }
        }
        return 1;
    }

    private static int release(CommandSourceStack source, Collection<ServerPlayer> targets) throws CommandSyntaxException {
        if (targets.size() > 1) {
            throw TOO_MANY_TARGETS.create();
        }
        for (ServerPlayer target : targets) {
            PlayerComponent targetComponent = PlayerComponent.get(target);
            WorldEvents events = WorldEvents.get(source.getLevel(), target.getX());
            targetComponent.setHasBeenCaptured(false);
            targetComponent.setShouldBeMuted(false);
            targetComponent.sync();
            target.setGameMode(targetComponent.getPrevGameMode() != null ? targetComponent.getPrevGameMode() : GameType.SURVIVAL);
            target.setCamera(target);
            // Aslinya NullPointerException kalau tidak ada Skinwalker aktif; di sini dilewati.
            if (events.activeSkinWalkerEntity != null) {
                events.activeSkinWalkerEntity.discard();
            }
            events.activeSkinWalkerEntity = null;
            ModNetwork.send(target, new PersonalSoundS2C("skinwalker_release", 1.0F, 1.0F));
        }
        return 1;
    }
}
