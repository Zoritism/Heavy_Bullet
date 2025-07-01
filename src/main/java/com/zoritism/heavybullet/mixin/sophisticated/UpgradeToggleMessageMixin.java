package com.zoritism.heavybullet.mixin.sophisticated;

import net.minecraft.server.level.ServerPlayer;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.network.UpgradeToggleMessage;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.zoritism.heavybullet.backpack.dockyard.DockyardUpgradeWrapper;
import java.util.Map;

@Mixin(UpgradeToggleMessage.class)
public class UpgradeToggleMessageMixin {
    @Inject(
            method = "lambda$handleMessage$1(Lnet/p3pp3rf1y/sophisticatedbackpacks/network/UpgradeToggleMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/p3pp3rf1y/sophisticatedbackpacks/backpack/wrapper/IBackpackWrapper;Ljava/util/Map;Lnet/p3pp3rf1y/sophisticatedcore/upgrades/IUpgradeWrapper;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void injectSetEnabled(
            UpgradeToggleMessage msg, ServerPlayer player, IBackpackWrapper w, Map<?, ?> slotWrappers, IUpgradeWrapper upgradeWrapper,
            CallbackInfo ci
    ) {
        if (upgradeWrapper instanceof DockyardUpgradeWrapper dockyardWrapper) {
            // dockyardWrapper.handleToggle(player); // если нужно особое поведение
            ci.cancel();
        }
    }
}