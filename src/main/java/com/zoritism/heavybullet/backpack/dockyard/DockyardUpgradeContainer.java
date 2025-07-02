package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ChunkMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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

        // Логирование ВСЕХ блок-рюкзаков с DockyardUpgrade при открытии любого интерфейса рюкзака
        if (!player.level().isClientSide) {
            logAllBlockBackpacksWithDockyardUpgrade(player.level());
        }

        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            DockyardUpgradeLogic.syncDockyardToClient(serverPlayer);
        }
    }

    /**
     * Сканирует все чанки и выводит координаты всех блок-рюкзаков с DockyardUpgrade
     */
    private void logAllBlockBackpacksWithDockyardUpgrade(Level level) {
        LOGGER.info("[DockyardUpgradeContainer] Все BLOCK BACKPACK с DockyardUpgrade:");
        int found = 0;
        if (level instanceof ServerLevel serverLevel) {
            try {
                Field chunkMapField = ServerLevel.class.getDeclaredField("chunkSource");
                chunkMapField.setAccessible(true);
                Object chunkSource = chunkMapField.get(serverLevel);

                Field chunkMapF = chunkSource.getClass().getDeclaredField("chunkMap");
                chunkMapF.setAccessible(true);
                Object chunkMap = chunkMapF.get(chunkSource);

                // getChunks() - protected, используем рефлексию
                Method getChunksMethod = chunkMap.getClass().getDeclaredMethod("getChunks");
                getChunksMethod.setAccessible(true);
                Iterable<?> chunkHolders = (Iterable<?>) getChunksMethod.invoke(chunkMap);

                for (Object chunkHolder : chunkHolders) {
                    // getChunkIfComplete() вернёт LevelChunk если чанк загружен
                    Method getChunkIfComplete = chunkHolder.getClass().getMethod("getChunkIfComplete");
                    Object chunkAccess = getChunkIfComplete.invoke(chunkHolder);
                    if (chunkAccess instanceof LevelChunk chunk) {
                        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                            BlockEntity be = entry.getValue();
                            if (be == null || be.isRemoved()) continue;
                            if (!be.getClass().getName().contains("sophisticatedbackpacks")) continue;
                            try {
                                var getUpgrades = be.getClass().getMethod("getUpgrades");
                                Object upgradesObj = getUpgrades.invoke(be);
                                if (upgradesObj instanceof List<?> upgrades) {
                                    for (Object stackObj : upgrades) {
                                        if (stackObj instanceof ItemStack stack && !stack.isEmpty()) {
                                            if (stack.getItem().getClass().getName().contains("DockyardUpgradeItem")) {
                                                LOGGER.info("[DockyardUpgradeContainer] BLOCK MODE: BlockPos={}", be.getBlockPos());
                                                found++;
                                                break;
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[DockyardUpgradeContainer] Ошибка при сканировании чанков: ", e);
            }
        }
        if (found == 0) {
            LOGGER.info("[DockyardUpgradeContainer] Нет ни одного BLOCK BACKPACK с DockyardUpgrade!");
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