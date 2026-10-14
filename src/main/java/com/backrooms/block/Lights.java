package com.backrooms.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Property yang dipakai bersama semua lampu neon.
 *
 * <p>Seperti di mod aslinya, lampu yang menyala TIDAK digambar sebagai model
 * blok ({@code getRenderShape} = INVISIBLE): block entity renderer menggambar
 * kotak bercahaya lewat shader Veil, dan penerangannya berasal dari point
 * light Veil di sisi klien. Lampunya sendiri tidak memancarkan block light.
 */
public final class Lights {

    public static final BooleanProperty ON = BooleanProperty.create("on");
    public static final BooleanProperty COPY = BooleanProperty.create("copy");
    public static final BooleanProperty BLACKOUT = BooleanProperty.create("blackout");

    private Lights() {}

    /** Menyala = {@code on=true} dan tidak sedang blackout, persis syarat render aslinya. */
    public static boolean lit(BlockState state) {
        return state.getValue(ON) && !state.getValue(BLACKOUT);
    }
}
