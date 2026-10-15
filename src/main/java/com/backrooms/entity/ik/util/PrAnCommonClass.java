package com.backrooms.entity.ik.util;

import java.util.logging.Logger;

/**
 * Port dari {@code com.sp.entity.ik.util.PrAnCommonClass}. Seperti aslinya,
 * {@link #isDev} bernilai true sejak awal. {@link #shouldRenderDebugLegs}
 * (kotak/garis biru bantu kaki IK) mati, F3+J di dev menyalakannya.
 */
public class PrAnCommonClass {
    public static Logger LOGGER = Logger.getLogger("PrAn");
    public static boolean isDev = true;
    public static boolean shouldRenderDebugLegs = false;

    public static void throwInDevOnly(RuntimeException exception) {
        if (isDev) {
            throw exception;
        }
    }

    public static void init() {
    }
}
