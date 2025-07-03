package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Информация о кораблях игрока теперь хранится в player.getPersistentData().
 * Эти данные вечные, не требуют копирования при смерти/клоне/портале.
 */
public class DockyardPlayerDataUtil {
    private static final String DOCKYARD_KEY = "heavybullet_dockyard";

    /** Получить (или создать) persistent data для слотов дока игрока */
    public static CompoundTag getDockyardData(Player player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(DOCKYARD_KEY, 10)) {
            persistent.put(DOCKYARD_KEY, new CompoundTag());
        }
        return persistent.getCompound(DOCKYARD_KEY);
    }

    /** Сохраняет корабль в слот */
    public static void saveShipToSlot(Player player, int slot, CompoundTag shipNbt) {
        CompoundTag dockyard = getDockyardData(player);
        dockyard.put("ship" + slot, shipNbt.copy());
        player.getPersistentData().put(DOCKYARD_KEY, dockyard);
    }

    /** Чтение корабля из слота */
    public static CompoundTag getShipFromSlot(Player player, int slot) {
        CompoundTag dockyard = getDockyardData(player);
        String key = "ship" + slot;
        return dockyard.contains(key) ? dockyard.getCompound(key).copy() : null;
    }

    /** Проверка, есть ли корабль в слоте */
    public static boolean hasShipInSlot(Player player, int slot) {
        CompoundTag dockyard = getDockyardData(player);
        return dockyard.contains("ship" + slot);
    }

    /** Удалить корабль из слота */
    public static void clearShipFromSlot(Player player, int slot) {
        CompoundTag dockyard = getDockyardData(player);
        dockyard.remove("ship" + slot);
        player.getPersistentData().put(DOCKYARD_KEY, dockyard);
    }
}