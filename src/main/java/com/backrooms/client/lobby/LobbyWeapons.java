package com.backrooms.client.lobby;

import com.backrooms.lobby.LobbyWeapon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumMap;
import java.util.Map;

/**
 * Item senjata TACZ yang dipegang karakter di lobby. Hanya untuk tampilan:
 * dibuat di klien, tidak pernah masuk inventori. Tanpa TACZ semuanya kosong.
 */
public final class LobbyWeapons {

    private static final ResourceLocation GUN_ITEM = new ResourceLocation("tacz", "modern_kinetic_gun");
    private static final Map<LobbyWeapon, ItemStack> STACKS = new EnumMap<>(LobbyWeapon.class);
    private static Boolean taczLoaded;

    private LobbyWeapons() {}

    public static boolean available() {
        if (taczLoaded == null) taczLoaded = ModList.get().isLoaded("tacz");
        return taczLoaded;
    }

    public static ItemStack stack(LobbyWeapon weapon) {
        if (weapon == null || weapon.gunId() == null || !available()) return ItemStack.EMPTY;
        return STACKS.computeIfAbsent(weapon, w -> {
            Item item = ForgeRegistries.ITEMS.getValue(GUN_ITEM);
            if (item == null) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(item);
            CompoundTag tag = stack.getOrCreateTag();
            tag.putString("GunId", w.gunId());
            tag.putBoolean("HasBulletInBarrel", true);
            tag.putInt("GunCurrentAmmoCount", 30);
            tag.putString("GunFireMode", "AUTO");
            return stack;
        });
    }
}
