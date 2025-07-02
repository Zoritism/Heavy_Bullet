package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;

public class DockyardUpgradeContainer extends UpgradeContainerBase<DockyardUpgradeWrapper, DockyardUpgradeContainer> {

    private static final Logger LOGGER = LogManager.getLogger("HeavyBullet/DockyardUpgradeContainer");
    private BlockPos dockyardBlockPos = null;
    private boolean blockMode = false;

    public DockyardUpgradeContainer(Player player, int upgradeContainerId, DockyardUpgradeWrapper upgradeWrapper, UpgradeContainerType<DockyardUpgradeWrapper, DockyardUpgradeContainer> type) {
        super(player, upgradeContainerId, upgradeWrapper, type);

        this.blockMode = false;
        this.dockyardBlockPos = null;

        // 1. Проверяем все блоки-рюкзаки с dockyard_upgrade вокруг игрока (радиус 10)
        BlockPos foundBlockPos = null;
        DockyardUpgradeWrapper currentWrapper = getUpgradeWrapper();
        String currentWrapperId = currentWrapper != null ? "wrapper" + Integer.toHexString(System.identityHashCode(currentWrapper)) : "null";

        if (!player.level().isClientSide && currentWrapper != null) {
            BlockPos playerPos = player.blockPosition();
            int radius = 10;
            for (BlockPos pos : BlockPos.betweenClosed(
                    playerPos.offset(-radius, -radius, -radius),
                    playerPos.offset(radius, radius, radius))) {

                BlockEntity be = player.level().getBlockEntity(pos);
                if (be == null || !be.getClass().getName().contains("sophisticatedbackpacks")) continue;

                CompoundTag beTag = be.saveWithFullMetadata();
                if (beTag.contains("backpackData", 10)) {
                    CompoundTag backpackData = beTag.getCompound("backpackData");
                    if (backpackData.contains("tag", 10)) {
                        CompoundTag tag = backpackData.getCompound("tag");
                        if (tag.contains("renderInfo", 10)) {
                            CompoundTag renderInfo = tag.getCompound("renderInfo");
                            if (renderInfo.contains("upgradeItems", 9)) {
                                ListTag upgradeItems = renderInfo.getList("upgradeItems", 10);
                                for (int i = 0; i < upgradeItems.size(); i++) {
                                    CompoundTag upgTag = upgradeItems.getCompound(i);
                                    if (upgTag.contains("id") && upgTag.getString("id").endsWith("dockyard_upgrade")) {
                                        // Создаём временный wrapper для этого блока
                                        try {
                                            Object storageWrapper = be.getClass().getMethod("getStorageWrapper").invoke(be);
                                            if (storageWrapper instanceof net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper sw) {
                                                DockyardUpgradeWrapper blockWrapper =
                                                        currentWrapper.getType().create(sw, null, __ -> {});
                                                String blockWrapperId = "wrapper" + Integer.toHexString(System.identityHashCode(blockWrapper));
                                                if (blockWrapperId.equals(currentWrapperId)) {
                                                    // Нашли совпадающий WrapperID — block mode!
                                                    foundBlockPos = pos.immutable();
                                                    this.blockMode = true;
                                                    this.dockyardBlockPos = foundBlockPos;
                                                    LOGGER.info("[DockyardUpgradeContainer] Переключено в BLOCK MODE для блока {} (WrapperID={})", foundBlockPos, blockWrapperId);
                                                    break;
                                                }
                                            }
                                        } catch (Exception e) {
                                            LOGGER.warn("[DockyardUpgradeContainer] Reflection error: {}", e.getMessage());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (this.blockMode) break;
            }
        }

        if (!this.blockMode) {
            // Не нашли подходящий block mode, остаёмся в item mode
            this.blockMode = false;
            this.dockyardBlockPos = null;
            LOGGER.info("[DockyardUpgradeContainer] Открытие рюкзака: режим ITEM (инвентарь). WrapperID={}", currentWrapperId);
        } else {
            LOGGER.info("[DockyardUpgradeContainer] Открытие рюкзака: режим BLOCK. BlockPos={} WrapperID={}", this.dockyardBlockPos, currentWrapperId);
        }

        // Стандартная логика синхронизации
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            DockyardUpgradeLogic.syncDockyardToClient(serverPlayer);
        }
    }

    public boolean isBlockMode() {
        return blockMode;
    }

    public BlockPos getOpenedBlockPos() {
        return dockyardBlockPos;
    }

    @Override
    public void handleMessage(CompoundTag data) {}
}