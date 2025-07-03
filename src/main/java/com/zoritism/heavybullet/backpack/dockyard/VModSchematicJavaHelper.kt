package com.zoritism.heavybullet.backpack.dockyard

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.max
import kotlin.math.sqrt

object VModSchematicJavaHelper {

    /**
     * Поиск корабля по позиции.
     * @return ServerShipHandle если найден, иначе null.
     */
    @JvmStatic
    fun findServerShip(level: ServerLevel, pos: BlockPos): DockyardUpgradeLogic.ServerShipHandle? {
        return try {
            val pipelineClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
            val getVsPipeline = pipelineClass.getMethod("getVsPipeline", Class.forName("net.minecraft.server.MinecraftServer"))
            val pipeline = getVsPipeline.invoke(null, level.server)
            if (pipeline == null) {
                return null
            }
            val shipWorld = pipeline.javaClass.getMethod("getShipWorld").invoke(pipeline)
            if (shipWorld == null) {
                return null
            }
            val allShips = shipWorld.javaClass.getMethod("getAllShips").invoke(shipWorld) as? Iterable<*>
                ?: return null
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
                        return object : DockyardUpgradeLogic.ServerShipHandle {
                            override fun getServerShip(): Any = ship
                            override fun getId(): Long = id
                        }
                    }
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Сохранить корабль в capability игрока (глобально, а не в рюкзаке!)
     * @param checkDistance - если true, проверять расстояние до игрока (item mode); если false, не проверять (block mode)
     * @return true если успешно, иначе false
     */
    @JvmStatic
    fun tryStoreShipToPlayerDockyard(
        level: ServerLevel,
        player: ServerPlayer,
        uuid: UUID,
        ship: DockyardUpgradeLogic.ServerShipHandle,
        nbt: CompoundTag,
        slot: Int,
        checkDistance: Boolean = true
    ): Boolean {
        // Не даём забирать корабль если у игрока уже есть в этом слоте
        val dockyardData = PlayerDockyardDataUtil.getOrCreate(player).dockyardData
        val key = "ship$slot"
        if (dockyardData.contains(key)) {
            return false
        }

        // Проверяем расстояние до игрока (максимум 4 блока), только если включено
        if (checkDistance) {
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
                if (dist > 4.0) {
                    return false
                }
            }
        }

        // Сохраняем в NBT
        val saveResult = saveShipToNBT(level, player, uuid, ship, nbt)
        if (!saveResult) return false

        // Только если успешно — сохраняем в capability игрока
        dockyardData.put(key, nbt)

        // Удаляем из мира, если только что успешно сохранили в NBT и capability
        val removeResult = try {
            removeShip(level, ship)
            true
        } catch (e: Exception) {
            dockyardData.remove(key)
            false
        }
        if (!removeResult) return false

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
        return try {
            val serverShip = ship.getServerShip()
            val id = serverShip.javaClass.getMethod("getId").invoke(serverShip) as Long
            nbt.putLong("vs_ship_id", id)
            // Добавим ship_name если есть (через reflection, если доступно)
            try {
                val getName = serverShip.javaClass.getMethod("getName")
                val nameObj = getName.invoke(serverShip)
                if (nameObj != null) {
                    val nameStr = nameObj.toString()
                    nbt.putString("vs_ship_name", nameStr)
                }
            } catch (_: Exception) {
                // имя не найдено - не критично
            }
            true
        } catch (e: Exception) {
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
        // Проверяем что в capability есть корабль
        val dockyardData = PlayerDockyardDataUtil.getOrCreate(player).dockyardData
        val key = "ship$slot"
        if (!dockyardData.contains(key)) {
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
                        return false // Уже есть такой корабль
                    }
                }
            }
        }
        // Спавним
        val success = spawnShipFromNBT(level, player, uuid, player.position(), nbt)
        if (success) {
            dockyardData.remove(key)
            return true
        }
        return false
    }

    /**
     * Спавн корабля из NBT
     * @param forceExactPos если true — использовать pos без проверок, иначе поведение по взгляду игрока
     * @return true если успешно, иначе false
     */
    @JvmStatic
    fun spawnShipFromNBT(
        level: ServerLevel,
        player: ServerPlayer,
        uuid: UUID,
        pos: Vec3,
        nbt: CompoundTag,
        forceExactPos: Boolean = false
    ): Boolean {
        try {
            if (!nbt.contains("vs_ship_id")) {
                return false
            }
            val shipId = nbt.getLong("vs_ship_id")
            val pipelineClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
            val getVsPipeline = pipelineClass.getMethod("getVsPipeline", Class.forName("net.minecraft.server.MinecraftServer"))
            val pipeline = getVsPipeline.invoke(null, level.server)
            if (pipeline == null) {
                return false
            }
            val shipWorld = pipeline.javaClass.getMethod("getShipWorld").invoke(pipeline)
            if (shipWorld == null) {
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

            val spawnPos: Vec3 = if (forceExactPos) {
                pos
            } else {
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
                    return false
                }

                // Новая позиция — финальная, по направлению взгляда на расстоянии finalSpawnDist,
                // но по вертикали поднята на половину высоты корабля
                val halfShipHeight = (maxY - minY) / 2.0
                Vec3(
                    eyePos.x + lookVec.x * finalSpawnDist,
                    eyePos.y + lookVec.y * finalSpawnDist + halfShipHeight,
                    eyePos.z + lookVec.z * finalSpawnDist
                )
            }

            try {
                val serverShip = ship as org.valkyrienskies.core.api.ships.ServerShip
                ShipTeleporter.teleportShip(level, serverShip, spawnPos.x, spawnPos.y, spawnPos.z)
                try {
                    val isStaticField = serverShip.javaClass.getDeclaredField("isStatic")
                    isStaticField.isAccessible = true
                    isStaticField.setBoolean(serverShip, false)
                } catch (e: Exception) {
                    // ignore
                }
                return true
            } catch (e: Exception) {
                return false
            }
        } catch (e: Exception) {
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
            ShipTeleporter.teleportShip(level, serverShip, 0.0, -1000.0, 0.0)
        } catch (e: Exception) {
            // ignore
        }
    }
}