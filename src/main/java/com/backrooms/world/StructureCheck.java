package com.backrooms.world;

import com.backrooms.block.ModBlocks;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pemeriksaan sekali jalan saat server menyala: setiap entri palet di setiap
 * .nbt struktur harus cocok dengan blok dan property yang terdaftar.
 *
 * <p>Perlu karena vanilla diam-diam mengganti nama blok yang tidak dikenal
 * jadi udara, dan diam-diam mengabaikan property yang tidak ada -- tanpa
 * pemeriksaan ini, satu salah ketik di port blok cukup untuk membuat sebagian
 * bangunan bolong tanpa satu pun pesan di log.
 */
public final class StructureCheck {

    private static final Logger LOGGER = LogUtils.getLogger();

    private StructureCheck() {}

    public static void run(MinecraftServer server) {
        Map<ResourceLocation, Resource> files = server.getResourceManager().listResources("structures",
                id -> id.getNamespace().equals(ModBlocks.NAMESPACE) && id.getPath().endsWith(".nbt"));
        Set<String> problems = new TreeSet<>();
        int entries = 0;
        for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
            try (InputStream in = file.getValue().open()) {
                CompoundTag root = NbtIo.readCompressed(in);
                ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
                for (int i = 0; i < palette.size(); i++) {
                    entries++;
                    check(palette.getCompound(i), problems);
                }
            } catch (Exception e) {
                problems.add(file.getKey() + ": tidak bisa dibaca (" + e.getMessage() + ")");
            }
        }
        if (problems.isEmpty()) {
            LOGGER.info("Cek struktur: {} file, {} entri palet, semua blok & property cocok.", files.size(), entries);
        } else {
            LOGGER.error("Cek struktur: {} masalah di {} file -- bagian bangunan ini akan jadi udara:", problems.size(), files.size());
            problems.forEach(p -> LOGGER.error("  {}", p));
        }
    }

    private static void check(CompoundTag entry, Set<String> problems) {
        ResourceLocation name = new ResourceLocation(entry.getString("Name"));
        if (!BuiltInRegistries.BLOCK.containsKey(name)) {
            problems.add("blok tidak terdaftar: " + name);
            return;
        }
        Block block = BuiltInRegistries.BLOCK.get(name);
        CompoundTag properties = entry.getCompound("Properties");
        for (String key : properties.getAllKeys()) {
            Property<?> property = block.getStateDefinition().getProperty(key);
            String value = properties.getString(key);
            if (property == null) {
                problems.add(name + ": property '" + key + "' tidak ada");
            } else if (property.getValue(value).isEmpty()) {
                problems.add(name + ": nilai " + key + "=" + value + " tidak dikenal");
            }
        }
    }
}
