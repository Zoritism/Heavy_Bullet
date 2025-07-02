package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
import java.util.function.Consumer;

public class DockyardUpgradeWrapper extends UpgradeWrapperBase<DockyardUpgradeWrapper, DockyardUpgradeItem> implements ITickableUpgrade {

    private static final Logger LOGGER = LogManager.getLogger("HeavyBullet/DockyardUpgradeWrapper");
    private static final Logger LOGIC_LOGGER = LogManager.getLogger("HeavyBullet/DockyardUpgradeLogic");

    private static final String NBT_PROCESS_ACTIVE = "DockyardProcessActive";
    private static final String NBT_PROCESS_TICKS = "DockyardProcessTicks";
    private static final String NBT_PROCESS_SHIP_ID = "DockyardProcessShipId";
    private static final String NBT_PROCESS_SLOT = "DockyardProcessSlot";
    private static final int ANIMATION_TICKS = 200; // 10 секунд на 20 TPS
    private static final int SHIP_RAY_DIST = 15;

    protected DockyardUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
        super(storageWrapper, upgrade, upgradeSaveHandler);
        // Для отладки: выводим класс storageWrapper
        System.out.println("[DockyardUpgradeWrapper] storageWrapper class = " + (storageWrapper != null ? storageWrapper.getClass().getName() : "null"));
    }

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

    @Override
    public boolean canBeDisabled() {
        return false;
    }

    @Override
    public void tick(@Nullable Entity entity, Level level, BlockPos blockPos) {
        if (level.isClientSide) return;

        if (entity == null && blockPos != null) {
            // BLOCK MODE
            LOGGER.info("[DockyardUpgradeWrapper] distinction: BLOCK MODE");
            BlockEntity be = getStorageBlockEntity(level, blockPos);
            if (be == null) {
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

                // Логирование обратного отсчёта каждую секунду
                if (ticks == 1 || ticks % 20 == 0) {
                    int secondsLeft = Math.max((ANIMATION_TICKS - ticks) / 20, 0);
                    LOGIC_LOGGER.info("[DockyardUpgradeLogic] seconds_left: {}", secondsLeft);
                }

                ServerLevel serverLevel = (ServerLevel) level;
                DockyardUpgradeLogic.ServerShipHandle ship = DockyardUpgradeLogic.findShipAboveBlock(serverLevel, blockPos, SHIP_RAY_DIST);
                boolean shipValid = ship != null && ship.getId() == shipId;
                if (!shipValid) {
                    clearProcess(tag, be);
                    return;
                }
                spawnDockyardParticles(serverLevel, blockPos, ship);

                if (ticks >= ANIMATION_TICKS) {
                    CompoundTag shipNbt = new CompoundTag();
                    boolean result = DockyardUpgradeLogic.saveShipToNbtPublic(ship, shipNbt, null);
                    if (result) {
                        DockyardDataHelper.saveShipToBlockSlot(be, shipNbt, slot);
                        DockyardUpgradeLogic.removeShipFromWorldPublic(ship, null);
                    }
                    clearProcess(tag, be);
                }
            }
        } else if (entity instanceof Player player) {
            // ITEM MODE
            LOGGER.info("[DockyardUpgradeWrapper] distinction: ITEM MODE, player={}", player.getName().getString());
            // tick-логика для предмета, если понадобится
        }
    }

    public static void startInsertShipProcess(BlockEntity be, int slot, long shipId) {
        CompoundTag tag = getPersistentDataStatic(be);
        tag.putBoolean(NBT_PROCESS_ACTIVE, true);
        tag.putInt(NBT_PROCESS_TICKS, 0);
        tag.putLong(NBT_PROCESS_SHIP_ID, shipId);
        tag.putInt(NBT_PROCESS_SLOT, slot);
        be.setChanged();
    }

    private void clearProcess(CompoundTag tag, BlockEntity be) {
        tag.putBoolean(NBT_PROCESS_ACTIVE, false);
        tag.putInt(NBT_PROCESS_TICKS, 0);
        tag.putLong(NBT_PROCESS_SHIP_ID, 0L);
        tag.putInt(NBT_PROCESS_SLOT, -1);
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

    private void syncBackpackShipsToBlock(BlockEntity be) {
        // Заглушка для миграции, если потребуется
    }

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

    private void spawnDockyardParticles(ServerLevel level, BlockPos blockPos, DockyardUpgradeLogic.ServerShipHandle ship) {
        Object vsShip = ship.getServerShip();
        AABB aabb = tryGetShipAABB(vsShip);
        if (aabb == null) {
            for (int i = 0; i < 4; i++) {
                double sx = blockPos.getX() + 0.5 + (Math.random() - 0.5) * 4.0;
                double sy = blockPos.getY() + 4 + Math.random() * 4.0;
                double sz = blockPos.getZ() + 0.5 + (Math.random() - 0.5) * 4.0;
                double dx = blockPos.getX() + 0.5;
                double dy = blockPos.getY() + 1.2;
                double dz = blockPos.getZ() + 0.5;
                level.sendParticles(ParticleTypes.FLAME, sx, sy, sz, 0, (dx-sx)/10, (dy-sy)/10, (dz-sz)/10, 0.01);
            }
            return;
        }
        double margin = 2.0;
        double minX = aabb.minX - margin, maxX = aabb.maxX + margin;
        double minY = aabb.minY - margin, maxY = aabb.maxY + margin;
        double minZ = aabb.minZ - margin, maxZ = aabb.maxZ + margin;
        double dx = blockPos.getX() + 0.5;
        double dy = blockPos.getY() + 1.2;
        double dz = blockPos.getZ() + 0.5;

        for (int i = 0; i < 8; i++) {
            double sx = minX + Math.random() * (maxX - minX);
            double sy = minY + Math.random() * (maxY - minY);
            double sz = minZ + Math.random() * (maxZ - minZ);
            level.sendParticles(ParticleTypes.FLAME, sx, sy, sz, 0, (dx-sx)/10, (dy-sy)/10, (dz-sz)/10, 0.01);
        }
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