package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.nbt.CompoundTag;

/**
 * Серверные данные дока игрока: корабли, связанные с данным игроком.
 */
public class PlayerDockyardData {
    private final CompoundTag dockyardData = new CompoundTag();

    public CompoundTag getDockyardData() {
        return dockyardData;
    }
}