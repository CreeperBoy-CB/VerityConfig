package com.cb2495.verityconfig;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings("removal")
@Mod(VerityConfig.MODID)
public class VerityConfig {
    public static final String MODID = "verityconfig";

    public VerityConfig() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        // 在客户端初始化完成后，注册事件监听
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTitleScreenOpen(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            Path configFile = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config/verity-common.toml");
            boolean needConfig = true;
            if (Files.exists(configFile)) {
                try {
                    String content = Files.readString(configFile);
                    if (content.contains("apiKey = \"") && !content.contains("apiKey = \"\"")) {
                        needConfig = false;
                    }
                } catch (Exception ignored) {}
            }
            if (needConfig) {
                Minecraft.getInstance().tell(() -> {
                    Minecraft.getInstance().setScreen(new WelcomeScreen());
                });
            }
        }
    }
}
//编译器环境太脆弱了多加一个前置都能崩溃