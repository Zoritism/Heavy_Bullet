package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.minecraft.server.level.ServerPlayer;

/**
 * Контейнер для Dockyard апгрейда.
 * Теперь поддерживает distinction блок/предмет на клиенте через sync blockPos.
 */
public class DockyardUpgradeContainer extends UpgradeContainerBase<DockyardUpgradeWrapper, DockyardUpgradeContainer> {
    // Синхронизированная позиция блока рюкзака (если открыт как блок, иначе null)
    private final BlockPos dockyardBlockPos;

    public DockyardUpgradeContainer(Player player, int upgradeContainerId, DockyardUpgradeWrapper upgradeWrapper, UpgradeContainerType<DockyardUpgradeWrapper, DockyardUpgradeContainer> type, BlockPos blockPos) {
        super(player, upgradeContainerId, upgradeWrapper, type);
        this.dockyardBlockPos = blockPos;
        // ВАЖНО: При открытии контейнера синхронизируем capability с клиентом
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            DockyardUpgradeLogic.syncDockyardToClient(serverPlayer);
        }
    }

    /**
     * Старый конструктор для предмета (нет blockPos)
     */
    public DockyardUpgradeContainer(Player player, int upgradeContainerId, DockyardUpgradeWrapper upgradeWrapper, UpgradeContainerType<DockyardUpgradeWrapper, DockyardUpgradeContainer> type) {
        this(player, upgradeContainerId, upgradeWrapper, type, null);
    }

    /**
     * @return Позиция блока рюкзака если открыт как блок, иначе null
     */
    public BlockPos getDockyardBlockPos() {
        return dockyardBlockPos;
    }

    @Override
    public void handleMessage(CompoundTag data) {
        // Пока функционал не реализован
    }
}