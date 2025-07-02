package com.zoritism.heavybullet.backpack.dockyard;

import com.zoritism.heavybullet.network.S2CSyncDockyardClientPacket;
import com.zoritism.heavybullet.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DockyardUpgradeLogic {

    public static void handleBottleShipClick(ServerPlayer player, boolean release) {
        handleDockyardShipClick(player, 0, release);
    }

    /**
     * distinction block/item реализован через BlockEntity, определяемый через BlockPos в контейнере.
     */
    public static void handleDockyardShipClick(ServerPlayer player, int slotIndex, boolean release) {
        DockyardUpgradeWrapper wrapper = null;
        BlockEntity blockEntity = null;
        Level level = null;
        BlockPos blockPos = null;

        if (player != null && player.containerMenu != null) {
            AbstractContainerMenu menu = player.containerMenu;

            // Получаем UpgradeWrapper
            try {
                Method m = menu.getClass().getMethod("getUpgradeWrapper");
                Object w = m.invoke(menu);
                if (w instanceof DockyardUpgradeWrapper wupg) {
                    wrapper = wupg;
                    level = player.level();

                    // Получаем UpgradeContainer и через него BlockPos
                    try {
                        Method getUpgradeContainer = menu.getClass().getMethod("getUpgradeContainer");
                        Object upgradeContainerObj = getUpgradeContainer.invoke(menu);
                        if (upgradeContainerObj instanceof DockyardUpgradeContainer dockyardMenu) {
                            blockPos = dockyardMenu.getOpenedBlockPos();
                        }
                    } catch (NoSuchMethodException ignored) {
                    } catch (Exception ignored) {
                    }

                    if (blockPos != null && level != null) {
                        blockEntity = level.getBlockEntity(blockPos);
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }

        final boolean isOpenedAsBlock = blockEntity != null;

        // =============== BLOCKENTITY LOGIC ===============
        if (isOpenedAsBlock) {
            // Выпустить корабль из блока
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

            // В блоке уже есть корабль
            if (DockyardDataHelper.hasShipInBlockSlot(blockEntity, slotIndex)) {
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.already_has_ship"), true);
                return;
            }

            // Для blockentity ищем корабль строго над блоком, не рейтрейсом от игрока!
            ServerLevel serverLevel = player.serverLevel();
            BlockPos pos = blockEntity.getBlockPos();
            // Важно: теперь ищем корабль вверх на 20 блоков!
            ServerShipHandle ship = findShipAboveBlock(serverLevel, pos, 20.0);

            if (ship != null) {
                // Проверка: если уже идёт процесс засовывания в этот слот — не запускать второй раз
                CompoundTag persistent = getOrCreatePersistentData(blockEntity);
                boolean isActive = persistent.getBoolean("DockyardProcessActive");
                int processSlot = persistent.getInt("DockyardProcessSlot");
                if (isActive && processSlot == slotIndex) {
                    player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.process_already_running"), true);
                    return;
                }
                // --- ЗАПУСК АНИМАЦИИ ЗАСОВЫВАНИЯ КОРАБЛЯ ---
                DockyardUpgradeWrapper.startInsertShipProcess(blockEntity, slotIndex, ship.getId());
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.process_started"), true);
            } else {
                player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_ship_found"), true);
            }
            return;
        }

        // =============== PLAYER CAPABILITY LOGIC ===============
        // Если не blockEntity — всегда работаем с capability игрока (рюкзак из инвентаря)
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
                        syncDockyardToClient(player);
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
                        syncDockyardToClient(player);
                    } else {
                        dockyardData.remove(key);
                        player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.remove_failed"), true);
                        syncDockyardToClient(player);
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

    public static void syncDockyardToClient(ServerPlayer player) {
        PlayerDockyardData data = PlayerDockyardDataUtil.getOrCreate(player);
        CompoundTag dockyard = data.getDockyardData();
        Map<Integer, CompoundTag> slots = new HashMap<>();
        for (int i = 0; i < 2; ++i) {
            String key = "ship" + i;
            if (dockyard.contains(key)) {
                slots.put(i, dockyard.getCompound(key).copy());
            }
        }
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncDockyardClientPacket(slots)
        );
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
        // Луч идёт строго вверх от позиции блока рюкзака
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

    // Получить или создать ForgeData/PersistentData для блока
    private static CompoundTag getOrCreatePersistentData(BlockEntity blockEntity) {
        CompoundTag beTag = blockEntity.saveWithFullMetadata();
        CompoundTag persistentData;
        if (beTag.contains("ForgeData", 10)) {
            persistentData = beTag.getCompound("ForgeData");
        } else {
            persistentData = new CompoundTag();
            beTag.put("ForgeData", persistentData);
        }
        return persistentData;
    }

    public static void startBlockShipInsert(ServerLevel level, BlockPos blockPos, int slotIndex) {
        // placeholder - не используется, вся логика перенесена в startInsertShipProcess
    }
}