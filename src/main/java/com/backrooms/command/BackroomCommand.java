package com.backrooms.command;

import com.backrooms.lobby.LobbyManager;
import com.backrooms.network.ClientActionS2C;
import com.backrooms.network.ModNetwork;
import com.backrooms.world.BackroomsLevel;
import com.backrooms.world.BuildArea;
import com.backrooms.world.LevelBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Arrays;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /backroom gotolevel0}, {@code gotolevel1}, {@code gotopoolrooms},
 * {@code gotoworld}, {@code lobby} (keluar room, kembali ke lobby). Bentuk berspasi {@code /backroom gotolevel <nama>} juga
 * diterima, dengan tab-completion.
 *
 * <p>Efek kamera dan senter (pengaturan klien pemain yang menjalankannya):
 * {@code /backroom camera on|off}, {@code /backroom camera backrooms on|off},
 * {@code /backroom camera luar on|off}, {@code /backroom flashlight [on|off]},
 * {@code /backroom skinwalker <pemain>}, {@code /backroom release <pemain>} (op).
 *
 * <p>Tidak butuh izin op: ini cara utama masuk ke backrooms.
 */
public final class BackroomCommand {

    private BackroomCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("backroom");

        for (BackroomsLevel level : BackroomsLevel.values()) {
            String name = level == BackroomsLevel.POOLROOMS ? "gotopoolrooms" : "gotolevel" + level.commandName();
            root.then(literal(name).executes(ctx -> {
                Teleports.goToLevel(ctx.getSource().getPlayerOrException(), level);
                return 1;
            }));
        }

        root.then(literal("gotolevel").then(argument("level", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        Arrays.stream(BackroomsLevel.values()).map(BackroomsLevel::commandName), builder))
                .executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "level");
                    BackroomsLevel level = BackroomsLevel.byCommandName(name);
                    if (level == null) {
                        ctx.getSource().sendFailure(Component.literal("Level tidak dikenal: " + name
                                + ". Pilihan: 0, 1, poolrooms").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    Teleports.goToLevel(ctx.getSource().getPlayerOrException(), level);
                    return 1;
                })));

        root.then(literal("prebuild").requires(source -> source.hasPermission(2))
                .then(argument("level", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(BackroomsLevel.values()).map(BackroomsLevel::commandName), builder))
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "level");
                            BackroomsLevel level = BackroomsLevel.byCommandName(name);
                            if (level == null) {
                                ctx.getSource().sendFailure(Component.literal("Level tidak dikenal: " + name));
                                return 0;
                            }
                            String status = LevelBuilder.prebuild(ctx.getSource().getServer(), BuildArea.main(level));
                            ctx.getSource().sendSuccess(() -> Component.literal(status), true);
                            return 1;
                        })));

        root.then(literal("gotoworld").executes(ctx -> {
            Teleports.goToWorld(ctx.getSource().getPlayerOrException());
            return 1;
        }));

        root.then(literal("lobby").executes(ctx -> {
            LobbyManager.goToLobby(ctx.getSource().getPlayerOrException());
            return 1;
        }));

        LiteralArgumentBuilder<CommandSourceStack> camera = literal("camera");
        onOff(camera, ClientActionS2C.Action.CAMERA_ALL);
        LiteralArgumentBuilder<CommandSourceStack> inside = literal("backrooms");
        onOff(inside, ClientActionS2C.Action.CAMERA_BACKROOMS);
        LiteralArgumentBuilder<CommandSourceStack> outside = literal("luar");
        onOff(outside, ClientActionS2C.Action.CAMERA_OUTSIDE);
        camera.then(inside).then(outside);
        root.then(camera);

        LiteralArgumentBuilder<CommandSourceStack> flashlight = literal("flashlight");
        flashlight.executes(ctx -> send(ctx.getSource().getPlayerOrException(), ClientActionS2C.Action.FLASHLIGHT_TOGGLE, true));
        onOff(flashlight, ClientActionS2C.Action.FLASHLIGHT);
        root.then(flashlight);

        // Skinwalker: /backroom skinwalker <pemain>, /backroom release <pemain> (op).
        SkinwalkerCommand.register(root);

        dispatcher.register(root);
    }

    private static void onOff(LiteralArgumentBuilder<CommandSourceStack> node, ClientActionS2C.Action action) {
        if (action == null) return;
        node.then(literal("on").executes(ctx -> send(ctx.getSource().getPlayerOrException(), action, true)));
        node.then(literal("off").executes(ctx -> send(ctx.getSource().getPlayerOrException(), action, false)));
    }

    private static int send(ServerPlayer player, ClientActionS2C.Action action, boolean value) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClientActionS2C(action, value));
        return 1;
    }
}
