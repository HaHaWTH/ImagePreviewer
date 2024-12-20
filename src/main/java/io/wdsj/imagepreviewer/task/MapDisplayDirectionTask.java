package io.wdsj.imagepreviewer.task;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.config.Config;
import io.wdsj.imagepreviewer.packet.PacketMapDisplay;
import io.wdsj.imagepreviewer.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public class MapDisplayDirectionTask implements Runnable {
    private final PacketMapDisplay display;
    public MapDisplayDirectionTask(PacketMapDisplay display) {
        this.display = display;
    }
    @Override
    public void run() {
        if (Config.isReloading) return;
        if (ImagePreviewer.config().preview_mode == 3) return;
        var owner = display.getOwner();
        if (owner.isDead()) {
            return;
        }
        var entity = display.getEntity();
        Location eyeLocation = owner.getEyeLocation().clone();
        Vector direction = eyeLocation.getDirection();
        switch (ImagePreviewer.config().preview_mode) {
            case 1:
                Location entityLoc = eyeLocation.add(direction.multiply(ImagePreviewer.config().image_distance_to_player));
                float[] alignment = LocationUtil.calculateYawPitch(entityLoc, eyeLocation);
                entityLoc.setYaw(alignment[0]);
                entityLoc.setPitch(alignment[1]);
                entity.teleport(SpigotConversionUtil.fromBukkitLocation(entityLoc));
                break;
            case 2:
                Location entityLoc2 = SpigotConversionUtil.toBukkitLocation(owner.getWorld(), entity.getLocation());
                float[] alignment2 = LocationUtil.calculateYawPitch(entityLoc2, eyeLocation);
                entity.rotateHead(alignment2[0], alignment2[1]);
                break;
            case 4:
                Location entityLoc4 = SpigotConversionUtil.toBukkitLocation(owner.getWorld(), entity.getLocation());
                float[] alignment4 = LocationUtil.calculateYawPitch(entityLoc4, eyeLocation);
                entity.rotateHead(alignment4[0], entityLoc4.getPitch());
                break;
            default:
                break;
        }
    }
}
