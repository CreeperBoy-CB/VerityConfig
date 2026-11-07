package com.cb2495.verityconfig.client;

import com.cb2495.verityconfig.VerityConfig;
import com.cb2495.verityconfig.util.Log;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 接管 Verity 在「§Mods」列表里的 Config 按钮。
 * <p>verity 用 {@code MinecraftForge.registerConfigScreen(...)} 把自己的 YACL 界面
 * 注册给了 Forge；<b>Forge 这个注册点只有一份</b>，后注册的会覆盖先注册的。
 * 所以只要本模组在 FML 客户端初始化阶段注册一次
 * {@link ConfigScreenHandler.ConfigScreenFactory}，玩家点 Verity 的「Config」
 * 看到的就是 {@link VerityConfigScreenFactory} 构建的中文界面。
 * <p>用 {@link ModLoadingContext#registerExtensionPoint} 而不是直接调
 * {@code MinecraftForge.registerConfigScreen}：它挂在 Forge 自己的入口上，
 * 两种注册最终落到同一个位置，覆盖关系与时机都照常。
 * <p>不满足条件时什么都不做，玩家仍然看到 Verity 原来的英文界面。
 */
@Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VerityConfigScreenHook {

    private VerityConfigScreenHook() {}

    /** Verity 的 modId。 */
    private static final String VERITY_MOD_ID = "verity";

    /** 是否已经接管过，避免重复注册与重复日志。 */
    private static boolean hooked;

    @SuppressWarnings("removal") // registerExtensionPoint 在 1.20.1 上就是标准写法
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (hooked) return;
        try {
            if (!ModList.get().isLoaded(VERITY_MOD_ID)) return;
            // Verity 装了但配置还读不到（配置类变了、或者还没初始化）：不接管
            if (VeritySpec.spec() == null) {
                Log.info("Verity 已加载但读不到它的配置，保留原配置界面");
                return;
            }
            ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (minecraft, previous) -> screen(previous)));
            hooked = true;
            Log.info("已接管 Verity 的配置界面，将显示中文版");
        } catch (RuntimeException | LinkageError e) {
            Log.warn("接管 Verity 配置界面失败，保留原界面: " + e);
        }
    }

    /**
     * 构建中文界面。
     * <p>单独包一层是为了在中文界面建不出来时退回 Verity 自己的界面，
     * 而不是把玩家扔回主菜单：{@link VerityConfigScreenFactory#create} 返回
     * {@code null} 表示「建不出来」。
     */
    private static Screen screen(Screen previous) {
        Screen chinese = VerityConfigScreenFactory.create(previous);
        return chinese != null ? chinese : verityScreen(previous);
    }

    /**
     * 调 Verity 自己的界面工厂。
     * <p>只有中文界面建不出来时才会走到这里；反射失败就返回 {@code null}，
     * 让 Forge 走它自己的兜底。
     */
    private static Screen verityScreen(Screen previous) {
        try {
            Class<?> clazz = Class.forName("varmite.verity.VerityConfigUI");
            var method = clazz.getMethod("createYACLScreen", Screen.class);
            Object built = method.invoke(null, previous);
            return built instanceof Screen screen ? screen : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.warn("退回 Verity 原配置界面失败: " + e);
            return null;
        }
    }
}
