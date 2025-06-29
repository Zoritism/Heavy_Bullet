package com.zoritism.heavybullet.backpack;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

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
                // Восстановить корабль из NBT — зависит от вашей интеграции с VS или bottle_ship
                spawnShipFromNbt(player, shipNbt);
                DockyardDataHelper.clearShipFromBackpack(backpack);
                player.displayClientMessage(Util.translatable("heavy_bullet.dockyard.ship_released"), true);
            }
        } else {
            // Навести прицелом на корабль, получить его
            LoadedShip ship = findShipPlayerIsLookingAt(player, 100);
            if (ship != null) {
                CompoundTag shipNbt = new CompoundTag();
                // Сохраняем корабль в NBT (зависит от вашей интеграции с VS или bottle_ship)
                saveShipToNbt(ship, shipNbt);
                DockyardDataHelper.saveShipToBackpack(backpack, shipNbt);
                // Удаляем корабль из мира
                removeShipFromWorld(ship);
                player.displayClientMessage(Util.translatable("heavy_bullet.dockyard.ship_stored"), true);
            }
        }
    }

    // Получить рюкзак игрока. Реализуй под свою механику!
    private static ItemStack getBackpackFromPlayer(ServerPlayer player) {
        // Пример: рюкзак в главной руке
        ItemStack stack = player.getMainHandItem();
        // Можно сделать проверку на твой предмет рюкзака
        return stack;
    }

    // Поиск корабля, на который смотрит игрок (RayTrace)
    private static LoadedShip findShipPlayerIsLookingAt(ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 target = eye.add(look.x * reach, look.y * reach, look.z * reach);
        HitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(
                eye, target, net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player
        ));

        Vec3 pos = hit.getLocation();
        ServerLevel level = player.serverLevel();
        // Получить объект корабля по координате (VS API)
        LoadedShip ship = VSGameUtilsKt.getShipObjectWorld(level).getShipObjectManagingPos(
                BlockPos.containing(pos.x, pos.y, pos.z)
        );
        return ship;
    }

    // Сохраняем корабль в NBT (псевдокод, зависит от API bottle_ship/VS)
    private static void saveShipToNbt(LoadedShip ship, CompoundTag nbt) {
        // Здесь должна быть сериализация корабля (API bottle_ship или VS)
        // Например: ship.save(nbt);
        // Или вызов bottle_ship/BottleWithShipItem логики
    }

    // Восстановить корабль из NBT (псевдокод)
    private static void spawnShipFromNbt(ServerPlayer player, CompoundTag nbt) {
        // Здесь должна быть десериализация и спавн корабля (API bottle_ship или VS)
        // Например: Ship.spawnFromNbt(nbt, player.level(), player.position());
    }

    // Удалить корабль из мира (псевдокод)
    private static void removeShipFromWorld(LoadedShip ship) {
        // Здесь должна быть корректная процедура удаления корабля
        // Например: ship.remove();
    }
}