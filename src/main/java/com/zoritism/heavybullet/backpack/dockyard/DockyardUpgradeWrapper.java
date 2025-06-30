package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.function.Consumer;

public class DockyardUpgradeWrapper extends UpgradeWrapperBase<DockyardUpgradeWrapper, DockyardUpgradeItem>
        implements ITickableUpgrade {

    // Ключи для временных процессных данных в NBT блока
    private static final String NBT_PROCESS_ACTIVE = "DockyardProcessActive";
    private static final String NBT_PROCESS_TICKS = "DockyardProcessTicks";
    private static final String NBT_PROCESS_SHIP_ID = "DockyardProcessShipId";
    private static final String NBT_PROCESS_SLOT = "DockyardProcessSlot";
    private static final String NBT_PROCESS_SHIP_POS_Y = "DockyardProcessShipPosY";

    // Сколько тиков длится процесс (10 секунд)
    private static final int ANIMATION_TICKS = 200;
    // Дистанция поиска корабля вверх
    private static final int SHIP_RAY_DIST = 15;

    protected DockyardUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
        super(storageWrapper, upgrade, upgradeSaveHandler);
    }

    @Override
    public boolean canBeDisabled() {
        return false;
    }

    @Override
    public void tick(@Nullable Entity entity, Level level, BlockPos blockPos) {
        // Только на сервере и только если установлен как блок
        if (level.isClientSide || blockPos == null) return;

        BlockEntity be = getBlockEntityFromWrapper(this.storageWrapper);
        if (be == null) return;
        CompoundTag tag = getOrCreateBlockNbt(be);

        if (tag.getBoolean(NBT_PROCESS_ACTIVE)) {
            int ticks = tag.getInt(NBT_PROCESS_TICKS);
            long shipId = tag.getLong(NBT_PROCESS_SHIP_ID);
            int slot = tag.getInt(NBT_PROCESS_SLOT);
            int shipPosY = tag.getInt(NBT_PROCESS_SHIP_POS_Y);

            ticks++;
            tag.putInt(NBT_PROCESS_TICKS, ticks);

            ServerLevel serverLevel = (ServerLevel) level;

            // Проверка: корабль всё ещё есть в вертикальном луче?
            DockyardUpgradeLogic.ServerShipHandle ship = DockyardUpgradeLogic.findShipAboveBlock(serverLevel, blockPos, SHIP_RAY_DIST);
            boolean shipValid = ship != null && ship.getId() == shipId;
            if (!shipValid) {
                // Прервать процесс: корабль исчез/смещён
                tag.putBoolean(NBT_PROCESS_ACTIVE, false);
                tag.putInt(NBT_PROCESS_TICKS, 0);
                tag.putLong(NBT_PROCESS_SHIP_ID, 0L);
                tag.putInt(NBT_PROCESS_SLOT, -1);
                tag.putInt(NBT_PROCESS_SHIP_POS_Y, 0);
                be.setChanged();
                // Можно отправить игрокам сообщение или эффект
                return;
            }

            // Спавним частицы (серверная часть)
            spawnDockyardParticles(serverLevel, blockPos, ship, shipPosY);

            if (ticks >= ANIMATION_TICKS) {
                // Завершаем процесс: прячем корабль в рюкзак
                ItemStack backpack = getBackpackItemFromBlockEntity(be);
                if (backpack != null) {
                    CompoundTag shipNbt = new CompoundTag();
                    boolean result = DockyardUpgradeLogic.saveShipToNbtPublic(ship, shipNbt, null); // null игрока, т.к. действия идут от блока
                    if (result) {
                        DockyardDataHelper.saveShipToBackpackSlot(backpack, shipNbt, slot);
                        DockyardUpgradeLogic.removeShipFromWorldPublic(ship, null);
                    }
                }
                // Сброс процесса
                tag.putBoolean(NBT_PROCESS_ACTIVE, false);
                tag.putInt(NBT_PROCESS_TICKS, 0);
                tag.putLong(NBT_PROCESS_SHIP_ID, 0L);
                tag.putInt(NBT_PROCESS_SLOT, -1);
                tag.putInt(NBT_PROCESS_SHIP_POS_Y, 0);
                be.setChanged();
            }
        }
    }

    /**
     * Запускает процесс поглощения корабля блоком рюкзака.
     */
    public void startBlockShipInsert(Level level, BlockPos blockPos, DockyardUpgradeLogic.ServerShipHandle ship, int slot) {
        if (level.isClientSide || blockPos == null || ship == null) return;
        BlockEntity be = getBlockEntityFromWrapper(this.storageWrapper);
        if (be == null) return;
        CompoundTag tag = getOrCreateBlockNbt(be);

        tag.putBoolean(NBT_PROCESS_ACTIVE, true);
        tag.putInt(NBT_PROCESS_TICKS, 0);
        tag.putLong(NBT_PROCESS_SHIP_ID, ship.getId());
        tag.putInt(NBT_PROCESS_SLOT, slot);
        tag.putInt(NBT_PROCESS_SHIP_POS_Y, blockPos.getY() + 1); // начальная высота луча
        be.setChanged();
    }

    /**
     * Получить BlockEntity из IStorageWrapper (SophisticatedBackpacks/Forge).
     * Использует рефлексию для совместимости между версиями.
     */
    @Nullable
    private BlockEntity getBlockEntityFromWrapper(IStorageWrapper wrapper) {
        if (wrapper == null) return null;
        try {
            // Попробуем через getBlockEntity(), если есть такой метод
            try {
                var method = wrapper.getClass().getMethod("getBlockEntity");
                Object beObj = method.invoke(wrapper);
                if (beObj instanceof BlockEntity blockEntity) {
                    return blockEntity;
                }
            } catch (NoSuchMethodException ignored) {}
            // Если метода нет - пробуем через поле, если известно (расширяем если нужно)
        } catch (Exception e) {
            // Игнорируем, не нашли
        }
        return null;
    }

    /**
     * Вернуть ItemStack рюкзака из BlockEntity (SophisticatedBackpacks).
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
     * Возвращает или создаёт NBT блока рюкзака (persistentData).
     */
    private CompoundTag getOrCreateBlockNbt(BlockEntity blockEntity) {
        CompoundTag tag = null;
        try {
            var method = blockEntity.getClass().getMethod("getPersistentData");
            Object result = method.invoke(blockEntity);
            if (result instanceof CompoundTag nbtTag) {
                tag = nbtTag;
            }
        } catch (Exception ignored) {}
        if (tag == null) {
            tag = new CompoundTag();
        }
        return tag;
    }

    /**
     * Серверная часть: имитация отправки частиц клиенту (реализовать через настоящие пакеты для клиентов)
     */
    private void spawnDockyardParticles(ServerLevel level, BlockPos blockPos, DockyardUpgradeLogic.ServerShipHandle ship, int shipPosY) {
        // Пример серверной генерации координат частиц
        java.util.Random random = new java.util.Random(level.getGameTime());
        for (int i = 0; i < 4; i++) {
            double sx = blockPos.getX() + 0.5 + (random.nextDouble() - 0.5) * 8;
            double sy = shipPosY + random.nextDouble() * 6;
            double sz = blockPos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 8;
            double dx = blockPos.getX() + 0.5;
            double dy = blockPos.getY() + 1.2;
            double dz = blockPos.getZ() + 0.5;
            // Здесь отправьте пакет клиенту для отображения частицы летящей по вектору (sx,sy,sz)->(dx,dy,dz)
        }
    }
}