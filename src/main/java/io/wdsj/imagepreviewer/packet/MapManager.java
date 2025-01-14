package io.wdsj.imagepreviewer.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.util.VirtualThreadUtil;
import me.tofaa.entitylib.APIConfig;
import me.tofaa.entitylib.EntityLib;
import me.tofaa.entitylib.spigot.SpigotEntityLibPlatform;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

public class MapManager implements Listener {
    private final Map<UUID, PacketMapDisplay> displays = new ConcurrentHashMap<>();
    public final Set<UUID> queuedPlayers = ConcurrentHashMap.newKeySet();
    private final ImagePreviewer plugin;
    private final ScheduledExecutorService executor;

    public MapManager(@NotNull ImagePreviewer plugin) {
        this.plugin = plugin;
        this.initialize();
        executor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder()
                .setNameFormat("ImagePreviewer MapManager")
                .setThreadFactory(VirtualThreadUtil.newVirtualThreadFactoryOrDefault())
                .setDaemon(true)
                .build()
        );
    }

    private void initialize() {
        final SpigotEntityLibPlatform platform = new SpigotEntityLibPlatform(plugin);
        final APIConfig settings = new APIConfig(PacketEvents.getAPI())
                .usePlatformLogger();
        EntityLib.init(platform, settings);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public ScheduledFuture<?> scheduleTaskAtFixedRate(Runnable runnable, long delay, long period) {
        return executor.scheduleAtFixedRate(runnable, delay, period, TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> scheduleTaskLater(Runnable runnable, long delay) {
        return executor.schedule(runnable, delay, TimeUnit.MILLISECONDS);
    }

    public void track(Player player, PacketMapDisplay display) {
        displays.put(player.getUniqueId(), display);
    }

    public void untrack(Player player) {
        UUID uuid = player.getUniqueId();
        displays.remove(uuid);
    }

    public PacketMapDisplay getDisplay(Player player) {
        return displays.get(player.getUniqueId());
    }

    public boolean hasRunningPreview(Player player) {
        return displays.containsKey(player.getUniqueId());
    }

    public List<PacketMapDisplay> getDisplays() {
        return List.copyOf(displays.values());
    }
    public void close() {
        executor.shutdown();
        displays.values().forEach(PacketMapDisplay::despawn);
        displays.clear();
        queuedPlayers.clear();
        HandlerList.unregisterAll(this);
    }

    public synchronized int getEntityId() {
        return SpigotReflectionUtil.generateEntityId();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        var uuid = player.getUniqueId();
        queuedPlayers.remove(uuid);
        if (hasRunningPreview(player)) {
            displays.remove(uuid).despawn();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        var uuid = player.getUniqueId();
        queuedPlayers.remove(uuid);
        if (hasRunningPreview(player)) {
            displays.remove(uuid).despawn();
        }
    }
}
