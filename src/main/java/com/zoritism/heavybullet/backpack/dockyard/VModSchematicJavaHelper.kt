package com.zoritism.heavybullet.backpack.dockyard

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.UUID
import kotlin.math.max
import kotlin.math.sqrt

object VModSchematicJavaHelper {

    private val LOGGER: Logger = LogManager.getLogger("HeavyBullet/VModSchematicJavaHelper")

    /**
     * Поиск корабля по позиции.
     * @return ServerShipHandle если найден, иначе null.
     */
    @JvmStatic
    fun findServerShip(level: ServerLevel, pos: BlockPos): DockyardUpgradeLogic.ServerShipHandle? {
        LOGGER.info("[findServerShip] Called for pos $pos")
        return try {
            val pipelineClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
            val getVsPipeline = pipelineClass.getMethod("getVsPipeline", Class.forName("net.minecraft.server.MinecraftServer"))
            val pipeline = getVsPipeline.invoke(null, level.server)
            if (pipeline == null) {
                LOGGER.warn("[findServerShip] VS pipeline was null")
                return null
            }
            val shipWorld = pipeline.javaClass.getMethod("getShipWorld").invoke(pipeline)
            if (shipWorld == null) {
                LOGGER.warn("[findServerShip] Ship world was null")
                return null
            }
            val allShips = shipWorld.javaClass.getMethod("getAllShips").invoke(shipWorld) as? Iterable<*>
                ?: run {
                    LOGGER.warn("[findServerShip] allShips was null")
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
                        LOGGER.info("[findServerShip] Ship found at $pos with id $id")
                        return object : DockyardUpgradeLogic.ServerShipHandle {
                            override fun getServerShip(): Any = ship
                            override fun getId(): Long = id
                        }
                    }
                }
            }
            LOGGER.info("[findServerShip] No ship found at $pos")
            null
        } catch (e: Exception) {
            LOGGER.error("[findServerShip] Exception: ${e.message}", e)
            null
        }
    }

    /**
     * Сохранить корабль в capability игрока (глобально, а не в рюкзаке!)
     * @return true если успешно, иначе false
     */
    @JvmStatic
    fun tryStoreShipToPlayerDockyard(
        level: ServerLevel,
        player: ServerPlayer,
        uuid: UUID,
        ship: DockyardUpgradeLogic.ServerShipHandle,
        nbt: CompoundTag,
        slot: Int
    ): Boolean {
        LOGGER.info("[tryStoreShipToPlayerDockyard] Called for player ${player.name.string}, slot $slot")
        // Не даём забирать корабль если у игрока уже есть в этом слоте
        val dockyardData = PlayerDockyardDataUtil.getOrCreate(player).dockyardData
        val key = "ship$slot"
        if (dockyardData.contains(key)) {
            LOGGER.info("[tryStoreShipToPlayerDockyard] Player already contains a ship in slot $slot")
            return false
        }

        // Проверяем расстояние до игрока (максимум 4 блока)
        val playerPos = player.eyePosition
        val shipObj = ship.getServerShip()
        val aabb = try { shipObj.javaClass.getMethod("getWorldAABB").invoke(shipObj) } catch (_: Exception) { null }
        if (aabb != null) {
            val minX = aabb.javaClass.getMethod("minX").invoke(aabb) as Double
            val maxX = aabb.javaClass.getMethod("maxX").invoke(aabb) as Double
            val minY = aabb.javaClass.getMethod("minY").invoke(aabb) as Double
            val maxY = aabb.javaClass.getMethod("maxY").invoke(aabb) as Double
            val minZ = aabb.javaClass.getMethod("minZ").invoke(aabb) as Double
            val maxZ = aabb.javaClass.getMethod("maxZ").invoke(aabb) as Double
            val dx = maxOf(minX - playerPos.x, 0.0, playerPos.x - maxX)
            val dy = maxOf(minY - playerPos.y, 0.0, playerPos.y - maxY)
            val dz = maxOf(minZ - playerPos.z, 0.0, playerPos.z - maxZ)
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            LOGGER.info("[tryStoreShipToPlayerDockyard] Distance to ship: $dist")
            if (dist > 4.0) {
                LOGGER.warn("[tryStoreShipToPlayerDockyard] Ship is too far from player")
                return false
            }
        }

        // Сохраняем в NBT
        val saveResult = saveShipToNBT(level, player, uuid, ship, nbt)
        LOGGER.info("[tryStoreShipToPlayerDockyard] Ship saved to NBT: $saveResult")
        if (!saveResult) return false

        // Только если успешно — сохраняем в capability игрока
        dockyardData.put(key, nbt)
        LOGGER.info("[tryStoreShipToPlayerDockyard] Ship saved to player's dockyard capability in slot $slot")

        // Удаляем из мира, если только что успешно сохранили в NBT и capability
        val removeResult = try {
            removeShip(level, ship)
            LOGGER.info("[tryStoreShipToPlayerDockyard] Ship removed from world")
            true
        } catch (e: Exception) {
            LOGGER.error("[tryStoreShipToPlayerDockyard] Exception while removing ship: ${e.message}", e)
            dockyardData.remove(key)
            false
        }
        if (!removeResult) return false

        LOGGER.info("[tryStoreShipToPlayerDockyard] Done, ship picked up successfully")
        return true
    }

    /**
     * Сохранить корабль в NBT без проверки состояния рюкзака и расстояния.
     * Используется только внутренне!
     */
    @JvmStatic
    fun saveShipToNBT(
        level: ServerLevel,
        player: ServerPlayer,
        uuid: UUID,
        ship: DockyardUpgradeLogic.ServerShipHandle,
        nbt: CompoundTag
    ): Boolean {
        LOGGER.info("[saveShipToNBT] Called for player ${player.name.string}")
        return try {
            val serverShip = ship.getServerShip()
            val id = serverShip.javaClass.getMethod("getId").invoke(serverShip) as Long
            nbt.putLong("vs_ship_id", id)
            LOGGER.info("[saveShipToNBT] Saved ship id $id to NBT")
            true
        } catch (e: Exception) {
            LOGGER.error("[saveShipToNBT] Exception: ${e.message}", e)
            false
        }
    }

    /**
     * Спавн корабля из NBT (в мир из capability игрока)
     * @return true если успешно, иначе false
     */
    @JvmStatic
    fun trySpawnShipFromPlayerDockyard(
        level: ServerLevel,
        player: ServerPlayer,
        uuid: UUID,
        nbt: CompoundTag,
        slot: Int
    ): Boolean {
        LOGGER.info("[trySpawnShipFromPlayerDockyard] Called for player ${player.name.string}, slot $slot")
        // Проверяем что в capability есть корабль
        val dockyardData = PlayerDockyardDataUtil.getOrCreate(player).dockyardData
        val key = "ship$slot"
        if (!dockyardData.contains(key)) {
            LOGGER.info("[trySpawnShipFromPlayerDockyard] No ship in player dockyard in slot $slot")
            return false
        }
        // Проверяем что корабль ещё не существует в мире (не дюп)
        val shipId = nbt.getLong("vs_ship_id")
        if (shipId != 0L) {
            val pipelineClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
            val getVsPipeline = pipelineClass.getMethod(
                "getVsPipeline",
                Class.forName("net.minecraft.server.MinecraftServer")
            )
            val pipeline = getVsPipeline.invoke(null, level.server)
            if (pipeline != null) {
                val shipWorld = pipeline.javaClass.getMethod("getShipWorld").invoke(pipeline)
                if (shipWorld != null) {
                    val getShipById = shipWorld.javaClass.getMethod("getShipById", java.lang.Long.TYPE)
                    val ship = getShipById.invoke(shipWorld, shipId)
                    if (ship != null) {
                        LOGGER.warn("[trySpawnShipFromPlayerDockyard] Ship with id $shipId already exists in world")
                        return false // Уже есть такой корабль
                    }
                }
            }
        }
        // Спавним
        val success = spawnShipFromNBT(level, player, uuid, player.position(), nbt)
        LOGGER.info("[trySpawnShipFromPlayerDockyard] Spawn result: $success")
        if (success) {
            dockyardData.remove(key)
            LOGGER.info("[trySpawnShipFromPlayerDockyard] Cleared ship from player's dockyard after spawn")
            return true
        }
        return false
    }

    /**
     * Спавн корабля из NBT (с проверкой на коллизии и расстояние)
     * @return true если успешно, иначе false
     */
    @JvmStatic
    fun spawnShipFromNBT(
        level: ServerLevel,
        player: ServerPlayer,
        uuid: UUID,
        pos: Vec3,
        nbt: CompoundTag
    ): Boolean {
        LOGGER.info("[spawnShipFromNBT] Called for player ${player.name.string}")
        try {
            if (!nbt.contains("vs_ship_id")) {
                LOGGER.warn("[spawnShipFromNBT] No vs_ship_id found in NBT")
                return false
            }
            val shipId = nbt.getLong("vs_ship_id")
            val pipelineClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
            val getVsPipeline = pipelineClass.getMethod("getVsPipeline", Class.forName("net.minecraft.server.MinecraftServer"))
            val pipeline = getVsPipeline.invoke(null, level.server)
            if (pipeline == null) {
                LOGGER.warn("[spawnShipFromNBT] VS pipeline was null")
                return false
            }
            val shipWorld = pipeline.javaClass.getMethod("getShipWorld").invoke(pipeline)
            if (shipWorld == null) {
                LOGGER.warn("[spawnShipFromNBT] Ship world was null")
                return false
            }

            // Поиск корабля по id
            var ship: Any? = null
            try {
                val getShipById = shipWorld.javaClass.getMethod("getShipById", java.lang.Long.TYPE)
                ship = getShipById.invoke(shipWorld, shipId)
            } catch (_: NoSuchMethodException) {
                val allShips = shipWorld.javaClass.getMethod("getAllShips").invoke(shipWorld) as? Iterable<*>
                if (allShips != null) {
                    ship = allShips.firstOrNull {
                        val id = it?.javaClass?.getMethod("getId")?.invoke(it) as? Long
                        id == shipId
                    }
                }
            }
            if (ship == null) {
                LOGGER.warn("[spawnShipFromNBT] No ship object found by id $shipId")
                return false
            }

            // Получаем размеры корабля (AABB)
            val aabb = ship.javaClass.getMethod("getWorldAABB").invoke(ship)
            val minX = aabb.javaClass.getMethod("minX").invoke(aabb) as Double
            val maxX = aabb.javaClass.getMethod("maxX").invoke(aabb) as Double
            val minY = aabb.javaClass.getMethod("minY").invoke(aabb) as Double
            val maxY = aabb.javaClass.getMethod("maxY").invoke(aabb) as Double
            val minZ = aabb.javaClass.getMethod("minZ").invoke(aabb) as Double
            val maxZ = aabb.javaClass.getMethod("maxZ").invoke(aabb) as Double

            val sizeX = maxX - minX
            val sizeY = maxY - minY
            val sizeZ = maxZ - minZ
            val maxSide = max(max(sizeX, sizeY), sizeZ)
            val spawnDist = (maxSide / 2.0) + 2.0 // половина корабля + 2 блока зазора

            val eyePos = player.eyePosition
            val lookVec = player.lookAngle.normalize()
            var targetPos = eyePos.add(lookVec.x * spawnDist, lookVec.y * spawnDist, lookVec.z * spawnDist)

            // Ограничение в 500 блоков — если spawnDist больше, то корабль появляется ровно на 500 блоках
            val maxAllowedDist = 500.0
            val actualDist = eyePos.distanceTo(targetPos)
            var finalSpawnDist = spawnDist
            if (actualDist > maxAllowedDist) {
                finalSpawnDist = maxAllowedDist
                targetPos = eyePos.add(lookVec.x * finalSpawnDist, lookVec.y * finalSpawnDist, lookVec.z * finalSpawnDist)
            }

            // Проверка на отсутствие препятствий для спавна корабля
            val context = net.minecraft.world.level.ClipContext(
                eyePos, targetPos,
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player
            )
            val hit: HitResult = level.clip(context)
            if (hit.type != HitResult.Type.MISS) {
                LOGGER.warn("[spawnShipFromNBT] Raycast blocked, can't spawn ship")
                return false
            }

            // Новая позиция — финальная, по направлению взгляда на расстоянии finalSpawnDist,
            // но по вертикали поднята на половину высоты корабля
            val halfShipHeight = (maxY - minY) / 2.0
            val spawnPos = Vec3(
                eyePos.x + lookVec.x * finalSpawnDist,
                eyePos.y + lookVec.y * finalSpawnDist + halfShipHeight,
                eyePos.z + lookVec.z * finalSpawnDist
            )

            try {
                val serverShip = ship as org.valkyrienskies.core.api.ships.ServerShip
                LOGGER.info("[spawnShipFromNBT] Teleporting ship $shipId to $spawnPos")
                ShipTeleporter.teleportShip(level, serverShip, spawnPos.x, spawnPos.y, spawnPos.z)
                try {
                    val isStaticField = serverShip.javaClass.getDeclaredField("isStatic")
                    isStaticField.isAccessible = true
                    isStaticField.setBoolean(serverShip, false)
                    LOGGER.info("[spawnShipFromNBT] Set isStatic=false for ship $shipId")
                } catch (e: Exception) {
                    LOGGER.warn("[spawnShipFromNBT] Could not set isStatic=false: ${e.message}")
                }
                return true
            } catch (e: Exception) {
                LOGGER.error("[spawnShipFromNBT] Exception during teleport: ${e.message}", e)
                return false
            }
        } catch (e: Exception) {
            LOGGER.error("[spawnShipFromNBT] Exception: ${e.message}", e)
            return false
        }
    }

    /**
     * Удаляет корабль из мира через vmod helper.
     */
    @JvmStatic
    fun removeShip(level: ServerLevel, ship: DockyardUpgradeLogic.ServerShipHandle) {
        try {
            val serverShip = ship.getServerShip() as org.valkyrienskies.core.api.ships.ServerShip
            LOGGER.info("[removeShip] Teleporting ship ${serverShip.id} to 0, -1000, 0")
            ShipTeleporter.teleportShip(level, serverShip, 0.0, -1000.0, 0.0)
        } catch (e: Exception) {
            LOGGER.error("[removeShip] Exception: ${e.message}", e)
        }
    }
}