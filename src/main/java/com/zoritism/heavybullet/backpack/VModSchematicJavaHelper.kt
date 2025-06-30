package com.zoritism.heavybullet.backpack

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

object VModSchematicJavaHelper {
    private val LOGGER: Logger = LoggerFactory.getLogger(VModSchematicJavaHelper::class.java)

    @JvmStatic
    fun findServerShip(level: ServerLevel, pos: BlockPos): DockyardUpgradeLogic.ServerShipHandle? {
        LOGGER.info("[VModSchematicJavaHelper] findServerShip called with pos=({}, {}, {}) in level={}", pos.x, pos.y, pos.z, level.dimension().location())
        try {
            val pipelineClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
            val getVsPipeline = pipelineClass.getMethod("getVsPipeline", Class.forName("net.minecraft.server.MinecraftServer"))
            val pipeline = getVsPipeline.invoke(null, level.server)
            if (pipeline == null) {
                LOGGER.warn("[VModSchematicJavaHelper] VS pipeline is null!")
                return null
            }
            val shipWorld = pipeline.javaClass.getMethod("getShipWorld").invoke(pipeline)
            if (shipWorld == null) {
                LOGGER.warn("[VModSchematicJavaHelper] ShipWorld is null!")
                return null
            }
            val allShips = shipWorld.javaClass.getMethod("getAllShips").invoke(shipWorld) as? Iterable<*>
            if (allShips == null) {
                LOGGER.warn("[VModSchematicJavaHelper] allShips is null or not iterable!")
                return null
            }
            val x = pos.x + 0.5
            val y = pos.y + 0.5
            val z = pos.z + 0.5
            for (ship in allShips) {
                if (ship == null) continue
                val aabb = ship.javaClass.getMethod("getWorldAABB").invoke(ship)
                if (aabb != null) {
                    val minX = aabb.javaClass.getMethod("minX").invoke(aabb) as Double
                    val maxX = aabb.javaClass.getMethod("maxX").invoke(aabb) as Double
                    val minY = aabb.javaClass.getMethod("minY").invoke(aabb) as Double
                    val maxY = aabb.javaClass.getMethod("maxY").invoke(aabb) as Double
                    val minZ = aabb.javaClass.getMethod("minZ").invoke(aabb) as Double
                    val maxZ = aabb.javaClass.getMethod("maxZ").invoke(aabb) as Double
                    if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
                        val id = ship.javaClass.getMethod("getId").invoke(ship) as Long
                        LOGGER.info("[VModSchematicJavaHelper] Ship found at pos ({}, {}, {}) with id={}", x, y, z, id)
                        return object : DockyardUpgradeLogic.ServerShipHandle {
                            override fun getServerShip(): Any = ship
                            override fun getId(): Long = id
                        }
                    }
                }
            }
            LOGGER.info("[VModSchematicJavaHelper] No ship found at pos ({}, {}, {})", x, y, z)
            return null
        } catch (e: Exception) {
            LOGGER.error("[VModSchematicJavaHelper] Exception in findServerShip: ", e)
            return null
        }
    }

    @JvmStatic
    fun saveShipToNBT(
        level: ServerLevel,
        player: ServerPlayer,
        uuid: UUID,
        ship: DockyardUpgradeLogic.ServerShipHandle,
        nbt: CompoundTag
    ): Boolean {
        LOGGER.info("[VModSchematicJavaHelper] saveShipToNBT called for player={} uuid={}", player.gameProfile.name, uuid)
        try {
            val serverShip = ship.getServerShip()
            val id = serverShip.javaClass.getMethod("getId").invoke(serverShip) as Long
            nbt.putLong("vs_ship_id", id)
            LOGGER.info("[VModSchematicJavaHelper] Saved ship id={} to NBT", id)
            return true
        } catch (e: Exception) {
            LOGGER.error("[VModSchematicJavaHelper] Exception in saveShipToNBT: ", e)
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
    ): Boolean {
        LOGGER.info("[VModSchematicJavaHelper] spawnShipFromNBT called at pos ({}, {}, {}) for player={}", pos.x, pos.y, pos.z, player.gameProfile.name)
        // В VMod/VS2 нет публичного API для создания корабля из NBT/id.
        // Возвращаем false, чтобы избежать ошибок в логике.
        return false
    }

    @JvmStatic
    fun removeShip(level: ServerLevel, ship: DockyardUpgradeLogic.ServerShipHandle) {
        LOGGER.info("[VModSchematicJavaHelper] removeShip called for ship id={}", ship.getId())
        try {
            val serverShip = ship.getServerShip()
            // BottleShip и VMod требуют именно org.valkyrienskies.core.api.ships.ServerShip
            val serverShipClass = try {
                Class.forName("org.valkyrienskies.core.api.ships.ServerShip")
            } catch (e: Exception) {
                LOGGER.error("[VModSchematicJavaHelper] ServerShip class not found!", e)
                return
            }
            // Приведение к ServerShip, если это прокси/реализация
            val castedServerShip = if (serverShipClass.isInstance(serverShip)) {
                serverShip
            } else {
                LOGGER.error("[VModSchematicJavaHelper] serverShip is not instance of ServerShip!")
                return
            }
            // Попробовать найти Teleport.teleportShip из BottleShip, если он есть в classpath
            val teleportClass = try {
                Class.forName("com.ForgeStove.bottle_ship.Teleport")
            } catch (e: Exception) {
                null
            }
            if (teleportClass != null) {
                // Используем примитивные типы double, а не Double::class.java!
                val teleportMethod = teleportClass.getMethod(
                    "teleportShip",
                    ServerLevel::class.java,
                    serverShipClass,
                    java.lang.Double.TYPE,
                    java.lang.Double.TYPE,
                    java.lang.Double.TYPE
                )
                teleportMethod.invoke(null, level, castedServerShip, 0.0, -1000.0, 0.0)
                LOGGER.info("[VModSchematicJavaHelper] Ship id={} teleported to void.", ship.getId())
            } else {
                LOGGER.warn("[VModSchematicJavaHelper] Teleport class not found, can't remove ship!")
            }
        } catch (e: Exception) {
            LOGGER.error("[VModSchematicJavaHelper] Exception in removeShip: ", e)
        }
    }
}