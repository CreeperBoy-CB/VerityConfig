package com.cb2495.verityconfig.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 彩蛋资源的内存缓存。
 * <p>欢迎界面加载时把所有用到的图片一次性读进显存并缓存好，
 * 这样点击版本号触发彩蛋时不会出现「点了半天图才出来」的卡顿。
 * <p>加载失败不会抛异常，只是把该项记为不可用：彩蛋坏掉不该影响
 * 正常进入配置流程。
 */
@SuppressWarnings("removal")
public final class EasterEggAssets {
    private EasterEggAssets() {}

    /** 资源根路径（图片所在目录）。 */
    private static final String BASE = "easteregg/";

    /** 小图：点击版本号后在屏幕中央淡入的那张。 */
    public static final String KID = "67-kid";
    /** 大图：播放 67.ogg 时全屏显示的那张。 */
    public static final String ANGRY_BIRD = "67_angry_bird_hearing";

    private record Cached(ResourceLocation texture, int width, int height) {
    }

    private static final Map<String, Cached> CACHE = new HashMap<>();

    /** 是否已经加载过；避免每次进入欢迎界面都重新读盘。 */
    private static boolean loaded = false;

    /**
     * 把所有彩蛋图片读入内存。
     * <p>只执行一次。重复调用直接返回，因此可以放心地放在每次
     * 进入欢迎界面时调用。
     */
    public static void loadAll() {
        if (loaded) return;
        loaded = true;
        load(KID);
        load(ANGRY_BIRD);
    }

    private static void load(String name) {
        String path = BASE + name + ".png";
        ResourceLocation location = new ResourceLocation(
                com.cb2495.verityconfig.VerityConfig.MODID, path);
        try {
            Resource resource = Minecraft.getInstance().getResourceManager()
                    .getResource(location).orElseThrow();
            try (InputStream is = resource.open()) {
                // 自己读字节再交给 NativeImage：DynamicTexture 会接管
                // NativeImage 的所有权，不能读两次，所以顺手把尺寸记下来
                NativeImage image = NativeImage.read(new ByteArrayInputStream(is.readAllBytes()));
                int width = image.getWidth();
                int height = image.getHeight();
                DynamicTexture texture = new DynamicTexture(image);
                texture.setBlurMipmap(false, false);
                ResourceLocation registered = Minecraft.getInstance().getTextureManager()
                        .register("verityconfig_easteregg_" + name, texture);
                CACHE.put(name, new Cached(registered, width, height));
            }
        } catch (Exception e) {
            System.err.println("[VerityConfig] 彩蛋图片加载失败: " + location);
            e.printStackTrace();
        }
    }

    /** 该图片是否可用（资源存在且已成功读入）。 */
    public static boolean isReady(String name) {
        return CACHE.containsKey(name);
    }

    public static ResourceLocation texture(String name) {
        Cached cached = CACHE.get(name);
        return cached == null ? null : cached.texture();
    }

    public static int width(String name) {
        Cached cached = CACHE.get(name);
        return cached == null ? 0 : cached.width();
    }

    public static int height(String name) {
        Cached cached = CACHE.get(name);
        return cached == null ? 0 : cached.height();
    }
}
