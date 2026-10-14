package com.backrooms.world;

import com.backrooms.block.ModBlocks;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Cek development (bagian dari self-test): setiap state yang mungkin dari
 * setiap blok harus punya varian di blockstate JSON-nya, dan setiap model
 * yang dirujuk -- berikut parent dan teksturnya -- harus ada di jar. Ini
 * meniru cara {@code ModelBakery} mencocokkan varian, tanpa perlu membuka
 * klien.
 */
public final class ModelCheck {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ROOT = "/assets/" + ModBlocks.NAMESPACE + "/";

    private ModelCheck() {}

    public static void run() {
        Set<String> problems = new TreeSet<>();
        Set<String> checkedModels = new HashSet<>();
        int states = 0;
        for (Map.Entry<String, RegistryObject<Block>> entry : ModBlocks.ALL.entrySet()) {
            String name = entry.getKey();
            Block block = entry.getValue().get();
            JsonObject json = read(ROOT + "blockstates/" + name + ".json");
            if (json == null) {
                problems.add(name + ": blockstate JSON tidak ada");
                continue;
            }
            JsonObject variants = json.getAsJsonObject("variants");
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                states++;
                boolean matched = false;
                for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
                    if (matches(state, variant.getKey())) {
                        matched = true;
                        checkVariant(name, variant.getValue(), problems, checkedModels);
                    }
                }
                if (!matched) problems.add(name + ": tidak ada model untuk " + state);
            }
        }
        if (problems.isEmpty()) {
            LOGGER.info("[selftest] Cek model: {} blok, {} state, {} model -- semua punya model & tekstur.",
                    ModBlocks.ALL.size(), states, checkedModels.size());
        } else {
            LOGGER.error("[selftest] Cek model: {} masalah", problems.size());
            problems.forEach(p -> LOGGER.error("[selftest]   {}", p));
        }
    }

    /** Sama dengan {@code ModelBakery.predicate}: semua pasangan key=value yang dikenal harus cocok. */
    private static boolean matches(BlockState state, String key) {
        if (key.isEmpty()) return true;
        for (String pair : key.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2) continue;
            Property<?> property = state.getBlock().getStateDefinition().getProperty(kv[0]);
            if (property == null) continue;
            if (!valueName(state, property).equals(kv[1])) return false;
        }
        return true;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static void checkVariant(String block, JsonElement variant, Set<String> problems, Set<String> checked) {
        if (variant.isJsonArray()) {
            variant.getAsJsonArray().forEach(v -> checkVariant(block, v, problems, checked));
            return;
        }
        checkModel(block, variant.getAsJsonObject().get("model").getAsString(), problems, checked);
    }

    private static void checkModel(String block, String model, Set<String> problems, Set<String> checked) {
        if (!model.startsWith(ModBlocks.NAMESPACE + ":") || !checked.add(model)) return;
        String path = model.substring(model.indexOf(':') + 1);
        JsonObject json = read(ROOT + "models/" + path + ".json");
        if (json == null) {
            problems.add(block + ": model " + model + " tidak ada");
            return;
        }
        if (json.has("parent")) {
            checkModel(block, json.get("parent").getAsString(), problems, checked);
        }
        if (json.has("textures")) {
            for (Map.Entry<String, JsonElement> texture : json.getAsJsonObject("textures").entrySet()) {
                String value = texture.getValue().getAsString();
                if (!value.startsWith(ModBlocks.NAMESPACE + ":")) continue;
                String file = ROOT + "textures/" + value.substring(value.indexOf(':') + 1) + ".png";
                if (ModelCheck.class.getResource(file) == null) {
                    problems.add(block + ": tekstur " + value + " (model " + model + ") tidak ada");
                }
            }
        }
    }

    private static JsonObject read(String path) {
        try (InputStream in = ModelCheck.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
