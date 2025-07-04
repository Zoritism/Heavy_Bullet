package com.zoritism.heavybullet.network;

import com.zoritism.heavybullet.item.FlashlightItem;
import com.zoritism.heavybullet.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundToggleFlashlightPacket {

    public static void encode(ServerboundToggleFlashlightPacket pkt, FriendlyByteBuf buf) {}
    public static ServerboundToggleFlashlightPacket decode(FriendlyByteBuf buf) {
        return new ServerboundToggleFlashlightPacket();
    }

    public static void handle(ServerboundToggleFlashlightPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Определяем, в какой руке находится фонарик
            InteractionHand hand = null;
            ItemStack stack = ItemStack.EMPTY;

            if (player.getMainHandItem().getItem() instanceof FlashlightItem) {
                hand = InteractionHand.MAIN_HAND;
                stack = player.getMainHandItem();
            } else if (player.getOffhandItem().getItem() instanceof FlashlightItem) {
                hand = InteractionHand.OFF_HAND;
                stack = player.getOffhandItem();
            } else {
                return; // В руках нет фонарика
            }

            FlashlightItem item = (FlashlightItem) stack.getItem();
            boolean isOn = item.isOn();
            int energy = stack.getOrCreateTag().getInt("Energy");

            // Если фонарик выключен и энергии нет — ничего не делаем
            if (!isOn && energy <= 0) {
                return;
            }

            // Готовим замену, устраняем неоднозначность через .get() и .getItem()
            ItemStack replacement = isOn
                    ? new ItemStack(ModItems.ENERGY_FLASHLIGHT.get())
                    : new ItemStack(ModItems.ENERGY_FLASHLIGHT_ON.get());
            replacement.getOrCreateTag().putInt("Energy", Math.max(0, energy));

            player.setItemInHand(hand, replacement);
        });
        ctx.get().setPacketHandled(true);
    }
}