package com.cb2495.verityconfig.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.cb2495.verityconfig.VerityConfig;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@SuppressWarnings("removal")
public class SponsorUtils {

    private static final ResourceLocation SPONSOR_TEXTURE = new ResourceLocation(VerityConfig.MODID, "textures/sponsor_qr.png");

    /**
     * 打开保存对话框，将赞赏码图片保存到用户选择的位置。
     * 应在非渲染线程调用。
     */
    public static void openSaveDialog() {
        new Thread(() -> {
            try {
                System.setProperty("java.awt.headless", "false");
                FileDialog dialog = new FileDialog((Frame) null, "保存赞赏码", FileDialog.SAVE);
                dialog.setFile("sponsor_qr.png");
                dialog.setVisible(true);
                String directory = dialog.getDirectory();
                String file = dialog.getFile();
                if (directory != null && file != null) {
                    Path target = Paths.get(directory, file);
                    saveSponsorImageTo(target);
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.displayClientMessage(
                                    Component.literal("赞赏码已保存到 " + target), false);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 将资源中的赞赏码图片复制到目标路径。
     */
    private static void saveSponsorImageTo(Path target) throws Exception {
        var resource = Minecraft.getInstance().getResourceManager().getResource(SPONSOR_TEXTURE).orElseThrow();
        try (InputStream is = resource.open()) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}