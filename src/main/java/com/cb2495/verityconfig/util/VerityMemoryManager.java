package com.cb2495.verityconfig.util;

import com.cb2495.verityconfig.VerityConfig;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT)
public class VerityMemoryManager {

    public static final String[] FILES = {
            "verity_chat_memory.json",
            "verity_memory.json"
    };

    private static final String CHAT_MEMORY = "verity_chat_memory.json";
    private static final String MEMORY = "verity_memory.json";

    // ---------- 自动备份相关 ----------
    private static final long BACKUP_DELAY_MS = 5000;
    private static long scheduledBackupTime = -1;

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        Component message = event.getMessage();
        if (message == null) return;

        String text = message.getString().trim();
        if (!text.startsWith("<Verity>")) return;
        if (text.contains("ERROR")) return;

        scheduledBackupTime = System.currentTimeMillis() + BACKUP_DELAY_MS;
    }

    /** 出错时取消待执行的备份，避免坏文件覆盖好备份 */
    public static void cancelPending() {
        scheduledBackupTime = -1;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (scheduledBackupTime < 0) return;

        if (System.currentTimeMillis() >= scheduledBackupTime) {
            scheduledBackupTime = -1;
            performBackup();
        }
    }

    private static void performBackup() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;

        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        backupFile(worldDir, CHAT_MEMORY, true);
        backupFile(worldDir, MEMORY, false);
    }

    private static void backupFile(Path worldDir, String relativePath, boolean checkCorrupted) {
        Path source = worldDir.resolve(relativePath);
        if (!Files.exists(source)) return;

        if (checkCorrupted && isCorruptedChatMemory(source)) {
            System.err.println("[VerityConfig] 检测到损坏的聊天记忆，跳过备份: " + source.getFileName());
            return;
        }

        Path target = source.resolveSibling(source.getFileName().toString() + ".bak");
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[VerityConfig] 已备份: " + target.getFileName());
        } catch (IOException e) {
            System.err.println("[VerityConfig] 备份失败: " + source + " - " + e.getMessage());
        }
    }

    /** 检查是否存在"空 AI 消息"（无 text 且无 tool calls），这会触发 invalid_request_error */
    private static boolean isCorruptedChatMemory(Path source) {
        try {
            String content = Files.readString(source, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            JsonObject memory = root.getAsJsonObject("verity-chat-memory");
            if (memory == null) return false;

            String messagesJson = memory.get("messages_json").getAsString();
            JsonArray messages = JsonParser.parseString(messagesJson).getAsJsonArray();

            for (JsonElement el : messages) {
                JsonObject msg = el.getAsJsonObject();
                String type = msg.has("type") ? msg.get("type").getAsString() : "";
                if (!"AI".equals(type)) continue;

                boolean hasText = msg.has("text") && !msg.get("text").getAsString().isEmpty();
                boolean hasToolCalls = msg.has("toolExecutionRequests")
                        && msg.getAsJsonArray("toolExecutionRequests").size() > 0;

                if (!hasText && !hasToolCalls) return true;
            }
        } catch (Exception e) {
            return true; // 解析失败也算损坏
        }
        return false;
    }

    // ---------- 修复 ----------
    /** 修复聊天记忆文件：移除"空 AI 消息" */
    public static void fix() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            sendMessage("仅单人存档可用");
            return;
        }
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        Path chatMemory = worldDir.resolve(CHAT_MEMORY);

        if (!Files.exists(chatMemory)) {
            sendMessage("未找到 " + CHAT_MEMORY);
            return;
        }

        try {
            String content = Files.readString(chatMemory, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            JsonObject memory = root.getAsJsonObject("verity-chat-memory");
            if (memory == null) {
                sendMessage("记忆文件结构异常，未找到 verity-chat-memory 节点");
                return;
            }

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

            if (removed == 0) {
                sendMessage("未发现需要修复的空 AI 消息");
                return;
            }

            memory.addProperty("messages_json", cleaned.toString());
            String pretty = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            Files.writeString(chatMemory, pretty, StandardCharsets.UTF_8);

            sendMessage("已修复，移除了 " + removed + " 条空 AI 消息");
        } catch (Exception e) {
            sendMessage("修复失败: " + e.getMessage());
        }
    }

    // ---------- 恢复 ----------
    public static void restore(String type) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            sendMessage("仅单人存档可用");
            return;
        }
        Path worldDir = server.getWorldPath(LevelResource.ROOT);

        List<String> targets = new ArrayList<>();
        switch (type) {
            case "vcm": targets.add(CHAT_MEMORY); break;
            case "vm": targets.add(MEMORY); break;
            case "all":
                targets.add(CHAT_MEMORY);
                targets.add(MEMORY);
                break;
            default:
                sendMessage("未知的恢复类型: " + type);
                return;
        }

        for (String file : targets) {
            Path source = worldDir.resolve(file);
            Path backup = source.resolveSibling(source.getFileName().toString() + ".bak");
            if (!Files.exists(backup)) {
                sendMessage("备份不存在: " + backup.getFileName());
                continue;
            }
            try {
                Files.copy(backup, source, StandardCopyOption.REPLACE_EXISTING);
                sendMessage("已恢复: " + source.getFileName());
            } catch (IOException e) {
                sendMessage("恢复失败: " + source.getFileName() + " - " + e.getMessage());
            }
        }
    }

    // ---------- 删除 ----------
    public static void delete() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            sendMessage("仅单人存档可用");
            return;
        }
        Path worldDir = server.getWorldPath(LevelResource.ROOT);

        for (String file : FILES) {
            Path source = worldDir.resolve(file);
            if (Files.exists(source)) {
                try {
                    Files.delete(source);
                    sendMessage("已删除: " + source.getFileName());
                } catch (IOException e) {
                    sendMessage("删除失败: " + source.getFileName() + " - " + e.getMessage());
                }
            }
        }
    }

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