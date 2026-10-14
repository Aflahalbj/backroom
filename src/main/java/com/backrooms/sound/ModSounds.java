package com.backrooms.sound;

import com.backrooms.block.ModBlocks;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Semua sound event spb-revamped, langsung dari {@code sounds.json} aslinya,
 * plus grup suara blok ({@code SprintBlockSoundGroup} di aslinya).
 *
 * <h2>File audionya belum ada</h2>
 *
 * File {@code .ogg}-nya sengaja tidak dibawa -- tempatnya disiapkan di
 * {@code assets/spb-revamped/sounds/} (lihat readme.txt di sana). Selama sebuah
 * event belum lengkap file-nya, grup suara blok memakai suara vanilla yang
 * setara (karpet = wol, beton = batu, rumput = rumput), jadi langkah kaki
 * tidak mendadak sunyi. Begitu file-nya ditempel dan mod di-build ulang,
 * suara aslinya otomatis dipakai.
 *
 * <p>Pengecekannya membaca jar mod ini sendiri, yang sama di server dan klien
 * selama keduanya memakai build yang sama.
 */
public final class ModSounds {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SOUNDS_JSON = "/assets/" + ModBlocks.NAMESPACE + "/sounds.json";

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ModBlocks.NAMESPACE);

    /** Nama event -> file .ogg yang dirujuknya (relatif ke folder sounds, tanpa ekstensi). */
    private static final Map<String, List<String>> FILES = readSoundsJson();
    private static final Map<String, RegistryObject<SoundEvent>> EVENTS = new LinkedHashMap<>();
    private static final Map<String, Boolean> AVAILABLE = new HashMap<>();

    static {
        for (String name : FILES.keySet()) {
            ResourceLocation id = new ResourceLocation(ModBlocks.NAMESPACE, name);
            EVENTS.put(name, SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id)));
        }
    }

    private ModSounds() {}

    /**
     * Event mod ini kalau semua file audionya sudah ada, selain itu cadangan
     * vanilla. Dievaluasi setiap kali suara diputar, jadi tidak bergantung
     * urutan registrasi.
     */
    private static Supplier<SoundEvent> orVanilla(String name, SoundEvent fallback) {
        return () -> available(name) ? EVENTS.get(name).get() : fallback;
    }

    /** Untuk "silence": kalau file-nya belum ada pun hasilnya tetap sunyi. */
    private static Supplier<SoundEvent> silence() {
        return () -> EVENTS.get("silence").get();
    }

    /** Sound event mod ini berdasarkan nama di sounds.json. */
    public static SoundEvent event(String name) {
        return EVENTS.get(name).get();
    }

    public static boolean available(String name) {
        return AVAILABLE.computeIfAbsent(name, key -> {
            List<String> files = FILES.get(key);
            if (files == null || files.isEmpty()) return false;
            for (String file : files) {
                if (ModSounds.class.getResource("/assets/" + ModBlocks.NAMESPACE + "/sounds/" + file + ".ogg") == null) {
                    return false;
                }
            }
            return true;
        });
    }

    // ------------------------------------------------- SprintBlockSoundGroup

    public static final SoundType SILENT = new ForgeSoundType(0.0F, 0.0F,
            silence(), silence(), silence(), silence(), silence());

    public static final SoundType CARPET = new ForgeSoundType(1.0F, 1.0F,
            () -> SoundEvents.WOOL_BREAK, orVanilla("carpet_walk", SoundEvents.WOOL_STEP),
            () -> SoundEvents.WOOL_PLACE, silence(), orVanilla("carpet_run", SoundEvents.WOOL_FALL));

    public static final SoundType CONCRETE = new ForgeSoundType(1.0F, 1.0F,
            () -> SoundEvents.STONE_BREAK, orVanilla("concrete_walk", SoundEvents.STONE_STEP),
            () -> SoundEvents.STONE_PLACE, silence(), orVanilla("concrete_run", SoundEvents.STONE_FALL));

    public static final SoundType WALL = new ForgeSoundType(1.0F, 1.0F,
            () -> SoundEvents.WOOD_BREAK, () -> SoundEvents.WOOD_STEP,
            () -> SoundEvents.WOOD_PLACE, silence(), () -> SoundEvents.WOOD_FALL);

    public static final SoundType CEILING_TILE = WALL;

    // ---------------------------------------------------------------- baca

    private static Map<String, List<String>> readSoundsJson() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        try (InputStream in = ModSounds.class.getResourceAsStream(SOUNDS_JSON)) {
            if (in == null) {
                LOGGER.error("{} tidak ada di jar; tidak ada sound event yang didaftarkan.", SOUNDS_JSON);
                return out;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                List<String> files = new ArrayList<>();
                for (JsonElement sound : entry.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                    String name = sound.isJsonObject() ? sound.getAsJsonObject().get("name").getAsString() : sound.getAsString();
                    files.add(name.substring(name.indexOf(':') + 1));
                }
                out.put(entry.getKey(), files);
            }
        } catch (Exception e) {
            LOGGER.error("Gagal membaca {}", SOUNDS_JSON, e);
        }
        return out;
    }
}
