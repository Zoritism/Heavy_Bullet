package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3dc;
import org.joml.Quaterniondc;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import net.spaceeye.vmod.utils.Vector3d;
import net.spaceeye.vmod.utils.vs.TeleportShipWithConnectedKt;


public class ShipTeleporter {


    public static void teleportShip(ServerLevel level, ServerShip ship, double x, double y, double z) {
        // Получаем текущий трансформ корабля
        ShipTransform transform = ship.getTransform();

        // Получаем масштаб, поворот
        Vector3dc scaling = transform.getShipToWorldScaling();
        Quaterniondc rotation = transform.getShipToWorldRotation();

        // Используем VS утилиту для телепортации (если есть зависимость на vmod)
        // Vector3d - это простая обёртка joml.Vector3d
        Vector3d newPosition = new Vector3d(x, y, z);

        // dimensionId нужен для мульти-мира, но в обычном мире можно получить его так:
        String dimensionId = org.valkyrienskies.mod.common.VSGameUtilsKt.getDimensionId(level);

        // null для yaw, pitch как в оригинале
        TeleportShipWithConnectedKt.teleportShipWithConnected(
                level, ship, newPosition, rotation, null, dimensionId
        );
    }
}