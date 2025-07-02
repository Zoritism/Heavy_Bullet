package com.zoritism.heavybullet.network;

import com.zoritism.heavybullet.backpack.dockyard.DockyardUpgradeLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Пакет для управления слотами дока: собрать или выпустить корабль.
 * slotIndex - номер слота (0 или 1)
 * action - true = выпустить, false = собрать
 * blockMode - true если открыт как блок, false если инвентарь
 * blockPos - координаты блока (asLong), если blockMode
 */
public class C2SHandleDockyardShipPacket {
    public final int slotIndex;
    public final boolean action; // false = собрать, true = выпустить
    public final boolean blockMode;
    public final long blockPos;

    public C2SHandleDockyardShipPacket(int slotIndex, boolean action, boolean blockMode, long blockPos) {
        this.slotIndex = slotIndex;
        this.action = action;
        this.blockMode = blockMode;
        this.blockPos = blockPos;
    }

    public static void encode(C2SHandleDockyardShipPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.slotIndex);
        buf.writeBoolean(pkt.action);
        buf.writeBoolean(pkt.blockMode);
        buf.writeLong(pkt.blockPos);
    }

    public static C2SHandleDockyardShipPacket decode(FriendlyByteBuf buf) {
        int slot = buf.readInt();
        boolean act = buf.readBoolean();
        boolean blockMode = buf.readBoolean();
        long blockPos = buf.readLong();
        return new C2SHandleDockyardShipPacket(slot, act, blockMode, blockPos);
    }

    public static void handle(C2SHandleDockyardShipPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() == null) {
                return;
            }
            try {
                DockyardUpgradeLogic.handleDockyardShipClick(ctx.get().getSender(), pkt.slotIndex, pkt.action, pkt.blockMode, pkt.blockPos);
            } catch (Exception e) {
                // ignore
            }
        });
        ctx.get().setPacketHandled(true);
    }
}