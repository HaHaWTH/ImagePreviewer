package io.wdsj.imagepreviewer.api;

import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.image.ImageData;
import io.wdsj.imagepreviewer.image.ImageLoader;
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

    public void spawnPreviewFromUrl(Player player, String url) {
        if (plugin.getMapManager().hasRunningPreview(player) || plugin.getMapManager().queuedPlayers.contains(player.getUniqueId())) return;
        ImageLoader.imageAsData(url)
                .thenAccept(imageData -> new PacketMapDisplay(plugin, player, imageData).spawn())
                .exceptionally(ex -> null);
    }

    public void spawnPreviewFromUrl(Player player, String url, long lifecycleTicks) {
        if (plugin.getMapManager().hasRunningPreview(player) || plugin.getMapManager().queuedPlayers.contains(player.getUniqueId())) return;
        ImageLoader.imageAsData(url)
                .thenAccept(imageData -> new PacketMapDisplay(plugin, player, imageData, lifecycleTicks).spawn())
                .exceptionally(ex -> null);
    }

    public boolean spawnPreview(Player player, ImageData imageData) {
        if (plugin.getMapManager().hasRunningPreview(player) || plugin.getMapManager().queuedPlayers.contains(player.getUniqueId())) return false;
        new PacketMapDisplay(plugin, player, imageData).spawn();
        return true;
    }

    public boolean spawnPreview(Player player, ImageData imageData, long lifecycleTicks) {
        if (plugin.getMapManager().hasRunningPreview(player) || plugin.getMapManager().queuedPlayers.contains(player.getUniqueId())) return false;
        new PacketMapDisplay(plugin, player, imageData, lifecycleTicks).spawn();
        return true;
    }

    public boolean despawnPreview(Player player) {
        PacketMapDisplay display = plugin.getMapManager().getDisplay(player);
        if (display == null) return false;
        display.despawn();
        return true;
    }
}
