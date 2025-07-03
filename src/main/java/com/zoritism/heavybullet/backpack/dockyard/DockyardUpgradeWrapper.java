package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.particles.ParticleTypes;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

public class DockyardUpgradeWrapper extends UpgradeWrapperBase<DockyardUpgradeWrapper, DockyardUpgradeItem> implements ITickableUpgrade {

    private static final Logger LOGGER = LogManager.getLogger("HeavyBullet/DockyardUpgradeWrapper");
    private static final Logger LOGIC_LOGGER = LogManager.getLogger("HeavyBullet/DockyardUpgradeLogic");

    private static final String NBT_PROCESS_ACTIVE = "DockyardProcessActive";
    private static final String NBT_PROCESS_TICKS = "DockyardProcessTicks";
    private static final String NBT_PROCESS_SHIP_ID = "DockyardProcessShipId";
    private static final String NBT_PROCESS_SLOT = "DockyardProcessSlot";
    private static final String NBT_PROCESS_PLAYER_UUID = "DockyardProcessPlayerUUID";
    private static final int ANIMATION_TICKS = 200; // 10 секунд на 20 TPS
    private static final int SHIP_RAY_DIST = 15;

    // Для анимированных частиц, ключ - blockPos.asLong()
    private static final Map<Long, List<ActiveParticle>> FLYING_PARTICLES = new HashMap<>();
    private static final Map<Long, ParticleProcessState> PARTICLE_PROCESS = new HashMap<>();

