package com.cb2495.verityconfig.client;

import com.cb2495.verityconfig.VerityConfig;
import com.cb2495.verityconfig.util.Log;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

/**
 * 在已经打开的界面上叠一层中文 Ollama 进度面板，并关掉 Verity 自己那份。
 * <p>对应 Verity 原来的 {@code ScreenEvent.Render.Post} 钩子。单独开一个顶层类，
 * 是因为 {@code @Mod.EventBusSubscriber} 只扫描模组自己的顶层类，
 * 写成 {@link OllamaHudTranslator} 的内部类注册不上（之前踩过这个坑）。
 */
@Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT)
public final class OllamaHudScreenHook {

    private OllamaHudScreenHook() {}

    /** 每帧都会跑，出错只记一次日志。 */
    private static boolean warned;

    /**
     * 定期把 Verity 自己的面板关掉。
     * <p>真正的防线是 {@link #suppressBeforeRender}（每次绘制前压制）；
     * 这里只是兜底，覆盖「长时间不刷界面」的情况，比如开着游戏内 HUD 时。
     * <p>中文面板的显示状态由 {@link OllamaHudTranslator} 自己的 {@code hudEnabled} 维护，
     * 和这个字段无关。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++ticks % 20 != 0) return;
        OllamaHudTranslator.suppressVerityHud();
    }

    private static int ticks;

    /**
     * 接管 Verity 的「显示/隐藏进度面板」快捷键。
     * <p>原版按键会调 {@code OllamaProgressHud.toggle()} 翻转它自己的 {@code visible}，
     * 但那个字段被我们一直压成 false，翻上去 20 tick 内又会被压回来——等于按键失灵。
     * <p>这里在按键之后把它翻回 false，改成翻转我们自己那个开关，并给一条提示，
     * 行为和原版一致（原版按下后也会在聊天栏提示）。
     */
    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Post event) {
        if (!OllamaHudTranslator.isTakenOver()) return;
        Screen screen = event.getScreen();
        // 聊天栏和输入框里不抢按键，原版也是这么排除的
        if (screen instanceof ChatScreen) return;
        if (screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox) return;
        if (!isToggleKey(event.getKeyCode(), event.getScanCode())) return;

        // Verity 自己的监听已经把这个字段翻成 true 了，翻回去，保持压制
        forceVerityHudHidden();

        boolean on = OllamaHudTranslator.toggleHud();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            // 两句都取自 hardcode_ts.txt 的 OllamaProgressHud.java 一节
            String text = on
                    ? "已显示 Ollama 下载进度"
                    : "Ollama 下载进度已隐藏 - 按 " + keyName() + " 再次显示";
            minecraft.player.displayClientMessage(
                    Component.literal(text).withStyle(ChatFormatting.YELLOW), true);
        }
    }

    /** 快捷键的显示名，取自 Verity 自己，改了键位这里会跟着变。 */
    private static String keyName() {
        try {
            Class<?> hud = Class.forName("varmite.verity.client.gui.OllamaProgressHud");
            Object name = hud.getMethod("keyName").invoke(null);
            return name == null ? "快捷键" : String.valueOf(name);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "快捷键";
        }
    }

    /** 这个键是不是 Verity 的进度面板快捷键。 */
    private static boolean isToggleKey(int keyCode, int scanCode) {
        try {
            Class<?> registry = Class.forName("varmite.verity.client.KeybindRegistry");
            Field field = registry.getField("TOGGLE_OLLAMA_PROGRESS");
            Object mapping = field.get(null);
            if (mapping instanceof net.minecraft.client.KeyMapping keyMapping) {
                return keyMapping.matches(keyCode, scanCode);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 拿不到就当作不是，不影响别的功能
        }
        return false;
    }

    /** 把 Verity 的 {@code visible} 直接写成 false。 */
    private static void forceVerityHudHidden() {
        try {
            Class<?> hud = Class.forName("varmite.verity.client.gui.OllamaProgressHud");
            Field visible = hud.getDeclaredField("visible");
            visible.setAccessible(true);
            visible.setBoolean(null, false);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 忽略：下一轮 suppressVerityHud 仍会处理
        }
    }

    /**
     * 每帧、每次界面绘制之前，把 Verity 的面板强行压回去。
     * <p><b>为什么必须在绘制前</b>：Verity 的 {@code onScreenRender} 会先看
     * {@code visible} 再决定画不画；而它的 {@code onScreenKey} 在按下快捷键时
     * 会把这个字段翻成 true。如果只在 tick 里压制，最多有 20 tick 的窗口
     * 让英文面板露出来。
     * <p>用 {@code HIGHEST} 保证我们在 Verity 的同名事件之前执行，
     * 这样无论玩家怎么按快捷键，那一帧看到的都是我们压过的状态。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void suppressBeforeRender(ScreenEvent.Render.Post event) {
        if (!OllamaHudTranslator.isTakenOver()) return;
        OllamaHudTranslator.suppressVerityHud();
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        try {
            Screen screen = event.getScreen();
            if (screen == null) return;
            // 在本地 AI 设置界面上挪到右下角：它自己那套标题、状态行、进度条
            // 都挤在顶部，面板放右上角会和它们叠在一起
            boolean setup = screen instanceof OllamaSetupScreenZh;
            OllamaHudTranslator.renderOverScreen(event.getGuiGraphics(), screen, screen.width, setup);
        } catch (RuntimeException e) {
            if (warned) return;
            warned = true;
            Log.warn("绘制中文 Ollama 进度面板出错: " + e);
        }
    }
}
