package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.minecraft.server.level.ServerPlayer;

/**
 * Контейнер для Dockyard апгрейда.
 * Важно: ничего менять здесь не нужно для поддержки distinction блок/предмет,
 * если регистрация UpgradeType и Wrapper реализованы корректно (см. DockyardUpgradeItem).
 * SophisticatedBackpacks автоматически передаст правильный storageWrapper.
 */
public class DockyardUpgradeContainer extends UpgradeContainerBase<DockyardUpgradeWrapper, DockyardUpgradeContainer> {
    public DockyardUpgradeContainer(Player player, int upgradeContainerId, DockyardUpgradeWrapper upgradeWrapper, UpgradeContainerType<DockyardUpgradeWrapper, DockyardUpgradeContainer> type) {
        super(player, upgradeContainerId, upgradeWrapper, type);
        // ВАЖНО: При открытии контейнера синхронизируем capability с клиентом
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            DockyardUpgradeLogic.syncDockyardToClient(serverPlayer);
        }
    }

    @Override
    public void handleMessage(CompoundTag data) {
        // Пока функционал не реализован
    }
}