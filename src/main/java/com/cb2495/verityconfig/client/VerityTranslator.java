package com.cb2495.verityconfig.client;

import com.cb2495.verityconfig.VerityConfig;
import com.cb2495.verityconfig.util.Log;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把 Verity 硬编码在代码里的英文文案换成中文。
 * <p>Verity 的配置界面、下拉框选项、聊天提示都是直接写在 Java 里的字面量，既不经过语言文件，
 * 也没有提供任何替换入口，只能在加载之后自己动手：
 * <ul>
 *   <li><b>配置界面</b>：YACL 是实时读「配置对象」取文字的（类别名、选项名、说明），
 *       所以把界面手上那个配置对象包一层，取文字的方法换成中文，其余原样转发。</li>
 *   <li><b>下拉框</b>：YACL 的枚举控件靠 {@code toString()} 取显示名，
 *       直接换掉枚举的显示名，这一类选项就全变了，且只在本模组加载后生效。</li>
 *   <li><b>聊天消息</b>：包一层 {@code Component#getString()}，
 *       Verity 发出的任何消息都会经过这里。</li>
 * </ul>
 * <p><b>为什么按「整段命中」替换，而不是改界面那一行行代码：</b>
 * 那个界面是把上百个 {@code Component.literal("…")} 串成一条链构建出来的，而且全是私有方法；
 * 拆开重写，代码量是原界面的两倍，还跟原版的混淆名绑死。
 * 按字样替换只依赖「这段文字出现在哪里」，以后补一句译文也只是往表里加一行。
 * <p>没命中的字样原样保留，所以最坏情况是某几句还是英文，不会变成空白或报错。
 */
public final class VerityTranslator {

    private VerityTranslator() {}

    // ---------- 运行状态 ----------

    private static volatile boolean ready;

    /** 初始化失败（Verity 换了实现）后不再重试，免得每次进主界面都报一次。 */
    private static boolean disabled;

    private static boolean loggedLanguage;

    private static final String[] VERITY_ENUMS = {
            "varmite.verity.types.AiProvider",
            "varmite.verity.types.AiModelPreset",
            "varmite.verity.types.TTSProvider",
            "varmite.verity.types.STTProvider",
            "varmite.verity.types.TTSLanguage",
            "varmite.verity.types.VerityVoice",
    };

    private static final String WORKING = "Working...";

    private static final String THINKING = "thinking";

    private static final String CANNOT_THINK = "I can't think right now... (Connection Error)";

    // ---------- 译文表 ----------

    /** 英文原句 → 中文。带 {@code \n} 的换行原样保留。 */
    private static final Map<String, String> UI = new LinkedHashMap<>();

    /** 枚举常量名 → 中文。 */
    private static final Map<String, String> ENUMS = new HashMap<>();

    static {
        registerUi();
        registerPhrases();
        registerEnums();
    }

    /** 原界面的全部字样，外加会出现在聊天栏里的那几句。 */
    private static void registerUi() {
        // ===== 标签页 =====
        put("Verity Configuration", "Verity 配置");
        put("General", "常规");
        put("AI Settings", "AI 设置");
        put("Voice Settings", "语音设置");
        put("Speech Recognition", "语音识别");
        put("Personalisation", "个性化");
        put("Advanced", "高级");

        // ===== 常规 =====
        put("Can Crash", "允许崩溃");
        put("Allow Verity to kick you from the server.", "允许 Verity 将你踢出服务器。");
        put("Play Video", "播放视频");
        put("Play the Verity Edit on the\nstartup of the client.", "在客户端启动时播放 Verity 剪辑。");
        put("Require Verity", "要求称呼");
        put("Require 'Verity' in every\nsentence to speak to him.",
                "必须在每句话中包含 'Verity' 才能与其交谈。");
        put("True Darkness", "极致黑暗");
        put("Toggle the extreme darkness.",
                "让视野更加黑暗。\n（即使关闭了，你的游戏亮度仍然会被锁定为“昏暗”）");
        put("Kill Entities", "击杀实体");
        put("Toggles if Verity can kill entities.", "允许 Verity 击杀实体。");
        put("Kill Wildlife", "击杀野生动物");
        put("Toggles if Verity should kill randomly wildlife.\nPigs, sheep, etc",
                "允许 Verity 随机击杀野生动物。\n（比如你刚驯服的猫）");
        put("Kill Villagers", "击杀村民");
        put("Toggles if Verity should kill villagers.", "允许 Verity 击杀村民。");
        put("Immersive Mode", "沉浸模式");
        put("Disables mod specific UIs to resemble the original series.",
                "禁用模组特定的 UI，以还原原作系列。");
        put("Hostile Day Threshold", "敌对天数阈值");
        put("The amount of in-game days before\nVerity turns into a demon.",
                "Verity 变成恶魔前的游戏内天数。");

        // ===== AI 设置 =====
        put("AI Provider", "AI 服务商");
        put("The provider used to power the AI.", "用于驱动 AI 的服务提供商。");
        put("Local AI Setup (Ollama)", "本地 AI 设置 (Ollama)");
        put("Run Verity's AI on your own PC: free, private, no API key.\n\n"
                        + "Installs Ollama and downloads a small model with one click, "
                        + "and lists the Ollama models already on this computer.",
                "在您自己的电脑上运行 Verity 的 AI：免费、私密、无需 API 密钥。\n\n"
                        + "一键安装 Ollama 并下载小模型，同时列出此电脑上已有的 Ollama 模型。");
        put("Open", "打开");
        put("API Key", "API 密钥");
        put("API Key of the provider.\nOnly needed if the provider requests one.",
                "服务商的 API 密钥。\n仅在服务商要求时填写。");
        put("Use Custom Model", "使用自定义模型");
        put("Enter a model ID instead of using the Smart or Fast preset.",
                "输入模型 ID 来替代「智能」或「快速」预设。");
        put("Ollama Model", "Ollama 模型");
        put("No Ollama models were found on this computer. Use Local AI Setup above to download one.",
                "未在此电脑上找到 Ollama 模型。请使用上方的「本地 AI 设置」下载一个。");
        put("Leave empty to let Verity pick an installed model automatically.",
                "留空以让 Verity 自动选择一个已安装的模型。");
        put("Custom AI Model", "自定义 AI 模型");
        put("AI Model Preset", "AI 模型预设");
        put("Higher-quality responses at a low price.", "价格低廉、质量更高的回复。");
        put("Faster responses at the lowest practical price.", "在尽可能低的价格下获得更快的回复。");
        // 这两行是运行时拼上一段值才成句的：值和英文混在一起不可能按整句查到，
        // 所以先把「固定的那部分」换成占位符再匹配，值本身原样带进译文
        // 「发送给 XX 的准确模型 ID」里的 XX 是当前供应商，几个都写进来
        for (String provider : new String[]{"OPENAI", "GEMINI", "GROQ", "OPENROUTER",
                "MISTRAL", "OLLAMA"}) {
            String sentence = "Exact model ID to send to " + provider + ".";
            put(sentence, "发送给 " + provider + " 的准确模型 ID。");
        }
        // 拼在说明后面的值：界面上会变成「模型：gpt-5.4-mini」。
        // 这里也把整句「说明 + 值」登记一遍，界面拼接出来的那条标题才能整句查到
        put("Model: " + MODEL_ID, "模型：" + MODEL_ID);
        put("(On supported models)\nMakes the AI think longer before answering.\n\n"
                        + "Local models: only applied to models that can think. "
                        + "qwen3 and qwen3.5 need it on to use their tools reliably; "
                        + "models without thinking are fast either way.",
                "（支持的模型适用）让 AI 在回答前思考更久。\n\n"
                        + "本地模型：仅对能够思考的模型生效。\n"
                        + "qwen3 与 qwen3.5 需要开启它才能可靠地调用工具；"
                        + "本身不思考的模型开不开都一样快。");

        // ===== 语音设置 =====
        put("Use Text To Speech (TTS)", "启用文字朗读 (TTS)");
        put("Whenever Verity speaks using Text To Speech", "让 Verity 可以通过文字朗读来说话。");
        put("Text To Speech Provider", "TTS (文字朗读) 服务商");
        put("Provider to use for Text To Speech", "用于文字朗读的服务商。");
        put("Voice", "音色");
        put("Choose the voice Verity has (GROQ, Local)", "选择 Verity 的音色（GROQ、本地）。");
        put("TTS API Key", "TTS API 密钥");
        put("Your TTS API Key (Cartesia)", "您的 TTS API 密钥（Cartesia）。");
        put("TTS Language", "TTS 语言");
        put("The language Verity speaks (Cartesia)", "Verity 说话使用的语言（Cartesia）。");
        put("Advanced TTS Voice", "高级 TTS 音色");
        put("The voice the selected TTS Provider uses (Cartesia Voice ID, Kokoro)",
                "所选 TTS 服务商使用的音色（Cartesia 音色 ID、Kokoro）。");

        // ===== 语音识别 =====
        put("Recognition Provider", "识别服务商");
        // 原版这句写的是 "Text To Speech"，那是笔误，这里按实际功能翻成语音转文字
        put("Provider to use for Speech To Text", "用于语音转文字（STT）的服务商。");
        put("Groq API Key", "Groq API 密钥");
        put("API Key for the groq Speech Recognition", "用于 Groq 语音识别的 API 密钥。");

        // ===== 个性化 =====
        put("Custom Color [HUE]", "自定义颜色 [色相]");
        put("0 = Original Colors. 1-360 = Hue Tint.", "0 = 原始颜色。1-360 = 色调。");
        // 颜色块后面跟的是色相数字，整段原样不动，只登记进来让这一行保持原样
        put("\u2588\u2588 " + HUE, "\u2588\u2588 " + HUE);
        put("Custom Name", "自定义名称");
        put("Rename Verity to anything you want!", "将 Verity 重命名为任何你喜欢的名字！");
        put("Personality", "性格（人设）");
        put("Can conflict when changed multiple\ntimes in one world",
                "在同一个世界中多次更改可能会导致冲突。");

        // ===== 高级 =====
        put("Developer Mode", "开发者模式");
        put("Turns on developer mode (more logs)", "开启开发者模式（输出更多日志）。");
        put("AI URL", "AI 接口 URL");
        put("Custom URL for dedicated AI servers", "专用于 AI 服务器的自定义 URL。");
        put("Text To Speech URL", "TTS 接口 URL");
        put("Custom URL for dedicated TTS servers.", "专用于 TTS 服务器的自定义 URL。");
        put("Speech Recognition URL", "语音识别接口 URL");
        put("Custom URL for dedicated SR servers.", "专用于语音识别（SR）服务器的自定义 URL。");
        put("Kokoro Model", "Kokoro 模型");
        put("Model used by Kokoro.", "Kokoro 使用的模型。");

        // ===== 聊天栏 =====
        put(CANNOT_THINK, "我现在无法思考...（连接错误）");
        put("AI Setup issue? Tutorial: " + TUTORIAL_URL, "AI 设置遇到问题？教程：" + TUTORIAL_URL);
        put("Local AI: Mods > Verity > Config > AI Settings > Local AI Setup "
                        + "installs Ollama and models in one click.",
                "本地 AI：模组 > Verity > 配置 > AI 设置 > 本地 AI 设置，一键安装 Ollama 和模型。");
        // 下面两句拦下来是有用途的：第一句换成本模组自己的帮助提示，第二句直接屏蔽
        put("Need help setting up this mod? Watch this tutorial.",
                "需要帮助设置此模组吗？观看这个教程（需要VPN）。");
        put("Setup Tutorial", "设置教程");
    }

    /** 教程视频地址：原文里的链接要在译文里原样保留，否则这句就对不上了。 */
    private static final String TUTORIAL_URL = "https://youtu.be/O3pSCBvJ_i0";

    /** 短提示：单独出现时同样是一整句，按整句匹配。 */
    private static void registerPhrases() {
        put("Detected on this computer:", "已在此电脑上检测到：");
        put("Installed on this computer", "已安装在此电脑上");
        put(WORKING, "处理中...");
        // 「thinking」在 Verity 里是模型属性（该模型会不会深度思考），不是进行时
        put(THINKING, "深度思考");
        put("In Use", "使用中");
        put("Server Offline", "服务器离线");
        put("Ollama Ready", "Ollama 已就绪");
        put("Checking...", "检查中...");
        put("Looking for Ollama...", "正在寻找 Ollama...");
        put("Cancel Download", "取消下载");
        put("Install Ollama", "安装 Ollama");
        put("Remove Ollama, keep models", "卸载 Ollama，保留模型");
        put("Remove Ollama and delete all models", "卸载 Ollama 并删除所有模型");
        put("Ollama is not running. Press Start Ollama above.",
                "Ollama 未运行。请按上方的「启动 Ollama」。");
        put("Ollama is not installed yet. Press Install Ollama above, or just pick a model below.",
                "Ollama 尚未安装。请按上方的「安装 Ollama」，或者直接在下方选择一个模型。");
        put("No models yet - download one below.", "暂无模型 - 请在下方下载一个。");
        put("No compatible microphones found on this system.", "未在此系统上找到兼容的麦克风。");
        registerCommandAndUninstall();
    }

    /**
     * 命令反馈与卸载界面。
     * <p>这两类文字都出现在聊天栏或普通界面的按钮上，走的是同一个
     * {@code Component#getString()} 钩子，所以只要登记译文就能生效。
     * <p>译文取自 {@code hardcode_ts.txt} 的
     * {@code ChangeKarmaCommand} / {@code RecoverVerityCommand} /
     * {@code VerityFaceCommand} / {@code OllamaUninstallScreen} 四节。
     */
    private static void registerCommandAndUninstall() {
        // --- /karma ---
        put("Karma updated to ", "好感度已调整到 ");

        // --- /recoververity ---
        put("Verity is already recovered.", "Verity 已经恢复。");
        put("Recovery is on cooldown. Time remaining: ", "恢复正在冷却中。剩余时间：");
        put("Failed to recover Verity.", "恢复 Verity 失败。");
        put("Verity has been recovered!", "Verity 已恢复！");
        put("Chat history cleared.", "聊天记录已清除。");
        put("Command ran.", "指令已执行。");

        // --- /verityface ---
        put("No Verity in this dimension.", "此维度中没有 Verity。");
        put("Verity's face set to ", "Verity 的表情已设置为 ");

        // --- 卸载确认界面 ---
        put("Remove Ollama?", "卸载 Ollama？");
        put("Remove Ollama, keep models", "卸载 Ollama，保留模型");
        put("Remove Ollama and delete all models", "卸载 Ollama 并删除所有模型");

        // 主界面的「Mod by Varmite」不放在这里：按钮绘制不走 getString()，
        // 放在表里既翻不到，还会让 TitleButtonTranslator 的原文比对失效。
        // 那个按钮由 TitleButtonTranslator 直接换 Component。
    }

    /**
     * 枚举下拉框。
     * <p>本来就是品牌名的照写不误——登记进来，是为了让「整个枚举都认识」这条规则放行；
     * 只要有一个常量不在表里，那一组就整个不动，免得出现一半中文一半英文。
     */
    private static void registerEnums() {
        putEnum("GEMINI", "Gemini");
        putEnum("GROQ", "Groq");
        putEnum("OPENROUTER", "OpenRouter");
        putEnum("MISTRAL", "Mistral");
        putEnum("OLLAMA", "Ollama");
        putEnum("OPENAI", "OpenAI");

        putEnum("SMART", "智能");
        putEnum("FAST", "快速");

        // TTS 服务商的原版显示就是大写常量名，翻成中文反而好懂。
        // 三个实现写在一行里：一行显示不下，YACL 会按「, 」再拆成多行
        putEnum("NATIVE", "系统 / GROQ / 本地");
        putEnum("CARTESIA", "Cartesia");
        putEnum("KOKORO", "Kokoro");

        putEnum("WHISPER", "Whisper");

        // 音色名是 TTS 模型自带的名字，不翻译
        putEnum("AUTUMN", "Autumn");
        putEnum("DIANA", "Diana");
        putEnum("HANNAH", "Hannah");
        putEnum("AUSTIN", "Austin");
        putEnum("DANIEL", "Daniel");
        putEnum("TROY", "Troy");

        // TTSLanguage 的常量首字母大写，按原名登记
        putEnum("English", "英语");
        putEnum("German", "德语");
    }

    /** 运行时才拼进来的值：用不会出现在任何真实文字里的记号代替。 */
    private static final String MODEL_ID = "<<MODELID>>";

    private static final String HUE = "<<HUE>>";

    private static final String PROMPT = "<<PROMPT>>";

    /** 模型 ID：只认小写、带点、冒号或斜杠的那种（{@code gpt-5.4-mini}、{@code qwen3:8b}）。 */
    private static final java.util.regex.Pattern MODEL_TEXT = java.util.regex.Pattern
            .compile("[a-z][a-z0-9.\\-]*(?:[./:][a-z0-9][a-z0-9.\\-]*)+");

    /** 色相：两个色块后面跟的数字。 */
    private static final java.util.regex.Pattern HUE_TEXT = java.util.regex.Pattern
            .compile("(?<=\u2588\u2588 )\\d{1,3}");

    /** 译文表；第一次用到时才建起来。 */
    private static volatile boolean tableBuilt;

    /**
     * 把运行时拼进来的值换成占位符，好让整句在表里查得到。
     * <p>只认「值」的样子（模型名、色相数字），不认普通英文单词——
     * 否则 AI 回答里随便一个词都可能被当成值，整段被换掉。
     */
    private static String abstractKeywords(String text) {
        String out = MODEL_TEXT.matcher(text).replaceAll(java.util.regex.Matcher.quoteReplacement(MODEL_ID));
        out = HUE_TEXT.matcher(out).replaceAll(HUE);
        return out;
    }

    private static void put(String english, String chinese) {
        UI.put(english, chinese);
    }

    private static void putEnum(String name, String chinese) {
        ENUMS.put(name, chinese);
    }

    // ---------- 文本替换 ----------

    /** 整句表：键和值都压平了换行（键里还换了占位符），长的排前面。 */
    private static final List<Map.Entry<String, String>> LONGEST_FIRST = new ArrayList<>();

    /** 原样保留换行的那份译文：查到了就用它，中文里该断开的地方才不会粘成一坨。 */
    private static final Map<String, String> WITH_LINE_BREAKS = new LinkedHashMap<>();

    /** 建整句表；这些正则只用一次，没必要在类加载时就跑。 */
    private static void buildTable() {
        if (tableBuilt) return;
        synchronized (VerityTranslator.class) {
            if (tableBuilt) return;
            for (Map.Entry<String, String> entry : UI.entrySet()) {
                String key = squashed(abstractKeywords(folded(entry.getKey())));
                LONGEST_FIRST.add(Map.entry(key, folded(entry.getValue())));
                WITH_LINE_BREAKS.putIfAbsent(key, entry.getValue());
            }
            // 拼在句子后面的值：上面每一条「说明」都可以再接一段值，所以每条都补一个占位符版
            List<Map.Entry<String, String>> grown = new ArrayList<>(LONGEST_FIRST);
            for (Map.Entry<String, String> entry : LONGEST_FIRST) {
                if (entry.getKey().contains(PROMPT)) continue;
                String tailKey = entry.getKey() + PROMPT;
                grown.add(Map.entry(tailKey, entry.getValue() + PROMPT));
                WITH_LINE_BREAKS.putIfAbsent(tailKey, WITH_LINE_BREAKS.get(entry.getKey()) + PROMPT);
            }
            LONGEST_FIRST.clear();
            LONGEST_FIRST.addAll(grown);
            LONGEST_FIRST.sort(Comparator.comparingInt(
                    (Map.Entry<String, String> e) -> e.getKey().length()).reversed());
            tableBuilt = true;
        }
    }

    /** 按「压平后的原文」查原样保留换行的译文。 */
    private static String lookup(String folded) {
        return WITH_LINE_BREAKS.get(folded);
    }

    private static final java.util.regex.Pattern NEWLINES = java.util.regex.Pattern.compile("[\\r\\n]+");

    /** 把换行压成一个空格：界面看到的标题里已经没有它写死的换行了。 */
    private static String folded(String text) {
        return NEWLINES.matcher(text).replaceAll(" ");
    }

    /** 连续空白压成一个空格。词表里的长句自己就带双空格，实际显示时只会剩一个。 */
    private static final java.util.regex.Pattern SPACES = java.util.regex.Pattern.compile(" {2,}");

    private static String squashed(String text) {
        return SPACES.matcher(text).replaceAll(" ");
    }

    /**
     * 把一段英文换成中文。只有两种情况会翻：
     * <ol>
     *   <li>整段就是表里的一句话——界面标题、聊天里的固定提示都属于这种；</li>
     *   <li>整段以表里的一句话开头、后面跟了一小段「值」——界面把说明和当前值
     *       拼成了同一条标题，比如说明后面接着 {@code Model: gpt-5.4-nano}，
     *       或者 Ollama 模型列表后面接着「留空即可」那一句。</li>
     * </ol>
     * <p>比对前两边都压平换行：界面看到的标题里已经没有它写死的换行了。
     * <p>其余一律原样返回。<b>这一条很要紧</b>：Verity 的 AI 回答也走这条路，
     * 如果按「句中包含某个词就换」，回答里的 ordinary 会把 {@code General} 换掉、
     * 网址里的 {@code /zh-cn/} 会把「中文」换进去。
     */
    static String translate(String text) {
        if (text == null || text.length() < 2) return text;
        buildTable();
        String flat = folded(text);

        String exact = lookup(squashed(abstractKeywords(flat)));
        if (exact != null) return exact;

        for (Map.Entry<String, String> entry : LONGEST_FIRST) {
            String key = entry.getKey();
            if (key.endsWith(PROMPT)) continue;
            if (flat.length() <= key.length()) continue;
            if (!flat.startsWith(key)) continue;
            String tail = flat.substring(key.length()).stripLeading();
            if (!isValueChunk(tail)) continue;
            String rebuilt = lookup(key + PROMPT);
            if (rebuilt == null) continue;
            // 值的部分自己也可能是一条词条（「Model: 模型名」），先单独翻一次
            String value = lookup(abstractKeywords(tail));
            if (value == null) value = tail;
            // 译文里可能不止一个占位符，只换最后一处：前面那些已经由表里的译文占好了位置
            int at = rebuilt.lastIndexOf(PROMPT);
            if (at < 0) return rebuilt;
            String out = rebuilt.substring(0, at) + value + rebuilt.substring(at + PROMPT.length());
            // 值里剩下的模型名占位符换回真名
            return out.replace(MODEL_ID, firstModelId(tail));
        }
        return text;
    }

    /** 从一段值里取出模型名本身。 */
    private static String firstModelId(String text) {
        java.util.regex.Matcher m = MODEL_TEXT.matcher(text);
        return m.find() ? m.group() : text;
    }

    /**
     * 拼在句子后面的那一段，是不是界面自己拼出来的「值」。
     * <p>只认两种：{@code Model: <模型名>}，或者说明后面直接跟一句「留空即可」
     * （Ollama 模型列表就是这种）。别的一律不认——
     * 否则 AI 回答「Voice is important」会被当成说明加值给翻掉。
     */
    private static boolean isValueChunk(String tail) {
        String rest = tail.stripLeading();
        if (rest.startsWith("Model:")) {
            rest = rest.substring("Model:".length()).stripLeading();
            int end = endOfValue(rest);
            return end == rest.length() && end > 0;
        }
        return LEAVE_EMPTY.equals(rest) || COMPUTER.equals(rest);
    }

    /** Ollama 模型列表后面跟着的那句说明。 */
    private static final String LEAVE_EMPTY =
            "Leave empty to let Verity pick an installed model automatically.";

    /**
     * Ollama 模型列表那条：前半是「已在此电脑上检测到：」，后半才是这句。
     * <p>放在这里是因为列表本身是登录后才知道的内容，没法写进词表。
     */
    private static final String COMPUTER = "Detected on this computer:";

    /** 从开头数出「一个值」有多长：模型名。 */
    private static int endOfValue(String text) {
        int i = 0;
        while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i))
                || text.charAt(i) == '.' || text.charAt(i) == '-' || text.charAt(i) == ':'
                || text.charAt(i) == '_' || text.charAt(i) == '/')) i++;
        return i;
    }

    // ---------- 初始化 ----------

    /** 惰性初始化：只有真的要翻译时才去改那些类。重复调用没有副作用。 */
    public static void ensureReady() {
        if (ready) return;
        ready = true;
        if (!isChinese()) {
            if (!loggedLanguage) {
                loggedLanguage = true;
                Log.info("游戏语言不是中文简体，跳过 Verity 硬编码文本的翻译");
            }
            disabled = true;
            return;
        }
        try {
            patchComponents();
            patchEnums();
        } catch (RuntimeException | LinkageError e) {
            disabled = true;
            Log.warn("Verity 硬编码文本翻译初始化失败，保持英文: " + e);
        }
    }

    /**
     * 只看游戏语言，不看系统语言。
     * <p>没切成中文时本模组什么都不碰：界面、下拉框、聊天全按 Verity 原样显示。
     */
    static boolean isChinese() {
        try {
            String code = Minecraft.getInstance().getLanguageManager().getSelected();
            return "zh_cn".equals(code);
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---------- 1. 聊天消息 ----------

    /** 被包之前 {@code getUnformattedText} 的方法对象；补丁里要靠它取原文。 */
    private static volatile Method plainMethod;

    /** 被包之前 {@code getString} 的方法对象；槽位认出来的是同一份引用。 */
    private static volatile Method stringMethod;

    /** {@code Component} 子类 → 它的 {@code getString()} 是不是直接转给 {@code getUnformattedText()}。 */
    private static final Map<Class<?>, Boolean> MIRROR = new HashMap<>();

    /** 补丁自己调回去时置位，避免无限递归。 */
    private static final ThreadLocal<Boolean> PATCHING = new ThreadLocal<>();

    /** {@code Component#m_240479_}：内容变了要通知缓存，否则画出来还是旧的。 */
    private static volatile Method contentChanged;

    /**
     * 包一层「取文本」的方法。
     * <p>聊天栏、字幕、日志最后都是拿 {@code getString()} 的内容去画的，
     * 所以 Verity 发的任何英文消息都会经过这里，不用去拦每一条具体文案。
     * <p>只换 {@code getString}、不换 {@code getUnformattedText}：后者要给聊天栏里
     * 带样式的名字（{@code <Verity>}、玩家名）重新分组，动了容易错位。
     * <p>原版把这两个方法对象存在 {@code Component} 的静态字段里，渲染时读的就是字段；
     * 把它们指向我们自己造的方法，等于全局换了实现，Verity 不必配合。
     */
    private static void patchComponents() {
        try {
            Class<?> type = Class.forName("net.minecraft.network.chat.Component");
            stringMethod = type.getMethod("getString");
            plainMethod = type.getMethod("getUnformattedText");
            contentChanged = type.getMethod("m_240479_");

            // 签名必须和原方法一致（含前两个合成参数），否则替换后被调用时 JVM 校验不过
            MethodHandle stringReplacement = MethodHandles.lookup().findStatic(VerityTranslator.class,
                    "textWithTranslation",
                    MethodType.methodType(Object.class, Component.class,
                            MethodHandles.Lookup.class, String.class, MethodType.class));
            MethodHandle plainReplacement = MethodHandles.lookup().findStatic(VerityTranslator.class,
                    "plainText",
                    MethodType.methodType(Object.class, Component.class,
                            MethodHandles.Lookup.class, String.class, MethodType.class));

            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            int installed = 0;
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != Method.class || !Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object value = field.get(null);
                if (stringMethod.equals(value) || plainMethod.equals(value)) {
                    modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
                    field.set(null, stringMethod.equals(value) ? stringReplacement : plainReplacement);
                    installed++;
                }
            }
            if (installed != 2) {
                Log.warn("Component 的取文本方法数量异常（" + installed + " 个），聊天文本保持英文");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Log.warn("无法翻译聊天文本: " + e);
        }
    }

    /**
     * 替换 {@code getString()} 的那份实现。
     * <p>签名必须和原方法完全一致（含前两个合成参数），否则 JVM 校验不过。
     */
    private static Object textWithTranslation(Component self,
                                              MethodHandles.Lookup caller,
                                              String name,
                                              MethodType type) {
        String plain = plain(self);
        if (Boolean.TRUE.equals(PATCHING.get())) return plain;
        PATCHING.set(Boolean.TRUE);
        try {
            String chinese = translate(plain);
            if (chinese.equals(plain)) return plain;
            // 原版拿 length() 当文本长度、拿 toString() 当文本内容，同一个字符串对象
            // 会被当成两种东西用，所以长度必须和原文一致。
            // 中文更短就补空格；更长就让这一帧返回 null（我们自己的那几句本帧不画）
            if (chinese.length() > plain.length()) return null;
            StringBuilder padded = new StringBuilder(chinese);
            while (padded.length() < plain.length()) padded.append(' ');
            return padded.toString();
        } finally {
            PATCHING.remove();
        }
    }

    /** 替换 {@code getUnformattedText()} 的那份实现：原样返回，只是不再走被包过的那条路。 */
    private static Object plainText(Component self,
                                    MethodHandles.Lookup caller,
                                    String name,
                                    MethodType type) {
        return plain(self);
    }

    /**
     * 取原文。
     * <p>{@code getString()} 在子类里的实现就是转去调 {@code getUnformattedText()}，
     * 而两份都已被换掉，直接调会绕回来；所以那种子类改调原来那份方法。
     */
    private static String plain(Component self) {
        try {
            if (mirrorsString(self)) return String.valueOf(plainMethod.invoke(self));
            return String.valueOf(stringMethod.invoke(self));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 这个子类的 {@code getString()} 是不是直接转给 {@code getUnformattedText()}。 */
    private static boolean mirrorsString(Component self) {
        return MIRROR.computeIfAbsent(self.getClass(), type -> {
            try {
                return type.getDeclaredMethod("getString").equals(stringMethod);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        });
    }

    // ---------- 2. 下拉框 ----------

    /**
     * 换掉 Verity 那几个枚举的显示名。
     * <p>YACL 的枚举控件用 {@code String.valueOf(value)} 取名、用类名 + 常量名存状态，
     * 只动显示名不影响保存；Verity 自己也没有拿显示名做过反查。
     */
    private static void patchEnums() {
        for (String className : VERITY_ENUMS) {
            try {
                Class<?> type = Class.forName(className, true, VerityConfig.class.getClassLoader());
                if (patchEnum(type)) Log.info("已翻译下拉框：" + type.getSimpleName());
            } catch (ClassNotFoundException | LinkageError e) {
                Log.warn("找不到 " + className + "，该下拉框保持英文");
            } catch (RuntimeException e) {
                Log.warn("无法翻译下拉框 " + className + ": " + e.getMessage());
            }
        }
    }

    private static boolean patchEnum(Class<?> type) {
        if (!type.isEnum()) return false;
        Object[] values = type.getEnumConstants();
        if (values == null || values.length == 0) return false;

        Map<Object, String> names = new LinkedHashMap<>();
        Set<String> used = new LinkedHashSet<>();
        for (Object value : values) {
            String chinese = ENUMS.get(((Enum<?>) value).name());
            if (chinese == null) return false;     // 有一个不认识就整组不动，免得只翻一半
            if (!used.add(chinese)) return false;  // 组内重名（YACL 画出来会一模一样），放弃
            names.put(value, chinese);
        }

        try {
            int swapped = 0;
            for (Map.Entry<Object, String> entry : names.entrySet()) {
                setEnumField(entry.getKey(), "name", entry.getValue());
                if (setEnumField(entry.getKey(), "toString", entry.getValue())) swapped++;
            }
            if (swapped != names.size()) {
                Log.warn(type.getSimpleName() + " 的显示名没能全部替换，该下拉框仍显示英文");
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** @return 是否真的写进去了 */
    private static boolean setEnumField(Object target, String name, String value)
            throws ReflectiveOperationException {
        Field field = Enum.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
        return value.equals(field.get(target));
    }

    // ---------- 3. 给界面用的入口 ----------

    /**
     * 把一句界面文字换成中文。
     * <p>配置界面现在由 {@code VerityConfigScreenFactory} 直接写出中文，
     * 不再需要它；这里保留给聊天侧和其它需要按句翻译的地方使用。
     * <p>顺带保证翻译表已初始化。翻不动就原样返回，所以最坏情况是某几句还是英文。
     */
    public static String translateLiteral(String text) {
        try {
            ensureReady();
            return translate(text);
        } catch (RuntimeException | LinkageError e) {
            return text;
        }
    }
}