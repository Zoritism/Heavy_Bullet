package com.zoritism.heavybullet.backpack

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.core.api.ships.ServerShip
import java.util.UUID

object VModSchematicJavaHelper {

    /**
     * Поиск корабля по координате с учетом центра блока и прямого обхода ShipWorld (аналогично VMod).
     * Если VSCoreAPI.serverShipWorld(level) у вас называется иначе — поправьте импорт!
     */
    @JvmStatic
    fun findServerShip(level: ServerLevel, pos: BlockPos): DockyardUpgradeLogic.ServerShipHandle? {
        // Используем центр блока для поиска (аналог VMod, +0.5!)
        val shipWorldGetter = try {
            val clazz = Class.forName("net.spaceeye.vmod.VSCoreAPI")
            clazz.getMethod("serverShipWorld", ServerLevel::class.java)
        } catch (e: Exception) {
            null
        }
        if (shipWorldGetter == null) return null
        val shipWorld = try {
            shipWorldGetter.invoke(null, level)
        } catch (e: Exception) {
            null
        } ?: return null

        // ships: Collection<ServerShip>
        val ships = try {
            shipWorld.javaClass.getMethod("getShips").invoke(shipWorld) as? Collection<*>
        } catch (e: Exception) {
            null
        } ?: return null

        val x = pos.x + 0.5
        val y = pos.y + 0.5
        val z = pos.z + 0.5

        for (ship in ships) {
            if (ship == null) continue
            val aabb = try {
                ship.javaClass.getMethod("getWorldAABB").invoke(ship)
            } catch (e: Exception) {
                null
            } ?: continue
            val minX = aabb.javaClass.getMethod("minX").invoke(aabb) as Double
            val maxX = aabb.javaClass.getMethod("maxX").invoke(aabb) as Double
            val minY = aabb.javaClass.getMethod("minY").invoke(aabb) as Double
            val maxY = aabb.javaClass.getMethod("maxY").invoke(aabb) as Double
            val minZ = aabb.javaClass.getMethod("minZ").invoke(aabb) as Double
            val maxZ = aabb.javaClass.getMethod("maxZ").invoke(aabb) as Double
            if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
                val shipId = ship.javaClass.getMethod("getId").invoke(ship) as Long
                return object : DockyardUpgradeLogic.ServerShipHandle {
                    override fun getServerShip(): Any = ship
                    override fun getId(): Long = shipId
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
        // ship.getServerShip() теперь — это instance ServerShip из vmod/VS2
        val serverShip = ship.getServerShip()
        val id = try {
            serverShip.javaClass.getMethod("getId").invoke(serverShip) as Long
        } catch (e: Exception) {
            return false
        }
        nbt.putLong("vs_ship_id", id)
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