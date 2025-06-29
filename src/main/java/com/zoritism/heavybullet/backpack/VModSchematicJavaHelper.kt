package com.zoritism.heavybullet.backpack

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.mod.common.VSGameUtilsKt
import org.valkyrienskies.core.apigame.world.ServerShipWorldCore
import org.valkyrienskies.core.api.ships.ServerShip
import java.util.UUID

object VModSchematicJavaHelper {

    @JvmStatic
    fun findServerShip(level: ServerLevel, pos: BlockPos): DockyardUpgradeLogic.ServerShipHandle? {
        val server = level.server
        val pipeline = VSGameUtilsKt.getVsPipeline(server)
        val shipWorld = pipeline.shipWorld as? ServerShipWorldCore ?: return null
        val allShips = shipWorld.allShips
        val x = pos.x.toDouble()
        val y = pos.y.toDouble()
        val z = pos.z.toDouble()
        for (ship in allShips) {
            val aabb = ship.worldAABB
            if (aabb != null &&
                x >= aabb.minX() && x <= aabb.maxX() &&
                y >= aabb.minY() && y <= aabb.maxY() &&
                z >= aabb.minZ() && z <= aabb.maxZ()
            ) {
                return object : DockyardUpgradeLogic.ServerShipHandle {
                    override fun getServerShip(): Any = ship
                    override fun getId(): Long = ship.id
                }
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
        // VS API не предоставляет прямой сериализации в NBT. Обычно используется что-то вроде ShipSchematic
        // Здесь предполагается, что твой мод/утилиты vmod предоставляют такие методы:
        // Например, serverShip.writeToNBT(nbt)
        // Псевдокод:
        try {
            // serverShip.writeToNBT(nbt) // Если есть такой метод
            // Или использовать ShipSchematic и сериализовать схему корабля
            // TODO: Реализуй сериализацию с помощью vmod/VS API
            // Заглушка:
            nbt.putLong("vs_ship_id", serverShip.id)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    @JvmStatic
    fun spawnShipFromNBT(
        level: ServerLevel,
        player: ServerPlayer,
        uuid: UUID,
        pos: Vec3,
        nbt: CompoundTag
    ) {
        // VS API требует воссоздания корабля из схемы.
        // Обычно делается через ShipSchematic или аналогичный класс:
        // ShipSchematic.spawn(level, pos, nbt)
        // Здесь примерная заглушка:
        try {
            val shipId = nbt.getLong("vs_ship_id")
            // Твой vmod должен реализовывать создание корабля по данным из NBT
            // TODO: Реализуй спаун через vmod/VS API
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun removeShip(level: ServerLevel, ship: DockyardUpgradeLogic.ServerShipHandle) {
        val serverShip = ship.getServerShip() as? ServerShip ?: return
        try {
            // VS API: Обычно у ServerShip есть метод remove() или world.removeShip(serverShip)
            // TODO: Реализуй удаление через vmod/VS API
            // serverShip.removeFromWorld() // если такое есть
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}