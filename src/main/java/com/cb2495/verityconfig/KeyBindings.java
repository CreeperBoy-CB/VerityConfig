package com.cb2495.verityconfig;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT)
public class KeyBindings {

    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "打开 Verity 配置",                 // 显示名称
            InputConstants.Type.KEYSYM,         // 按键类型，不能是鼠标
            GLFW.GLFW_KEY_BACKSLASH,            // 默认反斜杠键
            "Verity AI 配置工具"                // 类别
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG);
        // 调试输出，确认注册
        System.out.println("[VerityConfig] Key mapping registered: " + OPEN_CONFIG.getName());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // 避免在配置界面重复打开
        if (mc.screen instanceof VerityConfigScreen) return;

        while (OPEN_CONFIG.consumeClick()) {
            mc.setScreen(new VerityConfigScreen());
        }
    }
}