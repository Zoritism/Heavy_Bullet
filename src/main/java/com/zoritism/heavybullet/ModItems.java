package com.zoritism.heavybullet;

import com.zoritism.heavybullet.backpack.DockyardUpgradeItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeGroup;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "heavybullet");

    public static final RegistryObject<Item> DOCKYARD_UPGRADE = ITEMS.register("dockyard_upgrade",
            () -> new DockyardUpgradeItem(
                    new IUpgradeCountLimitConfig() {
                        @Override
                        public int getMaxUpgradesPerStorage(String storageType, ResourceLocation upgradeType) {
                            return 1;
                        }
                        @Override
                        public int getMaxUpgradesInGroupPerStorage(String storageType, UpgradeGroup group) {
                            return 1;
                        }
                    }
            )
    );
}