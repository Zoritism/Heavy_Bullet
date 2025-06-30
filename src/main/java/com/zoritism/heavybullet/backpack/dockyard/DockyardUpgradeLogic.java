package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

/**
 * Логика докового апгрейда с использованием vmod-схематики.
 * Теперь поддерживает два слота хранения в рюкзаке.
 */
public class DockyardUpgradeLogic {

    private static final Logger LOGGER = LogManager.getLogger("HeavyBullet/DockyardUpgradeLogic");

    /**
     * Старый API для одного слота (совместимость)
     */
    public static void handleBottleShipClick(ServerPlayer player, boolean release) {
        handleDockyardShipClick(player, 0, release);
    }

    /**
     * Новый API: универсальный обработчик для любого слота.
     * @param player игрок
     * @param slotIndex номер слота (0 или 1)
     * @param release true = выпуск, false = захват
     */
    public static void handleDockyardShipClick(ServerPlayer player, int slotIndex, boolean release) {
        // По умолчанию — legacy: использовать предмет в руке
        handleDockyardShipClick(player, slotIndex, release, getLikelyBackpackSlot(player));
    }

    /**
     * Новый универсальный обработчик для любого слота и любого рюкзака:
     * @param player игрок
     * @param slotIndex номер слота (0 или 1)
     * @param release true = выпуск, false = захват
     * @param backpackSlot индекс слота инвентаря, где находится рюкзак (или -1 если не найден)
     */
    public static void handleDockyardShipClick(ServerPlayer player, int slotIndex, boolean release, int backpackSlot) {
        LOGGER.info("[handleDockyardShipClick] Called for player={}, slotIndex={}, release={}, backpackSlot={}",
                player != null ? player.getName().getString() : "null", slotIndex, release, backpackSlot);

        ItemStack backpack = getBackpackFromSlot(player, backpackSlot);
        if (backpack == null || backpack.isEmpty()) {
            LOGGER.warn("[handleDockyardShipClick] Backpack not found for player={}, slot={}", player.getName().getString(), backpackSlot);
            player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.no_backpack_found"), true);
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
            return; // release обработан, не продолжаем дальше
        }

        // === STORE logic ===
        LOGGER.info("[handleDockyardShipClick] Trying to store ship in backpack slot {}", slotIndex);
        if (DockyardDataHelper.hasShipInBackpackSlot(backpack, slotIndex)) {
            LOGGER.warn("[handleDockyardShipClick] Backpack slot {} already has ship, cannot store another", slotIndex);
            player.displayClientMessage(Component.translatable("heavy_bullet.dockyard.already_has_ship"), true);
            return;
        }
        ServerShipHandle ship = findShipPlayerIsLookingAt(player, 4.0); // Только с 4 блоков!
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

    // === Новый универсальный способ получения рюкзака по слоту ===
    private static ItemStack getBackpackFromSlot(ServerPlayer player, int slot) {
        if (slot >= 0 && slot < player.getInventory().getContainerSize()) {
            ItemStack stack = player.getInventory().getItem(slot);
            LOGGER.info("[getBackpackFromSlot] Player={}, slot={}, returning stack={}", player.getName().getString(), slot, stack);
            return stack;
        }
        // Если нельзя однозначно определить слот — fallback на main hand
        LOGGER.info("[getBackpackFromSlot] Player={}, slot={} is invalid, fallback to main hand", player.getName().getString(), slot);
        return player.getMainHandItem();
    }

    // === Для совместимости: попытка угадать слот рюкзака (если вызывается старый API) ===
    private static int getLikelyBackpackSlot(ServerPlayer player) {
        // Если в main hand рюкзак — вернуть его слот
        ItemStack main = player.getMainHandItem();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (ItemStack.isSameItemSameTags(inv.getItem(i), main)) {
                return i;
            }
        }
        // Если не найден — вернуть main hand (обычно 0 или слот меча)
        return inv.selected;
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

        // Требуется vmod helper для поиска корабля по позиции!
        try {
            ServerShipHandle found = VModSchematicJavaHelper.findServerShip(level, BlockPos.containing(pos.x, pos.y, pos.z));
            LOGGER.info("[findShipPlayerIsLookingAt] Ship found by helper: {}", found != null);
            return found;
        } catch (Throwable t) {
            LOGGER.error("[findShipPlayerIsLookingAt] Exception: ", t);
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

    // --- Публичные обертки для доступа из других классов ---
    public static boolean saveShipToNbtPublic(ServerShipHandle ship, CompoundTag nbt, ServerPlayer player) {
        return saveShipToNbt(ship, nbt, player);
    }
    public static boolean removeShipFromWorldPublic(ServerShipHandle ship, ServerPlayer player) {
        return removeShipFromWorld(ship, player);
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
            LOGGER.info("[spawnShipFromNbt] VModSchematicJavaHelper.spawnShipFromNBT returned {}", result);
            return result;
        } catch (Throwable t) {
            LOGGER.error("[spawnShipFromNbt] Exception: ", t);
            return false;
        }
    }

    /**
     * Удаляет корабль из мира через vmod helper.
     * @return true если успешно, иначе false
     */
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

    /**
     * Java wrapper для ServerShip (требуется реализовать на стороне Kotlin в vmod).
     */
    public interface ServerShipHandle {
        Object getServerShip(); // возвращает ServerShip из vmod/VS
        long getId();
    }

    // Реализация object VModSchematicJavaHelper только на стороне Kotlin:
    // см. src/main/kotlin/com/zoritism/heavybullet/backpack/VModSchematicJavaHelper.kt

    // ----------- NEW LOGIC FOR BLOCK MODE -----------

    /**
     * Поиск корабля строго вертикальным рейкастом вверх от блока рюкзака.
     * @param level серверный уровень
     * @param blockPos позиция блока рюкзака (BlockPos)
     * @param maxDistance максимальная дистанция вверх (например, 15)
     * @return ServerShipHandle если найден, иначе null
     */
    @Nullable
    public static ServerShipHandle findShipAboveBlock(ServerLevel level, BlockPos blockPos, double maxDistance) {
        Vec3 from = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 1.2, blockPos.getZ() + 0.5); // чуть выше центра блока
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

    /**
     * Запуск процесса задержанного "засовывания" корабля в рюкзак-блок.
     * ВНИМАНИЕ: это только базовый каркас, нужно интегрировать в тики апгрейда!
     * @param level серверный уровень
     * @param blockPos позиция блока рюкзака
     * @param slotIndex индекс слота для корабля
     */
    public static void startBlockShipInsert(ServerLevel level, BlockPos blockPos, int slotIndex) {
        // Пример: сохраняем в NBT блока рюкзака "идёт процесс захвата", текущее время, id корабля, слота и т.д.
        // В тиках блока или апгрейда проверять процесс и завершать по таймеру/условиям

        // TODO: Получить BlockEntity рюкзака, записать временные флаги
        // TODO: Запустить анимацию (частицы) и каждую секунду/тик делать проверку наличия корабля над блоком

        // Это только пример вывода:
        LOGGER.info("[startBlockShipInsert] Ship insert process STARTED at {} for slot {}", blockPos, slotIndex);
    }
}