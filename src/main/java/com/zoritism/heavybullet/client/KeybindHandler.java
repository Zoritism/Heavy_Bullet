package com.zoritism.heavybullet.client;

import com.zoritism.heavybullet.network.NetworkHandler;
import com.zoritism.heavybullet.network.ServerboundToggleFlashlightPacket;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import org.lwjgl.glfw.GLFW;


@Mod.EventBusSubscriber(modid = "heavybullet", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class KeybindHandler {
    public static final String CATEGORY = "key.categories.flashlight";
    public static KeyMapping toggleFlashlightKey;

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        toggleFlashlightKey = new KeyMapping("key.flashlight.toggle", GLFW.GLFW_KEY_F, CATEGORY);
        event.register(toggleFlashlightKey);
        MinecraftForge.EVENT_BUS.register(KeyInputHandler.class);
    }

    public static class KeyInputHandler {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (toggleFlashlightKey != null && toggleFlashlightKey.consumeClick()) {
                NetworkHandler.CHANNEL.sendToServer(new ServerboundToggleFlashlightPacket());
            }
        }
    }
}