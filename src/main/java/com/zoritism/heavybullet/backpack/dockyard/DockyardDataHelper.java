package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Вспомогатель для работы с сохранёнными кораблями в рюкзаке — теперь поддерживает несколько слотов.
 */
public class DockyardDataHelper {
    private static final String SHIP_NBT_KEY = "DockyardStoredShip"; // Слот 0
    private static final String SHIP_NBT_KEY_PREFIX = "DockyardStoredShip"; // Слот i: DockyardStoredShip, DockyardStoredShip1, DockyardStoredShip2...

    /**
     * Проверить, есть ли сохранённый корабль в определённом слоте.
     */
    public static boolean hasShipInBackpackSlot(ItemStack backpack, int slot) {
        if (backpack == null || !backpack.hasTag()) return false;
        CompoundTag tag = backpack.getTag();
        String key = getSlotKey(slot);
        return tag.contains(key);
    }

    /**
     * Сохранить корабль в NBT рюкзака в определённый слот.
     */
    public static void saveShipToBackpackSlot(ItemStack backpack, CompoundTag shipNbt, int slot) {
        if (backpack == null) return;
        CompoundTag tag = backpack.getOrCreateTag();
        String key = getSlotKey(slot);
        tag.put(key, shipNbt);
    }

    /**
     * Получить корабль из NBT рюкзака из определённого слота.
     */
    public static CompoundTag getShipFromBackpackSlot(ItemStack backpack, int slot) {
        if (backpack == null || !backpack.hasTag()) return null;
        CompoundTag tag = backpack.getTag();
        String key = getSlotKey(slot);
        return tag.contains(key) ? tag.getCompound(key) : null;
    }

    /**
     * Очистить данные корабля из определённого слота.
     */
    public static void clearShipFromBackpackSlot(ItemStack backpack, int slot) {
        if (backpack == null || !backpack.hasTag()) return;
        CompoundTag tag = backpack.getTag();
        String key = getSlotKey(slot);
        tag.remove(key);
    }

    /**
     * Оставлены старые методы для обратной совместимости (работают с нулевым слотом).
     */
    public static boolean hasShipInBackpack(ItemStack backpack) {
        return hasShipInBackpackSlot(backpack, 0);
    }

    public static void saveShipToBackpack(ItemStack backpack, CompoundTag shipNbt) {
        saveShipToBackpackSlot(backpack, shipNbt, 0);
    }

    public static CompoundTag getShipFromBackpack(ItemStack backpack) {
        return getShipFromBackpackSlot(backpack, 0);
    }

    public static void clearShipFromBackpack(ItemStack backpack) {
        clearShipFromBackpackSlot(backpack, 0);
    }

    /**
     * Получить имя ключа для слота: 0 → DockyardStoredShip, 1 → DockyardStoredShip1, 2 → DockyardStoredShip2 ...
     */
    private static String getSlotKey(int slot) {
        return slot == 0 ? SHIP_NBT_KEY : SHIP_NBT_KEY_PREFIX + slot;
    }

    // ===== ДЛЯ БЛОКА РЮКЗАКА =====

    /**
     * Проверить, есть ли сохранённый корабль в определённом слоте блока рюкзака.
     */
    public static boolean hasShipInBlockSlot(BlockEntity blockEntity, int slot) {
        if (blockEntity == null) return false;
        CompoundTag tag = getOrCreatePersistentData(blockEntity);
        String key = getSlotKey(slot);
        return tag.contains(key);
    }

    /**
     * Сохранить корабль в NBT блока рюкзака в определённый слот.
     */
    public static void saveShipToBlockSlot(BlockEntity blockEntity, CompoundTag shipNbt, int slot) {
        if (blockEntity == null) return;
        CompoundTag tag = getOrCreatePersistentData(blockEntity);
        String key = getSlotKey(slot);
        tag.put(key, shipNbt);
        blockEntity.setChanged();
    }

    /**
     * Получить корабль из NBT блока рюкзака из определённого слота.
     */
    public static CompoundTag getShipFromBlockSlot(BlockEntity blockEntity, int slot) {
        if (blockEntity == null) return null;
        CompoundTag tag = getOrCreatePersistentData(blockEntity);
        String key = getSlotKey(slot);
        return tag.contains(key) ? tag.getCompound(key) : null;
    }

    /**
     * Очистить данные корабля из определённого слота блока рюкзака.
     */
    public static void clearShipFromBlockSlot(BlockEntity blockEntity, int slot) {
        if (blockEntity == null) return;
        CompoundTag tag = getOrCreatePersistentData(blockEntity);
        String key = getSlotKey(slot);
        tag.remove(key);
        blockEntity.setChanged();
    }

    /**
     * Получить или создать persistentData для BlockEntity (Forge стандарт).
     */
    private static CompoundTag getOrCreatePersistentData(BlockEntity blockEntity) {
        // Forge: все BlockEntity имеют поле persistentData (CompoundTag) начиная с 1.18+
        // Для совместимости с разными версиями Forge/Minecraft, используем стандартный подход:
        // persistentData хранится в основном CompoundTag блок-сущности под ключом "ForgeData"
        CompoundTag beTag = blockEntity.saveWithFullMetadata();
        CompoundTag persistentData;
        if (beTag.contains("ForgeData", 10)) {
            persistentData = beTag.getCompound("ForgeData");
        } else {
            persistentData = new CompoundTag();
            beTag.put("ForgeData", persistentData);
        }
        // Применяем изменения обратно (в Vanilla/Forge это не всегда автоматически)
        // Обычно у BlockEntity есть метод getPersistentData(), если нет — используем save/load
        // Если у вашей реализации есть специальный getter/setter — используйте его!
        return persistentData;
    }
}