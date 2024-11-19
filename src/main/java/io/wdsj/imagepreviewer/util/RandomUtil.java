package io.wdsj.imagepreviewer.util;

import org.bukkit.Bukkit;

import java.util.concurrent.ThreadLocalRandom;

public class RandomUtil {

    public static int genRandomInteger(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
    }

    public static int genRandomMapId() {
        int id = genRandomInteger(100, Integer.MAX_VALUE);
        if (Bukkit.getMap(id) != null) {
            return genRandomMapId();
        }
        return id;
    }
}
