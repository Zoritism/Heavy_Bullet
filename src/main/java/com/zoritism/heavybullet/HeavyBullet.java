package com.zoritism.heavybullet;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("heavybullet")
public class HeavyBullet {
    public HeavyBullet() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modEventBus);
    }
}