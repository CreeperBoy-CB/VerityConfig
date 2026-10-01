package com.cb2495.verityconfig.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 存档缩略图的加载与缓存。
 * <p>存档目录下的 icon.png 是缩略图；没有或读不出来时用原版的
 * 未知存档图标顶替，界面左上角始终有一格图，不会出现空缺。
 * <p>这里不用 {@code FaviconTexture} —— 它的 upload 要求图片正好
 * 64×64，否则直接抛异常，而玩家存档里的 icon.png 尺寸并不固定。
 * 自己建 {@link DynamicTexture} 可以接受任意尺寸，绘制时按目标
 * 大小缩放即可。
 * <p>整张图只读一次：界面每帧都会画，不能在渲染里读盘。
 */
@SuppressWarnings("removal")
public final class SaveIconCache {

    private SaveIconCache() {}

    /** 原版自带的未知存档图标，用作缺省值。 */
    public static final ResourceLocation DEFAULT_ICON =
            new ResourceLocation("textures/misc/unknown_server.png");

    /** 原版 unknown_server.png 的尺寸，缺省图标按它归一化。 */
    private static final int DEFAULT_ICON_SIZE = 32;

    /** 缓存：存档目录 -> 已注册的贴图；值为 null 表示读过但没有可用图标。 */
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    /** 已加载贴图的真实尺寸，绘制时做 uv 归一化必须用真实值。 */
    private static final Map<String, int[]> SIZES = new HashMap<>();

    /**
     * 取得某个存档的缩略图。
     * <p>必须保证贴图已注册，所以只在实际要画的时候调用；重复调用
     * 命中缓存，不会重复读盘。
     *
     * @param saveDir 存档目录
     * @return 可用的贴图位置；没有缩略图时返回 {@link #DEFAULT_ICON}
     */
    public static ResourceLocation get(Path saveDir) {
        if (saveDir == null) {
            return DEFAULT_ICON;
        }
        String key = key(saveDir);
        // 用 containsKey 而不是 get != null：读失败的存档也要记住，
        // 否则每帧都会重新尝试读一次盘
        if (CACHE.containsKey(key)) {
            ResourceLocation cached = CACHE.get(key);
            return cached == null ? DEFAULT_ICON : cached;
        }

        ResourceLocation loaded = load(saveDir, key);
        CACHE.put(key, loaded);
        return loaded == null ? DEFAULT_ICON : loaded;
    }

    /**
     * 贴图的真实宽度。
     * <p>绘制时 uv 归一化要用它；没有缩略图时给默认图标的尺寸 32。
     */
    public static int getWidth(Path saveDir) {
        int[] size = saveDir == null ? null : SIZES.get(key(saveDir));
        return size == null ? DEFAULT_ICON_SIZE : size[0];
    }

    /** 贴图的真实高度；语义同 {@link #getWidth}。 */
    public static int getHeight(Path saveDir) {
        int[] size = saveDir == null ? null : SIZES.get(key(saveDir));
        return size == null ? DEFAULT_ICON_SIZE : size[1];
    }

    private static String key(Path saveDir) {
        return saveDir.toAbsolutePath().normalize().toString();
    }

    /** 真正读图并注册贴图；任何失败都返回 null，由调用方退回默认图标。 */
    private static ResourceLocation load(Path saveDir, String key) {
        Path iconFile = saveDir.resolve("icon.png");
        if (!Files.isRegularFile(iconFile)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(iconFile)) {
            NativeImage image = NativeImage.read(in);
            int width = image.getWidth();
            int height = image.getHeight();
            // 贴图名不能带路径分隔符与非法字符，用哈希代替原始路径
            ResourceLocation location = new ResourceLocation(
                    com.cb2495.verityconfig.VerityConfig.MODID,
                    "save_icon/" + Integer.toHexString(key.hashCode()));
            DynamicTexture texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(location, texture);
            // 记下真实尺寸：绘制时 uv 归一化要用，且 DynamicTexture 已经
            // 接管了 image，这里只存数值不再碰它
            SIZES.put(key, new int[]{width, height});
            return location;
        } catch (Exception e) {
            // 图标坏掉不该影响导入功能，安静地退回默认图标
            return null;
        }
    }

    /**
     * 清空缓存并释放显存。
     * <p>离开导入界面时调用：存档可能被导入或删除，下次进来重新读，
     * 同时避免长期占着用不到的贴图。
     */
    public static void clear() {
        for (ResourceLocation location : CACHE.values()) {
            if (location == null) {
                continue;
            }
            try {
                Minecraft.getInstance().getTextureManager().release(location);
            } catch (Exception ignored) {
                // 释放失败不影响流程
            }
        }
        CACHE.clear();
        SIZES.clear();
    }
}
