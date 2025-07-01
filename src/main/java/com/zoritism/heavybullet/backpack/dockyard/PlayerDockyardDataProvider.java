package com.zoritism.heavybullet.backpack.dockyard;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerDockyardDataProvider implements ICapabilitySerializable<CompoundTag> {
    public static final ResourceLocation CAP_ID = new ResourceLocation("heavybullet", "dockyard");
    public static final Capability<PlayerDockyardData> DOCKYARD_CAP = CapabilityManager.get(new CapabilityToken<>(){});
    private final PlayerDockyardData instance = new PlayerDockyardData();

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == DOCKYARD_CAP ? LazyOptional.of(() -> instance).cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return instance.getDockyardData();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        instance.getDockyardData().merge(nbt);
    }
}