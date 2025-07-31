package io.wdsj.imagepreviewer.listener;

import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.packet.MapManager;
import io.wdsj.imagepreviewer.packet.PacketMapDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;

public class PaperDisplayListener implements Listener {
    private final MapManager mapManager;

    public PaperDisplayListener(ImagePreviewer plugin) {
        this.mapManager = plugin.getMapManager();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        PacketMapDisplay display = mapManager.getDisplay(player);

        if (display != null && event.getInventorySlots().contains(display.getOriginalHeldSlot())) {
            event.setCancelled(true);
            display.despawn();
        }
    }
}
