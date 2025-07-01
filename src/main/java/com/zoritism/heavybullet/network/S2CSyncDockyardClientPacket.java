package com.zoritism.heavybullet.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import com.zoritism.heavybullet.backpack.dockyard.DockyardClientCache;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class S2CSyncDockyardClientPacket {
    public final Map<Integer, CompoundTag> slots;

    public S2CSyncDockyardClientPacket(Map<Integer, CompoundTag> slots) {
        this.slots = slots;
    }

    public static void encode(S2CSyncDockyardClientPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.slots.size());
        for (Map.Entry<Integer, CompoundTag> e : pkt.slots.entrySet()) {
            buf.writeVarInt(e.getKey());
            buf.writeNbt(e.getValue());
        }
    }

    public static S2CSyncDockyardClientPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<Integer, CompoundTag> slots = new HashMap<>();
        for (int i = 0; i < size; i++) {
            int key = buf.readVarInt();
            CompoundTag nbt = buf.readNbt();
            slots.put(key, nbt);
        }
        return new S2CSyncDockyardClientPacket(slots);
    }

    public static void handle(S2CSyncDockyardClientPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DockyardClientCache.sync(pkt.slots);
        });
        ctx.get().setPacketHandled(true);
    }
}