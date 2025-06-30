package com.zoritism.heavybullet.network;

import com.zoritism.heavybullet.backpack.dockyard.DockyardUpgradeLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/**
 * Пакет для управления слотами дока: собрать или выпустить корабль.
 * slotIndex - номер слота (0 или 1)
 * action - true = выпустить, false = собрать
 * backpackSlotIndex - индекс рюкзака в инвентаре (добавлен для корректной работы с хоткеем)
 */
public class C2SHandleDockyardShipPacket {
    public final int slotIndex;
    public final boolean action; // false = собрать, true = выпустить
    public final int backpackSlotIndex; // индекс рюкзака в инвентаре

    private static final Logger LOGGER = LogManager.getLogger("HeavyBullet/Network");

    public C2SHandleDockyardShipPacket(int slotIndex, boolean action, int backpackSlotIndex) {
        this.slotIndex = slotIndex;
        this.action = action;
        this.backpackSlotIndex = backpackSlotIndex;
    }

    public static void encode(C2SHandleDockyardShipPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.slotIndex);
        buf.writeBoolean(pkt.action);
        buf.writeInt(pkt.backpackSlotIndex);
    }

    public static C2SHandleDockyardShipPacket decode(FriendlyByteBuf buf) {
        int slot = buf.readInt();
        boolean act = buf.readBoolean();
        int backpackSlot = buf.readInt();
        return new C2SHandleDockyardShipPacket(slot, act, backpackSlot);
    }

    public static void handle(C2SHandleDockyardShipPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            LOGGER.info("[C2SHandleDockyardShipPacket] handle called: slotIndex={}, action={}, backpackSlotIndex={}", pkt.slotIndex, pkt.action, pkt.backpackSlotIndex);
            if (ctx.get().getSender() == null) {
                LOGGER.warn("[C2SHandleDockyardShipPacket] Sender is null -- probably clientside or lost context!");
                return;
            }
            // Вызываем серверную механику для докового слота, передавая индекс рюкзака
            try {
                DockyardUpgradeLogic.handleDockyardShipClick(ctx.get().getSender(), pkt.slotIndex, pkt.action, pkt.backpackSlotIndex);
                LOGGER.info("[C2SHandleDockyardShipPacket] handleDockyardShipClick executed successfully.");
            } catch (Exception e) {
                LOGGER.error("[C2SHandleDockyardShipPacket] Exception in handleDockyardShipClick: ", e);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}