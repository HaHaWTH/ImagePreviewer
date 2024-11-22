package io.wdsj.imagepreviewer.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.wdsj.imagepreviewer.ImagePreviewer;
import io.wdsj.imagepreviewer.config.Config;
import io.wdsj.imagepreviewer.image.ImageData;
import io.wdsj.imagepreviewer.task.MapDisplayDirectionTask;
import io.wdsj.imagepreviewer.util.LocationUtil;
import io.wdsj.imagepreviewer.util.PacketUtil;
import io.wdsj.imagepreviewer.util.RandomUtil;
import me.tofaa.entitylib.meta.other.ItemFrameMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

public class PacketMapDisplay {
    private static final long TICK_TO_MILLISECONDS = 50L;
    private final ImagePreviewer plugin;
    private final WrapperEntity entity;
    private final Player owner;
    private final ImageData imageData;
    private final boolean isAnimated;
    private int currentFrame;
    private final int mapId;
    private ScheduledFuture<?> updateFrameTask;
    private ScheduledFuture<?> tickLifecycleTask;
    private ScheduledFuture<?> locationUpdateTask;
    private final long lifecycleTicks;
    private long ticksSurvived;

    public PacketMapDisplay(ImagePreviewer plugin, Player owner, ImageData imageData) {
        this.plugin = plugin;
        this.owner = owner;
        this.imageData = imageData;
        this.isAnimated = imageData.animated();
        this.lifecycleTicks = Config.isReloading ? 100L : ImagePreviewer.config().image_preview_lifetime;
        this.currentFrame = 0;
        this.ticksSurvived = 0;
        final int entityId = plugin.getMapManager().getEntityId();
        this.entity = new WrapperEntity(entityId, UUID.randomUUID(), EntityTypes.ITEM_FRAME);
        ItemFrameMeta meta = (ItemFrameMeta) entity.getEntityMeta();
        this.mapId = RandomUtil.genRandomMapId();
        meta.setItem(makeItemStack());
        meta.setInvisible(ImagePreviewer.config().use_invisible_item_frame);
        meta.setGlowing(ImagePreviewer.config().use_glowing_item_frame);
    }

    public PacketMapDisplay(ImagePreviewer plugin, Player owner, ImageData imageData, long lifecycleTicks) {
        this.plugin = plugin;
        this.owner = owner;
        this.imageData = imageData;
        this.isAnimated = imageData.animated();
        this.lifecycleTicks = lifecycleTicks;
        this.currentFrame = 0;
        this.ticksSurvived = 0;
        final int entityId = plugin.getMapManager().getEntityId();
        this.entity = new WrapperEntity(entityId, UUID.randomUUID(), EntityTypes.ITEM_FRAME);
        ItemFrameMeta meta = (ItemFrameMeta) entity.getEntityMeta();
        this.mapId = RandomUtil.genRandomMapId();
        meta.setItem(makeItemStack());
        meta.setInvisible(ImagePreviewer.config().use_invisible_item_frame);
        meta.setGlowing(ImagePreviewer.config().use_glowing_item_frame);
    }

    public void spawn() {
        plugin.getMapManager().queuedPlayers.remove(owner.getUniqueId());
        WrapperPlayServerMapData mapData = PacketUtil.makePacket(mapId, imageData.data().getFirst());
        entity.addViewer(owner.getUniqueId());
        Location ownerEyeLocation = owner.getEyeLocation().clone();
        Vector direction = ownerEyeLocation.getDirection();
        Location entityLoc = ownerEyeLocation.add(direction.multiply(ImagePreviewer.config().image_distance_to_player));
        float[] alignment = LocationUtil.calculateYawPitch(entityLoc, ownerEyeLocation);
        entityLoc.setYaw(alignment[0]);
        entityLoc.setPitch(alignment[1]);
        entity.spawn(SpigotConversionUtil.fromBukkitLocation(entityLoc));
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(owner, mapData);
        plugin.getMapManager().track(owner, this);
        if (isAnimated) startAnimation();
        tickLifecycleTask = plugin.getMapManager().scheduleTaskAtFixedRate(() -> {
            if (ticksSurvived >= lifecycleTicks) {
                this.despawn();
            } else {
                ticksSurvived += 1;
            }
        }, TICK_TO_MILLISECONDS, TICK_TO_MILLISECONDS);
        final long locationUpdateInterval = Config.isReloading ? 50L : ImagePreviewer.config().location_update_interval;
        locationUpdateTask = plugin.getMapManager().scheduleTaskAtFixedRate(new MapDisplayDirectionTask(this), locationUpdateInterval, locationUpdateInterval);
    }

    public WrapperEntity getEntity() {
        return entity;
    }

    public Player getOwner() {
        return owner;
    }

    public void despawn() {
        if (tickLifecycleTask != null) {
            tickLifecycleTask.cancel(false);
            tickLifecycleTask = null;
        }
        if (locationUpdateTask != null) {
            locationUpdateTask.cancel(false);
            locationUpdateTask = null;
        }
        entity.despawn();
        plugin.getMapManager().untrack(owner);
        ticksSurvived = 0;
        stopAnimation();
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public void setCurrentFrame(int currentFrame) {
        this.currentFrame = currentFrame;
    }

    private void startAnimation() {
        long delay;
        var dataDelay = imageData.parseFrameDelay();
        if (dataDelay != null && !Config.isReloading && ImagePreviewer.config().gif_adaptive_frame_delay) {
            delay = dataDelay;
        } else {
            delay = Config.isReloading ? 100L : ImagePreviewer.config().gif_frame_delay;
        }
        updateFrameTask = plugin.getMapManager().scheduleTaskAtFixedRate(() -> {
            int currentFrame = this.getCurrentFrame();
            int nextFrame = currentFrame + 1;
            if (nextFrame >= imageData.data().size()) {
                nextFrame = 0;
            }
            this.setCurrentFrame(nextFrame);
            this.updateFrame();
        }, 500L, delay);
    }

    private void stopAnimation() {
        if (updateFrameTask != null) {
            updateFrameTask.cancel(false);
            updateFrameTask = null;
        }
    }

    public void updateFrame() {
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(owner, PacketUtil.makePacket(mapId, imageData.data().get(currentFrame)));
    }

    private ItemStack makeItemStack() {
        return ItemStack.builder()
                .type(ItemTypes.FILLED_MAP)
                .amount(1)
                .nbt("map_id", new NBTInt(mapId))
                .nbt("map", new NBTInt(mapId))
                .component(ComponentTypes.MAP_ID, mapId)
                .build();
    }
}
