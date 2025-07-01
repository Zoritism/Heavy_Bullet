package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.LazyOptional;

public class PlayerDockyardDataUtil {
    public static PlayerDockyardData getOrCreate(ServerPlayer player) {
        LazyOptional<PlayerDockyardData> cap = player.getCapability(PlayerDockyardDataProvider.DOCKYARD_CAP);
        return cap.orElseThrow(() -> new IllegalStateException("No dockyard data on player"));
    }
}