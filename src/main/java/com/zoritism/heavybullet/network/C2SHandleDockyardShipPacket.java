package com.zoritism.heavybullet.network;

import com.zoritism.heavybullet.backpack.dockyard.DockyardUpgradeLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Пакет для управления слотами дока: собрать или выпустить корабль.
 * slotIndex - номер слота (0 или 1)
 * action - true = выпустить, false = собрать
 */
public class C2SHandleDockyardShipPacket {
    public final int slotIndex;
    public final boolean action; // false = собрать, true = выпустить

    public C2SHandleDockyardShipPacket(int slotIndex, boolean action) {
        this.slotIndex = slotIndex;
        this.action = action;
    }

    public static void encode(C2SHandleDockyardShipPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.slotIndex);
        buf.writeBoolean(pkt.action);
    }

    public static C2SHandleDockyardShipPacket decode(FriendlyByteBuf buf) {
        int slot = buf.readInt();
        boolean act = buf.readBoolean();
        return new C2SHandleDockyardShipPacket(slot, act);
    }

    public static void handle(C2SHandleDockyardShipPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() == null) {
                return;
            }
            try {
                // Новая логика: всегда слот нажимается как "toggle".
                // Если в слоте есть корабль, кнопка должна ВЫТАСКИВАТЬ корабль (release = true)
                // Если в слоте нет корабля, кнопка должна ЗАСОВЫВАТЬ корабль (release = false)
                DockyardUpgradeLogic.handleDockyardShipClick(ctx.get().getSender(), pkt.slotIndex, pkt.action);
            } catch (Exception e) {
                // ignore
            }
        });
        ctx.get().setPacketHandled(true);
    }
}