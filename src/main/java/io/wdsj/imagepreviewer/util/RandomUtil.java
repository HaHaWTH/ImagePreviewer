package io.wdsj.imagepreviewer.util;

import org.bukkit.Bukkit;

import java.util.concurrent.ThreadLocalRandom;

public class RandomUtil {

    public static int genRandomInteger(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
    }

    public static int genRandomMapId() {
        int id;
        do {
            id = genRandomInteger(100, Integer.MAX_VALUE);
        } while (Bukkit.getMap(id) != null);
        return id;
    }
}
