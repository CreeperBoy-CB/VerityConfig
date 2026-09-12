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

        String provider = VerityConfigManager.getCurrentProvider();

        if ("DeepSeek".equals(provider)) {
            handleDeepSeekError(json);
        } else if ("智谱 (GLM)".equals(provider)) {
            handleZhipuError(json);
        } else {
            sendGenericMessage();
        }
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

    private static void handleDeepSeekError(JsonObject json) {
        String errorMessage = json.has("message") ? json.get("message").getAsString() : "";

        // 401: Authentication Fails, Your api key: xxx is invalid
        if (errorMessage.contains("Authentication Fails, Your api key:") && errorMessage.contains("is invalid")) {
            sendMessage("填写了错误的 API Key，请输入 /vc cs 并重新走一遍 AI 配置流程");
        }
        // 402: Insufficient Balance
        else if (errorMessage.contains("Insufficient Balance")) {
            sendMessageWithLink("账号余额不足，请", "点击此处", "https://platform.deepseek.com/top_up", "前往 DeepSeek 官网充值");
        } else {
            sendGenericMessage();
        }
    }

    private static void handleZhipuError(JsonObject json) {
        Integer code = null;
        if (json.has("code")) {
            try {
                code = json.get("code").getAsInt();
            } catch (Exception ignored) {}
        }

        if (code == null) {
            sendGenericMessage();
            return;
        }

        switch (code) {
            case 1113:
                sendMessageWithLink("如果你选择了付费模型，需要前往", "此处", "https://open.bigmodel.cn/finance-center/finance/pay", "给智谱账号充值，否则请输入 /vc cs 并将模型切换至免费模型");
                break;
            case 1302:
                sendMessage("智谱限速较明显，建议切换至其他提供商或使用付费模型，如果不想切换可以等待一小段时间或输入 /vc cs 切换其他免费模型");
                break;
            case 1305:
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.displayClientMessage(
                Component.literal("[VerityConfig] ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(text).withStyle(ChatFormatting.WHITE)),
                false
        );
    }

    private static void sendMessageWithLink(String prefix, String linkText, String url, String suffix) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Component msg = Component.literal("[VerityConfig] ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(prefix).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(linkText)
                        .withStyle(style -> style
                                .withColor(ChatFormatting.AQUA)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击打开链接")))
                        ))
                .append(Component.literal(suffix).withStyle(ChatFormatting.WHITE));

        mc.player.displayClientMessage(msg, false);
    }
}