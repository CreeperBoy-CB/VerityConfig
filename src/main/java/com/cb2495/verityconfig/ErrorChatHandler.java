package com.cb2495.verityconfig;

import com.cb2495.verityconfig.util.VerityConfigManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT)
public class ErrorChatHandler {

    @SubscribeEvent
    public static void onClientChatReceived(ClientChatReceivedEvent event) {
        Component message = event.getMessage();
        if (message == null) return;

        String text = message.getString();
        if (!text.contains("ERROR")) return;

        // 提取 JSON 部分
        String jsonPart = extractJson(text);
        if (jsonPart == null) {
            sendGenericMessage();
            return;
        }

        JsonObject json = parseJson(jsonPart);
        if (json == null) {
            sendGenericMessage();
            return;
        }

        // 通用错误：记忆文件错误（所有 AI 通用）
        String code = getCode(json);
        if ("invalid_request_error".equals(code)) {
            sendMemoryRestoreHint();
            return;
        }

        String provider = VerityConfigManager.getCurrentProvider();

        if ("DeepSeek".equals(provider)) {
            handleDeepSeekError(json);
        } else if ("智谱 (GLM)".equals(provider)) {
            handleZhipuError(json);
        } else {
            sendGenericMessage();
        }
    }

    private static void sendMemoryRestoreHint() {
        pendingComponents.add(
                Component.literal("[VerityConfig] ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("记忆文件错误，可以通过 ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("/vc verity mfix rb vcm").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" 来恢复聊天记忆，").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("/vc verity mfix rb vm").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" 恢复长期记忆，").withStyle(ChatFormatting.WHITE))
        );
        pendingComponents.add(
                Component.literal("[VerityConfig] ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("如果仍不起作用，可使用 ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("/vc verity mfix rb all").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" 一次性恢复全部备份，或 ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("/vc verity mfix rb del").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" 删除记忆文件让 Verity 重新生成。").withStyle(ChatFormatting.WHITE))
        );
        pendingTicks = 2;
    }

    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private static JsonObject parseJson(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static final java.util.List<Component> pendingComponents = new java.util.ArrayList<>();
    private static int pendingTicks = 0;

    private static void handleDeepSeekError(JsonObject json) {
        String errorMessage = getMessage(json);

        if (errorMessage.contains("Authentication Fails, Your api key:") && errorMessage.contains("is invalid")) {
            sendMessage("填写了错误的 API Key，请输入 /vc cs 并重新走一遍 AI 配置流程");
        } else if (errorMessage.contains("Insufficient Balance")) {
            sendMessageWithLink("账号余额不足，请", "点击此处", "https://platform.deepseek.com/top_up", "前往 DeepSeek 官网充值");
        } else {
            sendGenericMessage();
        }
    }

    private static void handleZhipuError(JsonObject json) {
        String code = getCode(json);

        switch (code) {
            case "1113":
                sendMessageWithLink("如果你选择了付费模型，需要前往", "此处",
                        "https://open.bigmodel.cn/finance-center/finance/pay",
                        "给智谱账号充值，否则请输入 /vc cs 并将模型切换至免费模型");
                break;
            case "1302":
                sendMessage("智谱限速较明显，建议切换至其他提供商或使用付费模型，如果不想切换可以等待一小段时间或输入 /vc cs 切换其他免费模型");
                break;
            case "1305":
                sendMessage("智谱免费模型使用人数较多，可以尝试输入 /vc cs 切换至其他模型");
                break;
            default:
                sendGenericMessage();
        }
    }

    private static void sendGenericMessage() {
        sendMessage("请查看报错内容中的 message 段落，如果没有则重点观看整段信息，也可以把它发到QQ群或发给豆包");
    }

    private static void sendMessage(String text) {
        pendingComponents.add(
                Component.literal("[VerityConfig] ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(text).withStyle(ChatFormatting.WHITE))
        );
        pendingTicks = 2;
    }

    private static void sendMessageWithLink(String prefix, String linkText, String url, String suffix) {
        Component msg = Component.literal("[VerityConfig] ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(prefix).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(linkText)
                        .withStyle(style -> style
                                .withColor(ChatFormatting.AQUA)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击打开链接")))
                        ))
                .append(Component.literal(suffix).withStyle(ChatFormatting.WHITE));
        pendingComponents.add(msg);
        pendingTicks = 2;
    }

    private static String getCode(JsonObject json) {
        // 优先从 error 对象中取
        if (json.has("error") && json.get("error").isJsonObject()) {
            JsonObject error = json.getAsJsonObject("error");
            if (error.has("code")) return error.get("code").getAsString();
        }
        // 回退到顶层
        return json.has("code") ? json.get("code").getAsString() : "";
    }

    private static String getMessage(JsonObject json) {
        if (json.has("error") && json.get("error").isJsonObject()) {
            JsonObject error = json.getAsJsonObject("error");
            if (error.has("message")) return error.get("message").getAsString();
        }
        return json.has("message") ? json.get("message").getAsString() : "";
    }

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (pendingTicks <= 0) return;
        pendingTicks--;
        if (pendingTicks == 0 && !pendingComponents.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            for (Component c : pendingComponents) {
                if (mc.player != null) {
                    mc.player.displayClientMessage(c, false);
                }
            }
            pendingComponents.clear();
        }
    }

}