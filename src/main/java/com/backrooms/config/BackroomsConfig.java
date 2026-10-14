package com.backrooms.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** {@code config/backrooms-common.toml}. */
public final class BackroomsConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue REGION_RADIUS;
    public static final ForgeConfigSpec.IntValue EXIT_SPAWN_RADIUS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        REGION_RADIUS = builder
                .comment("Ukuran bangunan tiap dimensi, dalam region 80x80 blok dari pusat.",
                        "0 = 80x80 blok (level 0 hanya aula spawn), 1 = 240x240, 2 = 400x400, 3 = 560x560.",
                        "Hanya berlaku untuk level yang belum pernah dibangun.")
                .defineInRange("regionRadius", 1, 0, 6);
        EXIT_SPAWN_RADIUS = builder
                .comment("Jarak minimum (blok) dari spawn sebelum tangga/pintu keluar boleh muncul.",
                        "Mod aslinya memakai 300 karena dunianya tak terbatas; di sini lebih kecil supaya",
                        "struktur keluar tetap ada di dalam area bangunan.")
                .defineInRange("exitSpawnRadius", 120, 0, 10000);
        SPEC = builder.build();
    }

    private BackroomsConfig() {}
}
