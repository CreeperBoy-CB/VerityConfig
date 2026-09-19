package com.cb2495.verityconfig.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.cb2495.verityconfig.VerityConfig;

import javax.swing.UIManager;
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

    /** 确保外观只初始化一次，避免重复设置导致已创建的窗口样式错乱。 */
    private static boolean lookAndFeelInitialized = false;

    /**
     * 将 Swing/AWT 外观切换为系统默认（Windows 下即 Win7+ 的 Aero 风格）。
     * <p>不设置时 Java 会退回 Metal/Classic 外观，弹窗会呈现 XP 时代的方块样式。
     * 必须在创建任何窗口之前调用。
     */
    private static synchronized void initLookAndFeel() {
        if (lookAndFeelInitialized) return;
        lookAndFeelInitialized = true;
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // 换成系统外观失败不应影响保存功能，退回默认外观即可
            System.err.println("[VerityConfig] 无法应用系统外观，使用默认外观: " + e.getMessage());
        }
    }

    /**
     * 打开保存对话框，将赞赏码图片保存到用户选择的位置。
     * 应在非渲染线程调用。
     */
    public static void openSaveDialog() {
        new Thread(() -> {
            try {
                System.setProperty("java.awt.headless", "false");
                initLookAndFeel();
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