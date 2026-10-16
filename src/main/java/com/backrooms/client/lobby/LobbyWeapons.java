package com.backrooms.client.lobby;

import com.backrooms.lobby.LobbyWeapon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * Item senjata TACZ yang dipegang karakter di lobby. Hanya untuk tampilan:
 * dibuat di klien, tidak pernah masuk inventori. Tanpa TACZ semuanya kosong.
 */
public final class LobbyWeapons {

    private static final ResourceLocation GUN_ITEM = new ResourceLocation("tacz", "modern_kinetic_gun");
    private static final Map<String, ItemStack> STACKS = new java.util.HashMap<>();
    private static Boolean taczLoaded;

    private LobbyWeapons() {}

    public static boolean available() {
        if (taczLoaded == null) taczLoaded = ModList.get().isLoaded("tacz");
        return taczLoaded;
    }

    public static ItemStack stack(LobbyWeapon weapon) {
        return weapon == null ? ItemStack.EMPTY : stack(weapon.gunId());
    }

    /** Senjata TACZ dengan ID {@code gunId} (mis. pistol tangan kiri di cutscene), atau kosong. */
    public static ItemStack stack(String gunId) {
        if (gunId == null || !available()) return ItemStack.EMPTY;
        return STACKS.computeIfAbsent(gunId, id -> {
            Item item = ForgeRegistries.ITEMS.getValue(GUN_ITEM);
            if (item == null) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(item);
            CompoundTag tag = stack.getOrCreateTag();
            tag.putString("GunId", id);
            tag.putBoolean("HasBulletInBarrel", true);
            tag.putInt("GunCurrentAmmoCount", 30);
            tag.putString("GunFireMode", "AUTO");
            return stack;
        });
    }
}
