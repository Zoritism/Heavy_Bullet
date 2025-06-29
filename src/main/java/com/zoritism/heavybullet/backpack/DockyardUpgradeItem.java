package com.zoritism.heavybullet.backpack;

import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;

import java.util.Collections;
import java.util.List;

public class DockyardUpgradeItem extends UpgradeItemBase {
    private final UpgradeType<?> upgradeType;

    public DockyardUpgradeItem(IUpgradeCountLimitConfig config, UpgradeType<?> upgradeType) {
        super(config);
        this.upgradeType = upgradeType;
    }

    @Override
    public UpgradeType<?> getType() {
        return upgradeType;
    }

    @Override
    public List<UpgradeType<?>> getUpgradeConflicts() {
        return Collections.emptyList();
    }
}