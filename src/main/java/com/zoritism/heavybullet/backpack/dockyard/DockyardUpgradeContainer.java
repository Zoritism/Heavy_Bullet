package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.minecraft.server.level.ServerPlayer;

public class DockyardUpgradeContainer extends UpgradeContainerBase<DockyardUpgradeWrapper, DockyardUpgradeContainer> {
    private final BlockPos dockyardBlockPos;

    // Конструктор для блока
    public DockyardUpgradeContainer(Player player, int upgradeContainerId, DockyardUpgradeWrapper upgradeWrapper, UpgradeContainerType<DockyardUpgradeWrapper, DockyardUpgradeContainer> type, BlockPos blockPos) {
        super(player, upgradeContainerId, upgradeWrapper, type);
        this.dockyardBlockPos = blockPos;
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            DockyardUpgradeLogic.syncDockyardToClient(serverPlayer);
        }
    }

    // Конструктор для предмета (blockPos == null)
    public DockyardUpgradeContainer(Player player, int upgradeContainerId, DockyardUpgradeWrapper upgradeWrapper, UpgradeContainerType<DockyardUpgradeWrapper, DockyardUpgradeContainer> type) {
        this(player, upgradeContainerId, upgradeWrapper, type, null);
    }

    public BlockPos getDockyardBlockPos() {
        return dockyardBlockPos;
    }

    @Override
    public void handleMessage(CompoundTag data) {}
}