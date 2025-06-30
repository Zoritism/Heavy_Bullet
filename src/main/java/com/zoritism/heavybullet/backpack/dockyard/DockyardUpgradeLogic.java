package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
     * Новый API: универсальный обработчик для любого слота (без передачи слота рюкзака!)
     * @param player игрок
     * @param slotIndex номер слота (0 или 1)
     * @param release true = выпуск, false = захват
     */
    public static void handleDockyardShipClick(ServerPlayer player, int slotIndex, boolean release) {
        LOGGER.info("[handleDockyardShipClick] Called for player={}, slotIndex={}, release={}",
                player != null ? player.getName().getString() : "null", slotIndex, release);

        ItemStack backpack = ItemStack.EMPTY;
        DockyardUpgradeWrapper wrapper = null;

        // 1. Пробуем получить через GUI контейнер (DockyardUpgradeContainer)
        try {
            if (player != null && player.containerMenu != null) {
                if (player.containerMenu.getClass().getName().equals("com.zoritism.heavybullet.backpack.dockyard.DockyardUpgradeContainer")) {
                    Object container = player.containerMenu;
                    java.lang.reflect.Method m = container.getClass().getMethod("getUpgradeWrapper");
                    Object w = m.invoke(container);
                    if (w instanceof DockyardUpgradeWrapper) {
                        wrapper = (DockyardUpgradeWrapper) w;
                        backpack = wrapper.getStorageItemStack();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[handleDockyardShipClick] Exception while accessing DockyardUpgradeWrapper: ", e);
        }

        // 2. Если не нашли через GUI — fallback на предмет в руке или в инвентаре
        if (backpack == null || backpack.isEmpty()) {
            if (player != null) {
                // main hand (если там рюкзак)
                ItemStack hand = player.getMainHandItem();
                if (hand != null && !hand.isEmpty() && stackHasDockyardUpgrade(hand)) {
                    backpack = hand;
                } else {
                    // ищем рюкзак с апгрейдом в инвентаре
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack stack = player.getInventory().getItem(i);
                        if (stack != null && !stack.isEmpty() && stackHasDockyardUpgrade(stack)) {
                            backpack = stack;
                            break;
                        }
                    }
                }
            }
        }

        if (backpack == null || backpack.isEmpty()) {
            LOGGER.warn("[handleDockyardShipClick] No backpack found for player={}", player != null ? player.getName().getString() : "null");
            if (player != null) {
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_backpack_found"), true);
            }
            return;
        }

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
    }

    /**
     * Проверяет, есть ли апгрейд дока у рюкзака (itemstack).
     * Можно заменить на более точную проверку для своего апгрейда, если нужно.
     */
    private static boolean stackHasDockyardUpgrade(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return false;
        CompoundTag tag = stack.getTag();
        if (tag.contains("Upgrades")) {
            CompoundTag upgrades = tag.getCompound("Upgrades");
            if (upgrades.contains("heavybullet:dockyard_upgrade")) {
                return true;
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