package com.cb2495.verityconfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


public class WelcomeScreen extends Screen {

    // 新增：内存警告文字
    private String memoryWarning = null;

    public WelcomeScreen() {
        super(Component.literal("欢迎"));
        VerityConfig.loadVersions(); // 使用统一版本加载
        checkMemory();
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
            ModsListScreen.firstTimeSetup = true; // 标记为首次启动流程
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
        graphics.drawCenteredString(this.font, ModsListScreen.modpackVersion, centerX, this.height / 2 - 20, 0xFFFFFF);

        // ===== 内存警告（红色，版本号下方） =====
        if (memoryWarning != null) {
            graphics.drawCenteredString(this.font, memoryWarning, centerX, this.height / 2 - 6, 0xFFFF5555);
        }

// ===== 作者信息（灰色，位置动态调整） =====
        int authorY = memoryWarning != null ? this.height / 2 + 32 : this.height / 2 + 12;
        graphics.drawCenteredString(this.font, "作者 @CB2495", centerX, authorY, 0xFFAAAAAA);

// ===== 版本信息（灰色，位置动态调整） =====
        String versionInfo = "Verity 模组版本: " + ModsListScreen.verityVersion + "  |  配置工具版本: " + ModsListScreen.configModVersion;
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