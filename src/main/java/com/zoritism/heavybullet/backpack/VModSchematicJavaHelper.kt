package com.zoritism.heavybullet.backpack

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.core.api.ships.ServerShip
import java.util.UUID

object VModSchematicJavaHelper {

    @JvmStatic
    fun findServerShip(level: ServerLevel, pos: BlockPos): DockyardUpgradeLogic.ServerShipHandle? {
        // Используем reflection для вызова getShipManagingPos, если прямого импорта нет
        val serverShip: ServerShip? = try {
            val clazz = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
            val method = clazz.getMethod("getShipManagingPos", ServerLevel::class.java, BlockPos::class.java)
            method.invoke(null, level, pos) as? ServerShip
        } catch (e: Exception) {
            null
        }
        if (serverShip != null) {
            return object : DockyardUpgradeLogic.ServerShipHandle {
                override fun getServerShip(): Any = serverShip
                override fun getId(): Long = serverShip.id
            }
        }
        return null
    }

    @JvmStatic
    fun saveShipToNBT(
        level: ServerLevel,
        player: ServerPlayer,
        uuid: UUID,
        ship: DockyardUpgradeLogic.ServerShipHandle,
        nbt: CompoundTag
    ): Boolean {
        val serverShip = ship.getServerShip() as? ServerShip ?: return false
        nbt.putLong("vs_ship_id", serverShip.id)
        return true
    }

    @JvmStatic
    fun spawnShipFromNBT(
        level: ServerLevel,
        player: ServerPlayer,
        uuid: UUID,
        pos: Vec3,
        nbt: CompoundTag
    ) {
        // Нет публичного API для создания корабля из NBT/id в VS2.
        // Оставлено пустым, как и в BottleShip.
    }

    @JvmStatic
    fun removeShip(level: ServerLevel, ship: DockyardUpgradeLogic.ServerShipHandle) {
        // Нет публичного API для удаления корабля по id/handle в VS2.
        // Оставлено пустым, как и в BottleShip.
    }
}