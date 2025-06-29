package com.zoritism.heavybullet;

import com.zoritism.heavybullet.backpack.DockyardUpgradeItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "heavybullet");

    public static final RegistryObject<DockyardUpgradeItem> DOCKYARD_UPGRADE =
            ITEMS.register("dockyard_upgrade", DockyardUpgradeItem::new);
}