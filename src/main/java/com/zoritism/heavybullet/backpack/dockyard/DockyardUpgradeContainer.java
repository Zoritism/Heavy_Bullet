package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.minecraft.server.level.ServerPlayer;
import com.zoritism.heavybullet.network.NetworkHandler;
import com.zoritism.heavybullet.network.S2CSyncDockyardClientPacket;
import java.util.HashMap;
import java.util.Map;

public class DockyardUpgradeContainer extends UpgradeContainerBase<DockyardUpgradeWrapper, DockyardUpgradeContainer> {

    private BlockPos dockyardBlockPos = null;
    private boolean blockMode = false;

    public DockyardUpgradeContainer(Player player, int upgradeContainerId, DockyardUpgradeWrapper upgradeWrapper, UpgradeContainerType<DockyardUpgradeWrapper, DockyardUpgradeContainer> type) {
        super(player, upgradeContainerId, upgradeWrapper, type);

        this.blockMode = false;
        this.dockyardBlockPos = null;

        DockyardUpgradeWrapper currentWrapper = getUpgradeWrapper();

        if (!player.level().isClientSide && currentWrapper != null) {
            BlockPos playerPos = player.blockPosition();
            int radius = 10;
            for (BlockPos pos : BlockPos.betweenClosed(
                    playerPos.offset(-radius, -radius, -radius),
                    playerPos.offset(radius, radius, radius))) {

                BlockEntity be = player.level().getBlockEntity(pos);
                if (be == null) continue;
                String beClass = be.getClass().getName();
                if (!beClass.contains("sophisticatedbackpacks")) continue;

                CompoundTag beTag = be.saveWithFullMetadata();
                if (beTag.contains("backpackData", 10)) {
                    CompoundTag backpackData = beTag.getCompound("backpackData");
                    if (backpackData.contains("tag", 10)) {
                        CompoundTag tag = backpackData.getCompound("tag");
                        if (tag.contains("renderInfo", 10)) {
                            CompoundTag renderInfo = tag.getCompound("renderInfo");
                            if (renderInfo.contains("upgradeItems", 9)) {
                                ListTag upgradeItems = renderInfo.getList("upgradeItems", 10);
                                for (int i = 0; i < upgradeItems.size(); i++) {
                                    CompoundTag upgTag = upgradeItems.getCompound(i);
                                    if (upgTag.contains("id") && upgTag.getString("id").endsWith("dockyard_upgrade")) {
                                        ItemStack stack = ItemStack.EMPTY;
                                        if (upgTag.contains("id")) {
                                            try {
                                                stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                                        new ResourceLocation(upgTag.getString("id"))));
                                            } catch (Exception e) {
                                                // ignore
                                            }
                                        }
                                        try {
                                            Object storageWrapperObj = be.getClass().getMethod("getStorageWrapper").invoke(be);
                                            if (storageWrapperObj instanceof IStorageWrapper sw) {
                                                if (!stack.isEmpty() && stack.getItem() instanceof DockyardUpgradeItem dockyardUpgradeItem) {
                                                    DockyardUpgradeWrapper blockWrapper =
                                                            dockyardUpgradeItem.getType().create(sw, stack, __ -> {});
                                                    boolean storageWrapperMatches =
                                                            currentWrapper.getStorageWrapper() == blockWrapper.getStorageWrapper();
                                                    if (storageWrapperMatches) {
                                                        this.blockMode = true;
                                                        this.dockyardBlockPos = pos.immutable();
                                                        break;
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                            // ignore
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (this.blockMode) break;
            }
        }

        if (!this.blockMode) {
            this.blockMode = false;
            this.dockyardBlockPos = null;
        }

        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            // Синхронизация blockMode и blockPos вместе с слотами!
            Map<Integer, CompoundTag> slots = new HashMap<>();
            CompoundTag dockyard = currentWrapper != null && player instanceof ServerPlayer sp ?
                    DockyardPlayerDataUtil.getDockyardData(sp)
                    : new CompoundTag();
            for (int i = 0; i < 2; ++i) {
                String key = "ship" + i;
                if (dockyard.contains(key)) {
                    slots.put(i, dockyard.getCompound(key).copy());
                }
            }
            NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new S2CSyncDockyardClientPacket(slots, this.blockMode, this.dockyardBlockPos != null ? this.dockyardBlockPos.asLong() : 0L)
            );
        }
    }

    public boolean isBlockMode() {
        return blockMode;
    }

    public BlockPos getOpenedBlockPos() {
        return dockyardBlockPos;
    }

    @Override
    public void handleMessage(CompoundTag data) {}
}