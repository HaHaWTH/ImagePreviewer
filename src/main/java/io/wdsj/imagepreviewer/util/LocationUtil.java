package io.wdsj.imagepreviewer.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public class LocationUtil {
    public static float[] calculateYawPitch(Location entityPosition, Location targetPosition) {
        Vector entityVector = entityPosition.toVector();
        Vector targetVector = targetPosition.toVector();
        double dx = targetVector.getX() - entityVector.getX();
        double dy = targetVector.getY() - entityVector.getY();
        double dz = targetVector.getZ() - entityVector.getZ();

        double yaw = Math.toDegrees(Math.atan2(-dx, dz));

        double distanceXZ = Math.sqrt(dx * dx + dz * dz);
        double pitch = Math.toDegrees(Math.atan2(-dy, distanceXZ));

        return new float[]{(float) yaw, (float) pitch};
    }
}
