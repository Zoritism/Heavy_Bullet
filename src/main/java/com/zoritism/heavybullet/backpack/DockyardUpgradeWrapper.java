package com.zoritism.heavybullet.backpack;

import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;

import java.util.function.Consumer;

public class DockyardUpgradeWrapper extends UpgradeWrapperBase<DockyardUpgradeWrapper, DockyardUpgradeItem> implements IUpgradeWrapper {
    public DockyardUpgradeWrapper(IStorageWrapper storageWrapper, ItemStack upgrade, Consumer<ItemStack> upgradeSaveHandler) {
        super(storageWrapper, upgrade, upgradeSaveHandler);
        // Твоя логика, если нужна
    }

    // Свои методы, если нужны
}