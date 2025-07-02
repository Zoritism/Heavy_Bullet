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

        // Лог WrapperID для открытого рюкзака (инвентарь или блок)
        DockyardUpgradeWrapper wrapper = getUpgradeWrapper();
        String wrapperId = wrapper != null ? "wrapper" + Integer.toHexString(System.identityHashCode(wrapper)) : "null";
        LOGGER.info("[DockyardUpgradeContainer] Открытие рюкзака: режим {}. WrapperID={}",
                (this.dockyardBlockPos != null ? "BLOCK. BlockPos=" + this.dockyardBlockPos : "ITEM (инвентарь)"), wrapperId);

        // Логирование всех BLOCK BACKPACK с DockyardUpgrade (в чанках)
        if (!player.level().isClientSide) {
            logAllBlockBackpacksWithDockyardUpgrade(player.level());
            // Новый дополнительный лог: ближайшие блоки-рюкзаки с DockyardUpgrade вокруг игрока (радиус 10)
            logNearbyBackpackBlocksWithDockyardUpgrade(player);
        }

        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            DockyardUpgradeLogic.syncDockyardToClient(serverPlayer);
        }
    }

    /**
     * Логировать все блок-рюкзаки с DockyardUpgrade в радиусе 10 блоков вокруг игрока (координаты + WrapperID).
     * Пробует getUpgrades и getAllUpgrades.
     */
    private void logNearbyBackpackBlocksWithDockyardUpgrade(Player player) {
        Level level = player.level();
        BlockPos playerPos = player.blockPosition();
        int radius = 10;
        LOGGER.info("[DockyardUpgradeContainer] Ближайшие блок-рюкзаки с DockyardUpgrade в радиусе {} вокруг {}:", radius, player.getName().getString());
        boolean foundAny = false;
        for (BlockPos pos : BlockPos.betweenClosed(
                playerPos.offset(-radius, -radius, -radius),
                playerPos.offset(radius, radius, radius))) {

            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) continue;

            LOGGER.debug("[DockyardUpgradeContainer] BE at {}: class={}", pos, be.getClass().getName());

            String beClass = be.getClass().getName();
            if (!beClass.contains("sophisticatedbackpacks")) continue;

            List<?> upgrades = null;
            // Пробуем сначала getUpgrades, потом getAllUpgrades
            try {
                Method getUpgrades = be.getClass().getMethod("getUpgrades");
                Object upgradesObj = getUpgrades.invoke(be);
                if (upgradesObj instanceof List<?>) {
                    upgrades = (List<?>) upgradesObj;
                }
            } catch (NoSuchMethodException e) {
                try {
                    Method getAllUpgrades = be.getClass().getMethod("getAllUpgrades");
                    Object upgradesObj = getAllUpgrades.invoke(be);
                    if (upgradesObj instanceof List<?>) {
                        upgrades = (List<?>) upgradesObj;
                    }
                } catch (NoSuchMethodException e2) {
                    LOGGER.warn("[DockyardUpgradeContainer] BE at {}: нет методов getUpgrades или getAllUpgrades: {}, {}", pos, e.toString(), e2.toString());
                } catch (Exception e2) {
                    LOGGER.warn("[DockyardUpgradeContainer] BE at {}: ошибка при вызове getAllUpgrades: {}", pos, e2.toString());
                }
            } catch (Exception e) {
                LOGGER.warn("[DockyardUpgradeContainer] BE at {}: ошибка при вызове getUpgrades: {}", pos, e.toString());
            }

            if (upgrades != null) {
                for (Object stackObj : upgrades) {
                    if (stackObj instanceof ItemStack stack && !stack.isEmpty()) {
                        if (stack.getItem().getClass().getName().contains("DockyardUpgradeItem")) {
                            // Для WrapperID создаём временный DockyardUpgradeWrapper
                            String tempWrapperId = "null";
                            try {
                                var upgradeItem = stack.getItem();
                                if (upgradeItem instanceof DockyardUpgradeItem dockyardUpgradeItem) {
                                    var upgradeType = dockyardUpgradeItem.getType();
                                    var getStorageWrapper = be.getClass().getMethod("getStorageWrapper");
                                    Object storageWrapperObj = getStorageWrapper.invoke(be);
                                    if (storageWrapperObj instanceof net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper storageWrapper) {
                                        DockyardUpgradeWrapper tempWrapper = upgradeType.create(storageWrapper, stack, __ -> {});
                                        tempWrapperId = "wrapper" + Integer.toHexString(System.identityHashCode(tempWrapper));
                                    }
                                }
                            } catch (Exception e) {
                                // ignore
                            }
                            LOGGER.info("[DockyardUpgradeContainer] BLOCK_BACKPACK: Pos={}, WrapperID={}", be.getBlockPos(), tempWrapperId);
                            foundAny = true;
                            break;
                        }
                    }
                }
            }
        }
        if (!foundAny) {
            LOGGER.info("[DockyardUpgradeContainer] Нет найденных блок-рюкзаков с DockyardUpgrade в радиусе 10 вокруг игрока.");
        }
    }

    /**
     * Сканирует все чанки и выводит координаты всех блок-рюкзаков с DockyardUpgrade, а также их WrapperID
     * Пробует getUpgrades и getAllUpgrades.
     */
    private void logAllBlockBackpacksWithDockyardUpgrade(Level level) {
        LOGGER.info("[DockyardUpgradeContainer] Все BLOCK BACKPACK с DockyardUpgrade:");
        int found = 0;
        if (level instanceof ServerLevel serverLevel) {
            try {
                // chunkSource
                Object chunkSource = null;
                try {
                    Field chunkSourceField = ServerLevel.class.getDeclaredField("chunkSource");
                    chunkSourceField.setAccessible(true);
                    chunkSource = chunkSourceField.get(serverLevel);
                } catch (NoSuchFieldException e) {
                    for (Field field : ServerLevel.class.getDeclaredFields()) {
                        if (field.getType().getSimpleName().equals("ServerChunkCache")) {
                            field.setAccessible(true);
                            chunkSource = field.get(serverLevel);
                            break;
                        }
                    }
                }
                if (chunkSource == null) {
                    LOGGER.error("Не удалось найти поле chunkSource в ServerLevel");
                    return;
                }

                // chunkMap
                Object chunkMap = null;
                try {
                    Field chunkMapField = chunkSource.getClass().getDeclaredField("chunkMap");
                    chunkMapField.setAccessible(true);
                    chunkMap = chunkMapField.get(chunkSource);
                } catch (NoSuchFieldException e) {
                    for (Field field : chunkSource.getClass().getDeclaredFields()) {
                        if (field.getType().getSimpleName().toLowerCase().contains("chunkmap")) {
                            field.setAccessible(true);
                            chunkMap = field.get(chunkSource);
                            break;
                        }
                    }
                }
                if (chunkMap == null) {
                    LOGGER.error("Не удалось найти поле chunkMap в ServerChunkCache");
                    return;
                }

                // chunkHolders (Iterable)
                Iterable<?> chunkHolders = null;
                for (Field field : chunkMap.getClass().getDeclaredFields()) {
                    if (Iterable.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object value = field.get(chunkMap);
                        if (value != null) {
                            chunkHolders = (Iterable<?>) value;
                            break;
                        }
                    }
                }
                if (chunkHolders == null) {
                    LOGGER.error("[DockyardUpgradeContainer] Не найдено поле chunkHolders (Iterable<?>) в ChunkMap, логирование невозможно");
                    return;
                }

                for (Object chunkHolder : chunkHolders) {
                    // getLastAvailable() возвращает Optional<LevelChunk>
                    try {
                        Method getLastAvailable = chunkHolder.getClass().getMethod("getLastAvailable");
                        Object optionalChunk = getLastAvailable.invoke(chunkHolder);
                        if (optionalChunk == null) continue;
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

                            List<?> upgrades = null;
                            // Пробуем сначала getUpgrades, потом getAllUpgrades
                            try {
                                Method getUpgrades = be.getClass().getMethod("getUpgrades");
                                Object upgradesObj = getUpgrades.invoke(be);
                                if (upgradesObj instanceof List<?>) {
                                    upgrades = (List<?>) upgradesObj;
                                }
                            } catch (NoSuchMethodException e) {
                                try {
                                    Method getAllUpgrades = be.getClass().getMethod("getAllUpgrades");
                                    Object upgradesObj = getAllUpgrades.invoke(be);
                                    if (upgradesObj instanceof List<?>) {
                                        upgrades = (List<?>) upgradesObj;
                                    }
                                } catch (NoSuchMethodException e2) {
                                    LOGGER.warn("[DockyardUpgradeContainer] BE at {}: нет методов getUpgrades или getAllUpgrades: {}, {}", entry.getKey(), e.toString(), e2.toString());
                                } catch (Exception e2) {
                                    LOGGER.warn("[DockyardUpgradeContainer] BE at {}: ошибка при вызове getAllUpgrades: {}", entry.getKey(), e2.toString());
                                }
                            } catch (Exception e) {
                                LOGGER.warn("[DockyardUpgradeContainer] BE at {}: ошибка при вызове getUpgrades: {}", entry.getKey(), e.toString());
                            }

                            if (upgrades != null) {
                                for (Object stackObj : upgrades) {
                                    if (stackObj instanceof ItemStack stack && !stack.isEmpty()) {
                                        if (stack.getItem().getClass().getName().contains("DockyardUpgradeItem")) {
                                            // Для WrapperID создаём временный DockyardUpgradeWrapper
                                            String tempWrapperId = "null";
                                            try {
                                                var upgradeItem = stack.getItem();
                                                if (upgradeItem instanceof DockyardUpgradeItem dockyardUpgradeItem) {
                                                    var upgradeType = dockyardUpgradeItem.getType();
                                                    var getStorageWrapper = be.getClass().getMethod("getStorageWrapper");
                                                    Object storageWrapperObj = getStorageWrapper.invoke(be);
                                                    if (storageWrapperObj instanceof net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper storageWrapper) {
                                                        DockyardUpgradeWrapper tempWrapper = upgradeType.create(storageWrapper, stack, __ -> {});
                                                        tempWrapperId = "wrapper" + Integer.toHexString(System.identityHashCode(tempWrapper));
                                                    }
                                                }
                                            } catch (Exception e) {
                                                // ignore
                                            }
                                            LOGGER.info("[DockyardUpgradeContainer] BLOCK MODE: BlockPos={} WrapperID={}", be.getBlockPos(), tempWrapperId);
                                            found++;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
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