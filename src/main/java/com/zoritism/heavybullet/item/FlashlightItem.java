package com.zoritism.heavybullet.item;

import com.zoritism.heavybullet.config.ModConfigHandler;
import com.zoritism.heavybullet.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FlashlightItem extends Item {
    private final boolean isOn;

    public FlashlightItem(boolean isOn, Properties properties) {
        super(properties);
        this.isOn = isOn;
    }

    @Nullable
    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new EnergyCapabilityProvider(stack);
    }

    public static class EnergyCapabilityProvider implements ICapabilityProvider {
        private final LazyOptional<IEnergyStorage> energy;

        public EnergyCapabilityProvider(ItemStack stack) {
            this.energy = LazyOptional.of(() -> new ItemEnergyStorage(stack));
        }

        @Override
        public <T> LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, @Nullable net.minecraft.core.Direction side) {
            return cap == ForgeCapabilities.ENERGY ? energy.cast() : LazyOptional.empty();
        }
    }

    public static class ItemEnergyStorage implements IEnergyStorage {
        private final ItemStack container;

        public ItemEnergyStorage(ItemStack container) {
            this.container = container;
        }

        private int getEnergy() {
            CompoundTag tag = container.getOrCreateTag();
            return Math.max(0, tag.getInt("Energy"));
        }

        private void setEnergy(int energy) {
            CompoundTag tag = container.getOrCreateTag();
            tag.putInt("Energy", Math.max(0, energy));
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int energy = getEnergy();
            int maxEnergy = ModConfigHandler.COMMON.maxEnergy.get();
            int received = Math.min(maxEnergy - energy, maxReceive);
            if (!simulate && received > 0) {
                setEnergy(energy + received);
            }
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int energy = getEnergy();
            int extracted = Math.min(energy, maxExtract);
            if (!simulate && extracted > 0) {
                setEnergy(energy - extracted);
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            return getEnergy();
        }

        @Override
        public int getMaxEnergyStored() {
            return ModConfigHandler.COMMON.maxEnergy.get();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player player) {
            boolean inHand = player.getMainHandItem() == stack || player.getOffhandItem() == stack;

            if (!inHand) return;

            stack.getCapability(ForgeCapabilities.ENERGY).ifPresent(storage -> {
                if (isOn) {
                    int drain = ModConfigHandler.COMMON.energyPerTick.get();
                    if (storage.getEnergyStored() > 0) {
                        storage.extractEnergy(drain, false);
                    } else {
                        // Исправлено: Используем ENERGY_FLASHLIGHT вместо FLASHLIGHT
                        ItemStack off = new ItemStack(ModItems.ENERGY_FLASHLIGHT.get());
                        off.setTag(stack.getTag() != null ? stack.getTag().copy() : null);

                        if (player.getMainHandItem() == stack) {
                            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, off);
                        } else if (player.getOffhandItem() == stack) {
                            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, off);
                        }
                    }
                }
            });
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int energy = Math.max(0, stack.getOrCreateTag().getInt("Energy"));
        int max = ModConfigHandler.COMMON.maxEnergy.get();
        return Math.round(13.0F * energy / max);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float f = Math.max(0.0F, (float) stack.getOrCreateTag().getInt("Energy") / ModConfigHandler.COMMON.maxEnergy.get());
        return Mth.hsvToRgb(f / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int energy = Math.max(0, stack.getOrCreateTag().getInt("Energy"));
        tooltip.add(Component.literal("Энергия: " + energy + " / " + ModConfigHandler.COMMON.maxEnergy.get() + " RF")
                .withStyle(ChatFormatting.GRAY));
    }

    public boolean isOn() {
        return isOn;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged) return true;
        if (oldStack.getItem() != newStack.getItem()) return true;

        CompoundTag oldTag = oldStack.getTag();
        CompoundTag newTag = newStack.getTag();

        if (oldTag == null && newTag == null) return false;
        if (oldTag == null || newTag == null) return true;

        oldTag = oldTag.copy();
        newTag = newTag.copy();
        oldTag.remove("Energy");
        newTag.remove("Energy");

        return !oldTag.equals(newTag);
    }
}