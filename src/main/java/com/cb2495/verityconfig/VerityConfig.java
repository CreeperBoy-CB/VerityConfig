package com.cb2495.verityconfig;

import com.cb2495.verityconfig.config.GeneralSettings;
import com.cb2495.verityconfig.config.ModConfig;
import com.cb2495.verityconfig.config.VerityConfigManager;
import com.cb2495.verityconfig.screen.ModsListScreen;
import com.cb2495.verityconfig.screen.RestartPrompt;
import com.cb2495.verityconfig.screen.WelcomeScreen;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("removal")
@Mod(VerityConfig.MODID)
public class VerityConfig {
    public static final String MODID = "verityconfig";
    public static final String MODPACK_VERSION = "6.10"; // 整合包版本，可修改

    public VerityConfig() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        // 首次启动时生成默认配置，让玩家能在 config 目录里直接看到可改的选项。
        // 用 enqueueWork 切回主线程：FMLClientSetupEvent 在并行线程上触发，
        // 此时直接取 Minecraft 实例可能拿到未初始化完成的 gameDirectory。
        event.enqueueWork(() -> {
            ModConfig.createDefaultIfMissing();
            // 两份联动配置（verity-common.toml + simple_translate）缺任一份时，
            // 用内置模板补齐；已存在的文件不会被覆盖。
            // 必须排在下面的预读之前：预读会立刻读盘，晚了就读到空配置
            VerityConfigManager.copyTemplatesIfMissing();
            // 后台预读两份配置。界面的 init() 需要同步拿到值来填充控件，
            // 提前读到内存之后，渲染线程就不必再为读配置碰磁盘。
            VerityConfigManager.preload();
            GeneralSettings.preload();
        });
    }

    /** 配置自检是否已得到结论：主界面会被反复打开（取消重启、从存档退出等）。 */
    private static final AtomicBoolean TRANSLATE_RESYNC_SETTLED = new AtomicBoolean(false);

    @SubscribeEvent
    public void onTitleScreenOpen(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            // 简单翻译的 Key 为空、而 Verity 的有：按 Verity 的配置补写，
            // 补过就要重启才生效。放在欢迎页判断之前，否则首次启动会被欢迎页挡住。
            // 只有补写过才置位：置位后不再重复处理，未补写则下次进来还能再查
            if (!TRANSLATE_RESYNC_SETTLED.get()
                    && VerityConfigManager.resyncTranslateIfKeyMissing()) {
                TRANSLATE_RESYNC_SETTLED.set(true);
                Minecraft.getInstance().tell(() -> RestartPrompt.show(
                        "重启游戏以应用配置",
                        "翻译模组配置出现错误，现已修复，需要重启以应用修复，是否重启？"));
                return;
            }
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
        // modpackVersion 的默认值由 ModsListScreen 的字段初始化给出
        // （取自 MODPACK_VERSION），这里不再重复赋值。
        // 下面两个必须扫目录才知道，先退回「未知」
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