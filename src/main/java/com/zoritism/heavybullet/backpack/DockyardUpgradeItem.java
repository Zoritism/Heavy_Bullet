package com.zoritism.heavybullet.backpack;

import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;

import java.util.Collections;
import java.util.List;

public class DockyardUpgradeItem extends UpgradeItemBase<DockyardUpgradeWrapper> {
    private static final UpgradeType<DockyardUpgradeWrapper> TYPE = new UpgradeType<>(DockyardUpgradeWrapper::new);

    public DockyardUpgradeItem(IUpgradeCountLimitConfig config) {
        super(config);
    }

    @Override
    public UpgradeType<DockyardUpgradeWrapper> getType() {
        return TYPE;
    }

    @Override
    public List<UpgradeConflictDefinition> getUpgradeConflicts() {
        return Collections.emptyList();
    }
}