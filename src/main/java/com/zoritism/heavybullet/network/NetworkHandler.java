package com.zoritism.heavybullet.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    public static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("heavy_bullet", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                C2SHandleDockyardShipPacket.class,
                C2SHandleDockyardShipPacket::encode,
                C2SHandleDockyardShipPacket::decode,
                C2SHandleDockyardShipPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                S2CSyncDockyardClientPacket.class,
                S2CSyncDockyardClientPacket::encode,
                S2CSyncDockyardClientPacket::decode,
                S2CSyncDockyardClientPacket::handle
        );
        CHANNEL.registerMessage(
                packetId++,
                ServerboundToggleFlashlightPacket.class,
                ServerboundToggleFlashlightPacket::encode,
                ServerboundToggleFlashlightPacket::decode,
                ServerboundToggleFlashlightPacket::handle
        );
    }
}