package com.cb2495.verityconfig.util;

import com.cb2495.verityconfig.VerityConfig;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Verity 记忆文件管理。
 * <p>只提供两种操作：
 * <ul>
 *   <li><b>修复</b>（{@code mfix}）：清理聊天记忆中内容为空的 AI 消息；</li>
 *   <li><b>删除</b>（{@code mdel}）：删除记忆文件，让 Verity 重新生成。</li>
 * </ul>
 * 两者都只安排待处理操作，在玩家退出存档、回到世界选择界面时执行，
 * 避免在游戏运行中改动正在读写的文件（退出存档后文件句柄已关闭）。
 */
@Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT)
public class VerityMemoryManager {

    public static final String[] FILES = {
            "verity_chat_memory.json",
            "verity_memory.json"
    };

    private static final String CHAT_MEMORY = "verity_chat_memory.json";

    /** 待处理操作结果，留到下次进入存档时显示（世界选择界面没有聊天栏）。 */
    private static String pendingResult = null;
    private static boolean pendingResultError = false;

    // ---------- 待处理操作检查 ----------
    /**
     * 上次检查时是否处于「可执行待处理操作」的界面，用于避免每 tick 重复读文件。
     * 退出存档回到世界选择/主界面时会重新变为 false，从而再次触发检查。
     */
    private static boolean atWorldSelect = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 在世界选择界面或主界面检查待处理操作。
        // 退出存档后存档文件已关闭，此时即可安全执行，无需重启游戏。
        Minecraft mc = Minecraft.getInstance();
        boolean selectable = mc.screen instanceof SelectWorldScreen
                || mc.screen instanceof TitleScreen;
        if (!selectable) {
            atWorldSelect = false;
            return;
        }
        if (atWorldSelect) return; // 同一界面内只检查一次
        atWorldSelect = true;
        checkAndExecutePending();
    }

    // ---------- 指令接口（仅安排，不立即执行） ----------
    public static void fix() {
        schedulePending("mfix");
    }

    public static void delete() {
        schedulePending("mdel");
    }

    private static void schedulePending(String operation) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            sendMessage("仅单人存档可用");
            return;
        }
        Path worldDir = server.getWorldPath(LevelResource.ROOT);

        try {
            Path pendingFile = getPendingFile();
            Files.createDirectories(pendingFile.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("operation", operation);
            obj.addProperty("worldDir", worldDir.toAbsolutePath().toString());
            obj.addProperty("timestamp", System.currentTimeMillis());
            Files.writeString(pendingFile,
                    new GsonBuilder().setPrettyPrinting().create().toJson(obj),
                    StandardCharsets.UTF_8);

            sendMessage("需要退出存档重新进入以应用更改");
        } catch (Exception e) {
            sendMessage("创建待处理文件失败: " + e.getMessage());
        }
    }

    private static Path getPendingFile() {
        return FMLPaths.GAMEDIR.get().resolve(".cache/verity_config/pendingFix.json");
    }

    // ---------- 世界选择界面检查并执行待处理操作 ----------
    private static void checkAndExecutePending() {
        Path pendingFile = getPendingFile();
        if (!Files.exists(pendingFile)) return;

        try {
            String content = Files.readString(pendingFile, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            String operation = obj.get("operation").getAsString();
            String worldDirStr = obj.get("worldDir").getAsString();

            Path worldDir = Path.of(worldDirStr);
            if (!Files.exists(worldDir)) {
                System.out.println("[VerityConfig] 待处理操作的目标存档不存在，跳过: " + worldDir);
                Files.delete(pendingFile);
                return;
            }

            String result = executePending(operation, worldDir);
            System.out.println("[VerityConfig] 待处理操作已执行: " + result);
            // 此处处于世界选择界面，没有聊天栏与物品栏提示，结果留给下次进入存档时显示
            pendingResult = result;
            pendingResultError = false;

            Files.delete(pendingFile);
        } catch (Exception e) {
            System.err.println("[VerityConfig] 执行待处理操作失败: " + e.getMessage());
            pendingResult = "上次的修复/删除失败: " + e.getMessage();
            pendingResultError = true;
            try { Files.deleteIfExists(pendingFile); } catch (IOException ignored) {}
        }
    }

    /** 在下次进入存档时显示之前执行的结果。 */
    @SubscribeEvent
    public static void onClientPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (pendingResult == null) return;
        LocalPlayer player = event.getPlayer();
        if (player == null) return;

        String result = pendingResult;
        boolean error = pendingResultError;
        pendingResult = null;
        pendingResultError = false;

        player.displayClientMessage(
                Component.literal("[VerityConfig] ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(result)
                                .withStyle(error ? ChatFormatting.RED : ChatFormatting.GREEN)),
                false);
    }

    private static String executePending(String operation, Path worldDir) throws IOException {
        switch (operation) {
            case "mfix": {
                int removed = cleanEmptyAiMessages(worldDir.resolve(CHAT_MEMORY));
                return removed > 0 ? "已清理 " + removed + " 条空 AI 消息" : "未发现需要清理的空 AI 消息";
            }
            case "mdel":
                return deleteMemoryFiles(worldDir);
            default:
                return "未知操作: " + operation;
        }
    }

    private static int cleanEmptyAiMessages(Path chatMemory) throws IOException {
        if (!Files.exists(chatMemory)) return 0;

        String content = Files.readString(chatMemory, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(content).getAsJsonObject();
        JsonObject memory = root.getAsJsonObject("verity-chat-memory");
        if (memory == null) return 0;

        String messagesJson = memory.get("messages_json").getAsString();
        JsonArray messages = JsonParser.parseString(messagesJson).getAsJsonArray();

        JsonArray cleaned = new JsonArray();
        int removed = 0;
        for (JsonElement el : messages) {
            if (!el.isJsonObject()) {
                cleaned.add(el);
                continue;
            }
            JsonObject msg = el.getAsJsonObject();
            String type = msg.has("type") ? msg.get("type").getAsString() : "";
            if ("AI".equals(type)) {
                boolean hasText = msg.has("text") && !msg.get("text").getAsString().isEmpty();
                boolean hasToolCalls = msg.has("toolExecutionRequests")
                        && msg.getAsJsonArray("toolExecutionRequests").size() > 0;
                if (!hasText && !hasToolCalls) {
                    removed++;
                    continue;
                }
            }
            cleaned.add(el);
        }

        if (removed == 0) return 0;

        memory.addProperty("messages_json", cleaned.toString());
        String pretty = new GsonBuilder().setPrettyPrinting().create().toJson(root);
        Files.writeString(chatMemory, pretty, StandardCharsets.UTF_8);
        return removed;
    }

    private static String deleteMemoryFiles(Path worldDir) throws IOException {
        int success = 0;
        for (String file : FILES) {
            Path source = worldDir.resolve(file);
            if (Files.exists(source)) {
                Files.delete(source);
                success++;
            }
        }
        return success > 0 ? "已删除 " + success + " 个记忆文件" : "未找到可删除的记忆文件";
    }

    // ---------- 工具方法 ----------
    private static void sendMessage(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("[VerityConfig] ").withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal(text).withStyle(ChatFormatting.WHITE)),
                    false);
        }
    }

}
