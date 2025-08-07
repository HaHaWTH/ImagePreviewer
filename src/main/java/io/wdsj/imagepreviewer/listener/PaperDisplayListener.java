package io.wdsj.imagepreviewer.listener;

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.packet.MapManager;
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
        var display = mapManager.getDisplay(player);

        if (display != null && event.getInventorySlots().contains(display.getOriginalHeldSlot())) {
            event.setCancelled(true);
            display.despawn();
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInvChange(PlayerInventorySlotChangeEvent event) {
        var player = event.getPlayer();
        var display = mapManager.getDisplay(player);
        if (display != null && event.getSlot() == display.getOriginalHeldSlot()) {
            display.despawn();
        }
    }
}
