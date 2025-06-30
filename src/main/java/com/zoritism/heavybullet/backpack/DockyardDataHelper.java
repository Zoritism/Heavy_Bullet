package com.zoritism.heavybullet.backpack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class DockyardDataHelper {
    private static final String SHIP_NBT_KEY = "DockyardStoredShip";
    private static final String SCHEMATIC_NAME_KEY = "schematic_name";

    public static boolean hasShipInBackpack(ItemStack backpack) {
        if (backpack == null || !backpack.hasTag()) return false;
        CompoundTag tag = backpack.getTag();
        if (!tag.contains(SHIP_NBT_KEY)) return false;
        CompoundTag shipTag = tag.getCompound(SHIP_NBT_KEY);
        return shipTag.contains(SCHEMATIC_NAME_KEY);
    }

    public static void saveSchematicNameToBackpack(ItemStack backpack, String schematicName) {
        if (backpack == null) return;
        CompoundTag tag = backpack.getOrCreateTag();
        CompoundTag shipTag = new CompoundTag();
        shipTag.putString(SCHEMATIC_NAME_KEY, schematicName);
        tag.put(SHIP_NBT_KEY, shipTag);
    }

    public static String getSchematicNameFromBackpack(ItemStack backpack) {
        if (backpack == null || !backpack.hasTag()) return null;
        CompoundTag tag = backpack.getTag();
        if (!tag.contains(SHIP_NBT_KEY)) return null;
        CompoundTag shipTag = tag.getCompound(SHIP_NBT_KEY);
        return shipTag.contains(SCHEMATIC_NAME_KEY) ? shipTag.getString(SCHEMATIC_NAME_KEY) : null;
    }

    public static void clearShipFromBackpack(ItemStack backpack) {
        if (backpack == null || !backpack.hasTag()) return;
        CompoundTag tag = backpack.getTag();
        tag.remove(SHIP_NBT_KEY);
    }
}