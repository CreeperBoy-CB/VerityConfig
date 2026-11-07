package com.cb2495.verityconfig.client;

import com.cb2495.verityconfig.util.Log;

import net.minecraft.client.gui.screens.Screen;

/**
 * 让 Verity 原版配置界面的「关闭」回到本模组的中文界面。
 *
 * <p><b>问题。</b>{@code YACLScreen} 的三个出口——「完成」按钮、「取消」按钮、Esc——
 * 最终都只做一件事：
 * <pre>{@code
 * onClose() -> minecraft.setScreen(this.parent)
 * }</pre>
 * {@code parent} 是 {@code YACLScreen} 的私有 final 字段，构造时定死，
 * 没有 setter，{@code init()} 也不会重建它。改按钮的点击回调也不行：
 * 按钮每次 {@code init()} 都重新创建，而且 Esc 走的根本不是按钮。
 *
 * <p><b>做法。</b>拿到界面对象之后，用反射把那个 {@code parent} 字段换成
 * 一个「中转界面」（{@link HopScreen}）。原版界面照常工作、照常保存配置，
 * 只是关闭时落点从「主菜单」变成「中转界面」，而中转界面立刻显示中文界面。
 * 三个出口一次全覆盖，不需要拦截任何事件。
 *
 * <p><b>为什么不拦 {@code ScreenEvent.Opening}：</b>它虽然有 setter，
 * 但 1.20.1 的 Forge 里<b>没有任何地方发出这个事件</b>（整个 Forge jar 中只有
 * 它自己的定义类引用它），注册上去也永远不会被调用。
 *
 * <p><b>写 final 字段的安全性：</b>这里不是「重新定义类」，只是对一个已创建对象的
 * 字段做写入。该字段在 {@code onClose()} 里只被读取一次，读的时候我们早已写好。
 * 反射失败时什么也不做，玩家关掉原版界面仍然回主菜单——只是绕远，不会出错。
 */
public final class OriginalConfigBackHook {

    private OriginalConfigBackHook() {}

    /** {@code YACLScreen} 里那个「上一页」字段名。 */
    private static final String PARENT_FIELD = "parent";

    /** 只在客户端初始化时探测一次，避免每次跳转都去查反射。 */
    private static java.lang.reflect.Field parentField;

    private static boolean probed;

    /** 立即探测字段，失败只是记日志。 */
    static void probe() {
        if (probed) return;
        probed = true;
        try {
            Class<?> clazz = Class.forName("dev.isxander.yacl3.gui.YACLScreen");
            java.lang.reflect.Field field = clazz.getDeclaredField(PARENT_FIELD);
            field.setAccessible(true);
            parentField = field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.warn("读不到 YACL 的 " + PARENT_FIELD + " 字段，原版界面关闭后将回到原来的地方: " + e);
        }
    }

    /**
     * 把原版界面的关闭落点改到 {@code hop}。
     *
     * @param original 原版配置界面
     * @param hop      关闭后要显示的界面
     * @return 是否改成功
     */
    static boolean redirectClose(Screen original, Screen hop) {
        probe();
        if (parentField == null) return false;
        try {
            parentField.set(original, hop);
            return true;
        } catch (IllegalAccessException | RuntimeException e) {
            Log.warn("改写原版配置界面的上一页失败: " + e);
            return false;
        }
    }

    /**
     * 中转界面：什么都不画，出现的第一时间就把玩家送去中文界面。
     *
     * <p>用 {@link Screen#init()} 而不是 {@code render}：{@code setScreen(本类)}
     * 会立刻调用 {@code init()}，切换发生在原版界面被替换的同一帧，
     * 玩家看不到这个空白页。放一帧 {@code render} 反而会闪一下背景色。
     *
     * <p>推迟一帧执行 {@code setScreen}：{@code init()} 是在 {@code Minecraft.setScreen}
     * 内部调用的，此时 Screen 的初始化还没走完，直接在回调里再换界面会把流程打断。
     */
    static final class HopScreen extends Screen {

        /** 中文界面建不出来时退回的地方。 */
        private final Screen fallback;

        /** 已经被触发过，避免界面重开时反复跳转。 */
        private boolean jumped;

        HopScreen(Screen fallback) {
            super(net.minecraft.network.chat.Component.literal("Verity 配置"));
            this.fallback = fallback;
        }

        @Override
        protected void init() {
            if (jumped) return;
            jumped = true;
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            minecraft.execute(() -> {
                Screen chinese = VerityConfigScreenFactory.create(fallback, null);
                minecraft.setScreen(chinese != null ? chinese : fallback);
            });
        }

        /** 不画背景：正常情况下这个界面只存在一帧，越干净越好。 */
        @Override
        public void render(net.minecraft.client.gui.GuiGraphics graphics,
                int mouseX, int mouseY, float delta) {
            // 故意留空
        }

        /** 只是过场，不承担游戏菜单的职责。 */
        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
