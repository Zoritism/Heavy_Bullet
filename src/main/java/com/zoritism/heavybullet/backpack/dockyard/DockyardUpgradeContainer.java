package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;

public class DockyardUpgradeContainer extends UpgradeContainerBase<DockyardUpgradeWrapper, DockyardUpgradeContainer> {

    private static final Logger LOGGER = LogManager.getLogger("HeavyBullet/DockyardUpgradeContainer");
    private final BlockPos dockyardBlockPos;

    public DockyardUpgradeContainer(Player player, int upgradeContainerId, DockyardUpgradeWrapper upgradeWrapper, UpgradeContainerType<DockyardUpgradeWrapper, DockyardUpgradeContainer> type) {
        super(player, upgradeContainerId, upgradeWrapper, type);

        BlockPos pos = null;
        // SophisticatedBackpacks distinction через контекст:
        if (this instanceof net.p3pp3rf1y.sophisticatedbackpacks.common.gui.IContextAwareContainer contextAware) {
            var context = contextAware.getBackpackContext();
            var typeCtx = context.getType();
            if (typeCtx == net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext.ContextType.BLOCK_BACKPACK ||
                    typeCtx == net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext.ContextType.BLOCK_SUB_BACKPACK) {
                pos = context.getBackpackPosition(player);
            }
        }
        // Fallback для других случаев (через protected поле blockPos, если нет контекста)
        if (pos == null) {
            try {
                Field f = UpgradeContainerBase.class.getDeclaredField("blockPos");
                f.setAccessible(true);
                pos = (BlockPos) f.get(this);
            } catch (Exception ignored) {}
        }

        this.dockyardBlockPos = pos;

        if (this.dockyardBlockPos != null) {
            LOGGER.info("[DockyardUpgradeContainer] Открытие рюкзака: режим BLOCK. BlockPos={}", this.dockyardBlockPos);
            DockyardUpgradeWrapper wrapper = getUpgradeWrapper();
            LOGGER.info("[DockyardUpgradeContainer] BLOCK MODE: Wrapper {}, BlockPos={}", wrapper, this.dockyardBlockPos);
        } else {
            LOGGER.info("[DockyardUpgradeContainer] Открытие рюкзака: режим ITEM (инвентарь).");
        }

        // ГАРАНТИРОВАННО выводим координаты всех блоков с апгрейдом, если они есть в мире
        // (ВНИМАНИЕ: этот код гарантирует только вывод текущего BLOCK MODE, остальные блоки будут выведены если set заполнен)
        if (this.dockyardBlockPos != null) {
            LOGGER.info("[DockyardUpgradeContainer] [LOG ALWAYS] BLOCK MODE открыт на координатах: {}", this.dockyardBlockPos);
        }

        // Логируем список всех BLOCK MODE DockyardUpgradeWrapper с координатами блоков — только если set не пуст
        boolean found = false;
        for (DockyardUpgradeWrapper w : DockyardUpgradeWrapper.getAllBlockModeWrappers()) {
            if (w != null) {
                var be = w.getStorageBlockEntity();
                if (be != null && !be.isRemoved()) {
                    found = true;
                    LOGGER.info("[DockyardUpgradeContainer] BLOCK MODE (set): Wrapper {}, BlockPos={}", w, be.getBlockPos());
                }
            }
        }
        if (!found) {
            LOGGER.info("[DockyardUpgradeContainer] Нет ни одного зарегистрированного BLOCK MODE DockyardUpgrade (set пуст).");
        }

        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            DockyardUpgradeLogic.syncDockyardToClient(serverPlayer);
        }
    }

    /**
     * @return BlockPos если открыт как block entity, иначе null
     */
    public BlockPos getOpenedBlockPos() {
        return dockyardBlockPos;
    }

    @Override
    public void handleMessage(CompoundTag data) {}
}