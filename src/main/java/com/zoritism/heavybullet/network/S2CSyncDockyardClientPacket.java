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
    public final boolean blockMode;
    public final long blockPos;

    public S2CSyncDockyardClientPacket(Map<Integer, CompoundTag> slots, boolean blockMode, long blockPos) {
        this.slots = slots;
        this.blockMode = blockMode;
        this.blockPos = blockPos;
    }

    public static void encode(S2CSyncDockyardClientPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.slots.size());
        for (Map.Entry<Integer, CompoundTag> e : pkt.slots.entrySet()) {
            buf.writeVarInt(e.getKey());
            buf.writeNbt(e.getValue());
        }
        buf.writeBoolean(pkt.blockMode);
        buf.writeLong(pkt.blockPos);
    }

    public static S2CSyncDockyardClientPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<Integer, CompoundTag> slots = new HashMap<>();
        for (int i = 0; i < size; i++) {
            int key = buf.readVarInt();
            CompoundTag nbt = buf.readNbt();
            slots.put(key, nbt);
        }
        boolean blockMode = buf.readBoolean();
        long blockPos = buf.readLong();
        return new S2CSyncDockyardClientPacket(slots, blockMode, blockPos);
    }

    public static void handle(S2CSyncDockyardClientPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DockyardClientCache.sync(pkt.slots, pkt.blockMode, pkt.blockPos);
        });
        ctx.get().setPacketHandled(true);
    }
}