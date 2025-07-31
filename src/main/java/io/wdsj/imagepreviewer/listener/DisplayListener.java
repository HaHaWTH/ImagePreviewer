package io.wdsj.imagepreviewer.listener;

import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.packet.MapManager;
import io.wdsj.imagepreviewer.packet.PacketMapDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public class DisplayListener implements Listener {

    private final MapManager mapManager;

    public DisplayListener(ImagePreviewer plugin) {
        this.mapManager = plugin.getMapManager();
    }

    /**
     * Prevents the player from dropping the virtual map.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        PacketMapDisplay display = mapManager.getDisplay(player);

        if (display != null && player.getInventory().getHeldItemSlot() == display.getOriginalHeldSlot()) {
            event.setCancelled(true);
            display.despawn();
        }
    }

    /**
     * Despawns the map display if the player switches their held item slot.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerChangeSlot(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        PacketMapDisplay display = mapManager.getDisplay(player);

        if (display != null && event.getPreviousSlot() == display.getOriginalHeldSlot()) {
            display.despawn();
        }
    }

    /**
     * Prevents the player from moving the virtual map within their inventory.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        PacketMapDisplay display = mapManager.getDisplay(player);

        if (display != null && event.getSlot() == display.getOriginalHeldSlot()) {
            event.setCancelled(true);
            display.despawn();
        }
    }

    /**
     * Despawn the map when the player swapped hand items.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onSwapItem(PlayerSwapHandItemsEvent event) {
        var player = event.getPlayer();
        PacketMapDisplay display = mapManager.getDisplay(player);
        if (display == null) return;
        event.setCancelled(true);
        display.despawn();
    }
}