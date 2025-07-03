package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class PlayerDockyardCapabilityAttacher {
    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(
                    PlayerDockyardDataProvider.CAP_ID,
                    new PlayerDockyardDataProvider()
            );
        }
    }

    /**
     * Переносит данные dockyard capability при любом клонировании игрока (смерть, выход и т.д.).
     * Не инициирует никаких действий по автоспавну!
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().getCapability(PlayerDockyardDataProvider.DOCKYARD_CAP).ifPresent(oldCap -> {
            event.getEntity().getCapability(PlayerDockyardDataProvider.DOCKYARD_CAP).ifPresent(newCap -> {
                CompoundTag oldData = oldCap.getDockyardData();
                CompoundTag newData = newCap.getDockyardData();
                // Очищаем новые данные и копируем всё из старых
                for (String key : newData.getAllKeys()) {
                    newData.remove(key);
                }
                for (String key : oldData.getAllKeys()) {
                    newData.put(key, oldData.get(key).copy());
                }
            });
        });
    }
}