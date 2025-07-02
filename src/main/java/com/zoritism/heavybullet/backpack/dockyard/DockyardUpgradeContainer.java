package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.minecraft.server.level.ServerPlayer;

/**
 * Контейнер апгрейда Dockyard с поддержкой distinction между блоком и предметом.
 * Если контейнер открыт для блока, сохраняет BlockPos блока, иначе null.
 */
public class DockyardUpgradeContainer extends UpgradeContainerBase<DockyardUpgradeWrapper, DockyardUpgradeContainer> {

    private final BlockPos openedBlockPos;

    public DockyardUpgradeContainer(Player player, int upgradeContainerId, DockyardUpgradeWrapper upgradeWrapper, UpgradeContainerType<DockyardUpgradeWrapper, DockyardUpgradeContainer> type) {
        super(player, upgradeContainerId, upgradeWrapper, type);
        // distinction: если контейнер открыт для блока — SophisticatedBackpacks всегда передаст его через internal переменные
        // В большинстве случаев, если рюкзак открыт как блок, player.containerMenu будет instanceof BackpackContainerBase, и там можно получить blockPos
        BlockPos pos = null;
        // Попробуем получить BlockPos из wrapper (через storageWrapper), если это блок
        if (!player.level().isClientSide && player instanceof ServerPlayer && player.containerMenu != null) {
            // Попытка получить BlockPos через storageWrapper (если это блок)
            try {
                Object storageWrapper = upgradeWrapper.getStorageWrapper();
                if (storageWrapper != null) {
                    // Если есть метод getBlockEntity
                    try {
                        java.lang.reflect.Method m = storageWrapper.getClass().getMethod("getBlockEntity", net.minecraft.world.level.Level.class);
                        Object be = m.invoke(storageWrapper, player.level());
                        if (be instanceof net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
                            pos = blockEntity.getBlockPos();
                        }
                    } catch (NoSuchMethodException ignored) {
                        // Нет метода — это item режим
                    }
                }
            } catch (Exception ignored) {
            }
        }
        this.openedBlockPos = pos;
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            DockyardUpgradeLogic.syncDockyardToClient(serverPlayer);
        }
    }

    /**
     * @return BlockPos если контейнер открыт для блока, иначе null
     */
    public BlockPos getOpenedBlockPos() {
        return openedBlockPos;
    }

    @Override
    public void handleMessage(CompoundTag data) {}
}