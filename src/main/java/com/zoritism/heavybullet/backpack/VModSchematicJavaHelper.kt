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
            val schematicName = "hb_ship_${uuid.toString().replace("-", "")}"
            val schematicSaved = saveShipAsSchematic(player, ship, schematicName)
            if (schematicSaved) {
                nbt.putString("schematic_name", schematicName)
            } else {
                LOGGER.error("[VModSchematicJavaHelper] Failed to save schematic for ship id={}", id)
                return false
            }
            LOGGER.info("[VModSchematicJavaHelper] Saved ship id={} and schematic_name={} to NBT", id, schematicName)
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
        return try {
            if (nbt.contains("schematic_name")) {
                val schematicName = nbt.getString("schematic_name")
                spawnShipFromSchematic(player, schematicName)
            } else {
                LOGGER.error("[VModSchematicJavaHelper] NBT does not contain schematic_name, cannot spawn!")
                false
            }
        } catch (e: Exception) {
            LOGGER.error("[VModSchematicJavaHelper] Exception in spawnShipFromNBT: ", e)
            false
        }
    }

    /**
     * Сохраняет корабль как .nbt схему в папку world/vmod/schematics/ и кладёт имя схемы в NBT.
     * Имя схемы: hb_ship_<UUID>
     */
    @JvmStatic
    fun saveShipAsSchematic(
        player: ServerPlayer,
        ship: DockyardUpgradeLogic.ServerShipHandle,
        schematicName: String
    ): Boolean {
        LOGGER.info("[VModSchematicJavaHelper] saveShipAsSchematic called for player={} shipId={} schematic={}", player.gameProfile.name, ship.getId(), schematicName)
        try {
            val serverShip = ship.getServerShip()
            val aabb = serverShip.javaClass.getMethod("getWorldAABB").invoke(serverShip)
            val minX = (aabb.javaClass.getMethod("minX").invoke(aabb) as Double).toInt()
            val minY = (aabb.javaClass.getMethod("minY").invoke(aabb) as Double).toInt()
            val minZ = (aabb.javaClass.getMethod("minZ").invoke(aabb) as Double).toInt()
            val maxX = (aabb.javaClass.getMethod("maxX").invoke(aabb) as Double).toInt()
            val maxY = (aabb.javaClass.getMethod("maxY").invoke(aabb) as Double).toInt()
            val maxZ = (aabb.javaClass.getMethod("maxZ").invoke(aabb) as Double).toInt()
            val min = BlockPos(minX, minY, minZ)
            val max = BlockPos(maxX, maxY, maxZ)
            val level = player.serverLevel()

            // !!! ИСПРАВЛЕНИЕ: ищем object SchematicUtils, а не SchematicUtilsKt
            val schematicUtilsClass = Class.forName("net.spaceeye.vmod.schematic.SchematicUtils")
            val createSchematicFromWorld = schematicUtilsClass.getMethod(
                "createSchematicFromWorld",
                Class.forName("net.minecraft.world.level.Level"),
                BlockPos::class.java,
                BlockPos::class.java,
                String::class.java,
                java.lang.Boolean.TYPE
            )
            val schematicInstance = schematicUtilsClass.getField("INSTANCE").get(null)
            val schematic = createSchematicFromWorld.invoke(
                schematicInstance, level, min, max, schematicName, true
            )

            val schematicIOClass = Class.forName("net.spaceeye.vmod.schematic.SchematicIO")
            val saveSchematic = schematicIOClass.getMethod("saveSchematic", Class.forName("net.spaceeye.vmod.schematic.Schematic"), String::class.java)
            val schematicIOInstance = schematicIOClass.getField("INSTANCE").get(null)
            val schematicPath = getSchematicPath(level.server.serverDirectory.absolutePath, schematicName)
            saveSchematic.invoke(schematicIOInstance, schematic, schematicPath)
            LOGGER.info("[VModSchematicJavaHelper] Schematic '{}' saved at '{}'", schematicName, schematicPath)
            return true
        } catch (e: Exception) {
            LOGGER.error("[VModSchematicJavaHelper] Exception in saveShipAsSchematic: ", e)
            return false
        }
    }

    /**
     * Вставляет корабль из схемы в мир рядом с игроком.
     */
    @JvmStatic
    fun spawnShipFromSchematic(
        player: ServerPlayer,
        schematicName: String
    ): Boolean {
        LOGGER.info("[VModSchematicJavaHelper] spawnShipFromSchematic called for player={}, schematic={}", player.gameProfile.name, schematicName)
        try {
            val level = player.serverLevel()
            val schematicIOClass = Class.forName("net.spaceeye.vmod.schematic.SchematicIO")
            val loadSchematic = schematicIOClass.getMethod("loadSchematic", String::class.java)
            val schematicIOInstance = schematicIOClass.getField("INSTANCE").get(null)
            val schematicPath = getSchematicPath(level.server.serverDirectory.absolutePath, schematicName)
            val schematic = loadSchematic.invoke(schematicIOInstance, schematicPath)
            if (schematic == null) {
                LOGGER.error("[VModSchematicJavaHelper] Cannot load schematic: $schematicPath")
                return false
            }
            val schematicUtilsClass = Class.forName("net.spaceeye.vmod.schematic.SchematicUtils")
            val pasteSchematic = schematicUtilsClass.getMethod(
                "pasteSchematic",
                Class.forName("net.minecraft.world.level.Level"),
                Class.forName("net.spaceeye.vmod.schematic.Schematic"),
                BlockPos::class.java,
                Class.forName("net.minecraft.world.level.block.Mirror"),
                Class.forName("net.minecraft.world.level.block.Rotation")
            )
            val schematicUtilsInstance = schematicUtilsClass.getField("INSTANCE").get(null)
            val mirrorClass = Class.forName("net.minecraft.world.level.block.Mirror")
            val rotationClass = Class.forName("net.minecraft.world.level.block.Rotation")
            val mirrorNone = mirrorClass.getField("NONE").get(null)
            val rotationNone = rotationClass.getField("NONE").get(null)
            val pos = player.blockPosition().offset(1, 0, 0)
            pasteSchematic.invoke(
                schematicUtilsInstance,
                level, schematic, pos, mirrorNone, rotationNone
            )
            LOGGER.info("[VModSchematicJavaHelper] Schematic '{}' pasted at {}", schematicName, pos)
            return true
        } catch (e: Exception) {
            LOGGER.error("[VModSchematicJavaHelper] Exception in spawnShipFromSchematic: ", e)
            return false
        }
    }

    @JvmStatic
    fun removeShip(level: ServerLevel, ship: DockyardUpgradeLogic.ServerShipHandle) {
        LOGGER.info("[VModSchematicJavaHelper] removeShip called for ship id={}", ship.getId())
        try {
            val serverShip = ship.getServerShip()
            val serverShipClass = try {
                Class.forName("org.valkyrienskies.core.api.ships.ServerShip")
            } catch (e: Exception) {
                LOGGER.error("[VModSchematicJavaHelper] ServerShip class not found!", e)
                return
            }
            val castedServerShip = if (serverShipClass.isInstance(serverShip)) {
                serverShip
            } else {
                LOGGER.error("[VModSchematicJavaHelper] serverShip is not instance of ServerShip!")
                return
            }
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
                teleportMethod.invoke(null, level, castedServerShip, 0.0, -1000.0, 0.0)
                LOGGER.info("[VModSchematicJavaHelper] Ship id={} teleported to void.", ship.getId())
            } else {
                LOGGER.warn("[VModSchematicJavaHelper] Teleport class not found, can't remove ship!")
            }
        } catch (e: Exception) {
            LOGGER.error("[VModSchematicJavaHelper] Exception in removeShip: ", e)
        }
    }

    private fun getSchematicPath(serverDir: String, schematicName: String): String {
        val folder = java.io.File(serverDir, "vmod/schematics")
        folder.mkdirs()
        return java.io.File(folder, "$schematicName.nbt").absolutePath
    }
}