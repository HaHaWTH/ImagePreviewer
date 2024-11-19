package io.wdsj.imagepreviewer.task;

import com.github.Anon8281.universalScheduler.UniversalRunnable;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.config.Config;
import io.wdsj.imagepreviewer.packet.PacketMapDisplay;
import io.wdsj.imagepreviewer.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.List;

public class MapDisplayDirectionTask extends UniversalRunnable {
    private final ImagePreviewer plugin;
    public MapDisplayDirectionTask(ImagePreviewer plugin) {
        this.plugin = plugin;
    }
    @Override
    public void run() {
        if (Config.isReloading) return;
        if (ImagePreviewer.config().preview_mode == 3) return;
        List<PacketMapDisplay> displays = plugin.getMapManager().getDisplays();
        if (displays.isEmpty()) {
            return;
        }
        for (PacketMapDisplay display : displays) {
            var owner = display.getOwner();
            if (!owner.isOnline() || owner.isDead()) {
                continue;
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
                default:
                    break;
            }
        }
    }

}
