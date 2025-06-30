package com.zoritism.heavybullet.backpack

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
            if (pipeline == null) return null
            val shipWorld = pipeline.javaClass.getMethod("getShipWorld").invoke(pipeline)
            if (shipWorld == null) return null
            val allShips = shipWorld.javaClass.getMethod("getAllShips").invoke(shipWorld) as? Iterable<*> ?: return null
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
            null
        } catch (e: Exception) {
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
        // Не даём забирать корабль если в рюкзаке уже есть другой
        if (com.zoritism.heavybullet.backpack.DockyardDataHelper.hasShipInBackpack(backpack)) {
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
            if (dist > 4.0) {
                return false
            }
        }

        // Сохраняем в NBT
        val saveResult = saveShipToNBT(level, player, uuid, ship, nbt)
        if (!saveResult) return false

        // Только если успешно — сохраняем в рюкзак
        com.zoritism.heavybullet.backpack.DockyardDataHelper.saveShipToBackpack(backpack, nbt)

        // Удаляем из мира, если только что успешно сохранили в NBT и записали в рюкзак
        val removeResult = try {
            removeShip(level, ship)
            true
        } catch (_: Exception) {
            // Если не удалили — очищаем NBT в рюкзаке, чтобы не было дюпа
            com.zoritism.heavybullet.backpack.DockyardDataHelper.clearShipFromBackpack(backpack)
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
            true
        } catch (e: Exception) {
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
        // Проверяем что в рюкзаке есть корабль
        if (!com.zoritism.heavybullet.backpack.DockyardDataHelper.hasShipInBackpack(backpack)) {
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
                    if (ship != null) return false // Уже есть такой корабль
                }
            }
        }
        // Спавним
        val success = spawnShipFromNBT(level, player, uuid, player.position(), nbt)
        if (success) {
            com.zoritism.heavybullet.backpack.DockyardDataHelper.clearShipFromBackpack(backpack)
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
        try {
            if (!nbt.contains("vs_ship_id")) {
                return false
            }
            val shipId = nbt.getLong("vs_ship_id")
            val pipelineClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt")
            val getVsPipeline = pipelineClass.getMethod("getVsPipeline", Class.forName("net.minecraft.server.MinecraftServer"))
            val pipeline = getVsPipeline.invoke(null, level.server)
            if (pipeline == null) return false
            val shipWorld = pipeline.javaClass.getMethod("getShipWorld").invoke(pipeline)
            if (shipWorld == null) return false

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
            if (ship == null) return false

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

            val serverShipClass = Class.forName("org.valkyrienskies.core.api.ships.ServerShip")
            val teleportClass = try {
                Class.forName("com.ForgeStove.bottle_ship.Teleport")
            } catch (e: Exception) {
                null
            }
            if (teleportClass != null) {
                val teleportMethod = teleportClass.getMethod(
                    "teleportShip",
                    ServerLevel::class.java,
                    serverShipClass,
                    java.lang.Double.TYPE,
                    java.lang.Double.TYPE,
                    java.lang.Double.TYPE
                )
                teleportMethod.invoke(null, level, ship, spawnPos.x, spawnPos.y, spawnPos.z)
                try {
                    val isStaticField = serverShipClass.getDeclaredField("isStatic")
                    isStaticField.isAccessible = true
                    isStaticField.setBoolean(ship, false)
                } catch (_: Exception) {}
                return true
            } else {
                return false
            }
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Удаляет корабль из мира через vmod helper.
     */
    @JvmStatic
    fun removeShip(level: ServerLevel, ship: DockyardUpgradeLogic.ServerShipHandle) {
        try {
            val serverShip = ship.getServerShip()
            val serverShipClass = try {
                Class.forName("org.valkyrienskies.core.api.ships.ServerShip")
            } catch (_: Exception) {
                return
            }
            val castedServerShip = if (serverShipClass.isInstance(serverShip)) {
                serverShip
            } else {
                return
            }
            val teleportClass = try {
                Class.forName("com.ForgeStove.bottle_ship.Teleport")
            } catch (_: Exception) {
                null
            }
            if (teleportClass != null) {
                val teleportMethod = teleportClass.getMethod(
                    "teleportShip",
                    ServerLevel::class.java,
                    serverShipClass,
                    java.lang.Double.TYPE,
                    java.lang.Double.TYPE,
                    java.lang.Double.TYPE
                )
                teleportMethod.invoke(null, level, castedServerShip, 0.0, -1000.0, 0.0)
            }
        } catch (_: Exception) {
        }
    }
}