package com.backrooms.entity.ik.util;

import java.util.List;

/** Port dari {@code com.sp.entity.ik.util.ArrayUtil}. */
public class ArrayUtil {
    public static <T> T getFirst(List<T> list) {
        return list.get(0);
    }

    public static <T> T getLast(List<T> list) {
        return list.get(list.size() - 1);
    }

    public static <T> T getOrNull(List<T> list, int index) {
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }
}
