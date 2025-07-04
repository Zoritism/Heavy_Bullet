package com.zoritism.heavybullet;

import com.zoritism.heavybullet.backpack.dockyard.DockyardUpgradeItem;
import com.zoritism.heavybullet.item.FlashlightItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, "heavybullet");

    // Обычный фонарик (выключен)
    public static final RegistryObject<Item> FLASHLIGHT =
            ITEMS.register("energy_flashlight", () ->
                    new FlashlightItem(false, new Item.Properties().stacksTo(1)));

    // Включённый фонарик
    public static final RegistryObject<Item> FLASHLIGHT_ON =
            ITEMS.register("energy_flashlight_on", () ->
                    new FlashlightItem(true, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<DockyardUpgradeItem> DOCKYARD_UPGRADE =
            ITEMS.register("dockyard_upgrade", DockyardUpgradeItem::new);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}