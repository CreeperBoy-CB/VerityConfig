package com.cb2495.verityconfig.util;

import net.minecraft.client.Minecraft;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 配置文件的读写线程。
 * <p>这些文件都很小，但读写原本发生在渲染线程上：输入框每敲一个字符、
 * 拖动滑块每一帧都会触发一次「读-改-写」，造成的掉帧是能看见的。
 * 统一挪到一个后台线程之后，渲染线程只负责取值和更新控件。
 * <p>用<b>单线程</b>而不是线程池：多个保存都是对同一批文件做「读-改-写」，
 * 并发执行会互相覆盖，串行化是这里正确性的前提。
 */
public final class ConfigIO {

    private ConfigIO() {}

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "VerityConfig-IO");
        // 守护线程：不会阻止游戏退出
        thread.setDaemon(true);
        return thread;
    });

    static {
        // 退出时给排队中的写入一点时间落盘，避免刚改完就关游戏导致配置丢失。
        // 只等待已提交的任务；尚未到点的去抖改动由界面在关闭时 flush。
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            EXECUTOR.shutdown();
            try {
                EXECUTOR.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "VerityConfig-IO-shutdown"));
    }

    /** 在后台线程执行磁盘读写。 */
    public static void run(Runnable task) {
        EXECUTOR.execute(task);
    }

    /**
     * 把结果交回客户端主线程执行。
     * <p>后台线程不能直接碰控件，任何要更新界面的动作都必须经过这里；
     * 队列在主线程 tick 时统一排空，因此不会打断正在进行的渲染。
     */
    public static void backToClient(Runnable task) {
        Minecraft.getInstance().execute(task);
    }
}
