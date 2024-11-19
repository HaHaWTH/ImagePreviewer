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
import io.wdsj.imagepreviewer.util.LocationUtil;
import io.wdsj.imagepreviewer.util.RandomUtil;
import me.tofaa.entitylib.meta.other.ItemFrameMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

public class PacketMapDisplay {
    private final ImagePreviewer plugin;
    private final WrapperEntity entity;
    private final Player owner;
    private final List<byte[]> imageData;
    private final boolean isAnimated;
    private int currentFrame;
    private final int mapId;
    private ScheduledFuture<?> updateTask;

    public PacketMapDisplay(ImagePreviewer plugin, Player owner, List<byte[]> imageData) {
        this.plugin = plugin;
        this.owner = owner;
        this.imageData = imageData;
        this.isAnimated = !Config.isReloading && ImagePreviewer.config().process_multi_frame_gif && imageData.size() > 1;
        this.currentFrame = 0;
        final int entityId = plugin.getMapManager().getEntityId();
        entity = new WrapperEntity(entityId, UUID.randomUUID(), EntityTypes.ITEM_FRAME);
        ItemFrameMeta meta = (ItemFrameMeta) entity.getEntityMeta();
        this.mapId = RandomUtil.genRandomMapId();
        ItemStack stack = ItemStack.builder()
                .type(ItemTypes.FILLED_MAP)
                .amount(1)
                .nbt("map_id", new NBTInt(mapId))
                .component(ComponentTypes.MAP_ID, mapId)
                .build();
        meta.setItem(stack);
        meta.setInvisible(ImagePreviewer.config().use_invisible_item_frame);
        meta.setGlowing(ImagePreviewer.config().use_glowing_item_frame);
    }

    public void spawn() {
        WrapperPlayServerMapData mapData = new WrapperPlayServerMapData(
                mapId,
                (byte) 0,
                false,
                false,
                List.of(),
                128,
                128,
                0,
                0,
                imageData.getFirst()
        );
        entity.addViewer(owner.getUniqueId());
        Location ownerEyeLocation = owner.getEyeLocation().clone();
        Vector direction = ownerEyeLocation.getDirection();
        Location entityLoc = ownerEyeLocation.add(direction.multiply(ImagePreviewer.config().image_distance_to_player));
        float[] alignment = LocationUtil.calculateYawPitch(entityLoc, ownerEyeLocation);
        entityLoc.setYaw(alignment[0]);
        entityLoc.setPitch(alignment[1]);
        entity.spawn(SpigotConversionUtil.fromBukkitLocation(entityLoc));
        PacketEvents.getAPI().getPlayerManager().sendPacket(owner, mapData);
        plugin.getMapManager().add(owner, this);
        plugin.getMapManager().queuedPlayers.remove(owner.getUniqueId());
        if (isAnimated) {
            updateTask = plugin.getMapManager().scheduleTask(() -> {
                int currentFrame = this.getCurrentFrame();
                int nextFrame = currentFrame + 1;
                if (nextFrame >= imageData.size()) {
                    nextFrame = 0;
                }
                this.setCurrentFrame(nextFrame);
                this.updateFrame();
            }, 500L, Config.isReloading ? 100L : ImagePreviewer.config().gif_frame_delay);
        }
        ImagePreviewer.getScheduler().runTaskLaterAsynchronously(this::despawn, Config.isReloading ? 100L : ImagePreviewer.config().image_preview_lifetime);
    }

    public WrapperEntity getEntity() {
        return entity;
    }

    public Player getOwner() {
        return owner;
    }

    public int getMapId() {
        return mapId;
    }

    public List<byte[]> getImageData() {
        return imageData;
    }

    public void despawn() {
        plugin.getMapManager().remove(owner);
        entity.despawn();
        if (updateTask != null) {
            updateTask.cancel(false);
        }
    }

    public boolean isAnimated() {
        return isAnimated;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public void setCurrentFrame(int currentFrame) {
        this.currentFrame = currentFrame;
    }

    public void updateFrame() {
        WrapperPlayServerMapData mapData = new WrapperPlayServerMapData(
                mapId,
                (byte) 0,
                false,
                false,
                List.of(),
                128,
                128,
                0,
                0,
                imageData.get(currentFrame)
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(owner, mapData);
    }
}
