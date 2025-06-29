package com.zoritism.heavybullet.backpack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class DockyardDataHelper {
    private static final String SHIP_NBT_KEY = "DockyardStoredShip";

    // Проверить, есть ли сохранённый корабль
    public static boolean hasShipInBackpack(ItemStack backpack) {
        if (backpack == null || !backpack.hasTag()) return false;
        CompoundTag tag = backpack.getTag();
        return tag.contains(SHIP_NBT_KEY);
    }

    // Сохранить корабль в NBT рюкзака
    public static void saveShipToBackpack(ItemStack backpack, CompoundTag shipNbt) {
        if (backpack == null) return;
        CompoundTag tag = backpack.getOrCreateTag();
        tag.put(SHIP_NBT_KEY, shipNbt);
    }

    // Получить корабль из NBT рюкзака
    public static CompoundTag getShipFromBackpack(ItemStack backpack) {
        if (backpack == null || !backpack.hasTag()) return null;
        CompoundTag tag = backpack.getTag();
        return tag.contains(SHIP_NBT_KEY) ? tag.getCompound(SHIP_NBT_KEY) : null;
    }

    // Очистить данные корабля
    public static void clearShipFromBackpack(ItemStack backpack) {
        if (backpack == null || !backpack.hasTag()) return;
        CompoundTag tag = backpack.getTag();
        tag.remove(SHIP_NBT_KEY);
    }
}