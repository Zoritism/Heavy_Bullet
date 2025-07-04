package com.zoritism.heavybullet;

import com.zoritism.heavybullet.backpack.dockyard.DockyardUpgradeContainer;
import com.zoritism.heavybullet.backpack.dockyard.DockyardUpgradeTab;
import com.zoritism.heavybullet.backpack.dockyard.DockyardUpgradeWrapper;
import com.zoritism.heavybullet.config.ModConfigHandler;
import com.zoritism.heavybullet.client.KeybindHandler;
import com.zoritism.heavybullet.network.NetworkHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeGuiManager;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerRegistry;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;

@Mod(HeavyBulletMod.MODID)
public class HeavyBulletMod {

    public static final String MODID = "heavybullet";

    public static final UpgradeContainerType<DockyardUpgradeWrapper, DockyardUpgradeContainer> DOCKYARD_TYPE =
            new UpgradeContainerType<>(DockyardUpgradeContainer::new);

    public HeavyBulletMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Регистрация конфигурации
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModConfigHandler.COMMON_SPEC);

        // Регистрация предметов и сетевых сообщений
        ModItems.ITEMS.register(modEventBus);
        NetworkHandler.register();

        // Только на клиенте — регистрация клавиш
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            modEventBus.addListener(KeybindHandler::registerKeys);
        });

        MinecraftForge.EVENT_BUS.register(this);

        // Креативная вкладка и контейнеры
        modEventBus.addListener(this::registerContainers);
        modEventBus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        event.accept(ModItems.DOCKYARD_UPGRADE);
    }

    private void registerContainers(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ForgeRegistries.Keys.MENU_TYPES)) {
            return;
        }
        UpgradeContainerRegistry.register(ModItems.DOCKYARD_UPGRADE.getId(), DOCKYARD_TYPE);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                UpgradeGuiManager.registerTab(DOCKYARD_TYPE, DockyardUpgradeTab::new)
        );
    }
}