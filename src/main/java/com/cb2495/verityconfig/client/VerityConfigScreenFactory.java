package com.cb2495.verityconfig.client;

import com.cb2495.verityconfig.util.Log;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionEventListener;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.ControllerBuilder;
import dev.isxander.yacl3.api.controller.DropdownStringControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 用中文重建 Verity 的配置界面，交给 YACL 渲染。
 * <p><b>结构严格照 Verity 原界面：</b>六个标签页
 * （常规 / AI 设置 / 语音设置 / 语音识别 / 个性化 / 高级），每个标签页里的选项、
 * 顺序、控件类型都与 {@code VerityConfigUI} 一致。译文取自
 * {@code hardcode_ts.txt} 中 {@code VerityConfigUI.java} 那段对照表，不自行改写。
 * <p><b>为什么仍然用 YACL：</b>之前卡住的从来不是 YACL 本身，而是 Verity 把一串
 * 写死的英文字符串直接交给了它，界面建好之后文字已经复制进控件，事后替换
 * {@code config} 对象不会再被读取。这里反过来——文字从一开始就是中文。
 * <p><b>配置项仍然来自 Verity 自己的 {@code ForgeConfigSpec}：</b>读写、默认值、
 * 取值范围都由它负责，本模组不解析 toml，也不自己存一份配置。
 * <p>Verity 没装时 {@link #create(Screen)} 返回 {@code null}，调用方退回原界面。
 */
public final class VerityConfigScreenFactory {

    private VerityConfigScreenFactory() {}

    // ============================================================
    // 构建
    // ============================================================

    /**
     * 构建中文配置界面。
     *
     * @param previous 关闭界面后回到的地方
     * @return 界面；Verity 未安装（取不到 {@code SPEC}）时为 {@code null}
     */
    public static Screen create(Screen previous) {
        return create(previous, null);
    }

    /**
     * 构建中文配置界面，并尽量停在指定的标签页。
     * <p>重建界面后 YACL 总是回到第一个标签页，所以把原来那一页的标题传进来，
     * 建好后自动切回去。
     *
     * @param previous 关闭界面后回到的地方
     * @param keepTab  想停留的标签页标题；{@code null} 表示用默认的第一页
     * @return 界面；Verity 未安装（取不到 {@code SPEC}）时为 {@code null}
     */
    public static Screen create(Screen previous, String keepTab) {
        ForgeConfigSpec spec = VeritySpec.spec();
        if (spec == null) return null;
        try {
            YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
                    .title(Component.literal("Verity 配置"))
                    .category(general(spec))
                    .category(ai(previous))
                    .category(voice())
                    .category(speech())
                    .category(personalisation())
                    .category(advanced());
            // 点「完成」时由 YACL 调用：写盘后 Verity 自己会重载配置，无需重启
            builder.save(() -> VeritySpec.save(spec));
            if (keepTab != null) {
                builder.screenInit(screen -> selectTab(screen, keepTab));
            }
            return builder.build().generateScreen(previous);
        } catch (RuntimeException | LinkageError e) {
            Log.error("构建中文配置界面失败，退回 Verity 原界面: " + e);
            return null;
        }
    }

    // ============================================================
    // 标签页保持
    // ============================================================

    /**
     * 读当前所在的标签页标题；读不到时返回 {@code null}。
     * <p>{@code TabManager#getCurrentTab()} 返回的是 {@code CategoryTab}，
     * 它的 {@code getTabTitle()} 就是分组名（「常规」「AI 设置」……）。
     * <p>注意这里用的是 official 方法名，不是生产 jar 里看到的
     * {@code m_267695_}、{@code m_267600_} 这类 SRG 名：依赖经过
     * {@code fg.deobf} 重映射，开发环境下只有 official 名存在。
     */
    static String currentTab(YACLScreen screen) {
        try {
            if (screen == null || screen.tabNavigationBar == null) return null;
            var tab = screen.tabNavigationBar.getTabManager().getCurrentTab();
            if (!(tab instanceof YACLScreen.CategoryTab categoryTab)) return null;
            return categoryTab.getTabTitle().getString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 切到指定标题的标签页。
     * <p>索引必须从 {@code getTabs()} 里查：YACL 的 {@code init()} 自己也是用
     * {@code getTabs().indexOf(currentTab)} 算的，两边用同一个顺序才对得上。
     * <p>这个回调在界面初始化阶段执行，导航栏可能还没建好，所以推迟到下一帧。
     */
    private static void selectTab(YACLScreen screen, String title) {
        Minecraft.getInstance().execute(() -> {
            try {
                if (screen.tabNavigationBar == null) return;
                var tabs = screen.tabNavigationBar.getTabs();
                for (int i = 0; i < tabs.size(); i++) {
                    if (tabs.get(i) instanceof YACLScreen.CategoryTab categoryTab
                            && title.equals(categoryTab.getTabTitle().getString())) {
                        screen.tabNavigationBar.selectTab(i, false);
                        return;
                    }
                }
            } catch (RuntimeException e) {
                Log.warn("切回标签页失败: " + e);
            }
        });
    }

    // ============================================================
    // 常规
    // ============================================================

    private static ConfigCategory general(ForgeConfigSpec spec) {
        ConfigCategory.Builder category = ConfigCategory.createBuilder().name(Component.literal("常规"));

        tickBox(category, "CAN_CRASH");
        tickBox(category, "PLAY_VIDEO");
        tickBox(category, "REQUIRE_VERITY");
        tickBox(category, "TRUE_DARKNESS");
        tickBox(category, "KILL_ENTITIES");
        tickBox(category, "KILL_WILDLIFE");
        tickBox(category, "KILL_VILLAGERS");
        tickBox(category, "IMMERSIVE_MODE");
        // 原文用输入框（1~100），不是滑块
        integerField(category, "DAY_COUNT", 1, 100);

        // Verity 以后新增的开关自动出现在这里，不会被漏掉
        for (Field field : VeritySpec.extraBooleans(spec, LISTED_BOOLEANS)) {
            tickBox(category, field.getName());
        }
        return category.build();
    }

    // ============================================================
    // AI 设置
    // ============================================================

    private static ConfigCategory ai(Screen previous) {
        ConfigCategory.Builder category = ConfigCategory.createBuilder().name(Component.literal("AI 设置"));

        boolean ollama = isEnum("AI_PROVIDER", "OLLAMA");
        boolean customModel = ollama || VeritySpec.bool("USE_CUSTOM_AI_MODEL");

        // 换服务商要重建界面：下面几行的控件类型会跟着变
        enumOption(category, "AI_PROVIDER", previous);
        if (ollama) {
            category.option(ButtonOption.createBuilder()
                    .name(Component.literal("本地 AI 设置 (Ollama)"))
                    .text(Component.literal("打开"))
                    .description(OptionDescription.of(Component.literal(
                            "在您自己的电脑上运行 Verity 的 AI：免费、私密、无需 API 密钥。\n"
                                    + "一键安装 Ollama 并下载小模型，同时列出此电脑上已有的 Ollama 模型。\n\n"
                                    + "这个界面由 Verity 提供，暂时还是英文。")))
                    .action((screen, option) -> openOllamaSetup(previous))
                    .build());
        }
        string(category, "API_KEY");
        if (!ollama) {
            // 勾选后重建界面：模型那一行要跟着换控件类型
            tickBoxChangingLayout(category, "USE_CUSTOM_AI_MODEL", previous);
        }
        if (ollama) {
            ollamaModel(category);
        } else if (customModel) {
            // 非 Ollama 时这一项是「自定义 AI 模型」，说明也不同
            string(category, "AI_MODEL", "自定义 AI 模型", "发送给 AI 的准确模型 ID。");
        } else {
            enumOption(category, "AI_MODEL_PRESET", null);
        }
        tickBox(category, "AI_THINK");
        return category.build();
    }

    /** Ollama 模型：可手填，也可从本机已装模型里挑。 */
    private static void ollamaModel(ConfigCategory.Builder category) {
        if (!VeritySpec.available("AI_MODEL")) return;
        List<String> detected = ollamaModels();
        String note = (detected.isEmpty()
                ? "未在此电脑上找到 Ollama 模型。请使用上方的本地 AI 设置下载。"
                : "此电脑上已检测到的模型：\n" + String.join("\n", detected))
                + "\n\n留空以让 Verity 自动选择一个已安装的模型。";
        category.option(Option.<String>createBuilder()
                .name(Component.literal("Ollama 模型"))
                .description(OptionDescription.of(Component.literal(note)))
                .binding(binding("AI_MODEL"))
                .controller(opt -> DropdownStringControllerBuilder.create(opt)
                        .values(detected)
                        .allowAnyValue(true)
                        .allowEmptyValue(true))
                .build());
    }

    // ============================================================
    // 语音设置
    // ============================================================

    private static ConfigCategory voice() {
        ConfigCategory.Builder category = ConfigCategory.createBuilder().name(Component.literal("语音设置"));

        tickBox(category, "USE_TTS");
        enumOption(category, "TTS_PROVIDER", null);
        voiceEnum(category);
        string(category, "TTS_API_KEY");
        enumOption(category, "TTS_LANGUAGE", null);
        string(category, "ENDPOINT_VOICE");
        return category.build();
    }

    /**
     * 音色。
     * <p>原界面的「Voice」是 {@code VerityVoice} 枚举，但配置里存的是字符串，
     * 所以要用 {@code VOICE} 的字符串值做双向转换：读时字符串转枚举，
     * 写时枚举转字符串。直接当普通文本框会让人填出枚举里没有的名字。
     */
    private static void voiceEnum(ConfigCategory.Builder category) {
        if (!VeritySpec.available("VOICE")) return;
        Object sample = enumSample("varmite.verity.types.VerityVoice");
        if (!(sample instanceof Enum<?> sampleEnum)) return;

        @SuppressWarnings({"unchecked", "rawtypes"})
        Class<? extends Enum> enumClass = (Class<? extends Enum>) sampleEnum.getDeclaringClass();
        Enum<?> current = readVoice(enumClass);

        category.option(Option.<Object>createBuilder()
                .name(Component.literal("音色"))
                .description(OptionDescription.of(Component.literal("选择 Verity 的音色（GROQ、本地）。")))
                .binding(current,
                        () -> {
                            Enum<?> now = readVoice(enumClass);
                            return now == null ? current : now;
                        },
                        value -> {
                            if (value instanceof Enum<?> e) {
                                VeritySpec.set("VOICE", e.name());
                            }
                        })
                .controller(opt -> enumController(opt, sampleEnum, "VOICE"))
                .build());
    }

    /** 读 {@code VOICE} 字符串并转成枚举；无法解析时退回第一个常量。 */
    private static Enum<?> readVoice(Class<? extends Enum> enumClass) {
        Object raw = VeritySpec.get("VOICE");
        String text = raw == null ? "" : String.valueOf(raw).trim();
        Enum<?>[] constants = enumClass.getEnumConstants();
        if (constants == null || constants.length == 0) return null;
        for (Enum<?> constant : constants) {
            if (constant.name().equalsIgnoreCase(text)) return constant;
        }
        return constants[0];
    }

    // ============================================================
    // 语音识别
    // ============================================================

    private static ConfigCategory speech() {
        ConfigCategory.Builder category = ConfigCategory.createBuilder().name(Component.literal("语音识别"));
        enumOption(category, "STT_PROVIDER", null);
        string(category, "GROQ_KEY");
        return category.build();
    }

    // ============================================================
    // 个性化
    // ============================================================

    private static ConfigCategory personalisation() {
        ConfigCategory.Builder category = ConfigCategory.createBuilder().name(Component.literal("个性化"));
        huelPicker(category);
        string(category, "VERITY_CUSTOM_NAME");
        string(category, "PERSONALITY");
        return category.build();
    }

    /** 自定义颜色：0 表示原始颜色，1~360 是色相偏移。 */
    private static void huelPicker(ConfigCategory.Builder category) {
        if (!VeritySpec.available("COLOR")) return;
        category.option(Option.<Integer>createBuilder()
                .name(Component.literal("自定义颜色 [色相]"))
                .description(OptionDescription.of(Component.literal("0 = 原始颜色。1-360 = 色调。")))
                .binding(binding("COLOR"))
                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                        .range(0, 360)
                        .step(1)
                        .valueFormatter(VerityConfigScreenFactory::hueLabel))
                .build());
    }

    /** 色相值的显示文案：0 是默认色，其余带上色块。 */
    private static Component hueLabel(Integer value) {
        if (value == null || value == 0) return Component.literal("0（默认颜色）");
        return Component.literal("\u25A0\u25A0 " + value);
    }

    // ============================================================
    // 高级
    // ============================================================

    private static ConfigCategory advanced() {
        ConfigCategory.Builder category = ConfigCategory.createBuilder().name(Component.literal("高级"));
        tickBox(category, "DEV_MODE");
        string(category, "AI_ENDPOINT");
        string(category, "TTS_ENDPOINT");
        string(category, "STT_ENDPOINT");
        string(category, "KOKORO_MODEL");
        return category.build();
    }

    // ============================================================
    // 选项构造
    // ============================================================

    /**
     * 已单独列过的布尔项。
     * <p>其余布尔项由 {@link VeritySpec#extraBooleans} 自动补到「常规」里。
     */
    private static final List<String> LISTED_BOOLEANS = List.of(
            "CAN_CRASH", "PLAY_VIDEO", "REQUIRE_VERITY", "TRUE_DARKNESS",
            "KILL_ENTITIES", "KILL_WILDLIFE", "KILL_VILLAGERS", "IMMERSIVE_MODE",
            "USE_CUSTOM_AI_MODEL", "AI_THINK", "USE_TTS", "DEV_MODE");

    /** 布尔项 -> 勾选框。 */
    private static void tickBox(ConfigCategory.Builder category, String name) {
        if (!VeritySpec.available(name)) return;
        category.option(Option.<Boolean>createBuilder()
                .name(Component.literal(label(name)))
                .description(OptionDescription.of(Component.literal(hint(name))))
                .binding(binding(name))
                .controller(TickBoxControllerBuilder::create)
                .build());
    }

    /**
     * 布尔项 -> 勾选框，状态真的变了就重建整个界面。
     * <p>只给「这一项决定了后面还有哪些行」的开关用。
     */
    private static void tickBoxChangingLayout(ConfigCategory.Builder category, String name, Screen previous) {
        if (!VeritySpec.available(name)) return;
        category.option(Option.<Boolean>createBuilder()
                .name(Component.literal(label(name)))
                .description(OptionDescription.of(Component.literal(hint(name))))
                .binding(binding(name))
                .controller(TickBoxControllerBuilder::create)
                .addListener((option, event) -> {
                    if (event == OptionEventListener.Event.STATE_CHANGE) {
                        commitThenRecreate(option, previous);
                    }
                })
                .build());
    }

    /** 字符串项 -> 输入框。 */
    private static void string(ConfigCategory.Builder category, String name) {
        if (!VeritySpec.available(name)) return;
        category.option(Option.<String>createBuilder()
                .name(Component.literal(label(name)))
                .description(OptionDescription.of(Component.literal(hint(name))))
                .binding(binding(name))
                .controller(StringControllerBuilder::create)
                .build());
    }

    /**
     * 字符串项 -> 输入框，标题与说明由调用方指定。
     * <p>同一个配置项在不同情况下叫法不同：{@code AI_MODEL} 选 Ollama 时是
     * 「Ollama 模型」，选了别的服务商并开启自定义模型时是「自定义 AI 模型」。
     */
    private static void string(ConfigCategory.Builder category, String name, String title, String description) {
        if (!VeritySpec.available(name)) return;
        category.option(Option.<String>createBuilder()
                .name(Component.literal(title))
                .description(OptionDescription.of(Component.literal(description)))
                .binding(binding(name))
                .controller(StringControllerBuilder::create)
                .build());
    }

    /** 整数项 -> 数字输入框（原文的「敌对天数阈值」就是这个控件）。 */
    private static void integerField(ConfigCategory.Builder category, String name, int min, int max) {
        if (!VeritySpec.available(name)) return;
        category.option(Option.<Integer>createBuilder()
                .name(Component.literal(label(name)))
                .description(OptionDescription.of(Component.literal(hint(name))))
                .binding(binding(name))
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).min(min).max(max))
                .build());
    }

    /** 枚举项 -> 循环切换按钮，显示中文名，存回去的仍是原枚举常量。 */
    private static void enumOption(ConfigCategory.Builder category, String name, Screen previous) {
        if (!VeritySpec.available(name)) return;
        Object current = VeritySpec.get(name);
        if (!(current instanceof Enum<?> currentEnum)) return;
        // 没有任何可选值的枚举会变成一个点不动的按钮
        Enum<?>[] constants = currentEnum.getDeclaringClass().getEnumConstants();
        if (constants == null || constants.length == 0) return;

        Option.Builder<Object> builder = Option.<Object>createBuilder()
                .name(Component.literal(label(name)))
                .description(OptionDescription.of(Component.literal(hint(name))))
                .binding(enumBinding(name, current))
                .controller(opt -> enumController(opt, currentEnum, name));
        if (previous != null) {
            // 这一项决定了后面显示哪些行：改了就把界面整个重建一遍
            builder.addListener((option, event) -> {
                if (event == OptionEventListener.Event.STATE_CHANGE) {
                    commitThenRecreate(option, previous);
                }
            });
        }
        category.option(builder.build());
    }

    /**
     * 枚举项的控制器。
     * <p>{@link EnumControllerBuilder#create} 要求 {@code Option<T>} 且
     * {@code T extends Enum<T>}，而配置值在编译期只有 {@code Object}，
     * 泛型上限对不上。这里把 {@code Option<Object>} 宽转成原始类型 {@code Option}
     * 来满足约束——实际类型来自枚举常量本身，强转是安全的。
     * <p>返回类型必须写成 {@code ControllerBuilder<Object>}：泛型槽位的类型不能丢，
     * 否则 {@code .controller(...)} 的 lambda 返回类型对不上。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ControllerBuilder<Object> enumController(Option<Object> option, Enum<?> sample, String name) {
        Option rawOption = option;
        return EnumControllerBuilder.create(rawOption)
                .enumClass((Class) sample.getDeclaringClass())
                .valueFormatter(value -> Component.literal(enumLabel(name, value)));
    }

    // ============================================================
    // 绑定
    // ============================================================

    /**
     * 把一个配置项接到 YACL 上。
     * <p><b>默认值取内置模板，而不是 Verity 代码里写死的值。</b>两者并不相同：
     * 模板里 {@code useCustomAiModel = true}、{@code aiThink = false}，
     * 而 Verity 的 {@code define(...)} 给的是 {@code false} / {@code true}。
     * 玩家点「重置」时应当回到我们铺给它的那份配置。
     * <p>模板里没有这一项时退回 Verity 自己的默认值。
     */
    private static <T> dev.isxander.yacl3.api.Binding<T> binding(String name) {
        return new ConfigBinding<>(name);
    }

    /**
     * 直接连到 {@code ConfigValue} 的 YACL 绑定。
     * <p>当前值实时读配置；默认值读内置模板，读不到时退回 Verity 的默认值，
     * 再读不到才用当前值兜底——保证永远不返回 {@code null}。
     */
    private static final class ConfigBinding<T> implements dev.isxander.yacl3.api.Binding<T> {
        private final String name;

        ConfigBinding(String name) {
            this.name = name;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T getValue() {
            return (T) VeritySpec.get(name);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T defaultValue() {
            Object fromTemplate = VeritySpec.templateDefault(name);
            if (fromTemplate != null) return (T) fromTemplate;
            return (T) VeritySpec.get(name);
        }

        @Override
        public void setValue(T value) {
            VeritySpec.set(name, value);
        }
    }

    /** 枚举项的绑定：读回来的是枚举常量本身。 */
    /**
     * 枚举项的绑定：读回来的是枚举常量本身。
     * <p>{@code Binding.generic} 的第一个参数就是它的 {@code defaultValue()}，
     * 所以这里传模板里的枚举值；模板里没有这一项（或值对不上枚举常量）时
     * 才用传进来的 {@code verityFallback}。
     */
    private static dev.isxander.yacl3.api.Binding<Object> enumBinding(String name, Object verityFallback) {
        Object fromTemplate = VeritySpec.templateDefault(name);
        Object def = fromTemplate != null ? fromTemplate : verityFallback;
        return dev.isxander.yacl3.api.Binding.generic(
                def,
                () -> {
                    Object now = VeritySpec.get(name);
                    return now == null ? verityFallback : now;
                },
                value -> VeritySpec.set(name, value));
    }

    private static boolean isEnum(String name, String expected) {
        Object value = VeritySpec.get(name);
        return value instanceof Enum<?> e && expected.equals(e.name());
    }

    // ============================================================
    // 重建 / 跳转
    // ============================================================

    /**
     * 先把这个选项的待生效值写进配置，再重建界面。
     * <p><b>这一步不能省。</b>YACL 的 {@code SimpleStateManager} 在构造时就把
     * {@code binding.getValue()} 取进一个内部字段，之后显示和操作的都是这个快照，
     * 只有 {@code apply()} 才会写回绑定。而 {@code STATE_CHANGE} 事件触发时
     * 值还在快照里没写出去——此时若直接重建，新界面重新读配置拿到的还是旧值，
     * 界面就弹回去，表现为「这一项点不动」。
     */
    private static void commitThenRecreate(Option<?> option, Screen previous) {
        try {
            option.applyValue();
        } catch (RuntimeException e) {
            Log.warn("应用选项 " + option.name().getString() + " 时出错: " + e);
        }
        recreate(previous);
    }

    /**
     * 重建配置界面。
     * <p>有些选项决定了后面还有哪些行（是否使用自定义模型、选了哪个 AI 服务商），
     * 改完必须整个重建一遍，否则旧行会留在屏幕上、新行出不来。
     * <p>重建后要停回原来那一页，否则每点一次就被弹回第一个标签页。
     * <p>不能在监听器里直接换界面：YACL 正在往旧选项列表里写数据，
     * 换了之后它会继续操作已经没人看的对象。推迟到下一帧再换。
     */
    private static void recreate(Screen previous) {
        Minecraft minecraft = Minecraft.getInstance();
        // 当前标签页要在换界面之前读——换完就只剩新界面了
        String keepTab = minecraft.screen instanceof YACLScreen current ? currentTab(current) : null;
        minecraft.execute(() -> {
            Screen rebuilt = create(previous, keepTab);
            if (rebuilt != null) {
                minecraft.setScreen(rebuilt);
            }
        });
    }

    /**
     * 打开 Verity 自己的本地 AI 管理界面。
     * <p>下载模型、列出本机已装模型是一整套流程，不适合抄一遍；只在选了 Ollama
     * 之后才用得到，所以这里按类名反射打开，Verity 没装或者改了类名时只是这一步失效。
     */
    /**
     * 打开「本地 AI 设置」。
     * <p>用本模组自己画的 {@link OllamaSetupScreenZh}：布局与 Verity 原版一致，
     * 但文字从一开始就是中文，不依赖运行时去改别人的界面。
     * Verity 没装时 {@code create} 返回 null，此时退回原版界面。
     */
    private static void openOllamaSetup(Screen previous) {
        Screen target = OllamaSetupScreenZh.create(previous);
        if (target != null) {
            Minecraft.getInstance().setScreen(target);
            return;
        }
        try {
            Class<?> screenClass = Class.forName("varmite.verity.client.gui.OllamaSetupScreen");
            var constructor = screenClass.getConstructor(Supplier.class);
            Screen fallback = (Screen) constructor.newInstance((Supplier<Screen>) () -> create(previous));
            Minecraft.getInstance().setScreen(fallback);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.warn("打不开本地 AI 设置界面: " + e);
        }
    }

    /**
     * 本机已装、能对话的 Ollama 模型名。
     * <p>只读 Verity 缓存下来的那份清单（{@code lastKnown}），不联网——
     * 构建界面时不能卡住渲染线程。
     */
    private static List<String> ollamaModels() {
        try {
            Class<?> manager = Class.forName("varmite.verity.ollama.OllamaSetupManager");
            Object endpoint = manager.getMethod("endpoint").invoke(null);
            Class<?> models = Class.forName("varmite.verity.ollama.OllamaModels");
            Object list = models.getMethod("lastKnown", String.class).invoke(null, endpoint);
            if (!(list instanceof List<?> rawList)) return List.of();
            List<String> names = new ArrayList<>();
            for (Object item : rawList) {
                if (item == null) continue;
                Object name = item.getClass().getMethod("name").invoke(item);
                if (name == null) continue;
                String text = String.valueOf(name);
                // 向量模型不能对话，列出来只会让人选错
                if (text.toLowerCase(Locale.ROOT).contains("embed")) continue;
                names.add(text);
            }
            return names;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Ollama 管理类不在（Verity 版本不同）：下拉框退化成普通输入框
            return List.of();
        }
    }

    /** 取某个枚举类型的第一个常量，用来拿到类型本身；取不到时返回 null。 */
    private static Object enumSample(String enumClassName) {
        try {
            Class<?> clazz = Class.forName(enumClassName);
            Object[] constants = clazz.getEnumConstants();
            return constants != null && constants.length > 0 ? constants[0] : null;
        } catch (ClassNotFoundException | RuntimeException e) {
            return null;
        }
    }

    // ============================================================
    // 文案表
    // ============================================================

    /**
     * 选项的中文名，逐条对应 {@code hardcode_ts.txt} 里
     * {@code VerityConfigUI.java} 那段对照表。
     * <p>查不到的项显示字段名本身——Verity 以后新增了开关，界面上会多出一行，
     * 但不会整行消失或者报错。
     */
    private static String label(String name) {
        return switch (name) {
            // 常规
            case "CAN_CRASH" -> "允许崩溃";
            case "PLAY_VIDEO" -> "播放视频";
            case "REQUIRE_VERITY" -> "要求称呼";
            case "TRUE_DARKNESS" -> "极致黑暗";
            case "KILL_ENTITIES" -> "击杀实体";
            case "KILL_WILDLIFE" -> "击杀野生动物";
            case "KILL_VILLAGERS" -> "击杀村民";
            case "IMMERSIVE_MODE" -> "沉浸模式";
            case "DAY_COUNT" -> "敌对天数阈值";
            // AI 设置
            case "AI_PROVIDER" -> "AI 服务商";
            case "API_KEY" -> "API 密钥";
            case "USE_CUSTOM_AI_MODEL" -> "使用自定义模型";
            case "AI_MODEL" -> "Ollama 模型";
            case "AI_MODEL_PRESET" -> "AI 模型预设";
            case "AI_THINK" -> "思考模式";
            // 语音设置
            case "USE_TTS" -> "启用文字朗读 (TTS)";
            case "TTS_PROVIDER" -> "TTS (文字朗读) 服务商";
            case "VOICE" -> "音色";
            case "TTS_API_KEY" -> "TTS API 密钥";
            case "TTS_LANGUAGE" -> "TTS 语言";
            case "ENDPOINT_VOICE" -> "高级 TTS 音色";
            // 语音识别
            case "STT_PROVIDER" -> "识别服务商";
            case "GROQ_KEY" -> "Groq API 密钥";
            // 个性化
            case "COLOR" -> "自定义颜色 [色相]";
            case "VERITY_CUSTOM_NAME" -> "自定义名称";
            case "PERSONALITY" -> "性格 (人设)";
            // 高级
            case "DEV_MODE" -> "开发者模式";
            case "AI_ENDPOINT" -> "AI 接口 URL";
            case "TTS_ENDPOINT" -> "TTS 接口 URL";
            case "STT_ENDPOINT" -> "语音识别接口 URL";
            case "KOKORO_MODEL" -> "Kokoro 模型";
            default -> name;
        };
    }

    /** 选项的说明文字，同样取自对照表。 */
    private static String hint(String name) {
        return switch (name) {
            case "CAN_CRASH" -> "允许 Verity 将你踢出服务器。";
            case "PLAY_VIDEO" -> "在客户端启动时播放 Verity 剪辑。";
            case "REQUIRE_VERITY" -> "必须在每句话中包含 'Verity' 才能与其交谈。";
            case "TRUE_DARKNESS" -> "让视野更加黑暗。（即使关闭了你的游戏亮度仍然会被锁定为“昏暗”）";
            case "KILL_ENTITIES" -> "允许 Verity 击杀实体。";
            case "KILL_WILDLIFE" -> "允许 Verity 随机击杀野生动物。（比如你刚驯服的猫）";
            case "KILL_VILLAGERS" -> "允许 Verity 击杀村民。";
            case "IMMERSIVE_MODE" -> "禁用模组特定的 UI，以还原原作系列。";
            case "DAY_COUNT" -> "Verity 变成恶魔前的游戏内天数。";
            case "AI_PROVIDER" -> "用于驱动 AI 的服务提供商。";
            case "API_KEY" -> "服务商的 API 密钥。仅在服务商要求时填写。";
            case "USE_CUSTOM_AI_MODEL" -> "输入模型 ID 替代 智能 或 快速 预设。";
            case "AI_MODEL" -> "发送给 AI 的准确模型 ID。";
            case "AI_MODEL_PRESET" -> "智能：价格低廉且质量更高的回复。\n快速：实用最低价下的最快回复。";
            case "AI_THINK" -> "（支持的模型适用）让 AI 在回答前思考更久。本地模型：仅适用于能思考的模型。"
                    + "qwen3 和 qwen3.5 需要开启才能可靠地使用工具；没有思考功能的模型开启与否都很快。";
            case "USE_TTS" -> "让 Verity 可以通过语音说话。";
            case "TTS_PROVIDER" -> "用于文字朗读的服务商。";
            case "VOICE" -> "选择 Verity 的音色（GROQ、本地）。";
            case "TTS_API_KEY" -> "您的 TTS API 密钥（Cartesia）。";
            case "TTS_LANGUAGE" -> "Verity 说话的语言（Cartesia）。";
            case "ENDPOINT_VOICE" -> "所选 TTS 服务商使用的音色（Cartesia 音色 ID、Kokoro）。";
            case "STT_PROVIDER" -> "用于语音转文字（STT）的服务商。";
            case "GROQ_KEY" -> "用于 Groq 语音识别的 API 密钥。";
            case "COLOR" -> "0 = 原始颜色。1-360 = 色调。";
            case "VERITY_CUSTOM_NAME" -> "将 Verity 重命名为任何你喜欢的名字！";
            case "PERSONALITY" -> "在同一个世界中多次更改可能会导致冲突。";
            case "DEV_MODE" -> "开启开发者模式（输出更多日志）。";
            case "AI_ENDPOINT" -> "专用于 AI 服务器的自定义 URL。";
            case "TTS_ENDPOINT" -> "专用于 TTS 服务器的自定义 URL。";
            case "STT_ENDPOINT" -> "专用于语音识别（SR）服务器的自定义 URL。";
            case "KOKORO_MODEL" -> "Kokoro 使用的模型。";
            default -> "";
        };
    }

    /** 枚举项的中文显示名；查不到就显示原常量名。 */
    private static String enumLabel(String key, Object raw) {
        if (!(raw instanceof Enum<?> value)) return String.valueOf(raw);
        String name = value.name();
        return switch (key) {
            case "AI_PROVIDER" -> switch (name) {
                case "GEMINI" -> "Gemini";
                case "GROQ" -> "Groq";
                case "OPENROUTER" -> "OpenRouter";
                case "MISTRAL" -> "Mistral";
                case "OLLAMA" -> "Ollama（本地）";
                case "OPENAI" -> "OpenAI";
                default -> name;
            };
            case "AI_MODEL_PRESET" -> switch (name) {
                case "SMART" -> "智能";
                case "FAST" -> "快速";
                default -> name;
            };
            case "TTS_PROVIDER" -> switch (name) {
                case "NATIVE" -> "系统原生";
                case "LOCAL" -> "本地";
                case "GROQ" -> "Groq";
                case "KOKORO" -> "Kokoro";
                case "CARTESIA" -> "Cartesia";
                default -> name;
            };
            case "STT_PROVIDER" -> switch (name) {
                case "GROQ" -> "Groq";
                case "NATIVE" -> "本地（离线）";
                case "WHISPER" -> "Whisper";
                default -> name;
            };
            case "TTS_LANGUAGE" -> switch (name) {
                case "English" -> "英语";
                case "German" -> "德语";
                default -> name;
            };
            default -> name;
        };
    }
}
