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

import java.util.UUID;

/**
 * Логика бутылочного апгрейда с использованием vmod-схематики.
 * Требует vmod и kotlin обёртку для вызова из Java!
 */
public class DockyardUpgradeLogic {

    /**
     * Обработка нажатия на кнопку "Bottle Ship"
     * @param player игрок
     * @param release true = выпуск, false = захват
     */
    public static void handleBottleShipClick(ServerPlayer player, boolean release) {
        ItemStack backpack = getBackpackFromPlayer(player);
        if (backpack == null) return;

        if (release) {
            CompoundTag shipNbt = DockyardDataHelper.getShipFromBackpack(backpack);
            if (shipNbt != null) {
                spawnShipFromNbt(player, shipNbt);
                DockyardDataHelper.clearShipFromBackpack(backpack);
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_released"), true);
            }
        } else {
            ServerShipHandle ship = findShipPlayerIsLookingAt(player, 100);
            if (ship != null) {
                CompoundTag shipNbt = new CompoundTag();
                boolean result = saveShipToNbt(ship, shipNbt, player);
                if (result) {
                    DockyardDataHelper.saveShipToBackpack(backpack, shipNbt);
                    removeShipFromWorld(ship, player);
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_stored"), true);
                } else {
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.save_failed"), true);
                }
            }
        }
    }

    private static ItemStack getBackpackFromPlayer(ServerPlayer player) {
        // Пример: рюкзак в главной руке
        ItemStack stack = player.getMainHandItem();
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

        if (hit == null) return null;
        Vec3 pos = hit.getLocation();
        ServerLevel level = player.serverLevel();

        // Требуется vmod helper для поиска корабля по позиции!
        // Например: return VModSchematicJavaHelper.findServerShip(level, BlockPos.containing(pos.x, pos.y, pos.z));
        return VModSchematicJavaHelper.findServerShip(level, BlockPos.containing(pos.x, pos.y, pos.z));
    }

    /**
     * Сохраняет корабль в NBT через vmod-схематику.
     * @param ship ServerShipHandle (java-wrapper, должен содержать ссылку на ServerShip)
     * @param nbt целевой CompoundTag
     * @param player игрок (для сообщений об ошибках)
     * @return true если успешно, иначе false
     */
    private static boolean saveShipToNbt(ServerShipHandle ship, CompoundTag nbt, ServerPlayer player) {
        if (ship == null) return false;
        ServerLevel level = player.serverLevel();
        UUID uuid = UUID.randomUUID();

        // Реальный вызов через kotlin helper
        // return VModSchematicJavaHelper.saveShipToNBT(level, player, uuid, ship, nbt);
        return VModSchematicJavaHelper.saveShipToNBT(level, player, uuid, ship, nbt);
    }

    /**
     * Восстанавливает корабль из NBT через vmod-схематику (paste).
     */
    private static void spawnShipFromNbt(ServerPlayer player, CompoundTag nbt) {
        ServerLevel level = player.serverLevel();
        UUID uuid = UUID.randomUUID();
        Vec3 pos = player.position();
        // Можно добавить выбор вращения и позиционирование

        // Вызов через kotlin helper
        VModSchematicJavaHelper.spawnShipFromNBT(level, player, uuid, pos, nbt);
    }

    /**
     * Удаляет корабль из мира через vmod helper.
     */
    private static void removeShipFromWorld(ServerShipHandle ship, ServerPlayer player) {
        if (ship == null) return;
        ServerLevel level = player.serverLevel();
        VModSchematicJavaHelper.removeShip(level, ship);
    }

    /**
     * Java wrapper для ServerShip (требуется реализовать на стороне Kotlin в vmod).
     */
    public interface ServerShipHandle {
        Object getServerShip(); // возвращает ServerShip из vmod/VS
        long getId();
    }

    /**
     * Вспомогательный класс для обращения к vmod API из Java.
     * Должен быть реализован на стороне Kotlin и предоставлять статические методы:
     * - findServerShip(ServerLevel, BlockPos): ServerShipHandle
     * - saveShipToNBT(ServerLevel, ServerPlayer, UUID, ServerShipHandle, CompoundTag): boolean
     * - spawnShipFromNBT(ServerLevel, ServerPlayer, UUID, Vec3, CompoundTag)
     * - removeShip(ServerLevel, ServerShipHandle)
     */
    public static class VModSchematicJavaHelper {
        public static ServerShipHandle findServerShip(ServerLevel level, BlockPos pos) {
            throw new UnsupportedOperationException("Implement in Kotlin!");
        }

        public static boolean saveShipToNBT(ServerLevel level, ServerPlayer player, UUID uuid, ServerShipHandle ship, CompoundTag nbt) {
            throw new UnsupportedOperationException("Implement in Kotlin!");
        }

        public static void spawnShipFromNBT(ServerLevel level, ServerPlayer player, UUID uuid, Vec3 pos, CompoundTag nbt) {
            throw new UnsupportedOperationException("Implement in Kotlin!");
        }

        public static void removeShip(ServerLevel level, ServerShipHandle ship) {
            throw new UnsupportedOperationException("Implement in Kotlin!");
        }
    }
}