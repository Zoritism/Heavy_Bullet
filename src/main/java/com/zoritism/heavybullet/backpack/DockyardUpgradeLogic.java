package com.zoritism.heavybullet.backpack;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Логика бутылочного апгрейда с использованием vmod-схематики.
 * Требует vmod и kotlin обёртку для вызова из Java!
 */
public class DockyardUpgradeLogic {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockyardUpgradeLogic.class);

    /**
     * Обработка нажатия на кнопку "Bottle Ship"
     * @param player игрок
     * @param release true = выпуск, false = захват
     */
    public static void handleBottleShipClick(ServerPlayer player, boolean release) {
        LOGGER.info("[DockyardUpgrade] handleBottleShipClick called. Player: {}, release: {}", player.getGameProfile().getName(), release);

        ItemStack backpack = getBackpackFromPlayer(player);
        if (backpack == null) {
            LOGGER.warn("[DockyardUpgrade] No backpack found for player {}", player.getGameProfile().getName());
            return;
        }

        if (release) {
            LOGGER.info("[DockyardUpgrade] Trying to release stored ship from backpack...");
            CompoundTag shipNbt = DockyardDataHelper.getShipFromBackpack(backpack);
            if (shipNbt != null) {
                LOGGER.info("[DockyardUpgrade] Ship NBT found in backpack. Attempting to spawn ship...");
                boolean restored = spawnShipFromNbt(player, shipNbt);
                if (restored) {
                    DockyardDataHelper.clearShipFromBackpack(backpack);
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_released"), true);
                    LOGGER.info("[DockyardUpgrade] Ship was released, spawned from schematic, and NBT cleared from backpack.");
                } else {
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.restore_failed"), true);
                }
            } else {
                LOGGER.warn("[DockyardUpgrade] No stored ship NBT in backpack.");
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_stored"), true);
            }
        } else {
            LOGGER.info("[DockyardUpgrade] Trying to capture ship player is looking at...");
            ServerShipHandle ship = findShipPlayerIsLookingAt(player, 100);
            if (ship != null) {
                LOGGER.info("[DockyardUpgrade] Ship found at look position: id={}", ship.getId());
                CompoundTag shipNbt = new CompoundTag();
                boolean result = saveShipToNbt(ship, shipNbt, player);
                if (result) {
                    LOGGER.info("[DockyardUpgrade] Ship successfully saved to schematic NBT.");
                    DockyardDataHelper.saveShipToBackpack(backpack, shipNbt);
                    removeShipFromWorld(ship, player);
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_stored"), true);
                    LOGGER.info("[DockyardUpgrade] Ship removed from world and stored in backpack.");
                } else {
                    LOGGER.error("[DockyardUpgrade] Ship failed to save to schematic NBT.");
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.save_failed"), true);
                }
            } else {
                LOGGER.warn("[DockyardUpgrade] No ship found at player's look position.");
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_found"), true);
            }
        }
    }

    private static ItemStack getBackpackFromPlayer(ServerPlayer player) {
        // Пример: рюкзак в главной руке
        ItemStack stack = player.getMainHandItem();
        LOGGER.info("[DockyardUpgrade] getBackpackFromPlayer: stack={}", stack);
        return stack;
    }

    /**
     * Поиск корабля, на который смотрит игрок (raytrace).
     * Возвращает ServerShipHandle - java-обёртка над ServerShip, реализованная в моде (требуется kotlin helper).
     */
    @Nullable
    private static ServerShipHandle findShipPlayerIsLookingAt(ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 target = eye.add(look.x * reach, look.y * reach, look.z * reach);
        HitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(
                eye, target, net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player
        ));

        if (hit == null) {
            LOGGER.warn("[DockyardUpgrade] Raytrace did not hit anything.");
            return null;
        }
        Vec3 pos = hit.getLocation();
        ServerLevel level = player.serverLevel();

        LOGGER.info("[DockyardUpgrade] Raytrace hit position: ({}, {}, {})", pos.x, pos.y, pos.z);

        // Требуется vmod helper для поиска корабля по позиции!
        try {
            ServerShipHandle found = VModSchematicJavaHelper.findServerShip(level, BlockPos.containing(pos.x, pos.y, pos.z));
            LOGGER.info("[DockyardUpgrade] findServerShip returned: {}", found == null ? "null" : "id=" + found.getId());
            return found;
        } catch (Throwable t) {
            LOGGER.error("[DockyardUpgrade] Exception in findServerShip: ", t);
            return null;
        }
    }

    /**
     * Сохраняет корабль в NBT через vmod-схематику.
     * @param ship ServerShipHandle (java-wrapper, должен содержать ссылку на ServerShip)
     * @param nbt целевой CompoundTag
     * @param player игрок (для сообщений об ошибках)
     * @return true если успешно, иначе false
     */
    private static boolean saveShipToNbt(ServerShipHandle ship, CompoundTag nbt, ServerPlayer player) {
        if (ship == null) {
            LOGGER.error("[DockyardUpgrade] saveShipToNbt: ship is null");
            return false;
        }
        ServerLevel level = player.serverLevel();
        UUID uuid = UUID.randomUUID();

        try {
            LOGGER.info("[DockyardUpgrade] Calling VModSchematicJavaHelper.saveShipToNBT...");
            boolean result = VModSchematicJavaHelper.saveShipToNBT(level, player, uuid, ship, nbt);
            LOGGER.info("[DockyardUpgrade] saveShipToNbt result: {}", result);
            return result;
        } catch (Throwable t) {
            LOGGER.error("[DockyardUpgrade] Exception in saveShipToNbt: ", t);
            return false;
        }
    }

    /**
     * Восстанавливает корабль из NBT через vmod-схематику (paste).
     * @return true если успешно, иначе false
     */
    private static boolean spawnShipFromNbt(ServerPlayer player, CompoundTag nbt) {
        ServerLevel level = player.serverLevel();
        UUID uuid = UUID.randomUUID();
        Vec3 pos = player.position();
        LOGGER.info("[DockyardUpgrade] Spawning ship from NBT at player position: ({}, {}, {})", pos.x, pos.y, pos.z);

        try {
            boolean result = VModSchematicJavaHelper.spawnShipFromNBT(level, player, uuid, pos, nbt);
            LOGGER.info("[DockyardUpgrade] spawnShipFromNBT call completed: {}", result);
            return result;
        } catch (Throwable t) {
            LOGGER.error("[DockyardUpgrade] Exception in spawnShipFromNbt: ", t);
            return false;
        }
    }

    /**
     * Удаляет корабль из мира через vmod helper.
     */
    private static void removeShipFromWorld(ServerShipHandle ship, ServerPlayer player) {
        if (ship == null) {
            LOGGER.warn("[DockyardUpgrade] removeShipFromWorld: ship is null");
            return;
        }
        ServerLevel level = player.serverLevel();
        try {
            LOGGER.info("[DockyardUpgrade] Removing ship from world: id={}", ship.getId());
            VModSchematicJavaHelper.removeShip(level, ship);
            LOGGER.info("[DockyardUpgrade] removeShipFromWorld call completed.");
        } catch (Throwable t) {
            LOGGER.error("[DockyardUpgrade] Exception in removeShipFromWorld: ", t);
        }
    }

    /**
     * Java wrapper для ServerShip (требуется реализовать на стороне Kotlin в vmod).
     */
    public interface ServerShipHandle {
        Object getServerShip(); // возвращает ServerShip из vmod/VS
        long getId();
    }

    // Реализация object VModSchematicJavaHelper только на стороне Kotlin:
    // см. src/main/kotlin/com/zoritism/heavybullet/backpack/VModSchematicJavaHelper.kt
}