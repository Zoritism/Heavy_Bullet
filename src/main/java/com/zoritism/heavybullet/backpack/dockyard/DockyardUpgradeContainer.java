package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.minecraft.server.level.ServerPlayer;

public class DockyardUpgradeContainer extends UpgradeContainerBase<DockyardUpgradeWrapper, DockyardUpgradeContainer> {

    // Используем поле UpgradeContainerBase для хранения blockPos (только если открыт для блока)
    public DockyardUpgradeContainer(Player player, int upgradeContainerId, DockyardUpgradeWrapper upgradeWrapper, UpgradeContainerType<DockyardUpgradeWrapper, DockyardUpgradeContainer> type) {
        super(player, upgradeContainerId, upgradeWrapper, type);

        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            DockyardUpgradeLogic.syncDockyardToClient(serverPlayer);
        }
    }

    /**
     * SophisticatedBackpacks сам прокидывает blockPos в UpgradeContainerBase, если контейнер открыт для блока.
     * Просто вызываем getBlockPos() базового класса.
     */
    public BlockPos getOpenedBlockPos() {
        return super.getBlockPos();
    }

    @Override
    public void handleMessage(CompoundTag data) {}
}