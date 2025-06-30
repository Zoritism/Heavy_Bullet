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

/**
 * DockyardUpgradeWrapper:
 * - Хранит ссылку на storageWrapper, апгрейд и обработчик сохранения.
 * - Позволяет получать доступ к storageWrapper, ItemStack рюкзака, BlockEntity (если блок).
 * - В блоковом режиме умеет синхронизировать persistentData блока и предмета.
 * - Используется для показа UI и серверной логики апгрейда.
 */
public class DockyardUpgradeWrapper extends UpgradeWrapperBase<DockyardUpgradeWrapper, DockyardUpgradeItem>
        implements ITickableUpgrade {

    // Ключи для временных процессных данных в persistentData блока
    private static final String NBT_PROCESS_ACTIVE = "DockyardProcessActive";
    private static final String NBT_PROCESS_TICKS = "DockyardProcessTicks";
    private static final String NBT_PROCESS_SHIP_ID = "DockyardProcessShipId";
    private static final String NBT_PROCESS_SLOT = "DockyardProcessSlot";

    // Сколько тиков длится процесс (10 секунд)
    private static final int ANIMATION_TICKS = 200;
    // Дистанция поиска корабля вверх
    private static final int SHIP_RAY_DIST = 15;

    protected DockyardUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
        super(storageWrapper, upgrade, upgradeSaveHandler);
    }

    /** Публичный геттер для storageWrapper — нужен для UI. */
    public IStorageWrapper getStorageWrapper() {
        return this.storageWrapper;
    }

    /** Получить ItemStack рюкзака, к которому относится этот апгрейд (если это предмет, а не блок). */
    @Nullable
    public ItemStack getStorageItemStack() {
        try {
            Method m = storageWrapper.getClass().getMethod("getStack");
            Object stack = m.invoke(storageWrapper);
            if (stack instanceof ItemStack s) return s;
        } catch (Exception ignored) {}
        return null;
    }

    /** Получить BlockEntity, если апгрейд вставлен в блок. */
    @Nullable
    public BlockEntity getStorageBlockEntity() {
        try {
            Method m = storageWrapper.getClass().getMethod("getBlockEntity");
            Object be = m.invoke(storageWrapper);
            if (be instanceof BlockEntity blockEntity) return blockEntity;
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public boolean canBeDisabled() {
        return false;
    }

    @Override
    public void tick(@Nullable Entity entity, Level level, BlockPos blockPos) {
        // Работает только для блока, только на сервере
        if (level.isClientSide || blockPos == null) return;

        BlockEntity be = getStorageBlockEntity();
        if (be == null) return;
        CompoundTag tag = getPersistentData(be);

        // Автоматически переносить корабли из предмета в persistentData при первом запуске блока
        syncBackpackShipsToBlock(be);

        // --- PROCESS BLOCK SHIP CAPTURE ---
        if (tag.getBoolean(NBT_PROCESS_ACTIVE)) {
            int ticks = tag.getInt(NBT_PROCESS_TICKS);
            long shipId = tag.getLong(NBT_PROCESS_SHIP_ID);
            int slot = tag.getInt(NBT_PROCESS_SLOT);

            ticks++;
            tag.putInt(NBT_PROCESS_TICKS, ticks);

            ServerLevel serverLevel = (ServerLevel) level;

            // Проверка: корабль всё ещё есть сверху?
            DockyardUpgradeLogic.ServerShipHandle ship = DockyardUpgradeLogic.findShipAboveBlock(serverLevel, blockPos, SHIP_RAY_DIST);
            boolean shipValid = ship != null && ship.getId() == shipId;
            if (!shipValid) {
                // Остановить процесс
                clearProcess(tag, be);
                return;
            }

            // Генерировать частицы между кораблём и рюкзаком
            spawnDockyardParticles(serverLevel, blockPos, ship);

            if (ticks >= ANIMATION_TICKS) {
                // После задержки — сохранить корабль в persistentData блока!
                CompoundTag shipNbt = new CompoundTag();
                boolean result = DockyardUpgradeLogic.saveShipToNbtPublic(ship, shipNbt, null); // null игрока, т.к. действия идут от блока
                if (result) {
                    DockyardDataHelper.saveShipToBlockSlot(be, shipNbt, slot);
                    DockyardUpgradeLogic.removeShipFromWorldPublic(ship, null);
                }
                clearProcess(tag, be);
            }
        }
    }

    private void clearProcess(CompoundTag tag, BlockEntity be) {
        tag.putBoolean(NBT_PROCESS_ACTIVE, false);
        tag.putInt(NBT_PROCESS_TICKS, 0);
        tag.putLong(NBT_PROCESS_SHIP_ID, 0L);
        tag.putInt(NBT_PROCESS_SLOT, -1);
        be.setChanged();
    }

    /**
     * Запускает процесс поглощения корабля блоком рюкзака (блоковый режим!).
     */
    public void startBlockShipInsert(Level level, BlockPos blockPos, DockyardUpgradeLogic.ServerShipHandle ship, int slot) {
        if (level.isClientSide || blockPos == null || ship == null) return;
        BlockEntity be = getStorageBlockEntity();
        if (be == null) return;
        CompoundTag tag = getPersistentData(be);

        // Если в этом слоте уже есть корабль – не начинать процесс
        if (DockyardDataHelper.hasShipInBlockSlot(be, slot)) return;

        tag.putBoolean(NBT_PROCESS_ACTIVE, true);
        tag.putInt(NBT_PROCESS_TICKS, 0);
        tag.putLong(NBT_PROCESS_SHIP_ID, ship.getId());
        tag.putInt(NBT_PROCESS_SLOT, slot);
        be.setChanged();
    }

    /**
     * Получить persistentData для BlockEntity (или создать если нужно).
     */
    private CompoundTag getPersistentData(BlockEntity blockEntity) {
        try {
            Method m = blockEntity.getClass().getMethod("getPersistentData");
            Object result = m.invoke(blockEntity);
            if (result instanceof CompoundTag tag) {
                return tag;
            }
        } catch (Exception ignored) {}
        // Fallback: не найдено поле persistentData, используем временный тег (данные не сохранятся)
        return new CompoundTag();
    }

    /**
     * На блоке: при первом запуске блока копировать корабли из предмета рюкзака в persistentData блока,
     * и наоборот при снятии блока – если persistentData есть, копировать в предмет.
     */
    private void syncBackpackShipsToBlock(BlockEntity be) {
        try {
            // Если persistentData уже заполнен – ничего не делаем
            boolean hasShips = DockyardDataHelper.hasShipInBlockSlot(be, 0) ||
                    DockyardDataHelper.hasShipInBlockSlot(be, 1);
            if (hasShips) return;

            // Получить предмет рюкзака из блока
            ItemStack backpack = getBackpackItemFromBlockEntity(be);
            if (backpack == null || !backpack.hasTag()) return;

            for (int slot = 0; slot <= 1; slot++) {
                if (DockyardDataHelper.hasShipInBackpackSlot(backpack, slot)) {
                    CompoundTag ship = DockyardDataHelper.getShipFromBackpackSlot(backpack, slot);
                    if (ship != null) {
                        DockyardDataHelper.saveShipToBlockSlot(be, ship, slot);
                        DockyardDataHelper.clearShipFromBackpackSlot(backpack, slot);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Получить ItemStack рюкзака из BlockEntity (SophisticatedBackpacks).
     * Обычно это первый слот, но может отличаться для разных реализаций.
     */
    @Nullable
    private ItemStack getBackpackItemFromBlockEntity(BlockEntity be) {
        try {
            var method = be.getClass().getMethod("getItem", int.class);
            Object result = method.invoke(be, 0);
            if (result instanceof ItemStack stack) {
                return stack;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Серверная часть: генерирует частицы-огоньки между кораблём и рюкзаком-блоком
     */
    private void spawnDockyardParticles(ServerLevel level, BlockPos blockPos, DockyardUpgradeLogic.ServerShipHandle ship) {
        Object vsShip = ship.getServerShip();
        AABB aabb = tryGetShipAABB(vsShip);
        if (aabb == null) {
            // Fallback: просто над блоком, если корабль неизвестен
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
        // Размер частицы-области увеличен относительно корабля
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

    /**
     * Получить AABB корабля через reflection (VS API).
     */
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
        } catch (Exception ignored) {}
        return null;
    }

    // При необходимости реализовать syncBlockShipsToBackpack для обратной синхронизации при снятии блока
}