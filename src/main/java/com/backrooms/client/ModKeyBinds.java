package com.backrooms.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Port dari {@code com.sp.ModKeyBinds}. Tombol senter aslinya R; di sini B sesuai permintaan. */
public final class ModKeyBinds {

    public static final KeyMapping TOGGLE_FLASHLIGHT = new KeyMapping("key.spb-revamped.toggle_flashlight",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "spb-revamped.keybinds");

    private ModKeyBinds() {}
}
