package com.cb2495.verityconfig.util;

/**
 * 尾沿去抖任务：连续触发只会真正执行一次。
 * <p>{@link #schedule()} 每被调用一次就把执行时刻往后推，只有在
 * {@link #DELAY_MS} 毫秒内不再有新的调用时才执行。用于「停止输入后再保存」
 * 和「停止拖动后再保存」——连续敲键或拖滑块原本每一步都会写一次磁盘。
 * <p>需要每 tick 调用 {@link #tick()} 来推进计时。
 */
public final class DebouncedTask {

    /** 静默多久之后才真正执行（毫秒）。 */
    public static final long DELAY_MS = 700;

    private final Runnable action;

    /** 计划执行时刻；负数表示当前没有待执行的改动。 */
    private volatile long dueAt = -1L;

    public DebouncedTask(Runnable action) {
        this.action = action;
    }

    /** 记录一次改动。重复调用只会把执行时刻继续往后推。 */
    public void schedule() {
        dueAt = System.currentTimeMillis() + DELAY_MS;
    }

    /** 推进计时；到点后执行一次。应在每 tick 调用。 */
    public void tick() {
        if (dueAt < 0L || System.currentTimeMillis() < dueAt) return;
        dueAt = -1L;
        action.run();
    }

    /**
     * 立即执行尚未到点的改动（没有待执行内容时什么也不做）。
     * <p>关闭界面或窗口尺寸变化会重建控件，必须先把改动落盘：否则
     * {@code init()} 会从磁盘重新读值，把用户刚输入的内容覆盖掉。
     */
    public void flush() {
        if (dueAt < 0L) return;
        dueAt = -1L;
        action.run();
    }

    /**
     * 取消待执行的改动并立即执行一次。
     * <p>用于「点一下就该马上生效」的操作（例如粘贴 API Key 按钮），
     * 顺带把已经排队的那次去抖取消掉，避免同一个状态写两遍。
     */
    public void runNow() {
        dueAt = -1L;
        action.run();
    }

    /** 是否有尚未执行的改动。 */
    public boolean isPending() {
        return dueAt >= 0L;
    }
}
