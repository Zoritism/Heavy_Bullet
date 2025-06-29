package com.zoritism.heavybullet.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SHandleBottleShipPacket {
    public final boolean release; // false = capture, true = release

    public C2SHandleBottleShipPacket(boolean release) {
        this.release = release;
    }

    public static void encode(C2SHandleBottleShipPacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.release);
    }

    public static C2SHandleBottleShipPacket decode(FriendlyByteBuf buf) {
        return new C2SHandleBottleShipPacket(buf.readBoolean());
    }

    public static void handle(C2SHandleBottleShipPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // TODO: Реализовать серверную логику захвата/выпуска корабля
            // Например: DockyardUpgradeLogic.handleBottleShipClick(ctx.get().getSender(), pkt.release);
        });
        ctx.get().setPacketHandled(true);
    }
}