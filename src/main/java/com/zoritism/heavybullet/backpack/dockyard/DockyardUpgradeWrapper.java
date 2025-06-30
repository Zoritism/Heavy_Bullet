package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DockyardUpgradeWrapper extends UpgradeWrapperBase<DockyardUpgradeWrapper, DockyardUpgradeItem> implements ITickableUpgrade {

    private static final Logger LOGGER = LogManager.getLogger("HeavyBullet/DockyardUpgradeWrapper");

    private static final String NBT_PROCESS_ACTIVE = "DockyardProcessActive";
    private static final String NBT_PROCESS_TICKS = "DockyardProcessTicks";
    private static final String NBT_PROCESS_SHIP_ID = "DockyardProcessShipId";
    private static final String NBT_PROCESS_SLOT = "DockyardProcessSlot";
    private static final int ANIMATION_TICKS = 200;
    private static final int SHIP_RAY_DIST = 15;

    protected DockyardUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
        super(storageWrapper, upgrade, upgradeSaveHandler);
    }

    public IStorageWrapper getStorageWrapper() {
        return this.storageWrapper;
    }

    @Nullable
    public ItemStack getStorageItemStack() {
        // Попробовать получить именно рюкзак (ItemStack), а не апгрейд!
        try {
            // SophisticatedBackpacks: BackpackWrapper.getStorage() или getStack()
            Method m;
            Object stack = null;
            try {
                m = storageWrapper.getClass().getMethod("getStack");
                stack = m.invoke(storageWrapper);
            } catch (NoSuchMethodException e) {
                try {
                    m = storageWrapper.getClass().getMethod("getStorage");
                    stack = m.invoke(storageWrapper);
                } catch (NoSuchMethodException ignore) {}
            }
            if (stack instanceof ItemStack s && !s.isEmpty()) {
                LOGGER.info("[DockyardUpgradeWrapper] getStorageItemStack: returning storageWrapper stack = {}, NBT={}", s, s.hasTag() ? s.getTag() : "no NBT");
                return s;
            }
        } catch (Exception e) {
            LOGGER.error("[DockyardUpgradeWrapper] getStorageItemStack exception: ", e);
        }
        // Не пиши WARN, если это предмет — это штатно!
        return ItemStack.EMPTY;
    }

    @Nullable
    public BlockEntity getStorageBlockEntity() {
        try {
            Method m = storageWrapper.getClass().getMethod("getBlockEntity");
            Object be = m.invoke(storageWrapper);
            LOGGER.info("[DockyardUpgradeWrapper] getStorageBlockEntity: storageWrapper={}, blockEntity={}", storageWrapper, be);
            if (be instanceof BlockEntity blockEntity) return blockEntity;
        } catch (NoSuchMethodException nsme) {
            LOGGER.debug("[DockyardUpgradeWrapper] getStorageBlockEntity: method getBlockEntity() not present on {}", storageWrapper.getClass().getName());
        } catch (Exception e) {
            LOGGER.error("[DockyardUpgradeWrapper] getStorageBlockEntity exception: ", e);
        }
        return null;
    }

    @Override
    public boolean canBeDisabled() {
        return false;
    }

    // --- Остальной код без изменений ---

    @Override
    public void tick(@Nullable Entity entity, Level level, BlockPos blockPos) {
        LOGGER.info("[DockyardUpgradeWrapper] tick called. entity={}, level={}, blockPos={}", entity, level, blockPos);

        if (level.isClientSide || blockPos == null) return;

        BlockEntity be = getStorageBlockEntity();
        if (be == null) {
            // Не пиши WARN, это штатно если апгрейд стоит в предметном рюкзаке!
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
            LOGGER.info("[DockyardUpgradeWrapper] tick: process active, ticks={}, shipId={}, slot={}, shipValid={}", ticks, shipId, slot, shipValid);
            if (!shipValid) {
                clearProcess(tag, be);
                LOGGER.info("[DockyardUpgradeWrapper] tick: process stopped, ship not valid.");
                return;
            }
            spawnDockyardParticles(serverLevel, blockPos, ship);

            if (ticks >= ANIMATION_TICKS) {
                CompoundTag shipNbt = new CompoundTag();
                boolean result = DockyardUpgradeLogic.saveShipToNbtPublic(ship, shipNbt, null);
                if (result) {
                    DockyardDataHelper.saveShipToBlockSlot(be, shipNbt, slot);
                    DockyardUpgradeLogic.removeShipFromWorldPublic(ship, null);
                    LOGGER.info("[DockyardUpgradeWrapper] tick: ship saved to block slot {} and removed from world.", slot);
                }
                clearProcess(tag, be);
            }
        }
    }

    private void clearProcess(CompoundTag tag, BlockEntity be) {
        LOGGER.info("[DockyardUpgradeWrapper] clearProcess for BlockEntity={}", be);
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
            LOGGER.info("[DockyardUpgradeWrapper] getPersistentData: blockEntity={}, persistentData={}", blockEntity, result);
            if (result instanceof CompoundTag tag) {
                return tag;
            }
        } catch (Exception e) {
            LOGGER.error("[DockyardUpgradeWrapper] getPersistentData exception: ", e);
        }
        return new CompoundTag();
    }

    private void syncBackpackShipsToBlock(BlockEntity be) {
        try {
            boolean hasShips = DockyardDataHelper.hasShipInBlockSlot(be, 0) ||
                    DockyardDataHelper.hasShipInBlockSlot(be, 1);
            LOGGER.info("[DockyardUpgradeWrapper] syncBackpackShipsToBlock: hasShipsInBlock0={}, hasShipsInBlock1={}",
                    DockyardDataHelper.hasShipInBlockSlot(be, 0), DockyardDataHelper.hasShipInBlockSlot(be, 1));
            if (hasShips) return;

            ItemStack backpack = getBackpackItemFromBlockEntity(be);
            LOGGER.info("[DockyardUpgradeWrapper] syncBackpackShipsToBlock: backpack={}, NBT={}", backpack, (backpack != null && backpack.hasTag()) ? backpack.getTag() : "no NBT");
            if (backpack == null || !backpack.hasTag()) return;

            for (int slot = 0; slot <= 1; slot++) {
                if (DockyardDataHelper.hasShipInBackpackSlot(backpack, slot)) {
                    CompoundTag ship = DockyardDataHelper.getShipFromBackpackSlot(backpack, slot);
                    LOGGER.info("[DockyardUpgradeWrapper] syncBackpackShipsToBlock: moving ship from backpack slot {} to block", slot);
                    if (ship != null) {
                        DockyardDataHelper.saveShipToBlockSlot(be, ship, slot);
                        DockyardDataHelper.clearShipFromBackpackSlot(backpack, slot);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[DockyardUpgradeWrapper] syncBackpackShipsToBlock exception: ", e);
        }
    }

    @Nullable
    private ItemStack getBackpackItemFromBlockEntity(BlockEntity be) {
        try {
            var method = be.getClass().getMethod("getItem", int.class);
            Object result = method.invoke(be, 0);
            LOGGER.info("[DockyardUpgradeWrapper] getBackpackItemFromBlockEntity: blockEntity={}, result={}, NBT={}", be, result, (result instanceof ItemStack s && s.hasTag()) ? s.getTag() : "no NBT");
            if (result instanceof ItemStack stack) {
                return stack;
            }
        } catch (Exception e) {
            LOGGER.error("[DockyardUpgradeWrapper] getBackpackItemFromBlockEntity exception: ", e);
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
                LOGGER.info("[DockyardUpgradeWrapper] tryGetShipAABB: AABB=({}, {}, {}, {}, {}, {})", minX, minY, minZ, maxX, maxY, maxZ);
                return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            }
        } catch (Exception e) {
            LOGGER.error("[DockyardUpgradeWrapper] tryGetShipAABB exception: ", e);
        }
        return null;
    }
}