    protected DockyardUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
        super(storageWrapper, upgrade, upgradeSaveHandler);
    }

    @Override
    public void tick(@Nullable Entity entity, Level level, BlockPos blockPos) {
        if (level.isClientSide) return;

        if (entity == null && blockPos != null) {
            BlockEntity be = getStorageBlockEntity(level, blockPos);
            if (be == null) {
                cleanupFlyingParticles(blockPos);
                PARTICLE_PROCESS.remove(blockPos.asLong());
                return;
            }
            CompoundTag tag = getPersistentData(be);

            syncBackpackShipsToBlock(be);

            if (tag.getBoolean(NBT_PROCESS_ACTIVE)) {
                int ticks = tag.getInt(NBT_PROCESS_TICKS);
                long shipId = tag.getLong(NBT_PROCESS_SHIP_ID);
                int slot = tag.getInt(NBT_PROCESS_SLOT);

                ticks++;
                tag.putInt(NBT_PROCESS_TICKS, ticks);

                if (ticks == 1 || ticks % 20 == 0) {
                    int secondsLeft = Math.max((ANIMATION_TICKS - ticks) / 20, 0);
                    LOGIC_LOGGER.info("[DockyardUpgradeLogic] seconds_left: {}", secondsLeft);
                }

                ServerLevel serverLevel = (ServerLevel) level;
                DockyardUpgradeLogic.ServerShipHandle ship = DockyardUpgradeLogic.findShipAboveBlock(serverLevel, blockPos, SHIP_RAY_DIST);
                boolean shipValid = ship != null && ship.getId() == shipId;
                if (!shipValid) {
                    LOGIC_LOGGER.warn("[DockyardUpgradeLogic] Ship not found or ID mismatch at process end. Aborting insert for slot {}", slot);
                    clearProcess(tag, be);
                    cleanupFlyingParticles(blockPos);
                    PARTICLE_PROCESS.remove(blockPos.asLong());
                    return;
                }

                int animationTicks = Math.min(ticks, ANIMATION_TICKS);
                double process = animationTicks / (double) ANIMATION_TICKS; // 0.0 -> 1.0

                tickProcessParticles(serverLevel, blockPos, ship, process);

                if (ticks >= ANIMATION_TICKS) {
                    CompoundTag shipNbt = new CompoundTag();
                    UUID playerUuid = tag.hasUUID(NBT_PROCESS_PLAYER_UUID) ? tag.getUUID(NBT_PROCESS_PLAYER_UUID) : null;
                    ServerPlayer player = null;
                    if (playerUuid != null) {
                        player = serverLevel.getServer().getPlayerList().getPlayer(playerUuid);
                    }

                    boolean result = false;
                    String failReason = null;

                    if (player != null) {
                        try {
                            result = VModSchematicJavaHelper.tryStoreShipToPlayerDockyard(
                                    serverLevel,
                                    player,
                                    UUID.randomUUID(),
                                    ship,
                                    shipNbt,
                                    slot,
                                    false
                            );
                            if (result) {
                                LOGIC_LOGGER.info("[DockyardUpgradeLogic] Ship stored to player {} slot {}", player.getGameProfile().getName(), slot);
                            } else {
                                String key = "ship" + slot;
                                if (player.getCapability(PlayerDockyardDataProvider.DOCKYARD_CAP).map(cap -> cap.getDockyardData().contains(key)).orElse(false)) {
                                    failReason = "slot already occupied";
                                } else {
                                    failReason = "unknown reason (see tryStoreShipToPlayerDockyard)";
                                }
                                LOGIC_LOGGER.warn("[DockyardUpgradeLogic] Failed to save ship to player {} slot {}: {}", player.getGameProfile().getName(), slot, failReason);
                            }
                        } catch (Exception e) {
                            LOGIC_LOGGER.error("[DockyardUpgradeLogic] Exception during tryStoreShipToPlayerDockyard: {}", e.getMessage(), e);
                        }
                    } else {
                        LOGIC_LOGGER.warn("[DockyardUpgradeLogic] No player UUID found or player offline, ship NOT stored");
                    }

                    if (result) {
                        DockyardUpgradeLogic.removeShipFromWorldPublic(ship, serverLevel);
                    } else {
                        LOGIC_LOGGER.warn("[DockyardUpgradeLogic] Ship was NOT removed from world due to failed save.");
                    }

                    clearProcess(tag, be);
                    cleanupFlyingParticles(blockPos);
                    PARTICLE_PROCESS.remove(blockPos.asLong());
                }
            } else {
                cleanupFlyingParticles(blockPos);
                PARTICLE_PROCESS.remove(blockPos.asLong());
            }
        }
    }

    // ---------------- ПАРТИКЛЫ ----------------

    private void tickProcessParticles(ServerLevel level, BlockPos blockPos, DockyardUpgradeLogic.ServerShipHandle ship, double process) {
        long key = blockPos.asLong();
        if (!PARTICLE_PROCESS.containsKey(key)) {
            PARTICLE_PROCESS.put(key, new ParticleProcessState());
        }

        // Количество и скорость
        double minPercent = 0.1; // 10%
        double maxPercent = 1.0;
        double percent = minPercent + (maxPercent - minPercent) * process;

        double minSpeed = 0.5;
        double maxSpeed = 2.0;
        double speed = minSpeed + (maxSpeed - minSpeed) * process;

        Object vsShip = ship.getServerShip();
        AABB aabb = tryGetShipAABB(vsShip);

        double margin = 2.0;
        double targetX = blockPos.getX() + 0.5;
        double targetY = blockPos.getY() + 0.7;
        double targetZ = blockPos.getZ() + 0.5;

        int particleCount;
        if (aabb != null) {
            double sizeX = aabb.maxX - aabb.minX + 2 * margin;
            double sizeY = aabb.maxY - aabb.minY + 2 * margin;
            double sizeZ = aabb.maxZ - aabb.minZ + 2 * margin;
            // Геометрическая прогрессия: particleCount = min + (max-min)*((volume/vol0)^exp)
            double volume = Math.max(sizeX * sizeY * sizeZ, 1.0);
            double minParticles = 6;
            double maxParticles = 90;
            double vol0 = 100;
            double exp = 0.7;
            double norm = Math.pow(Math.min(volume / vol0, 1.0), exp);
            particleCount = (int) (minParticles + (maxParticles - minParticles) * norm);
        } else {
            particleCount = 8;
        }
        int desiredCount = (int) Math.ceil(particleCount * percent);

        List<ActiveParticle> list = FLYING_PARTICLES.computeIfAbsent(key, k -> new ArrayList<>());

        // Добавляем новые частицы, если их меньше чем нужно
        if (aabb != null) {
            double minX = aabb.minX - margin, maxX = aabb.maxX + margin;
            double minY = aabb.minY - margin, maxY = aabb.maxY + margin;
            double minZ = aabb.minZ - margin, maxZ = aabb.maxZ + margin;
            while (list.size() < desiredCount) {
                double sx = minX + Math.random() * (maxX - minX);
                double sy = minY + Math.random() * (maxY - minY);
                double sz = minZ + Math.random() * (maxZ - minZ);

                double dx = targetX - sx;
                double dy = targetY - sy;
                double dz = targetZ - sz;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double life = Math.max(1.0, len / speed);

                // Частьцы теперь двигаются по вектору!
                double vx = dx / life;
                double vy = dy / life;
                double vz = dz / life;

                list.add(new ActiveParticle(sx, sy, sz, vx, vy, vz, life));
            }
        } else {
            double minX = blockPos.getX() - 2, maxX = blockPos.getX() + 2;
            double minY = blockPos.getY() + 2, maxY = blockPos.getY() + 6;
            double minZ = blockPos.getZ() - 2, maxZ = blockPos.getZ() + 2;
            while (list.size() < desiredCount) {
                double sx = minX + Math.random() * (maxX - minX);
                double sy = minY + Math.random() * (maxY - minY);
                double sz = minZ + Math.random() * (maxZ - minZ);

                double dx = targetX - sx;
                double dy = targetY - sy;
                double dz = targetZ - sz;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double life = Math.max(1.0, len / speed);

                double vx = dx / life;
                double vy = dy / life;
                double vz = dz / life;

                list.add(new ActiveParticle(sx, sy, sz, vx, vy, vz, life));
            }
        }

        // Апдейт и рендер частиц (движение по траектории, не респавн!)
        Iterator<ActiveParticle> iter = list.iterator();
        while (iter.hasNext()) {
            ActiveParticle p = iter.next();
            p.update();
            // Используем частицы эндермена (PORTAL)
            level.sendParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            if (p.isArrived()) {
                iter.remove();
            }
        }
    }

    private void cleanupFlyingParticles(BlockPos blockPos) {
        FLYING_PARTICLES.remove(blockPos.asLong());
    }

    private static class ParticleProcessState {}

    private static class ActiveParticle {
        double x, y, z;
        final double vx, vy, vz;
        final double lifeTicks;
        int age;

        public ActiveParticle(double sx, double sy, double sz, double vx, double vy, double vz, double lifeTicks) {
            this.x = sx; this.y = sy; this.z = sz;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.lifeTicks = lifeTicks;
            this.age = 0;
        }
        public void update() {
            if (!isArrived()) {
                this.x += vx;
                this.y += vy;
                this.z += vz;
            }
            age++;
        }
        public boolean isArrived() {
            return age >= lifeTicks;
        }
    }

    // ---- остальной код без изменений ----
    public IStorageWrapper getStorageWrapper() {
        return this.storageWrapper;
    }

    @Nullable
    public ItemStack getStorageItemStack() {
        if (storageWrapper == null) return ItemStack.EMPTY;
        try {
            Method m = storageWrapper.getClass().getMethod("getStack");
            Object stack = m.invoke(storageWrapper);
            if (stack instanceof ItemStack s && !s.isEmpty()) {
                return s;
            }
        } catch (NoSuchMethodException e) {
            try {
                Method m2 = storageWrapper.getClass().getMethod("getStorage");
                Object stack = m2.invoke(storageWrapper);
                if (stack instanceof ItemStack s && !s.isEmpty()) {
                    return s;
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ex) {
            }
        } catch (Exception e) {
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    public BlockEntity getStorageBlockEntity(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        return (be != null && !be.isRemoved()) ? be : null;
    }

    public static void startInsertShipProcess(BlockEntity be, int slot, long shipId, UUID playerUuid) {
        CompoundTag tag = getPersistentDataStatic(be);
        tag.putBoolean(NBT_PROCESS_ACTIVE, true);
        tag.putInt(NBT_PROCESS_TICKS, 0);
        tag.putLong(NBT_PROCESS_SHIP_ID, shipId);
        tag.putInt(NBT_PROCESS_SLOT, slot);
        if (playerUuid != null) {
            tag.putUUID(NBT_PROCESS_PLAYER_UUID, playerUuid);
        }
        be.setChanged();
    }

    public static void startInsertShipProcess(BlockEntity be, int slot, long shipId) {
        startInsertShipProcess(be, slot, shipId, null);
    }

    private void clearProcess(CompoundTag tag, BlockEntity be) {
        tag.putBoolean(NBT_PROCESS_ACTIVE, false);
        tag.putInt(NBT_PROCESS_TICKS, 0);
        tag.putLong(NBT_PROCESS_SHIP_ID, 0L);
        tag.putInt(NBT_PROCESS_SLOT, -1);
        tag.remove(NBT_PROCESS_PLAYER_UUID);
        be.setChanged();
    }

    private CompoundTag getPersistentData(BlockEntity blockEntity) {
        try {
            Method m = blockEntity.getClass().getMethod("getPersistentData");
            Object result = m.invoke(blockEntity);
            if (result instanceof CompoundTag tag) {
                return tag;
            }
        } catch (Exception e) {
        }
        return new CompoundTag();
    }

    private static CompoundTag getPersistentDataStatic(BlockEntity blockEntity) {
        try {
            Method m = blockEntity.getClass().getMethod("getPersistentData");
            Object result = m.invoke(blockEntity);
            if (result instanceof CompoundTag tag) {
                return tag;
            }
        } catch (Exception e) {
        }
        return new CompoundTag();
    }

    private void syncBackpackShipsToBlock(BlockEntity be) {}

    @Nullable
    private ItemStack getBackpackItemFromBlockEntity(BlockEntity be) {
        try {
            var method = be.getClass().getMethod("getItem", int.class);
            Object result = method.invoke(be, 0);
            if (result instanceof ItemStack stack) {
                return stack;
            }
        } catch (Exception e) {
        }
        return null;
    }

    @Nullable
    private AABB tryGetShipAABB(Object vsShip) {
        if (vsShip == null) return null;
        try {
            Object aabbObj = vsShip.getClass().getMethod("getWorldAABB").invoke(vsShip);
            if (aabbObj != null) {
                double minX = (double) aabbObj.getClass().getMethod("minX").invoke(aabbObj);
                double minY = (double) aabbObj.getClass().getMethod("minY").invoke(aabbObj);
                double minZ = (double) aabbObj.getClass().getMethod("minZ").invoke(aabbObj);
                double maxX = (double) aabbObj.getClass().getMethod("maxX").invoke(aabbObj);
                double maxY = (double) aabbObj.getClass().getMethod("maxY").invoke(aabbObj);
                double maxZ = (double) aabbObj.getClass().getMethod("maxZ").invoke(aabbObj);
                return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            }
        } catch (Exception e) {
        }
        return null;
    }
}