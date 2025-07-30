package io.wdsj.imagepreviewer.api;

import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.image.ImageData;
import io.wdsj.imagepreviewer.packet.PacketMapDisplay;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused")
public class ImagePreviewerAPI {
    private final ImagePreviewer plugin;
    private static ImagePreviewerAPI api;

    protected ImagePreviewerAPI(ImagePreviewer plugin) {
        this.plugin = plugin;
    }

    @NotNull
    public static ImagePreviewerAPI getApi() {
        if (api == null) throw new IllegalStateException("ImagePreviewerAPI is not initialized");
        return api;
    }

    @ApiStatus.Internal
    public static void init(ImagePreviewer plugin) {
        api = new ImagePreviewerAPI(plugin);
    }

    @Nullable
    public PacketMapDisplay getPreviewDisplay(Player player) {
        return plugin.getMapManager().getDisplay(player);
    }

    public boolean isPreviewRunning(Player player) {
        return plugin.getMapManager().hasRunningPreview(player);
    }

    public boolean spawnPreview(Player player, ImageData imageData) {
        if (plugin.getMapManager().hasRunningPreview(player) || plugin.getMapManager().queuedPlayers.contains(player.getUniqueId())) return false;
        return new PacketMapDisplay(plugin, player, imageData).spawn();
    }

    public boolean spawnPreview(Player player, ImageData imageData, long lifecycleTicks) {
        if (plugin.getMapManager().hasRunningPreview(player) || plugin.getMapManager().queuedPlayers.contains(player.getUniqueId())) return false;
        return new PacketMapDisplay(plugin, player, imageData, lifecycleTicks).spawn();
    }

    public boolean despawnPreview(Player player) {
        PacketMapDisplay display = plugin.getMapManager().getDisplay(player);
        if (display == null) return false;
        display.despawn();
        return true;
    }
}
