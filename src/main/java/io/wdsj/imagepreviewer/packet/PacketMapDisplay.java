package io.wdsj.imagepreviewer.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.config.Config;
import io.wdsj.imagepreviewer.hook.floodgate.FloodgateHook;
import io.wdsj.imagepreviewer.image.ImageData;
import io.wdsj.imagepreviewer.util.PacketUtil;
import io.wdsj.imagepreviewer.util.RandomUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages displaying image data to a player by sending packets
 * to make them hold a virtual map in their main hand.
 * This display is client-side only.
 */
public class PacketMapDisplay {
    private static final long TICK_TO_MILLISECONDS = 50L;
    private static final int PLAYER_INVENTORY_WINDOW_ID = -2;

    private final ImagePreviewer plugin;
    private final Player owner;
    private final ImageData imageData;
    private final boolean isAnimated;
    private final int mapId;
    private final long lifecycleTicks;
    private final AtomicLong ticksSurvived;

    private int originalHeldSlot;
    private int currentFrame;
    private ItemStack originalItem;

    private ScheduledFuture<?> updateFrameTask;
    private ScheduledFuture<?> tickLifecycleTask;

    public PacketMapDisplay(ImagePreviewer plugin, Player owner, ImageData imageData) {
        this(plugin, owner, imageData, Config.isReloading ? 100L : ImagePreviewer.config().image_preview_lifetime);
    }

    public PacketMapDisplay(ImagePreviewer plugin, Player owner, ImageData imageData, long lifecycleTicks) {
        this.plugin = plugin;
        this.owner = owner;
        this.imageData = imageData;
        this.isAnimated = imageData.animated();
        this.lifecycleTicks = lifecycleTicks;
        this.currentFrame = 0;
        this.ticksSurvived = new AtomicLong(0L);
        this.mapId = RandomUtil.genRandomMapId();
    }

    /**
     * Attempts to spawn the map display in the player's hand.
     * Fails if the player's main hand is not empty.
     *
     * @return true if the display was spawned, false otherwise.
     */
    public boolean spawn() {
        plugin.getMapManager().queuedPlayers.remove(owner.getUniqueId());
        try {
            owner.updateInventory();
        } catch (Throwable ignored) {
        }
        PlayerInventory inventory = owner.getInventory();
        boolean useOffhand = ImagePreviewer.config().use_offhand;
        if (!ImagePreviewer.config().allow_nonempty_hand) {
            if (!useOffhand && inventory.getItemInMainHand().getType() != Material.AIR) return false;
            if (useOffhand && inventory.getItemInOffHand().getType() != Material.AIR) return false;
        }

        this.originalHeldSlot = useOffhand && (!FloodgateHook.isFloodgatePresent() || !FloodgateHook.isFloodgatePlayer(owner)) ? 40 : inventory.getHeldItemSlot();
        this.originalItem = SpigotConversionUtil.fromBukkitItemStack(inventory.getItemInMainHand());

        ItemStack mapItemStack = makeMapItemStack();
        WrapperPlayServerSetSlot setSlotPacket = new WrapperPlayServerSetSlot(
                PLAYER_INVENTORY_WINDOW_ID,
                0,
                originalHeldSlot,
                mapItemStack
        );
        WrapperPlayServerMapData mapDataPacket = PacketUtil.makePacket(mapId, imageData.frameData().getFirst());

        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(owner, setSlotPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(owner, mapDataPacket);

        plugin.getMapManager().track(owner, this);
        if (isAnimated) {
            startAnimation();
        }
        startLifecycleTicker();

        return true;
    }

    public void despawn() {
        cancelTasks();
        plugin.getMapManager().untrack(owner);
        ticksSurvived.set(0L);
        try {
            owner.updateInventory();
        } catch (Throwable ignored) {
        }
    }

    public void cancelTasks() {
        if (tickLifecycleTask != null) {
            tickLifecycleTask.cancel(false);
            tickLifecycleTask = null;
        }
        stopAnimation();
    }

    /**
     * Starts the task that updates the frames for animated images.
     */
    private void startAnimation() {
        long delay;
        var dataDelay = imageData.parseFrameDelay();
        if (dataDelay != null && !Config.isReloading && ImagePreviewer.config().gif_adaptive_frame_delay) {
            delay = dataDelay;
        } else {
            delay = Config.isReloading ? 100L : ImagePreviewer.config().gif_frame_delay;
        }

        updateFrameTask = plugin.getMapManager().scheduleTaskAtFixedRate(() -> {
            currentFrame++;
            if (currentFrame >= imageData.frameData().size()) {
                currentFrame = 0;
            }
            this.updateFrame();
        }, 500L, delay);
    }

    /**
     * Stops the animation task.
     */
    private void stopAnimation() {
        if (updateFrameTask != null) {
            updateFrameTask.cancel(false);
            updateFrameTask = null;
        }
    }

    /**
     * Sends the next frame of the image to the player's map.
     */
    public void updateFrame() {
        if (currentFrame < imageData.frameData().size()) {
            WrapperPlayServerMapData mapDataPacket = PacketUtil.makePacket(mapId, imageData.frameData().get(currentFrame));
            PacketEvents.getAPI().getPlayerManager().sendPacketSilently(owner, mapDataPacket);
        }
    }

    /**
     * Starts the task that ticks down the display's lifetime, despawning it when expired.
     */
    private void startLifecycleTicker() {
        tickLifecycleTask = plugin.getMapManager().scheduleTaskAtFixedRate(() -> {
            if (ticksSurvived.incrementAndGet() >= lifecycleTicks) {
                this.despawn();
            }
        }, TICK_TO_MILLISECONDS, TICK_TO_MILLISECONDS);
    }

    /**
     * Builds the ItemStack for the virtual map.
     */
    private ItemStack makeMapItemStack() {
        return ItemStack.builder()
                .type(ItemTypes.FILLED_MAP)
                .amount(1)
                .nbt("map_id", new NBTInt(mapId))
                .nbt("map", new NBTInt(mapId))
                .component(ComponentTypes.MAP_ID, mapId)
                .build();
    }

    // --- Getters ---

    public Player getOwner() {
        return owner;
    }

    public long getLifetimeLeft() {
        return lifecycleTicks - ticksSurvived.get();
    }

    public int getOriginalHeldSlot() {
        return originalHeldSlot;
    }
}
