package com.backrooms.client.skinwalker;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Port dari {@code com.sp.entity.client.SkinWalkerCapturedFlavorText}: pesan untuk korban yang kulitnya dicuri. */
public class SkinWalkerCapturedFlavorText {
    private static final Minecraft client = Minecraft.getInstance();
    private static final Options options = client.options;
    private static boolean shownMovementText = false;
    public static boolean triedToOpenInventory = false;
    private static boolean shownInventoryText = false;
    public static boolean triedToLeave = false;
    private static boolean shownTriedToLeaveText = false;
    public static boolean triedToChat = false;
    private static boolean shownTriedToChatText = false;
    private static int textCount = 0;
    private static boolean shownTextTaunt = false;
    private static int tick = 0;

    public static void tickFlavorText(Player player) {
        if (isPressingMoveKeys() && !shownMovementText) {
            send(player, Component.translatable("skinwalker.flavor-text.move1").append(Component.translatable("skinwalker.flavor-text.move2").withStyle(ChatFormatting.RED)));
            shownMovementText = true;
            ++textCount;
        }

        if (triedToOpenInventory && !shownInventoryText) {
            send(player, Component.translatable("skinwalker.flavor-text.inventory").withStyle(ChatFormatting.RED));
            shownInventoryText = true;
            ++textCount;
        }

        if (triedToLeave && !shownTriedToLeaveText) {
            send(player, Component.translatable("skinwalker.flavor-text.leave").withStyle(ChatFormatting.RED));
            shownTriedToLeaveText = true;
            ++textCount;
        }

        if (triedToChat && !shownTriedToChatText) {
            send(player, Component.translatable("skinwalker.flavor-text.chat").withStyle(ChatFormatting.RED));
            shownTriedToChatText = true;
            ++textCount;
        }

        if (!shownTextTaunt && !shownTriedToChatText && textCount >= 2) {
            ++tick;
            if (tick == 100) {
                send(player, Component.translatable("skinwalker.flavor-text.taunt1").withStyle(ChatFormatting.RED));
            }
            if (tick == 135) {
                send(player, Component.translatable("skinwalker.flavor-text.taunt2").withStyle(ChatFormatting.GOLD));
            }
            if (tick == 300) {
                send(player, Component.translatable("skinwalker.flavor-text.taunt3").append(Component.translatable("skinwalker.flavor-text.taunt4")).withStyle(ChatFormatting.RED));
                shownTextTaunt = true;
            }
        }
    }

    /** {@code PlayerEntity.sendMessage(Text)} di klien = pesan chat lokal. */
    private static void send(Player player, Component message) {
        player.displayClientMessage(message, false);
    }

    private static boolean isPressingMoveKeys() {
        return options.keyUp.isDown() || options.keyDown.isDown() || options.keyLeft.isDown() || options.keyRight.isDown() || options.keyJump.isDown() || options.keyShift.isDown();
    }
}
