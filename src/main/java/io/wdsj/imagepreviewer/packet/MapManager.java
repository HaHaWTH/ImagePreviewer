package io.wdsj.imagepreviewer.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import io.wdsj.imagepreviewer.ImagePreviewer;
import me.tofaa.entitylib.APIConfig;
import me.tofaa.entitylib.EntityLib;
import me.tofaa.entitylib.spigot.SpigotEntityLibPlatform;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class MapManager {
    private static final Map<UUID, PacketMapDisplay> displays = new ConcurrentHashMap<>();
    public final List<UUID> queuedPlayers = new CopyOnWriteArrayList<>();
    private final ImagePreviewer plugin;
    private final ScheduledExecutorService excutor;

    public MapManager(@NotNull ImagePreviewer plugin) {
        this.plugin = plugin;
        this.initialize();
        excutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder()
                .setNameFormat("ImagePreviewer-MapManager")
                .setDaemon(true)
                .build()
        );
    }

    private void initialize() {
        final SpigotEntityLibPlatform platform = new SpigotEntityLibPlatform(plugin);
        final APIConfig settings = new APIConfig(PacketEvents.getAPI())
                .usePlatformLogger();
        EntityLib.init(platform, settings);
    }

    public ScheduledFuture<?> scheduleTask(Runnable runnable, long delay, long period) {
        return excutor.scheduleAtFixedRate(runnable, delay, period, TimeUnit.MILLISECONDS);
    }

    public void add(Player player, PacketMapDisplay display) {
        displays.put(player.getUniqueId(), display);
    }

    public void remove(Player player) {
        displays.remove(player.getUniqueId());
    }
    public boolean hasRunningPreview(Player player) {
        return displays.containsKey(player.getUniqueId());
    }

    public List<PacketMapDisplay> getDisplays() {
        return List.copyOf(displays.values());
    }
    public void close() {
        excutor.shutdown();
        displays.clear();
    }

    public synchronized int getEntityId() {
        return SpigotReflectionUtil.generateEntityId();
    }
}
