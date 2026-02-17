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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ScheduledFuture;

/**
 * A single-use class that manages displaying image data to a player by sending packets
 * to make them hold a virtual map in their hands.
 * This display is client-side only.
 */
public class PacketMapDisplay {
    private static final long TICK_TO_MILLISECONDS = 50L;
    private static final int PLAYER_INVENTORY_WINDOW_ID = -2;

    private static final VarHandle VH_TICKS_SURVIVED;
    private static final VarHandle VH_CURRENT_FRAME;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            VH_TICKS_SURVIVED = l.findVarHandle(PacketMapDisplay.class, "ticksSurvived", long.class);
            VH_CURRENT_FRAME = l.findVarHandle(PacketMapDisplay.class, "currentFrame", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final ImagePreviewer plugin;
    private final Player owner;
    private final ImageData imageData;
    private final boolean isAnimated;
    private final int mapId;
    private final long lifecycleTicks;

    private volatile long ticksSurvived;
    private volatile int currentFrame;
    private volatile boolean isSpawned;

    private int originalHeldSlot;

    private final ItemStack cachedMapItem;

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
        this.mapId = RandomUtil.genRandomMapId();
        this.cachedMapItem = makeMapItemStack();
    }

    /**
     * Attempts to spawn the map display in the player's hand.
     * Fails if the player's main hand is not empty.
     * This should be called on the main thread.
     *
     * @return true if the display was spawned, false otherwise.
     */
    public boolean spawn() {
        if (isSpawned) {
            return false;
        }
        plugin.getMapManager().queuedPlayers.remove(owner.getUniqueId());
        PlayerInventory inventory = owner.getInventory();
        boolean useOffhand = ImagePreviewer.config().use_offhand;
        if (!ImagePreviewer.config().allow_nonempty_hand) {
            if (!useOffhand && inventory.getItemInMainHand().getType() != Material.AIR) return false;
            if (useOffhand && inventory.getItemInOffHand().getType() != Material.AIR) return false;
        }

        this.originalHeldSlot = useOffhand && (!FloodgateHook.isFloodgatePresent() || !FloodgateHook.isFloodgatePlayer(owner)) ? 40 : inventory.getHeldItemSlot();

        this.sendItemStack(originalHeldSlot, (int) VH_CURRENT_FRAME.getOpaque(this));

        plugin.getMapManager().track(owner, this);
        if (isAnimated) {
            startAnimation();
        }
        isSpawned = true;
        startLifecycleTicker();
        return true;
    }

    private void sendItemStack(int originalHeldSlot, int mapDataFrameIndex) {
        WrapperPlayServerSetSlot setSlotPacket = new WrapperPlayServerSetSlot(
                PLAYER_INVENTORY_WINDOW_ID,
                0,
                originalHeldSlot,
                this.cachedMapItem
        );
        WrapperPlayServerMapData mapDataPacket = PacketUtil.makePacket(mapId, imageData.frameData().get(mapDataFrameIndex));

        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(owner, setSlotPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(owner, mapDataPacket);
    }

    public void despawn() {
        despawn(true);
    }

    public synchronized void despawn(boolean updateInventory) {
        if (!isSpawned) {
            return;
        }
        cancelTasks();
        plugin.getMapManager().untrack(owner);
        if (updateInventory) {
            WrapperPlayServerSetSlot setSlotPacket = new WrapperPlayServerSetSlot(
                    PLAYER_INVENTORY_WINDOW_ID,
                    0,
                    originalHeldSlot,
                    SpigotConversionUtil.fromBukkitItemStack(owner.getInventory().getItem(originalHeldSlot))
            );
            PacketEvents.getAPI().getPlayerManager().sendPacketSilently(owner, setSlotPacket);
        }
        isSpawned = false;
    }

    private void cancelTasks() {
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
        if (dataDelay != -1 && !Config.isReloading && ImagePreviewer.config().gif_adaptive_frame_delay) {
            delay = dataDelay;
        } else {
            delay = Config.isReloading ? 100L : ImagePreviewer.config().gif_frame_delay;
        }

        updateFrameTask = plugin.getMapManager().scheduleAsyncTaskAtFixedRate(() -> {
            int current = (int) VH_CURRENT_FRAME.getOpaque(this);
            int next = current + 1;
            if (next >= imageData.frameData().size()) {
                next = 0;
            }
            VH_CURRENT_FRAME.setOpaque(this, next);

            this.updateFrame(next);
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

    public void updateFrame(int frameIndex) {
        WrapperPlayServerMapData mapDataPacket = PacketUtil.makePacket(mapId, imageData.frameData().get(frameIndex));
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(owner, mapDataPacket);
    }

    private void startLifecycleTicker() {
        tickLifecycleTask = plugin.getMapManager().scheduleAsyncTaskAtFixedRate(() -> {
            long survived = (long) VH_TICKS_SURVIVED.getAndAdd(this, 1L) + 1;

            if (survived >= lifecycleTicks) {
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
        return lifecycleTicks - (long) VH_TICKS_SURVIVED.getVolatile(this);
    }

    public int getOriginalHeldSlot() {
        return originalHeldSlot;
    }
}