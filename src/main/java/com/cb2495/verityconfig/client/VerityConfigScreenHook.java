package com.cb2495.verityconfig.client;

import com.cb2495.verityconfig.VerityConfig;
import com.cb2495.verityconfig.util.Log;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 接管 Verity 在「§Mods」列表里的 Config 按钮。
 *
 * <p><b>关键：扩展点要挂到 Verity 自己的 ModContainer 上，不是我们的。</b>
 *
 * <p>Forge 的「Mods → Config」按钮是这样找界面的（{@code ModListScreen.displayModConfig}）：
 * <pre>{@code
 * ConfigScreenHandler.getScreenFactoryFor(选中的 mod 的 IModInfo)
 * }</pre>
 * 而 {@code getScreenFactoryFor} 内部是：
 * <pre>{@code
 * ModList.get().getModContainerById(modInfo.getModId())
 *        .flatMap(container -> container.getCustomExtension(ConfigScreenFactory.class))
 * }</pre>
 * 也就是说，<b>它按「哪个 mod 被选中」去查那个 mod 的容器</b>。
 * 选中 Verity 时 {@code modId} 是 {@code "verity"}，只会查到 Verity 容器上的扩展点。
 *
 * <p>而 {@code ModLoadingContext.registerExtensionPoint} 写的是
 * {@code getContainer()}——也就是<b>我们自己的</b>容器。所以那样注册的东西
 * 玩家永远看不到：它挂在 verityconfig 名下，而没人会点 verityconfig 的 Config 按钮。
 * （之前的版本就是这样，看起来注册成功了，实际一直显示原版界面。）
 *
 * <p><b>正确做法：</b>用 {@link ModList#getModContainerById} 拿到 Verity 的
 * {@link ModContainer}，直接调它的 {@code registerExtensionPoint}。那个方法内部就是一个
 * {@code Map.put}，没有「已存在则拒绝」的检查，所以能覆盖 Verity 先前注册的那一份。
 *
 * <p>注册时机放在 {@code FMLClientSetupEvent}，且用 {@code LOWEST} 优先级排在最后执行，
 * 这样正常情况下我们写在 Verity 之后，赢下来。但优先级排序不能当作唯一保证——
 * 所以另外补了一道保险（{@link ScreenGuard}）：每次 Mods 列表界面初始化时
 * 无条件再挂一遍，点 Config 按钮时一定是我们这一份。
 */
@Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VerityConfigScreenHook {

    private VerityConfigScreenHook() {}

    /** Verity 的 modId。 */
    private static final String VERITY_MOD_ID = "verity";

    /** 是否已经接管过，避免重复注册与重复日志。 */
    private static boolean hooked;

    /**
     * 在客户端初始化阶段把中文界面挂到 Verity 的容器上。
     *
     * <p>用 {@code LOWEST}：Mod 总线上 Verity 的 {@code onClientSetup} 是默认优先级，
     * 我们排到最后执行，确保它写完之后我们再覆盖。
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onClientSetup(FMLClientSetupEvent event) {
        install();
    }

    /**
     * 把中文界面的工厂挂到 Verity 的 ModContainer 上。
     *
     * @return 是否挂成功
     */
    public static boolean install() {
        if (hooked) return true;
        try {
            if (!ModList.get().isLoaded(VERITY_MOD_ID)) return false;
            // Verity 装了但配置还读不到（配置类改了或者还没初始化）：不接管
            if (VeritySpec.spec() == null) {
                Log.info("Verity 已加载但读不到它的配置，保留原配置界面");
                return false;
            }
            var container = ModList.get().getModContainerById(VERITY_MOD_ID).orElse(null);
            if (container == null) {
                Log.warn("找不到 Verity 的 ModContainer，保留原配置界面");
                return false;
            }
            container.registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (minecraft, previous) -> screen(previous)));
            hooked = true;
            Log.info("已接管 Verity 的配置界面，将显示中文版");
            return true;
        } catch (RuntimeException | LinkageError e) {
            Log.warn("接管 Verity 配置界面失败，保留原界面: " + e);
            return false;
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
        return chinese != null ? chinese : VerityConfigScreenFactory.originalScreen(previous);
    }

    /**
     * 保险：万一 Verity 的注册晚于我们（把我们的覆盖掉了），这里再补一次。
     *
     * <p>钩在「Mods 列表界面打开」这一刻：玩家要点 Config 按钮，必须先有这个界面，
     * 而界面初始化早于任何按钮点击。所以只要在这里补装，点下去时一定是我们的工厂。
     *
     * <p>放在 FORGE 总线上（而不是 Mod 总线）：界面事件只在游戏运行时发生。
     */
    @Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ScreenGuard {

        private ScreenGuard() {}

        @SubscribeEvent
        public static void onScreenInit(net.minecraftforge.client.event.ScreenEvent.Init.Post event) {
            // Mods 列表每次打开都补一次：那个 Map 写入只是 put，重复写同一个工厂无副作用
            if (event.getScreen() instanceof net.minecraftforge.client.gui.ModListScreen) {
                reinstallIfNeeded();
            }
        }
    }

    /**
     * 无条件把中文界面工厂再挂一遍。
     *
     * <p>和 {@link #install()} 的区别：这里不看 {@code hooked}，
     * 因为要救的正是「我们装过、但被 Verity 后注册覆盖掉」的情况。
     */
    public static void reinstallIfNeeded() {
        if (!ModList.get().isLoaded(VERITY_MOD_ID)) return;
        if (VeritySpec.spec() == null) return;
        try {
            var container = ModList.get().getModContainerById(VERITY_MOD_ID).orElse(null);
            if (container == null) return;
            container.registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (minecraft, previous) -> screen(previous)));
        } catch (RuntimeException | LinkageError e) {
            Log.warn("重新接管 Verity 配置界面失败: " + e);
        }
    }
}
