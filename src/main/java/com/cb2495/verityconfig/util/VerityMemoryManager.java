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
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

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

    // ---------- 待处理操作检查 ----------
    private static boolean pendingChecked = false;

    // ---------- 自动备份触发 ----------
    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        Component message = event.getMessage();
        if (message == null) return;

        String text = message.getString().trim();
        if (!text.startsWith("<Verity>")) return;
        if (text.contains("ERROR")) return;

        scheduledBackupTime = System.currentTimeMillis() + BACKUP_DELAY_MS;
    }

    public static void cancelPending() {
        scheduledBackupTime = -1;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 主界面检查待处理操作（只执行一次）
        if (!pendingChecked) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof TitleScreen) {
                pendingChecked = true;
                checkAndExecutePending();
            }
        }

        // 备份延时
        if (scheduledBackupTime >= 0 && System.currentTimeMillis() >= scheduledBackupTime) {
            scheduledBackupTime = -1;
            performBackup();
        }
    }

    // ---------- 备份 ----------
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
            return true;
        }
        return false;
    }

    // ---------- 指令接口（仅安排，不立即执行） ----------
    public static void fix() {
        schedulePending("mfix", "");
    }

    public static void restore(String target) {
        schedulePending("rb", target);
    }

    public static void delete() {
        schedulePending("del", "");
    }

    private static void schedulePending(String operation, String target) {
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
            obj.addProperty("target", target);
            obj.addProperty("worldDir", worldDir.toAbsolutePath().toString());
            obj.addProperty("timestamp", System.currentTimeMillis());
            Files.writeString(pendingFile,
                    new GsonBuilder().setPrettyPrinting().create().toJson(obj),
                    StandardCharsets.UTF_8);

            sendMessage("操作已安排，请退出并重新启动游戏后生效");
            sendRestartHint();
        } catch (Exception e) {
            sendMessage("创建待处理文件失败: " + e.getMessage());
        }
    }

    private static Path getPendingFile() {
        return FMLPaths.GAMEDIR.get().resolve(".cache/verity_config/pendingFix.json");
    }

    // ---------- 主界面检查并执行待处理操作 ----------
    private static void checkAndExecutePending() {
        Path pendingFile = getPendingFile();
        if (!Files.exists(pendingFile)) return;

        try {
            String content = Files.readString(pendingFile, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            String operation = obj.get("operation").getAsString();
            String target = obj.has("target") ? obj.get("target").getAsString() : "";
            String worldDirStr = obj.get("worldDir").getAsString();

            Path worldDir = Path.of(worldDirStr);
            if (!Files.exists(worldDir)) {
                System.out.println("[VerityConfig] 待处理操作的目标存档不存在，跳过: " + worldDir);
                Files.delete(pendingFile);
                return;
            }

            String result = executePending(operation, target, worldDir);
            System.out.println("[VerityConfig] 待处理操作已执行: " + result);

            Minecraft.getInstance().gui.setOverlayMessage(
                    Component.literal("[VerityConfig] " + result).withStyle(ChatFormatting.GREEN),
                    false
            );

            Files.delete(pendingFile);
        } catch (Exception e) {
            System.err.println("[VerityConfig] 执行待处理操作失败: " + e.getMessage());
            Minecraft.getInstance().gui.setOverlayMessage(
                    Component.literal("[VerityConfig] 上次的修复/恢复/删除失败: " + e.getMessage())
                            .withStyle(ChatFormatting.RED),
                    false
            );
            try { Files.deleteIfExists(pendingFile); } catch (IOException ignored) {}
        }
    }

    private static String executePending(String operation, String target, Path worldDir) throws IOException {
        switch (operation) {
            case "mfix": {
                int removed = cleanEmptyAiMessages(worldDir.resolve(CHAT_MEMORY));
                return removed > 0 ? "已清理 " + removed + " 条空 AI 消息" : "未发现需要清理的空 AI 消息";
            }
            case "rb":
                return restoreFromBackup(target, worldDir);
            case "del":
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

    private static String restoreFromBackup(String target, Path worldDir) throws IOException {
        List<String> targets = new ArrayList<>();
        switch (target) {
            case "vcm": targets.add(CHAT_MEMORY); break;
            case "vm": targets.add(MEMORY); break;
            case "all":
                targets.add(CHAT_MEMORY);
                targets.add(MEMORY);
                break;
            default:
                return "未知的恢复类型: " + target;
        }

        int success = 0;
        for (String file : targets) {
            Path source = worldDir.resolve(file);
            Path backup = source.resolveSibling(source.getFileName().toString() + ".bak");
            if (!Files.exists(backup)) continue;
            Files.copy(backup, source, StandardCopyOption.REPLACE_EXISTING);
            success++;
        }
        return success > 0 ? "已恢复 " + success + " 个文件" : "未找到可恢复的备份";
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

    private static void sendRestartHint() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Component msg = Component.literal("[VerityConfig] ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("操作需要重启游戏才能生效，请输入 ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("/vc exitgame")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.AQUA)
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                        net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, "/vc exitgame"))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("点击填入命令，按回车执行")))
                        ))
                .append(Component.literal(" 退出游戏，随后再次启动。").withStyle(ChatFormatting.WHITE));

        mc.player.displayClientMessage(msg, false);
    }
}