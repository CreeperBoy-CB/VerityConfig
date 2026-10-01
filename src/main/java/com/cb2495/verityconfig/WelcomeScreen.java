package com.cb2495.verityconfig;

import com.cb2495.verityconfig.util.EasterEggAssets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


public class WelcomeScreen extends Screen {

    // 新增：内存警告文字
    private String memoryWarning = null;

    /** 版本号上的彩蛋。 */
    private final EasterEggController easterEgg = new EasterEggController();

    public WelcomeScreen() {
        super(Component.literal("欢迎"));
        VerityConfig.loadVersions(); // 使用统一版本加载
        checkMemory();
        // 进入欢迎界面就把彩蛋资源读进内存，点击时才不会有加载卡顿
        EasterEggAssets.loadAll();
    }

    /**
     * 版本号在屏幕上的矩形，用于点击检测。
     * <p>渲染时记录、点击时读取，避免两处各写一遍坐标而算岔。
     */
    private int[] versionRect = null;

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
        // 点过之后染成淡蓝色，暗示这里还藏着东西
        int versionY = this.height / 2 - 20;
        int versionColor = easterEgg.isVersionClicked()
                ? EasterEggController.VERSION_CLICKED_COLOR
                : 0xFFFFFF;
        graphics.drawCenteredString(this.font, ModsListScreen.modpackVersion, centerX, versionY, versionColor);
        // 记录版本号区域，供点击检测使用
        int versionWidth = this.font.width(ModsListScreen.modpackVersion);
        this.versionRect = new int[]{
                centerX - versionWidth / 2, versionY,
                centerX + versionWidth / 2, versionY + this.font.lineHeight
        };

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

        // 按钮等控件由 super.render 绘制，必须放在它之前调用；
        // 彩蛋则要盖住这些控件，所以放到 super.render 之后
        super.render(graphics, mouseX, mouseY, partialTick);

        easterEgg.render(graphics, this.width, this.height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 演出期间吞掉全部点击，避免误触「继续」跳到下一个界面
        if (easterEgg.blocksInput()) {
            return true;
        }
        if (button == 0 && !isOverAnyWidget(mouseX, mouseY)) {
            // 小图显形后，点击范围收窄到小图本身：超出的点击不增加不透明度。
            // 按钮仍排除在外 —— 小图会滞留十几秒，若连按钮也被吃掉，
            // 用户想进配置界面就得干等它淡完。
            if (easterEgg.isKidVisible()) {
                return easterEgg.isInsideKid(mouseX, mouseY)
                        && easterEgg.onClickVersion();
            }
            if (versionRect != null
                    && mouseX >= versionRect[0] && mouseX <= versionRect[2]
                    && mouseY >= versionRect[1] && mouseY <= versionRect[3]) {
                return easterEgg.onClickVersion();
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 鼠标是否落在某个按钮上（用于把按钮从彩蛋的整屏点击里让出来）。 */
    private boolean isOverAnyWidget(double mouseX, double mouseY) {
        for (var renderable : this.renderables) {
            if (renderable instanceof net.minecraft.client.gui.components.AbstractWidget widget
                    && widget.visible
                    && widget.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        // 离开本界面时停掉彩蛋音乐，否则会在配置界面继续响
        easterEgg.onScreenClosed();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}