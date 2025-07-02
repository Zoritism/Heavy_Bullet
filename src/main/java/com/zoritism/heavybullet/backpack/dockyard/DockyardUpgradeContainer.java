package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
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
            logNearbyBackpackBlocksWithDockyardUpgrade(player);
        }

        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            DockyardUpgradeLogic.syncDockyardToClient(serverPlayer);
        }
    }

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
            String beClass = be.getClass().getName();
            if (!beClass.contains("sophisticatedbackpacks")) continue;

            LOGGER.debug("[DockyardUpgradeContainer] BE at {}: class={}", pos, be.getClass().getName());

            // Логируем весь NBT для диагностики
            CompoundTag beTag = be.saveWithFullMetadata();
            LOGGER.debug("[DockyardUpgradeContainer] NBT for {}: {}", pos, beTag);

            boolean found = false;
            // Сначала ищем в "Upgrades"
            found = checkUpgradeList(beTag, "Upgrades", be.getBlockPos());
            // Fallback: ищем в "upgrades" (на всякий случай)
            if (!found) {
                found = checkUpgradeList(beTag, "upgrades", be.getBlockPos());
            }
            if (found) foundAny = true;
            else
                LOGGER.info("[DockyardUpgradeContainer] BLOCK_BACKPACK at {}: DockyardUpgrade не найден (NBT: {})", be.getBlockPos(), beTag);
        }
        if (!foundAny) {
            LOGGER.info("[DockyardUpgradeContainer] Нет найденных блок-рюкзаков с DockyardUpgrade в радиусе 10 вокруг игрока.");
        }
    }

    private boolean checkUpgradeList(CompoundTag beTag, String upgradesKey, BlockPos pos) {
        if (beTag.contains(upgradesKey, 9)) { // 9 = ListTag
            ListTag upgrades = beTag.getList(upgradesKey, 10); // 10 = CompoundTag
            for (int i = 0; i < upgrades.size(); i++) {
                CompoundTag upgTag = upgrades.getCompound(i);
                if (upgTag.contains("id") && upgTag.getString("id").equals("heavybullet:dockyard_upgrade")) {
                    LOGGER.info("[DockyardUpgradeContainer] BLOCK_BACKPACK: Pos={}, WrapperID={}", pos, "NBT");
                    return true;
                }
            }
        }
        return false;
    }

    private void logAllBlockBackpacksWithDockyardUpgrade(Level level) {
        LOGGER.info("[DockyardUpgradeContainer] Все BLOCK BACKPACK с DockyardUpgrade:");
        int found = 0;
        if (level instanceof ServerLevel serverLevel) {
            try {
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
                    try {
                        java.lang.reflect.Method getLastAvailable = chunkHolder.getClass().getMethod("getLastAvailable");
                        Object optionalChunk = getLastAvailable.invoke(chunkHolder);
                        if (optionalChunk == null) continue;
                        java.lang.reflect.Method isPresent = optionalChunk.getClass().getMethod("isPresent");
                        boolean present = (boolean) isPresent.invoke(optionalChunk);
                        if (!present) continue;
                        java.lang.reflect.Method get = optionalChunk.getClass().getMethod("get");
                        Object chunkObj = get.invoke(optionalChunk);
                        if (!(chunkObj instanceof LevelChunk chunk)) continue;

                        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                            BlockEntity be = entry.getValue();
                            if (be == null || be.isRemoved()) continue;
                            if (!be.getClass().getName().contains("sophisticatedbackpacks")) continue;

                            CompoundTag beTag = be.saveWithFullMetadata();
                            LOGGER.debug("[DockyardUpgradeContainer] NBT for {}: {}", entry.getKey(), beTag);

                            boolean hasUpgrade = false;
                            hasUpgrade = checkUpgradeList(beTag, "Upgrades", be.getBlockPos());
                            if (!hasUpgrade) hasUpgrade = checkUpgradeList(beTag, "upgrades", be.getBlockPos());
                            if (hasUpgrade) found++;
                            else
                                LOGGER.info("[DockyardUpgradeContainer] BLOCK_BACKPACK at {}: DockyardUpgrade не найден (NBT: {})", be.getBlockPos(), beTag);
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

    public BlockPos getOpenedBlockPos() {
        return dockyardBlockPos;
    }

    @Override
    public void handleMessage(CompoundTag data) {}
}