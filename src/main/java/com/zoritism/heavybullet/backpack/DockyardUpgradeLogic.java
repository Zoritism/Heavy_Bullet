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

import java.util.Random;
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
            if (shipNbt != null && shipNbt.contains("schematic_name")) {
                String schematicName = shipNbt.getString("schematic_name");
                LOGGER.info("[DockyardUpgrade] Schematic name in backpack: {}", schematicName);
                boolean spawned = VModSchematicJavaHelper.spawnSchematicAtPlayer(player, schematicName);
                if (spawned) {
                    DockyardDataHelper.clearShipFromBackpack(backpack);
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_released"), true);
                    LOGGER.info("[DockyardUpgrade] Ship was released from schematic and NBT cleared from backpack.");
                } else {
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.restore_failed"), true);
                    LOGGER.error("[DockyardUpgrade] Failed to spawn ship from schematic: {}", schematicName);
                }
            } else {
                LOGGER.warn("[DockyardUpgrade] No stored schematic in backpack.");
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_stored"), true);
            }
        } else {
            LOGGER.info("[DockyardUpgrade] Trying to capture ship player is looking at...");
            ServerShipHandle ship = findShipPlayerIsLookingAt(player, 100);
            if (ship != null) {
                LOGGER.info("[DockyardUpgrade] Ship found at look position: id={}", ship.getId());
                String schematicName = generateRandomSchematicName();
                boolean result = VModSchematicJavaHelper.saveShipAsSchematic(player, ship, schematicName);
                if (result) {
                    CompoundTag schematicNbt = new CompoundTag();
                    schematicNbt.putString("schematic_name", schematicName);
                    DockyardDataHelper.saveShipToBackpack(backpack, schematicNbt);
                    removeShipFromWorld(ship, player);
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_stored"), true);
                    LOGGER.info("[DockyardUpgrade] Ship removed from world and stored as schematic in backpack. Schematic: {}", schematicName);
                } else {
                    LOGGER.error("[DockyardUpgrade] Ship failed to save as schematic.");
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.save_failed"), true);
                }
            } else {
                LOGGER.warn("[DockyardUpgrade] No ship found at player's look position.");
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_found"), true);
            }
        }
    }

    private static String generateRandomSchematicName() {
        Random rand = new Random();
        int num = 10000 + rand.nextInt(90000);
        return "hb_ship_" + num;
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
}