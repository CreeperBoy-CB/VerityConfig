package com.cb2495.verityconfig.screen;

import com.cb2495.verityconfig.VerityConfig;
import com.cb2495.verityconfig.util.Log;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@SuppressWarnings("removal")
public class SponsorScreen extends Screen {
    private static final ResourceLocation SPONSOR_TEXTURE = new ResourceLocation(VerityConfig.MODID, "textures/sponsor_qr.png");

    private ResourceLocation loadedTexture;
    private int imageWidth, imageHeight;   // 原始像素尺寸
    private boolean loadFailed = false;

    public SponsorScreen() {
        super(Component.literal("赞助"));
    }

    @Override
    protected void init() {
        super.init();
        loadSponsorImage();

        // 退出按钮
        this.addRenderableWidget(Button.builder(Component.literal("退出"), btn -> {
            Minecraft.getInstance().setScreen(new VerityConfigScreen());
        }).pos(this.width / 2 - 50, this.height - 40).size(100, 20).build());
    }

    private void loadSponsorImage() {
        // 先释放上一次注册的纹理，见 releaseSponsorImage 的说明
        releaseSponsorImage();
        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(SPONSOR_TEXTURE).orElseThrow();
            try (InputStream is = resource.open()) {
                byte[] bytes = IOUtils.toByteArray(is);
                NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(bytes));
                imageWidth = nativeImage.getWidth();
                imageHeight = nativeImage.getHeight();
                DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
                dynamicTexture.setBlurMipmap(false, false);
                loadedTexture = Minecraft.getInstance().getTextureManager()
                        .register("verityconfig_sponsor", dynamicTexture);
            }
        } catch (Exception e) {
            Log.error("无法加载赞助图片: " + SPONSOR_TEXTURE, e);
            loadFailed = true;
        }
    }

    /**
     * 释放已注册的赞助图片纹理。
     * <p>{@code TextureManager.register(String, DynamicTexture)} 每次调用都会生成一个
     * 新的 {@code dynamic/xxx_N} 名字，<b>不会</b>覆盖上一次注册的同名纹理，
     * 因此旧纹理既不会被替换也不会被自动关闭。不显式 release 的话，
     * 每打开一次界面（init() 也会在窗口缩放时重跑）就会泄漏一份解码后的
     * {@link NativeImage} 堆外内存和对应的显存。
     */
    private void releaseSponsorImage() {
        if (loadedTexture != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(loadedTexture);
            } catch (Exception ignored) {
                // 释放失败不影响界面关闭
            }
            loadedTexture = null;
        }
        imageWidth = 0;
        imageHeight = 0;
    }

    @Override
    public void removed() {
        releaseSponsorImage();
        super.removed();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, "支持主播", this.width / 2, 15, 0xFFFFFF);

        if (loadedTexture != null && imageWidth > 0 && imageHeight > 0) {
            double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            int maxGuiWidth = this.width - 20;
            int maxGuiHeight = this.height - 90; // 预留底部按钮空间

            int guiWidth = (int) Math.ceil(imageWidth / guiScale);
            int guiHeight = (int) Math.ceil(imageHeight / guiScale);

            if (guiWidth > maxGuiWidth || guiHeight > maxGuiHeight) {
                double scaleX = (double) maxGuiWidth / guiWidth;
                double scaleY = (double) maxGuiHeight / guiHeight;
                double scale = Math.min(scaleX, scaleY);
                guiWidth = (int) Math.ceil(guiWidth * scale);
                guiHeight = (int) Math.ceil(guiHeight * scale);
            }

            int x = (this.width - guiWidth) / 2;
            int y = 30 + (maxGuiHeight - guiHeight) / 2;

            float scaleX = (float) guiWidth / imageWidth;
            float scaleY = (float) guiHeight / imageHeight;
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scaleX, scaleY, 1.0f);
            graphics.blit(loadedTexture, 0, 0, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
            graphics.pose().popPose();
        } else {
            graphics.drawCenteredString(this.font, "图片加载失败", this.width / 2, this.height / 2 - 10, 0xFF5555);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new VerityConfigScreen());
    }
}