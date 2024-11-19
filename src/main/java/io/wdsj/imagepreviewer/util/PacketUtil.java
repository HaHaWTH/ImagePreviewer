package io.wdsj.imagepreviewer.util;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;

import java.util.List;

public class PacketUtil {
    public static WrapperPlayServerMapData makePacket(int mapId, byte[] data) {
        return new WrapperPlayServerMapData(
                mapId,
                (byte) 0,
                false,
                false,
                List.of(),
                128,
                128,
                0,
                0,
                data
        );
    }
}
