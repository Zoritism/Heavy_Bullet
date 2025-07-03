package com.zoritism.heavybullet.backpack.dockyard;

import com.zoritism.heavybullet.network.S2CSyncDockyardClientPacket;
import com.zoritism.heavybullet.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DockyardUpgradeLogic {

    private static final int ANIMATION_TICKS = 200; // 10 секунд на 20 TPS

    public static void handleBottleShipClick(ServerPlayer player, boolean release) {
        handleDockyardShipClick(player, 0, release, false, 0L);
    }

    public static void handleDockyardShipClick(ServerPlayer player, int slotIndex, boolean release, boolean blockMode, long blockPosLong) {
        BlockEntity blockEntity = null;
        BlockPos blockPos = null;
        Level level = player.level();

        if (blockMode && blockPosLong != 0L && level instanceof ServerLevel serverLevel) {
            blockPos = BlockPos.of(blockPosLong);
            blockEntity = serverLevel.getBlockEntity(blockPos);
        }

        final boolean isOpenedAsBlock = blockEntity != null;

        // =============== BLOCKENTITY LOGIC ===============
        if (isOpenedAsBlock) {
            if (release) {
                PlayerDockyardData data = PlayerDockyardDataUtil.getOrCreate(player);
                CompoundTag dockyardData = data.getDockyardData();
                String key = "ship" + slotIndex;
                if (dockyardData.contains(key)) {
                    CompoundTag shipNbt = dockyardData.getCompound(key);
                    boolean restored = false;
                    if (shipNbt != null && !shipNbt.isEmpty()) {
                        restored = releaseShipFromBlock(player, blockEntity, shipNbt);
                    }
                    if (restored) {
                        dockyardData.remove(key);
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_released"), true);
                        syncDockyardToClient(player);
                    } else {
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.restore_failed"), true);
                    }
                } else {
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_stored"), true);
                }
                return;
            }

            PlayerDockyardData data = PlayerDockyardDataUtil.getOrCreate(player);
            CompoundTag dockyardData = data.getDockyardData();
            String key = "ship" + slotIndex;
            if (dockyardData.contains(key)) {
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.already_has_ship"), true);
                return;
            }

            ServerLevel serverLevel = (ServerLevel) level;
            BlockPos pos = blockEntity.getBlockPos();
            ServerShipHandle ship = findShipAboveBlock(serverLevel, pos, 20.0);

            if (ship != null) {
                CompoundTag persistent = getOrCreatePersistentData(blockEntity);
                boolean isActive = persistent.getBoolean("DockyardProcessActive");
                int processSlot = persistent.getInt("DockyardProcessSlot");
                if (isActive && processSlot == slotIndex) {
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.process_already_running"), true);
                    return;
                }
                DockyardUpgradeWrapper.startInsertShipProcess(blockEntity, slotIndex, ship.getId(), player.getUUID());
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.process_started"), true);
            } else {
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_found"), true);
            }
            return;
        }

        // =============== PLAYER CAPABILITY LOGIC ===============
        if (player != null) {
            PlayerDockyardData data = PlayerDockyardDataUtil.getOrCreate(player);
            CompoundTag dockyardData = data.getDockyardData();

            String key = "ship" + slotIndex;

            if (release) {
                if (dockyardData.contains(key)) {
                    CompoundTag shipNbt = dockyardData.getCompound(key);
                    boolean restored = false;
                    if (shipNbt != null && !shipNbt.isEmpty()) {
                        restored = spawnShipFromNbt(player, shipNbt);
                    }
                    if (restored) {
                        dockyardData.remove(key);
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_released"), true);
                        syncDockyardToClient(player);
                    } else {
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.restore_failed"), true);
                    }
                } else {
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_stored"), true);
                }
                return;
            }

            if (dockyardData.contains(key)) {
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.already_has_ship"), true);
                return;
            }
            ServerShipHandle ship = findShipPlayerIsLookingAt(player, 4.0);
            if (ship != null) {
                CompoundTag shipNbt = new CompoundTag();
                boolean result = saveShipToNbt(player.serverLevel(), ship, shipNbt, player);
                if (result) {
                    dockyardData.put(key, shipNbt);
                    boolean removed = removeShipFromWorld(ship, player.serverLevel());
                    if (removed) {
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_stored"), true);
                        syncDockyardToClient(player);
                    } else {
                        dockyardData.remove(key);
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.remove_failed"), true);
                        syncDockyardToClient(player);
                    }
                } else {
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.save_failed"), true);
                }
            } else {
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_found"), true);
            }
            return;
        }

        if (player != null) {
            player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_backpack_found"), true);
        }
    }

    public static void handleDockyardShipClick(ServerPlayer player, int slotIndex, boolean release) {
        boolean blockMode = false;
        long blockPosLong = 0L;
        if (player != null && player.containerMenu != null) {
            try {
                java.lang.reflect.Method getUpgradeContainer = player.containerMenu.getClass().getMethod("getUpgradeContainer");
                Object cont = getUpgradeContainer.invoke(player.containerMenu);
                if (cont instanceof DockyardUpgradeContainer duc) {
                    blockMode = duc.isBlockMode();
                    BlockPos pos = duc.getOpenedBlockPos();
                    if (blockMode && pos != null) {
                        blockPosLong = pos.asLong();
                    }
                }
            } catch (Exception ignored) {}
        }
        handleDockyardShipClick(player, slotIndex, release, blockMode, blockPosLong);
    }

    public static void syncDockyardToClient(ServerPlayer player) {
        PlayerDockyardData data = PlayerDockyardDataUtil.getOrCreate(player);
        CompoundTag dockyard = data.getDockyardData();
        Map<Integer, CompoundTag> slots = new HashMap<>();
        for (int i = 0; i < 2; ++i) {
            String key = "ship" + i;
            if (dockyard.contains(key)) {
                slots.put(i, dockyard.getCompound(key).copy());
            }
        }

        boolean blockMode = false;
        long blockPos = 0L;

        if (player.containerMenu != null) {
            try {
                java.lang.reflect.Method getUpgradeContainer = player.containerMenu.getClass().getMethod("getUpgradeContainer");
                Object cont = getUpgradeContainer.invoke(player.containerMenu);
                if (cont instanceof DockyardUpgradeContainer duc) {
                    blockMode = duc.isBlockMode();
                    BlockPos pos = duc.getOpenedBlockPos();
                    if (blockMode && pos != null) {
                        blockPos = pos.asLong();
                    }
                }
            } catch (Exception ignored) {}
        }

        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncDockyardClientPacket(slots, blockMode, blockPos)
        );
    }

    @Nullable
    private static ServerShipHandle findShipPlayerIsLookingAt(ServerPlayer player, double maxDistance) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 target = eye.add(look.x * maxDistance, look.y * maxDistance, look.z * maxDistance);
        HitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(
                eye, target, net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player
        ));

        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return null;
        }
        Vec3 pos = hit.getLocation();
        double dist = eye.distanceTo(pos);

        if (dist > maxDistance + 0.01) {
            return null;
        }

        ServerLevel level = player.serverLevel();

        try {
            return VModSchematicJavaHelper.findServerShip(level, BlockPos.containing(pos.x, pos.y, pos.z));
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean saveShipToNbt(ServerLevel level, ServerShipHandle ship, CompoundTag nbt, @Nullable ServerPlayer player) {
        if (ship == null) {
            return false;
        }
        UUID uuid = UUID.randomUUID();

        try {
            return VModSchematicJavaHelper.saveShipToNBT(level, player, uuid, ship, nbt);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean saveShipToNbtPublic(ServerLevel level, ServerShipHandle ship, CompoundTag nbt, @Nullable ServerPlayer player) {
        return saveShipToNbt(level, ship, nbt, player);
    }

    public static boolean removeShipFromWorldPublic(ServerShipHandle ship, ServerLevel level) {
        return removeShipFromWorld(ship, level);
    }

    /**
     * Выпустить корабль из блока-рюкзака строго над ним (block mode).
     * Корректно вычисляет координату спавна так, чтобы minY корабля оказался ровно на blockY+5.0.
     */
    private static boolean releaseShipFromBlock(ServerPlayer player, BlockEntity blockEntity, CompoundTag shipNbt) {
        ServerLevel level = player.serverLevel();
        BlockPos blockPos = blockEntity.getBlockPos();

        double x = blockPos.getX() + 0.5;
        double y = blockPos.getY() + 0.5;
        double z = blockPos.getZ() + 0.5;

        // ВСЕГДА только из NBT (гарантируемые значения с упаковки корабля)
        double minY = shipNbt.contains("aabb_minY") ? shipNbt.getDouble("aabb_minY") : 0.0;
        double maxY = shipNbt.contains("aabb_maxY") ? shipNbt.getDouble("aabb_maxY") : 0.0;
        double minX = shipNbt.contains("aabb_minX") ? shipNbt.getDouble("aabb_minX") : 0.0;
        double maxX = shipNbt.contains("aabb_maxX") ? shipNbt.getDouble("aabb_maxX") : 0.0;
        double minZ = shipNbt.contains("aabb_minZ") ? shipNbt.getDouble("aabb_minZ") : 0.0;
        double maxZ = shipNbt.contains("aabb_maxZ") ? shipNbt.getDouble("aabb_maxZ") : 0.0;
        double length = shipNbt.contains("aabb_length") ? shipNbt.getDouble("aabb_length") : (maxX - minX);
        double width = shipNbt.contains("aabb_width") ? shipNbt.getDouble("aabb_width") : (maxZ - minZ);
        double height = shipNbt.contains("aabb_height") ? shipNbt.getDouble("aabb_height") : (maxY - minY);

        // Центр корабля
        double centerY = (minY + maxY) / 2.0;
        // Смещение: насколько нужно сдвинуть корабль, чтобы его minY оказался на blockY+5.0
        double offsetY = (y + 5.0) - minY;
        // Итоговая точка спавна: по центру X/Z, по Y = centerY + offsetY
        double spawnY = centerY + offsetY;

        Vec3 spawnPos = new Vec3(x, spawnY, z);

        // 1. Рейтрейс вверх на 50 блоков
        Vec3 rayStart = new Vec3(x, y + 1.0, z);
        Vec3 rayEnd = new Vec3(x, y + 50.0, z);
        ClipContext clipContext = new ClipContext(rayStart, rayEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
        HitResult hit = level.clip(clipContext);
        boolean canSpawn = hit == null || hit.getType() == HitResult.Type.MISS;
        if (!canSpawn) {
            player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.spawn_blocked"), true);
            return false;
        }

        // Для красивых частиц — рамка вокруг корабля
        AABB aabb = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        UUID uuid = UUID.randomUUID();
        boolean result = VModSchematicJavaHelper.spawnShipFromNBT(level, player, uuid, spawnPos, shipNbt, true);

        if (result) {
            spawnFlameParticleCloud(level, aabb, spawnPos);
        }

        return result;
    }

    private static Object getVsShipById(ServerLevel level, long shipId) {
        try {
            Class<?> pipelineClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            Method getVsPipeline = pipelineClass.getMethod("getVsPipeline", Class.forName("net.minecraft.server.MinecraftServer"));
            Object pipeline = getVsPipeline.invoke(null, level.getServer());
            if (pipeline == null) return null;
            Object shipWorld = pipeline.getClass().getMethod("getShipWorld").invoke(pipeline);
            if (shipWorld == null) return null;
            Method getShipById = shipWorld.getClass().getMethod("getShipById", long.class);
            Object ship = getShipById.invoke(shipWorld, shipId);
            return ship;
        } catch (Exception e) {
            return null;
        }
    }

    private static void spawnFlameParticleCloud(ServerLevel level, @Nullable AABB aabb, Vec3 spawnPos) {
        double minX, maxX, minY, maxY, minZ, maxZ;
        if (aabb != null) {
            minX = aabb.minX - 5.0; maxX = aabb.maxX + 5.0;
            minY = aabb.minY - 5.0; maxY = aabb.maxY + 5.0;
            minZ = aabb.minZ - 5.0; maxZ = aabb.maxZ + 5.0;
        } else {
            minX = spawnPos.x - 10.0; maxX = spawnPos.x + 10.0;
            minY = spawnPos.y - 5.0; maxY = spawnPos.y + 15.0;
            minZ = spawnPos.z - 10.0; maxZ = spawnPos.z + 10.0;
        }
        int totalParticles = 350;
        for (int i = 0; i < totalParticles; i++) {
            double px = minX + Math.random() * (maxX - minX);
            double py = minY + Math.random() * (maxY - minY);
            double pz = minZ + Math.random() * (maxZ - minZ);
            double dx = (Math.random() - 0.5) * 0.55;
            double dy = 0.25 + Math.random() * 0.35;
            double dz = (Math.random() - 0.5) * 0.55;
            level.sendParticles(ParticleTypes.FLAME, px, py, pz, 0, dx, dy, dz, 1.35);
        }
    }

    private static boolean spawnShipFromNbt(ServerPlayer player, CompoundTag nbt) {
        ServerLevel level = player.serverLevel();
        UUID uuid = UUID.randomUUID();
        Vec3 pos = player.position();

        try {
            return VModSchematicJavaHelper.spawnShipFromNBT(level, player, uuid, pos, nbt, false);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean removeShipFromWorld(ServerShipHandle ship, ServerLevel level) {
        if (ship == null || level == null) {
            return false;
        }
        try {
            VModSchematicJavaHelper.removeShip(level, ship);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public interface ServerShipHandle {
        Object getServerShip();
        long getId();
    }

    @Nullable
    public static ServerShipHandle findShipAboveBlock(ServerLevel level, BlockPos blockPos, double maxDistance) {
        Vec3 from = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 1.2, blockPos.getZ() + 0.5);
        int steps = (int) Math.ceil(maxDistance);
        for (int i = 0; i <= steps; i++) {
            int y = (int) (from.y + i);
            BlockPos pos = new BlockPos((int) from.x, y, (int) from.z);
            ServerShipHandle ship = null;
            try {
                ship = VModSchematicJavaHelper.findServerShip(level, pos);
            } catch (Throwable t) {
                // ignore
            }
            if (ship != null) {
                return ship;
            }
        }
        return null;
    }

    private static CompoundTag getOrCreatePersistentData(BlockEntity blockEntity) {
        CompoundTag beTag = blockEntity.saveWithFullMetadata();
        CompoundTag persistentData;
        if (beTag.contains("ForgeData", 10)) {
            persistentData = beTag.getCompound("ForgeData");
        } else {
            persistentData = new CompoundTag();
            beTag.put("ForgeData", persistentData);
        }
        return persistentData;
    }

    public static void startBlockShipInsert(ServerLevel level, BlockPos blockPos, int slotIndex) {
        // placeholder - не используется, вся логика перенесена в startInsertShipProcess
    }
}