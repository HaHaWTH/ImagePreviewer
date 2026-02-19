package io.wdsj.imagepreviewer.listener;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import io.wdsj.imagepreviewer.packet.MapManager;

public class DropItemPacketListener extends PacketListenerAbstract {
    private final MapManager mapManager;
    public DropItemPacketListener(MapManager mapManager) {
        super(PacketListenerPriority.LOW);
        this.mapManager = mapManager;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;
        var packet = new WrapperPlayClientPlayerDigging(event);
        var action = packet.getAction();
        if (action != DiggingAction.DROP_ITEM && action != DiggingAction.DROP_ITEM_STACK) return;
        var display = mapManager.getDisplay(event.getPlayer());
        if (display != null) {
            display.despawn();
            event.setCancelled(true);
        }
    }
}
