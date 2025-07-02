package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.resources.ResourceLocation;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeGroup;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Исправлено: UpgradeType теперь корректно поддерживает оба режима (блок/предмет)
 */
public class DockyardUpgradeItem extends UpgradeItemBase<DockyardUpgradeWrapper> {

    // Используем правильный конструктор UpgradeType, чтобы SophisticatedBackpacks мог корректно передавать storageWrapper для блока
    private static final UpgradeType<DockyardUpgradeWrapper> TYPE = new UpgradeType<>(
            (IStorageWrapper storageWrapper, net.minecraft.world.item.ItemStack upgrade, Consumer<net.minecraft.world.item.ItemStack> upgradeSaveHandler) ->
                    new DockyardUpgradeWrapper(storageWrapper, upgrade, upgradeSaveHandler)
    );

    public DockyardUpgradeItem() {
        super(new IUpgradeCountLimitConfig() {
            @Override
            public int getMaxUpgradesPerStorage(String s, @Nullable ResourceLocation resourceLocation) {
                return Integer.MAX_VALUE;
            }

            @Override
            public int getMaxUpgradesInGroupPerStorage(String s, UpgradeGroup upgradeGroup) {
                return Integer.MAX_VALUE;
            }
        });
    }

    @Override
    public UpgradeType<DockyardUpgradeWrapper> getType() {
        return TYPE;
    }

    @Override
    public List<UpgradeConflictDefinition> getUpgradeConflicts() {
        return List.of();
    }
}