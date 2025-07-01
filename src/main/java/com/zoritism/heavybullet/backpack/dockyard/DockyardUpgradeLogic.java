package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

public class DockyardUpgradeLogic {

    private static final Logger LOGGER = LogManager.getLogger("HeavyBullet/DockyardUpgradeLogic");

    public static void handleBottleShipClick(ServerPlayer player, boolean release) {
        handleDockyardShipClick(player, 0, release);
    }

    /**
     * Универсальный обработчик для любого слота и источника (рюкзак-предмет или блок).
     * @param player игрок
     * @param slotIndex номер слота (0 или 1)
     * @param release true = выпуск, false = захват
     */
    public static void handleDockyardShipClick(ServerPlayer player, int slotIndex, boolean release) {
        LOGGER.info("[handleDockyardShipClick] Called for player={}, slotIndex={}, release={}",
                player != null ? player.getName().getString() : "null", slotIndex, release);

        DockyardUpgradeWrapper wrapper = null;
        BlockEntity blockEntity = null;
        ItemStack backpack = ItemStack.EMPTY;

        // 1. Пробуем получить через GUI контейнер DockyardUpgradeContainer (если открыт)
        try {
            if (player != null && player.containerMenu != null) {
                if (player.containerMenu.getClass().getName()
                        .equals("com.zoritism.heavybullet.backpack.dockyard.DockyardUpgradeContainer")) {
                    Object container = player.containerMenu;
                    java.lang.reflect.Method m = container.getClass().getMethod("getUpgradeWrapper");
                    Object w = m.invoke(container);
                    if (w instanceof DockyardUpgradeWrapper wupg) {
                        wrapper = wupg;
                        blockEntity = wrapper.getStorageBlockEntity();
                        backpack = wrapper.getStorageItemStack();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[handleDockyardShipClick] Exception while accessing DockyardUpgradeWrapper: ", e);
        }

        // ==== BLOCKENTITY LOGIC ====
        if (blockEntity != null) {
            // === RELEASE logic ===
            if (release) {
                LOGGER.info("[handleDockyardShipClick] Trying to release ship from block slot {}", slotIndex);
                CompoundTag shipNbt = DockyardDataHelper.getShipFromBlockSlot(blockEntity, slotIndex);
                if (shipNbt != null) {
                    boolean restored = spawnShipFromNbt(player, shipNbt);
                    LOGGER.info("[handleDockyardShipClick] Spawn ship result: {}", restored);
                    if (restored) {
                        DockyardDataHelper.clearShipFromBlockSlot(blockEntity, slotIndex);
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_released"), true);
                    } else {
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.restore_failed"), true);
                    }
                } else {
                    LOGGER.info("[handleDockyardShipClick] No ship stored in block slot {}", slotIndex);
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_stored"), true);
                }
                return;
            }

            // === STORE logic ===
            LOGGER.info("[handleDockyardShipClick] Trying to store ship in block slot {}", slotIndex);
            if (DockyardDataHelper.hasShipInBlockSlot(blockEntity, slotIndex)) {
                LOGGER.warn("[handleDockyardShipClick] Block slot {} already has ship, cannot store another", slotIndex);
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.already_has_ship"), true);
                return;
            }
            ServerShipHandle ship = findShipPlayerIsLookingAt(player, 4.0);
            LOGGER.info("[handleDockyardShipClick] findShipPlayerIsLookingAt result: {}", ship != null ? "found" : "not found");
            if (ship != null) {
                CompoundTag shipNbt = new CompoundTag();
                boolean result = saveShipToNbt(ship, shipNbt, player);
                LOGGER.info("[handleDockyardShipClick] saveShipToNbt result: {}", result);
                if (result) {
                    DockyardDataHelper.saveShipToBlockSlot(blockEntity, shipNbt, slotIndex);
                    boolean removed = removeShipFromWorld(ship, player);
                    LOGGER.info("[handleDockyardShipClick] removeShipFromWorld result: {}", removed);
                    if (removed) {
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_stored"), true);
                    } else {
                        DockyardDataHelper.clearShipFromBlockSlot(blockEntity, slotIndex);
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.remove_failed"), true);
                    }
                } else {
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.save_failed"), true);
                }
            } else {
                LOGGER.info("[handleDockyardShipClick] No ship found in sight");
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_found"), true);
            }
            return;
        }

        // ==== ITEMSTACK LOGIC ====
        if (backpack != null && !backpack.isEmpty()) {
            // === RELEASE logic ===
            if (release) {
                LOGGER.info("[handleDockyardShipClick] Trying to release ship from backpack slot {}", slotIndex);
                CompoundTag shipNbt = DockyardDataHelper.getShipFromBackpackSlot(backpack, slotIndex);
                if (shipNbt != null) {
                    boolean restored = spawnShipFromNbt(player, shipNbt);
                    LOGGER.info("[handleDockyardShipClick] Spawn ship result: {}", restored);
                    if (restored) {
                        DockyardDataHelper.clearShipFromBackpackSlot(backpack, slotIndex);
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_released"), true);
                    } else {
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.restore_failed"), true);
                    }
                } else {
                    LOGGER.info("[handleDockyardShipClick] No ship stored in backpack slot {}", slotIndex);
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_stored"), true);
                }
                return;
            }

            // === STORE logic ===
            LOGGER.info("[handleDockyardShipClick] Trying to store ship in backpack slot {}", slotIndex);
            if (DockyardDataHelper.hasShipInBackpackSlot(backpack, slotIndex)) {
                LOGGER.warn("[handleDockyardShipClick] Backpack slot {} already has ship, cannot store another", slotIndex);
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.already_has_ship"), true);
                return;
            }
            ServerShipHandle ship = findShipPlayerIsLookingAt(player, 4.0);
            LOGGER.info("[handleDockyardShipClick] findShipPlayerIsLookingAt result: {}", ship != null ? "found" : "not found");
            if (ship != null) {
                CompoundTag shipNbt = new CompoundTag();
                boolean result = saveShipToNbt(ship, shipNbt, player);
                LOGGER.info("[handleDockyardShipClick] saveShipToNbt result: {}", result);
                if (result) {
                    DockyardDataHelper.saveShipToBackpackSlot(backpack, shipNbt, slotIndex);
                    boolean removed = removeShipFromWorld(ship, player);
                    LOGGER.info("[handleDockyardShipClick] removeShipFromWorld result: {}", removed);
                    if (removed) {
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_stored"), true);
                    } else {
                        DockyardDataHelper.clearShipFromBackpackSlot(backpack, slotIndex);
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.remove_failed"), true);
                    }
                } else {
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.save_failed"), true);
                }
            } else {
                LOGGER.info("[handleDockyardShipClick] No ship found in sight");
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_found"), true);
            }
            return;
        }

        // Если ничего не найдено
        LOGGER.warn("[handleDockyardShipClick] No backpack/block found for player={}", player != null ? player.getName().getString() : "null");
        if (player != null) {
            player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_backpack_found"), true);
        }
    }

    /**
     * Поиск SophisticatedBackpacks-рюкзака с dockyard-апгрейдом у игрока.
     */
    private static ItemStack findBackpackWithDockyardUpgrade(ServerPlayer player) {
        if (player == null) return ItemStack.EMPTY;
        // Проверяем обе руки
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (!stack.isEmpty() && isSophBackpackWithDockyard(stack)) {
                return stack;
            }
        }
        // Проверяем инвентарь
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && isSophBackpackWithDockyard(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Проверяет, что ItemStack - это рюкзак SophisticatedBackpacks с dockyard-апгрейдом.
     * Проверка строго через ListTag "Upgrades" (формат SophisticatedBackpacks).
     */
    private static boolean isSophBackpackWithDockyard(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return false;
        // Быстрая эвристика: item id должен содержать "backpack"
        if (!stack.getItem().toString().toLowerCase().contains("backpack")) return false;
        CompoundTag tag = stack.getTag();
        if (tag.contains("Upgrades", Tag.TAG_LIST)) {
            ListTag upgrades = tag.getList("Upgrades", Tag.TAG_COMPOUND);
            for (int i = 0; i < upgrades.size(); i++) {
                CompoundTag upgradeTag = upgrades.getCompound(i);
                if (upgradeTag.contains("id", Tag.TAG_STRING)) {
                    String upgId = upgradeTag.getString("id");
                    if ("heavybullet:dockyard_upgrade".equals(upgId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    private static ServerShipHandle findShipPlayerIsLookingAt(ServerPlayer player, double maxDistance) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 target = eye.add(look.x * maxDistance, look.y * maxDistance, look.z * maxDistance);
        HitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(
                eye, target, net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player
        ));

        LOGGER.info("[findShipPlayerIsLookingAt] Player={}, eye={}, look={}, target={}, hit={}",
                player.getName().getString(), eye, look, target, hit);

        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            LOGGER.info("[findShipPlayerIsLookingAt] No block/entity hit (MISS)");
            return null;
        }
        Vec3 pos = hit.getLocation();
        double dist = eye.distanceTo(pos);

        LOGGER.info("[findShipPlayerIsLookingAt] Hit at {}, distance={}", pos, dist);

        if (dist > maxDistance + 0.01) {
            LOGGER.info("[findShipPlayerIsLookingAt] Hit too far: {} > {}", dist, maxDistance);
            return null;
        }

        ServerLevel level = player.serverLevel();

        try {
            ServerShipHandle found = VModSchematicJavaHelper.findServerShip(level, BlockPos.containing(pos.x, pos.y, pos.z));
            LOGGER.info("[findShipPlayerIsLookingAt] Ship found by helper: {}", found != null);
            return found;
        } catch (Throwable t) {
            LOGGER.error("[findShipPlayerIsLookingAt] Exception: ", t);
            return null;
        }
    }

    private static boolean saveShipToNbt(ServerShipHandle ship, CompoundTag nbt, ServerPlayer player) {
        if (ship == null) {
            LOGGER.warn("[saveShipToNbt] Ship is null!");
            return false;
        }
        ServerLevel level = player.serverLevel();
        UUID uuid = UUID.randomUUID();

        try {
            boolean result = VModSchematicJavaHelper.saveShipToNBT(level, player, uuid, ship, nbt);
            LOGGER.info("[saveShipToNbt] VModSchematicJavaHelper.saveShipToNBT returned {}", result);
            return result;
        } catch (Throwable t) {
            LOGGER.error("[saveShipToNbt] Exception: ", t);
            return false;
        }
    }

    public static boolean saveShipToNbtPublic(ServerShipHandle ship, CompoundTag nbt, ServerPlayer player) {
        return saveShipToNbt(ship, nbt, player);
    }
    public static boolean removeShipFromWorldPublic(ServerShipHandle ship, ServerPlayer player) {
        return removeShipFromWorld(ship, player);
    }

    private static boolean spawnShipFromNbt(ServerPlayer player, CompoundTag nbt) {
        ServerLevel level = player.serverLevel();
        UUID uuid = UUID.randomUUID();
        Vec3 pos = player.position();

        try {
            boolean result = VModSchematicJavaHelper.spawnShipFromNBT(level, player, uuid, pos, nbt);
            LOGGER.info("[spawnShipFromNbt] VModSchematicJavaHelper.spawnShipFromNBT returned {}", result);
            return result;
        } catch (Throwable t) {
            LOGGER.error("[spawnShipFromNbt] Exception: ", t);
            return false;
        }
    }

    private static boolean removeShipFromWorld(ServerShipHandle ship, ServerPlayer player) {
        if (ship == null) {
            LOGGER.warn("[removeShipFromWorld] Ship is null!");
            return false;
        }
        ServerLevel level = player.serverLevel();
        try {
            VModSchematicJavaHelper.removeShip(level, ship);
            LOGGER.info("[removeShipFromWorld] Ship removed successfully");
            return true;
        } catch (Throwable t) {
            LOGGER.error("[removeShipFromWorld] Exception: ", t);
            return false;
        }
    }

    public interface ServerShipHandle {
        Object getServerShip();
        long getId();
    }

    @Nullable
    public static ServerShipHandle findShipAboveBlock(ServerLevel level, BlockPos blockPos, double maxDistance) {
        Vec3 from = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 1.2, blockPos.getZ() + 0.5);
        int steps = (int) Math.ceil(maxDistance);
        for (int i = 0; i <= steps; i++) {
            int y = (int) (from.y + i);
            BlockPos pos = new BlockPos((int) from.x, y, (int) from.z);
            ServerShipHandle ship = null;
            try {
                ship = VModSchematicJavaHelper.findServerShip(level, pos);
            } catch (Throwable t) {
                LOGGER.error("[findShipAboveBlock] Exception at pos {}: {}", pos, t);
            }
            if (ship != null) {
                LOGGER.info("[findShipAboveBlock] Found ship at {}", pos);
                return ship;
            }
        }
        LOGGER.info("[findShipAboveBlock] No ship found above block at {}", blockPos);
        return null;
    }

    public static void startBlockShipInsert(ServerLevel level, BlockPos blockPos, int slotIndex) {
        LOGGER.info("[startBlockShipInsert] Ship insert process STARTED at {} for slot {}", blockPos, slotIndex);
    }
}