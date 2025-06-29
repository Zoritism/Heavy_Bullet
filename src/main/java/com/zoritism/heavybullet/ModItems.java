package com.zoritism.heavybullet;

import com.zoritism.heavybullet.backpack.DockyardUpgradeItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "heavybullet");

    // Фабрика принимает три параметра
    public static final UpgradeType<DockyardUpgradeItem> DOCKYARD_UPGRADE_TYPE = new UpgradeType<>(
            (storageWrapper, upgradeStack, upgradeSaveHandler) -> new DockyardUpgradeItem(storageWrapper, upgradeStack, upgradeSaveHandler)
    );

    public static final RegistryObject<Item> DOCKYARD_UPGRADE = ITEMS.register("dockyard_upgrade",
            () -> new DockyardUpgradeItem(
                    new IUpgradeCountLimitConfig() {
                        @Override
                        public int getCountLimit() {
                            return 1;
                        }
                        @Override
                        public Item.Properties getProperties() {
                            return new Item.Properties().stacksTo(1);
                        }
                    }
            )
    );
}