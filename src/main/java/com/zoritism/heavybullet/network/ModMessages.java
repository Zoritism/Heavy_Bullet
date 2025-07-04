package com.zoritism.heavybullet.network;

import com.zoritism.heavybullet.HeavyBulletMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {
    private static final String VERSION = "1";
    private static final ResourceLocation CHANNEL_NAME =
            ResourceLocation.fromNamespaceAndPath("heavy_bullet", "main");

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_NAME,
            () -> VERSION,
            VERSION::equals,
            VERSION::equals
    );

    private static int packetId = 0;

    private static int nextId() {
        return packetId++;
    }

    public static void register(IEventBus modEventBus) {
        CHANNEL.registerMessage(
                nextId(),
                ServerboundToggleFlashlightPacket.class,
                ServerboundToggleFlashlightPacket::encode,
                ServerboundToggleFlashlightPacket::decode,
                ServerboundToggleFlashlightPacket::handle
        );
    }

    // Метод для отправки пакета на сервер
    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}
