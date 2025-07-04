package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

public class DockyardClientCache {
    private static final Map<Integer, CompoundTag> slots = new HashMap<>();
    private static boolean blockMode = false;
    private static long blockPos = 0L;

    public static void sync(Map<Integer, CompoundTag> newMap, boolean blockMode, long blockPos) {
        slots.clear();
        slots.putAll(newMap);
        DockyardClientCache.blockMode = blockMode;
        DockyardClientCache.blockPos = blockPos;
    }

    public static boolean hasShipInSlot(int slot) {
        return slots.containsKey(slot);
    }

    public static String getShipIdOrName(int slot) {
        CompoundTag tag = slots.get(slot);
        if (tag == null) return "";
        if (tag.contains("vs_ship_name")) return tag.getString("vs_ship_name");
        if (tag.contains("vs_ship_id")) return "id:" + tag.getLong("vs_ship_id");
        return "<ship>";
    }

    public static boolean getBlockMode() {
        return blockMode;
    }
    public static long getBlockPos() {
        return blockPos;
    }
}