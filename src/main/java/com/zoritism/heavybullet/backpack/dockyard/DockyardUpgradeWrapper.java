package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.particles.ParticleTypes;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

public class DockyardUpgradeWrapper extends UpgradeWrapperBase<DockyardUpgradeWrapper, DockyardUpgradeItem> implements ITickableUpgrade {

    private static final String NBT_PROCESS_ACTIVE = "DockyardProcessActive";
    private static final String NBT_PROCESS_TICKS = "DockyardProcessTicks";
    private static final String NBT_PROCESS_SHIP_ID = "DockyardProcessShipId";
    private static final String NBT_PROCESS_SLOT = "DockyardProcessSlot";
    private static final String NBT_PROCESS_PLAYER_UUID = "DockyardProcessPlayerUUID";
    private static final int ANIMATION_TICKS = 200; // 10 секунд на 20 TPS
    private static final int SHIP_RAY_DIST = 15;

    // Ключ: blockPos.asLong(), Value: List<ActiveParticle>
    private static final Map<Long, List<ActiveParticle>> FLYING_PARTICLES = new HashMap<>();

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

                ServerLevel serverLevel = (ServerLevel) level;
                DockyardUpgradeLogic.ServerShipHandle ship = DockyardUpgradeLogic.findShipAboveBlock(serverLevel, blockPos, SHIP_RAY_DIST);
                boolean shipValid = ship != null && ship.getId() == shipId;
                if (!shipValid) {
                    clearProcess(tag, be);
                    cleanupFlyingParticles(blockPos);
                    return;
                }

                int animationTicks = Math.min(ticks, ANIMATION_TICKS);
                double process = animationTicks / (double) ANIMATION_TICKS;

                tickProcessParticlesSimulated(serverLevel, blockPos, ship, process);

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
                        } catch (Exception e) {
                            // ignore
                        }
                    }

                    if (result) {
                        DockyardUpgradeLogic.removeShipFromWorldPublic(ship, serverLevel);
                    }

                    clearProcess(tag, be);
                    cleanupFlyingParticles(blockPos);
                }
            } else {
                cleanupFlyingParticles(blockPos);
            }
        }
    }

    /**
     * Спавнит и двигает "симулированные" частицы: каждый тик их позиция обновляется, они летят к цели.
     */
    private void tickProcessParticlesSimulated(ServerLevel level, BlockPos blockPos, DockyardUpgradeLogic.ServerShipHandle ship, double process) {
        long key = blockPos.asLong();

        // Целевая точка: центр блока рюкзака, но ниже на 0.5 блока
        double targetX = blockPos.getX() + 0.5;
        double targetY = blockPos.getY(); // ниже центра блока
        double targetZ = blockPos.getZ() + 0.5;

        Object vsShip = ship.getServerShip();
        AABB aabb = tryGetShipAABB(vsShip);

        double margin = 2.0;
        int particleCount;
        if (aabb != null) {
            double sizeX = aabb.maxX - aabb.minX + 2 * margin;
            double sizeY = aabb.maxY - aabb.minY + 2 * margin;
            double sizeZ = aabb.maxZ - aabb.minZ + 2 * margin;
            double volume = Math.max(sizeX * sizeY * sizeZ, 1.0);

            double minParticles = 4;
            double maxParticles = 90;
            double vol0 = 125.0;
            double maxVolumeForDouble = 125000.0;
            double exp = 1.1;

            double multiplier = 1.0;
            if (volume > vol0) {
                multiplier += Math.min(1.0, (volume - vol0) / (maxVolumeForDouble - vol0));
            }
            double norm = Math.pow(Math.min(volume / vol0, 1.0), exp);
            particleCount = (int) ((minParticles + (maxParticles - minParticles) * norm) * multiplier);
        } else {
            particleCount = 4;
        }

        double minPercent = 0.1, maxPercent = 1.0;
        double percent = minPercent + (maxPercent - minPercent) * process;
        int desiredCount = (int) Math.ceil(particleCount * percent);

        List<ActiveParticle> particles = FLYING_PARTICLES.computeIfAbsent(key, k -> new ArrayList<>());

        // Удалить лишние (старейшие) если уменьшилось desiredCount
        while (particles.size() > desiredCount) {
            particles.remove(0);
        }

        // Добавляем новые частицы, если их не хватает
        if (aabb != null) {
            double minX = aabb.minX - margin, maxX = aabb.maxX + margin;
            double minY = aabb.minY - margin, maxY = aabb.maxY + margin;
            double minZ = aabb.minZ - margin, maxZ = aabb.maxZ + margin;
            while (particles.size() < desiredCount) {
                double sx = minX + Math.random() * (maxX - minX);
                double sy = minY + Math.random() * (maxY - minY);
                double sz = minZ + Math.random() * (maxZ - minZ);

                double dx = targetX - sx;
                double dy = targetY - sy;
                double dz = targetZ - sz;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance < 0.01) distance = 0.01;

                double lifetimeTicks = 24.0 + Math.random() * 16.0;
                double vx = dx / lifetimeTicks;
                double vy = dy / lifetimeTicks;
                double vz = dz / lifetimeTicks;

                particles.add(new ActiveParticle(sx, sy, sz, vx, vy, vz, lifetimeTicks));
            }
        } else {
            double minX = blockPos.getX() - 2, maxX = blockPos.getX() + 2;
            double minY = blockPos.getY() + 2, maxY = blockPos.getY() + 6;
            double minZ = blockPos.getZ() - 2, maxZ = blockPos.getZ() + 2;
            while (particles.size() < desiredCount) {
                double sx = minX + Math.random() * (maxX - minX);
                double sy = minY + Math.random() * (maxY - minY);
                double sz = minZ + Math.random() * (maxZ - minZ);

                double dx = targetX - sx;
                double dy = targetY - sy;
                double dz = targetZ - sz;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance < 0.01) distance = 0.01;

                double lifetimeTicks = 24.0 + Math.random() * 16.0;
                double vx = dx / lifetimeTicks;
                double vy = dy / lifetimeTicks;
                double vz = dz / lifetimeTicks;

                particles.add(new ActiveParticle(sx, sy, sz, vx, vy, vz, lifetimeTicks));
            }
        }

        // Обновить позиции всех частиц и отрисовать
        Iterator<ActiveParticle> iter = particles.iterator();
        while (iter.hasNext()) {
            ActiveParticle p = iter.next();
            p.update();
            level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            if (p.isArrived()) {
                iter.remove();
            }
        }
    }

    private void cleanupFlyingParticles(BlockPos blockPos) {
        FLYING_PARTICLES.remove(blockPos.asLong());
    }

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