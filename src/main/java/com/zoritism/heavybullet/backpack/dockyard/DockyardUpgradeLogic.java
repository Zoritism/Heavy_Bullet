package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Переписано для хранения информации о кораблях в capability игрока (PlayerDockyardData).
 * Для блока рюкзака (BlockEntity) логика остаётся прежней.
 */
public class DockyardUpgradeLogic {

    public static void handleBottleShipClick(ServerPlayer player, boolean release) {
        handleDockyardShipClick(player, 0, release);
    }

    /**
     * Операция только с открытым контейнером SophisticatedBackpacks!
     */
    public static void handleDockyardShipClick(ServerPlayer player, int slotIndex, boolean release) {
        DockyardUpgradeWrapper wrapper = null;
        BlockEntity blockEntity = null;
        ItemStack backpack = null;

        // Получаем UpgradeWrapper из открытого DockyardUpgradeContainer (только из открытого GUI!)
        try {
            if (player != null && player.containerMenu != null) {
                AbstractContainerMenu menu = player.containerMenu;
                try {
                    java.lang.reflect.Method m = menu.getClass().getMethod("getUpgradeWrapper");
                    Object w = m.invoke(menu);
                    if (w instanceof DockyardUpgradeWrapper wupg) {
                        wrapper = wupg;
                        blockEntity = wrapper.getStorageBlockEntity();
                        backpack = wrapper.getStorageItemStack();
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        } catch (Exception e) {
            // ignore
        }

        // Если не удалось получить UpgradeWrapper — ошибка (никаких поисков рюкзака у игрока)!
        if (wrapper == null || (blockEntity == null && (backpack == null || backpack.isEmpty()))) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_backpack_found"), true);
            }
            return;
        }

        // ==== BLOCKENTITY LOGIC ====
        if (blockEntity != null) {
            if (release) {
                CompoundTag shipNbt = DockyardDataHelper.getShipFromBlockSlot(blockEntity, slotIndex);
                if (shipNbt != null) {
                    boolean restored = spawnShipFromNbt(player, shipNbt);
                    if (restored) {
                        DockyardDataHelper.clearShipFromBlockSlot(blockEntity, slotIndex);
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_released"), true);
                    } else {
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.restore_failed"), true);
                    }
                } else {
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_stored"), true);
                }
                return;
            }

            if (DockyardDataHelper.hasShipInBlockSlot(blockEntity, slotIndex)) {
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.already_has_ship"), true);
                return;
            }
            ServerShipHandle ship = findShipPlayerIsLookingAt(player, 4.0);
            if (ship != null) {
                CompoundTag shipNbt = new CompoundTag();
                boolean result = saveShipToNbt(ship, shipNbt, player);
                if (result) {
                    DockyardDataHelper.saveShipToBlockSlot(blockEntity, shipNbt, slotIndex);
                    boolean removed = removeShipFromWorld(ship, player);
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
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_found"), true);
            }
            return;
        }

        // ==== PLAYER GLOBAL LOGIC ====
        if (player != null) {
            PlayerDockyardData data = PlayerDockyardDataUtil.getOrCreate(player);
            CompoundTag dockyardData = data.getDockyardData();

            String key = "ship" + slotIndex;

            if (release) {
                if (dockyardData.contains(key)) {
                    CompoundTag shipNbt = dockyardData.getCompound(key);
                    boolean restored = spawnShipFromNbt(player, shipNbt);
                    if (restored) {
                        dockyardData.remove(key);
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_released"), true);
                        data.setDirty();
                    } else {
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.restore_failed"), true);
                    }
                } else {
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_stored"), true);
                }
                return;
            }

            if (dockyardData.contains(key)) {
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.already_has_ship"), true);
                return;
            }
            ServerShipHandle ship = findShipPlayerIsLookingAt(player, 4.0);
            if (ship != null) {
                CompoundTag shipNbt = new CompoundTag();
                boolean result = saveShipToNbt(ship, shipNbt, player);
                if (result) {
                    dockyardData.put(key, shipNbt);
                    boolean removed = removeShipFromWorld(ship, player);
                    if (removed) {
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.ship_stored"), true);
                        data.setDirty();
                    } else {
                        dockyardData.remove(key);
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.remove_failed"), true);
                    }
                } else {
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.save_failed"), true);
                }
            } else {
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_found"), true);
            }
            return;
        }

        // Этот случай невозможен если контейнер реально открыт SophisticatedBackpacks!
        if (player != null) {
            player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_backpack_found"), true);
        }
    }

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

        try {
            return VModSchematicJavaHelper.findServerShip(level, BlockPos.containing(pos.x, pos.y, pos.z));
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean saveShipToNbt(ServerShipHandle ship, CompoundTag nbt, ServerPlayer player) {
        if (ship == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        UUID uuid = UUID.randomUUID();

        try {
            return VModSchematicJavaHelper.saveShipToNBT(level, player, uuid, ship, nbt);
        } catch (Throwable t) {
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
            return VModSchematicJavaHelper.spawnShipFromNBT(level, player, uuid, pos, nbt);
        } catch (Throwable t) {
            return false;
        }
    }

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
                // ignore
            }
            if (ship != null) {
                return ship;
            }
        }
        return null;
    }

    public static void startBlockShipInsert(ServerLevel level, BlockPos blockPos, int slotIndex) {
        // placeholder
    }
}