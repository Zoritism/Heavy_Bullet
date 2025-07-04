package com.zoritism.heavybullet.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ModConfigHandler {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        COMMON = new Common(builder);
        COMMON_SPEC = builder.build();
    }

    public static class Common {
        public final ForgeConfigSpec.IntValue maxEnergy;
        public final ForgeConfigSpec.IntValue energyPerTick;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.push("Flashlight Settings");

            maxEnergy = builder
                    .comment("Максимальный заряд фонарика (в RF)")
                    .defineInRange("maxEnergy", 200000, 1000, Integer.MAX_VALUE);

            energyPerTick = builder
                    .comment("Расход энергии включённого фонаря в RF за тик")
                    .defineInRange("energyPerTick", 100, 0, Integer.MAX_VALUE);

            builder.pop();
        }
    }
}
