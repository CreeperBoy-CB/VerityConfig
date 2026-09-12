package com.cb2495.verityconfig;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("removal")
@Mod(VerityConfig.MODID)
public class VerityConfig {
    public static final String MODID = "verityconfig";
    public static final String MODPACK_VERSION = "7.0"; // 整合包版本，可修改

    public VerityConfig() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
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

    /**
     * 扫描 mods 文件夹，提取版本信息并写入 ModsListScreen 的静态字段
     */
    public static void loadVersions() {
        ModsListScreen.modpackVersion = MODPACK_VERSION;
        ModsListScreen.verityVersion = "未知";
        ModsListScreen.configModVersion = "未知";

        Path modsDir = FMLPaths.MODSDIR.get();
        File dir = modsDir.toFile();
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("[Verity配置工具]")) {
                ModsListScreen.configModVersion = extractVersion(name);
            } else if (name.startsWith("[Verity]")) {
                ModsListScreen.verityVersion = extractVersion(name);
            }
        }
    }

    private static String extractVersion(String filename) {
        Pattern quotePattern = Pattern.compile("['\"]([^'\"]*)['\"]");
        Matcher matcher = quotePattern.matcher(filename);
        if (matcher.find()) {
            return matcher.group(1);
        }
        Pattern versionPattern = Pattern.compile("(\\d+(?:\\.\\d+)+)");
        Matcher versionMatcher = versionPattern.matcher(filename);
        if (versionMatcher.find()) {
            return versionMatcher.group(1);
        }
        return "未知";
    }
}