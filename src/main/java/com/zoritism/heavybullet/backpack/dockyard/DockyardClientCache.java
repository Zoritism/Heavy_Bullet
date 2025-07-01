package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DockyardClientCache {
    private static final Logger LOGGER = LogManager.getLogger("HeavyBullet/DockyardClientCache");
    private static final Map<Integer, CompoundTag> slots = new HashMap<>();

    public static void sync(Map<Integer, CompoundTag> newMap) {
        slots.clear();
        slots.putAll(newMap);
        LOGGER.info("[DockyardClientCache] Sync called. Current slots:");
        for (Map.Entry<Integer, CompoundTag> entry : slots.entrySet()) {
            LOGGER.info("[DockyardClientCache] slot {} = {}", entry.getKey(), entry.getValue());
        }
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
}