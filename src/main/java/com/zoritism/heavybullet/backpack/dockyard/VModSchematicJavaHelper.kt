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
     * Сохранить корабль в NBT (забрать в рюкзак)
     * @return true если успешно, иначе false
     */
    @JvmStatic
    fun tryStoreShipToBackpack(
        level: ServerLevel,
        player: ServerPlayer,
        backpack: net.minecraft.world.item.ItemStack,
        uuid: UUID,
        ship: DockyardUpgradeLogic.ServerShipHandle,
        nbt: CompoundTag
    ): Boolean {
        LOGGER.info("[tryStoreShipToBackpack] Called for player ${player.name.string}")
        // Не даём забирать корабль если в рюкзаке уже есть другой
        if (DockyardDataHelper.hasShipInBackpack(backpack)) {
            LOGGER.info("[tryStoreShipToBackpack] Backpack already contains a ship")
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
            // Реально ближайшее расстояние до бокса, а не центра
            val dx = maxOf(minX - playerPos.x, 0.0, playerPos.x - maxX)
            val dy = maxOf(minY - playerPos.y, 0.0, playerPos.y - maxY)
            val dz = maxOf(minZ - playerPos.z, 0.0, playerPos.z - maxZ)
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            LOGGER.info("[tryStoreShipToBackpack] Distance to ship: $dist")
            if (dist > 4.0) {
                LOGGER.warn("[tryStoreShipToBackpack] Ship is too far from player")
                return false
            }
        }

        // Сохраняем в NBT
        val saveResult = saveShipToNBT(level, player, uuid, ship, nbt)
        LOGGER.info("[tryStoreShipToBackpack] Ship saved to NBT: $saveResult")
        if (!saveResult) return false

        // Только если успешно — сохраняем в рюкзак
        DockyardDataHelper.saveShipToBackpack(backpack, nbt)
        LOGGER.info("[tryStoreShipToBackpack] Ship saved to backpack NBT")

        // Удаляем из мира, если только что успешно сохранили в NBT и записали в рюкзак
        val removeResult = try {
            removeShip(level, ship)
            LOGGER.info("[tryStoreShipToBackpack] Ship removed from world")
            true
        } catch (e: Exception) {
            LOGGER.error("[tryStoreShipToBackpack] Exception while removing ship: ${e.message}", e)
            // Если не удалили — очищаем NBT в рюкзаке, чтобы не было дюпа
            DockyardDataHelper.clearShipFromBackpack(backpack)
            false
        }
        if (!removeResult) return false

        LOGGER.info("[tryStoreShipToBackpack] Done, ship picked up successfully")
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
     * Спавн корабля из NBT (в мир из рюкзака)
     * @return true если успешно, иначе false
     */
    @JvmStatic
    fun trySpawnShipFromBackpack(
        level: ServerLevel,
        player: ServerPlayer,
        backpack: net.minecraft.world.item.ItemStack,
        uuid: UUID,
        nbt: CompoundTag
    ): Boolean {
        LOGGER.info("[trySpawnShipFromBackpack] Called for player ${player.name.string}")
        // Проверяем что в рюкзаке есть корабль
        if (!DockyardDataHelper.hasShipInBackpack(backpack)) {
            LOGGER.info("[trySpawnShipFromBackpack] No ship in backpack")
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
                        LOGGER.warn("[trySpawnShipFromBackpack] Ship with id $shipId already exists in world")
                        return false // Уже есть такой корабль
                    }
                }
            }
        }
        // Спавним
        val success = spawnShipFromNBT(level, player, uuid, player.position(), nbt)
        LOGGER.info("[trySpawnShipFromBackpack] Spawn result: $success")
        if (success) {
            DockyardDataHelper.clearShipFromBackpack(backpack)
            LOGGER.info("[trySpawnShipFromBackpack] Cleared ship from backpack after spawn")
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