package com.zoritism.heavybullet;

import com.zoritism.heavybullet.backpack.DockyardUpgradeContainer;
import com.zoritism.heavybullet.backpack.DockyardUpgradeTab;
import com.zoritism.heavybullet.backpack.DockyardUpgradeWrapper;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeGuiManager;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerRegistry;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;

@Mod("heavybullet")
public class HeavyBullet {
    public static final UpgradeContainerType<DockyardUpgradeWrapper, DockyardUpgradeContainer> DOCKYARD_TYPE =
            new UpgradeContainerType<>(DockyardUpgradeContainer::new);

    public HeavyBullet() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modEventBus);

        modEventBus.addListener(this::registerContainers);
    }

    private void registerContainers(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ForgeRegistries.Keys.MENU_TYPES)) {
            return;
        }
        UpgradeContainerRegistry.register(ModItems.DOCKYARD_UPGRADE.getId(), DOCKYARD_TYPE);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            UpgradeGuiManager.registerTab(DOCKYARD_TYPE, DockyardUpgradeTab::new);
        });
    }
}