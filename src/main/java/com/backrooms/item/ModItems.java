package com.backrooms.item;

import com.backrooms.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Item untuk setiap blok (seperti {@code registerBlockItem} aslinya) dan tab kreatif "Backrooms". */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModBlocks.NAMESPACE);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModBlocks.NAMESPACE);

    private static final List<RegistryObject<Item>> BLOCK_ITEMS = new ArrayList<>();

    static {
        for (Map.Entry<String, RegistryObject<Block>> entry : ModBlocks.ALL.entrySet()) {
            RegistryObject<Block> block = entry.getValue();
            BLOCK_ITEMS.add(ITEMS.register(entry.getKey(), () -> new BlockItem(block.get(), new Item.Properties())));
        }
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("spbrevamped", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemgroup.spbrevamped"))
            .icon(() -> new ItemStack(ModBlocks.CARPET_BLOCK.get()))
            .displayItems((parameters, output) -> BLOCK_ITEMS.forEach(item -> output.accept(item.get())))
            .build());

    private ModItems() {}
}
