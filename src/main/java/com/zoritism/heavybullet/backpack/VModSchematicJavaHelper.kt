package com.zoritism.heavybullet.backpack

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3
import java.util.UUID

object VModSchematicJavaHelper {
@JvmStatic
fun findServerShip(level: ServerLevel, pos: BlockPos): DockyardUpgradeLogic.ServerShipHandle? {
        // Реализация поиска корабля через vmod/VS API
        // ...
        return null // TODO
        }

@JvmStatic
fun saveShipToNBT(level: ServerLevel, player: ServerPlayer, uuid: UUID, ship: DockyardUpgradeLogic.ServerShipHandle, nbt: CompoundTag): Boolean {
    // Сохраняем корабль через vmod/VS API
    // ...
    return false // TODO
}

@JvmStatic
fun spawnShipFromNBT(level: ServerLevel, player: ServerPlayer, uuid: UUID, pos: Vec3, nbt: CompoundTag) {
    // Воссоздаём корабль через vmod/VS API
    // ...
}

@JvmStatic
fun removeShip(level: ServerLevel, ship: DockyardUpgradeLogic.ServerShipHandle) {
    // Удаляем корабль через vmod/VS API
    // ...
}
}