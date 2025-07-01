package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Вспомогатель для работы с сохранёнными кораблями в блоке рюкзака.
 */
public class DockyardDataHelper {
    private static final String SHIP_NBT_KEY = "DockyardStoredShip"; // Слот 0
    private static final String SHIP_NBT_KEY_PREFIX = "DockyardStoredShip"; // Слот i: DockyardStoredShip, DockyardStoredShip1, DockyardStoredShip2...

    private static String getSlotKey(int slot) {
        return slot == 0 ? SHIP_NBT_KEY : SHIP_NBT_KEY_PREFIX + slot;
    }

    // ===== ДЛЯ БЛОКА РЮКЗАКА =====

    public static boolean hasShipInBlockSlot(BlockEntity blockEntity, int slot) {
        if (blockEntity == null) return false;
        CompoundTag tag = getOrCreatePersistentData(blockEntity);
        String key = getSlotKey(slot);
        return tag.contains(key);
    }

    public static void saveShipToBlockSlot(BlockEntity blockEntity, CompoundTag shipNbt, int slot) {
        if (blockEntity == null) return;
        CompoundTag tag = getOrCreatePersistentData(blockEntity);
        String key = getSlotKey(slot);
        tag.put(key, shipNbt);
        blockEntity.setChanged();
    }

    public static CompoundTag getShipFromBlockSlot(BlockEntity blockEntity, int slot) {
        if (blockEntity == null) return null;
        CompoundTag tag = getOrCreatePersistentData(blockEntity);
        String key = getSlotKey(slot);
        return tag.contains(key) ? tag.getCompound(key) : null;
    }

    public static void clearShipFromBlockSlot(BlockEntity blockEntity, int slot) {
        if (blockEntity == null) return;
        CompoundTag tag = getOrCreatePersistentData(blockEntity);
        String key = getSlotKey(slot);
        tag.remove(key);
        blockEntity.setChanged();
    }

    private static CompoundTag getOrCreatePersistentData(BlockEntity blockEntity) {
        CompoundTag beTag = blockEntity.saveWithFullMetadata();
        CompoundTag persistentData;
        if (beTag.contains("ForgeData", 10)) {
            persistentData = beTag.getCompound("ForgeData");
        } else {
            persistentData = new CompoundTag();
            beTag.put("ForgeData", persistentData);
        }
        return persistentData;
    }
}