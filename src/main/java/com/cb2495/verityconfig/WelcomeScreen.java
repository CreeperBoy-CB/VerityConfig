package com.cb2495.verityconfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WelcomeScreen extends Screen {

    private final String modpackVersion = "6.5.1";   // 整合包版本，可修改
    private String verityVersion = "未知";
    private String configModVersion = "未知";

    // 新增：内存警告文字
    private String memoryWarning = null;

    public WelcomeScreen() {
        super(Component.literal("欢迎"));
        loadModVersions();
        checkMemory(); // 新增：检测内存
    }

    // 新增：检测JVM最大内存
    private void checkMemory() {
        long maxMemoryBytes = Runtime.getRuntime().maxMemory();
        long maxMemoryMB = maxMemoryBytes / (1024 * 1024);
        if (maxMemoryMB < 2048) {
            memoryWarning = "警告：当前 JVM 内存不足 2048 MB，可能影响游戏体验";
        }
    }

    private Button solutionButton; // 解决方法按钮

    private void loadModVersions() {
        Path modsDir = FMLPaths.MODSDIR.get();
        File dir = modsDir.toFile();

        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("[Verity配置工具]")) {
                configModVersion = extractVersion(name);
            } else if (name.startsWith("[Verity]")) {
                verityVersion = extractVersion(name);
            }
        }
    }

    private String extractVersion(String filename) {
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

    @Override
    protected void init() {
        // 创建“点击此处获取解决方法”按钮（仅内存不足时显示）
        this.solutionButton = Button.builder(Component.literal("点击此处获取解决方法"), btn -> {
            Minecraft.getInstance().setScreen(new HelperScreen());
        }).pos(this.width / 2 - 100, this.height / 2 + 6).size(200, 20).build();
        this.solutionButton.visible = memoryWarning != null; // 无警告时隐藏
        this.addRenderableWidget(this.solutionButton);

        int centerX = this.width / 2;
        this.addRenderableWidget(Button.builder(Component.literal("继续"), btn -> {
            ModsListScreen.modpackVersion = this.modpackVersion;
            ModsListScreen.verityVersion = this.verityVersion;
            ModsListScreen.configModVersion = this.configModVersion;
            ModsListScreen.firstTimeSetup = true;
            Minecraft.getInstance().setScreen(new VerityConfigScreen());
        }).pos(centerX - 50, this.height - 50).size(100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int centerX = this.width / 2;

        // ===== 大字标题：Verity 整合包（金色，2倍缩放，严格居中） =====
        String title = "Verity 整合包";
        int titleWidth = this.font.width(title);
        int scaledTitleWidth = titleWidth * 2;
        int titleX = centerX - scaledTitleWidth / 2;
        int titleY = this.height / 2 - 40;

        // 阴影
        graphics.pose().pushPose();
        graphics.pose().translate(titleX + 2, titleY + 2, 0);
        graphics.pose().scale(2.0f, 2.0f, 1.0f);
        graphics.drawString(this.font, title, 0, 0, 0x55000000, false);
        graphics.pose().popPose();

        // 金色主体
        graphics.pose().pushPose();
        graphics.pose().translate(titleX, titleY, 0);
        graphics.pose().scale(2.0f, 2.0f, 1.0f);
        graphics.drawString(this.font, title, 0, 0, 0xFFFFD700, false);
        graphics.pose().popPose();

        // ===== 版本号（白色，紧贴标题下方） =====
        graphics.drawCenteredString(this.font, modpackVersion, centerX, this.height / 2 - 20, 0xFFFFFF);

        // ===== 内存警告（红色，版本号下方） =====
        if (memoryWarning != null) {
            graphics.drawCenteredString(this.font, memoryWarning, centerX, this.height / 2 - 6, 0xFFFF5555);
        }

// ===== 作者信息（灰色，位置动态调整） =====
        int authorY = memoryWarning != null ? this.height / 2 + 32 : this.height / 2 + 12;
        graphics.drawCenteredString(this.font, "作者 @CB2495", centerX, authorY, 0xFFAAAAAA);

// ===== 版本信息（灰色，位置动态调整） =====
        String versionInfo = "Verity 模组版本: " + verityVersion + "  |  配置工具版本: " + configModVersion;
        int versionInfoY = memoryWarning != null ? this.height / 2 + 46 : this.height / 2 + 26;
        graphics.drawCenteredString(this.font, versionInfo, centerX, versionInfoY, 0xFFAAAAAA);

        // ===== 底部提示文字（灰色） =====
        graphics.drawCenteredString(this.font, "点击 继续 开始配置", centerX, this.height - 12, 0xFFAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}