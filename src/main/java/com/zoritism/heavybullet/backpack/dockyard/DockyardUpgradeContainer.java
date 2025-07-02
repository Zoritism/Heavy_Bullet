package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DockyardUpgradeContainer extends UpgradeContainerBase<DockyardUpgradeWrapper, DockyardUpgradeContainer> {

    private static final Logger LOGGER = LogManager.getLogger("HeavyBullet/DockyardUpgradeContainer");

    private BlockPos dockyardBlockPos = null;
    private boolean blockMode = false;

    public DockyardUpgradeContainer(Player player, int upgradeContainerId, DockyardUpgradeWrapper upgradeWrapper, UpgradeContainerType<DockyardUpgradeWrapper, DockyardUpgradeContainer> type) {
        super(player, upgradeContainerId, upgradeWrapper, type);

        this.blockMode = false;
        this.dockyardBlockPos = null;

        DockyardUpgradeWrapper currentWrapper = getUpgradeWrapper();
        String currentWrapperId = currentWrapper != null ? "wrapper" + Integer.toHexString(System.identityHashCode(currentWrapper)) : "null";

        LOGGER.info("[DockyardUpgradeContainer] === Открытие DockyardUpgrade ===");
        LOGGER.info("[DockyardUpgradeContainer] WrapperID текущего контейнера: {}", currentWrapperId);

        if (!player.level().isClientSide && currentWrapper != null) {
            BlockPos playerPos = player.blockPosition();
            int radius = 10;
            LOGGER.info("[DockyardUpgradeContainer] Поиск блоков-рюкзаков с dockyard_upgrade в радиусе {} вокруг игрока {}...", radius, player.getName().getString());
            for (BlockPos pos : BlockPos.betweenClosed(
                    playerPos.offset(-radius, -radius, -radius),
                    playerPos.offset(radius, radius, radius))) {

                BlockEntity be = player.level().getBlockEntity(pos);
                if (be == null) continue;
                String beClass = be.getClass().getName();
                if (!beClass.contains("sophisticatedbackpacks")) continue;

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
                                        ItemStack stack = ItemStack.EMPTY;
                                        if (upgTag.contains("id")) {
                                            try {
                                                stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                                        new ResourceLocation(upgTag.getString("id"))));
                                            } catch (Exception e) {
                                                LOGGER.warn("[DockyardUpgradeContainer] Не удалось создать ItemStack из id {}: {}", upgTag.getString("id"), e.getMessage());
                                            }
                                        }
                                        try {
                                            Object storageWrapperObj = be.getClass().getMethod("getStorageWrapper").invoke(be);
                                            if (storageWrapperObj instanceof IStorageWrapper sw) {
                                                if (!stack.isEmpty() && stack.getItem() instanceof DockyardUpgradeItem dockyardUpgradeItem) {
                                                    DockyardUpgradeWrapper blockWrapper =
                                                            dockyardUpgradeItem.getType().create(sw, stack, __ -> {});
                                                    boolean storageWrapperMatches =
                                                            currentWrapper.getStorageWrapper() == blockWrapper.getStorageWrapper();
                                                    LOGGER.info("[DockyardUpgradeContainer] Найден BLOCK_BACKPACK: Pos={}, storageWrapperMatches={}", pos, storageWrapperMatches);
                                                    if (storageWrapperMatches) {
                                                        this.blockMode = true;
                                                        this.dockyardBlockPos = pos.immutable();
                                                        LOGGER.info("[DockyardUpgradeContainer] Определено: режим BLOCK для блока {} (storageWrapper совпал)", this.dockyardBlockPos);
                                                        break;
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                            LOGGER.warn("[DockyardUpgradeContainer] Reflection error at Pos={}: {}", pos, e.getMessage());
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
            this.blockMode = false;
            this.dockyardBlockPos = null;
            LOGGER.info("[DockyardUpgradeContainer] Определено: режим ITEM (инвентарь). WrapperID={}", currentWrapperId);
        } else {
            LOGGER.info("[DockyardUpgradeContainer] Открытие рюкзака: режим BLOCK. BlockPos={} WrapperID={}", this.dockyardBlockPos, currentWrapperId);
        }

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