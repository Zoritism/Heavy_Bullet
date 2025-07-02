package com.zoritism.heavybullet;

import com.zoritism.heavybullet.backpack.dockyard.DockyardUpgradeContainer;
import com.zoritism.heavybullet.backpack.dockyard.DockyardUpgradeTab;
import com.zoritism.heavybullet.backpack.dockyard.DockyardUpgradeWrapper;
import com.zoritism.heavybullet.network.NetworkHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
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

        modEventBus.addListener(this::registerContainers);
        modEventBus.addListener(this::addCreative);

        ModItems.ITEMS.register(modEventBus);

        // Register network packets before game world is loaded!
        NetworkHandler.register();
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Можно добавить предмет апгрейда в creative tab, если нужно
        event.accept(ModItems.DOCKYARD_UPGRADE);
    }

    private void registerContainers(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ForgeRegistries.Keys.MENU_TYPES)) {
            return;
        }
        // Критически важно: регистрация апгрейда через UpgradeContainerRegistry,
        // чтобы SophisticatedBackpacks мог корректно передавать storageWrapper блока
        UpgradeContainerRegistry.register(ModItems.DOCKYARD_UPGRADE.getId(), DOCKYARD_TYPE);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                UpgradeGuiManager.registerTab(DOCKYARD_TYPE, DockyardUpgradeTab::new)
        );
    }
}