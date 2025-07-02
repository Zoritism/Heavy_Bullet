package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

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
     * Сканирует все чанки и выводит координаты всех блок-рюкзаков с DockyardUpgrade (совместимо с Forge 1.20.1)
     */
    private void logAllBlockBackpacksWithDockyardUpgrade(Level level) {
        LOGGER.info("[DockyardUpgradeContainer] Все BLOCK BACKPACK с DockyardUpgrade:");
        int found = 0;
        if (level instanceof ServerLevel serverLevel) {
            try {
                // Достаём chunkMap через ServerLevel::chunkSource (поле), далее через chunkMap (поле)
                Object chunkMap = null;
                try {
                    Field chunkSourceField = ServerLevel.class.getDeclaredField("chunkSource");
                    chunkSourceField.setAccessible(true);
                    Object chunkSource = chunkSourceField.get(serverLevel);
                    Field chunkMapField = chunkSource.getClass().getDeclaredField("chunkMap");
                    chunkMapField.setAccessible(true);
                    chunkMap = chunkMapField.get(chunkSource);
                } catch (Exception e) {
                    LOGGER.error("Не удалось достать chunkMap: ", e);
                }
                if (chunkMap == null) return;

                // Получаем getChunkHolders() - Iterable
                Method getChunkHolders = chunkMap.getClass().getMethod("getChunkHolders");
                Iterable<?> chunkHolders = (Iterable<?>) getChunkHolders.invoke(chunkMap);

                for (Object chunkHolder : chunkHolders) {
                    // getLastAvailable() возвращает Optional<LevelChunk>
                    Method getLastAvailable = chunkHolder.getClass().getMethod("getLastAvailable");
                    Object optionalChunk = getLastAvailable.invoke(chunkHolder);
                    if (optionalChunk == null) continue;
                    // Optional<LevelChunk>: get(), isPresent()
                    Method isPresent = optionalChunk.getClass().getMethod("isPresent");
                    boolean present = (boolean) isPresent.invoke(optionalChunk);
                    if (!present) continue;
                    Method get = optionalChunk.getClass().getMethod("get");
                    Object chunkObj = get.invoke(optionalChunk);
                    if (!(chunkObj instanceof LevelChunk chunk)) continue;

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