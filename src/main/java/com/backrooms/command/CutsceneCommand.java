package com.backrooms.command;

import com.backrooms.lobby.LobbyManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /backroom cutscene drop play} (OP 2): uji cutscene perkenalan room di
 * lobby, hanya untuk pemain yang menjalankannya; tidak ada yang dipindah ke Backrooms.
 */
public final class CutsceneCommand {

    private CutsceneCommand() {}

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("cutscene").requires(source -> source.hasPermission(2))
                .then(Commands.literal("drop")
                        .then(Commands.literal("play").executes(ctx -> playDrop(ctx.getSource().getPlayerOrException())))));
    }

    private static int playDrop(ServerPlayer player) {
        if (!LobbyManager.previewDrop(player)) {
            player.sendSystemMessage(Component.literal("Cutscene drop hanya bisa diputar di lobby (tunggu latarnya selesai dibangun)."));
            return 0;
        }
        return 1;
    }
}
