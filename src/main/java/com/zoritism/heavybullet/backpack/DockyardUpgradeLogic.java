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
        if (backpack == null) {
            return;
        }

        if (release) {
            CompoundTag shipNbt = DockyardDataHelper.getShipFromBackpack(backpack);
            if (shipNbt != null) {
                boolean restored = spawnShipFromNbt(player, shipNbt);
                if (restored) {
                    DockyardDataHelper.clearShipFromBackpack(backpack);
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_released"), true);
                } else {
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.restore_failed"), true);
                }
            } else {
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_stored"), true);
            }
        } else {
            // Предохранитель: если уже есть корабль в рюкзаке — запрещаем захват нового
            if (DockyardDataHelper.hasShipInBackpack(backpack)) {
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.already_has_ship"), true);
                return;
            }
            ServerShipHandle ship = findShipPlayerIsLookingAt(player, 4.0); // Только с 4 блоков!
            if (ship != null) {
                CompoundTag shipNbt = new CompoundTag();
                boolean result = saveShipToNbt(ship, shipNbt, player);
                if (result) {
                    DockyardDataHelper.saveShipToBackpack(backpack, shipNbt);
                    boolean removed = removeShipFromWorld(ship, player);
                    if (removed) {
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_stored"), true);
                    } else {
                        DockyardDataHelper.clearShipFromBackpack(backpack);
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.remove_failed"), true);
                    }
                } else {
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.save_failed"), true);
                }
            } else {
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_found"), true);
            }
        }
    }

    private static ItemStack getBackpackFromPlayer(ServerPlayer player) {
        // Пример: рюкзак в главной руке
        return player.getMainHandItem();
    }

    /**
     * Поиск корабля, на который смотрит игрок (raytrace).
     * Возвращает ServerShipHandle - java-обёртка над ServerShip, реализованная в моде (требуется kotlin helper).
     * Разрешено только если точка попадания ближе чем maxDistance.
     */
    @Nullable
    private static ServerShipHandle findShipPlayerIsLookingAt(ServerPlayer player, double maxDistance) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 target = eye.add(look.x * maxDistance, look.y * maxDistance, look.z * maxDistance);
        HitResult hit = player.level().clip(new net.minecraft.world.level.ClipContext(
                eye, target, net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player
        ));

        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return null;
        }
        Vec3 pos = hit.getLocation();
        double dist = eye.distanceTo(pos);

        if (dist > maxDistance + 0.01) {
            return null;
        }

        ServerLevel level = player.serverLevel();

        // Требуется vmod helper для поиска корабля по позиции!
        try {
            ServerShipHandle found = VModSchematicJavaHelper.findServerShip(level, BlockPos.containing(pos.x, pos.y, pos.z));
            return found;
        } catch (Throwable t) {
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
            return false;
        }
        ServerLevel level = player.serverLevel();
        UUID uuid = UUID.randomUUID();

        try {
            boolean result = VModSchematicJavaHelper.saveShipToNBT(level, player, uuid, ship, nbt);
            return result;
        } catch (Throwable t) {
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

        try {
            boolean result = VModSchematicJavaHelper.spawnShipFromNBT(level, player, uuid, pos, nbt);
            return result;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Удаляет корабль из мира через vmod helper.
     * @return true если успешно, иначе false
     */
    private static boolean removeShipFromWorld(ServerShipHandle ship, ServerPlayer player) {
        if (ship == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        try {
            VModSchematicJavaHelper.removeShip(level, ship);
            return true;
        } catch (Throwable t) {
            return false;
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