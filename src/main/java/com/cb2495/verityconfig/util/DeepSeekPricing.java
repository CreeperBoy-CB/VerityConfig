package com.cb2495.verityconfig.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DeepSeek API 的峰谷计价时段判断。
 * <p>
 * 规则（北京时间）：
 * <ul>
 *   <li>高峰：周一至周五（不含中国法定节假日）9:00-12:00、14:00-18:00</li>
 *   <li>空闲：其余全部时段，含周末与中国法定节假日全天</li>
 *   <li>调休上班的周末仍按空闲时段计费</li>
 * </ul>
 * <p>
 * 法定节假日通过第三方接口查询，失败时退回本地判断（仅区分周一至周五与周末），
 * 此时无法识别法定节假日，可能把节假日的工作日误判为高峰。
 */
public final class DeepSeekPricing {

    private DeepSeekPricing() {}

    /** 节假日查询接口，日期以 YYYY-MM-DD 拼接。 */
    private static final String HOLIDAY_API = "https://api.apisbo.com/holidays/date/";

    /** 高峰上午段起点。 */
    private static final LocalTime MORNING_START = LocalTime.of(9, 0);
    /** 高峰上午段终点。 */
    private static final LocalTime MORNING_END = LocalTime.of(12, 0);
    /** 高峰下午段起点。 */
    private static final LocalTime AFTERNOON_START = LocalTime.of(14, 0);
    /** 高峰下午段终点。 */
    private static final LocalTime AFTERNOON_END = LocalTime.of(18, 0);

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("M月d日 H:mm");

    /** 已查询过的日期 -> 是否空闲日（法定节假日或周末）。 */
    private static final Map<LocalDate, Boolean> holidayCache = new ConcurrentHashMap<>();
    /** 正在请求中的日期，避免重复发起同一日的请求。 */
    private static final java.util.Set<LocalDate> prefetching = ConcurrentHashMap.newKeySet();
    /** 当天已尝试过请求的标记，避免接口失败后每 tick 重复发起请求。 */
    private static volatile LocalDate todayAttempted = null;
    /** 是否正在批量预取未来日期。 */
    private static volatile boolean bulkPrefetching = false;

    // ---------- 查询与缓存 ----------

    /**
     * 若当天尚未查询过，则异步拉取节假日信息。
     * <p>网络请求放到后台线程，避免阻塞客户端主线程。
     * <p>当天只尝试一次：接口失败后不再重复请求，直接沿用周末判断。
     */
    public static void refreshIfNeeded() {
        LocalDate today = LocalDate.now();
        if (holidayCache.containsKey(today)) return;
        if (today.equals(todayAttempted)) return;
        todayAttempted = today;

        CompletableFuture.runAsync(() -> queryHoliday(today));
    }

    /**
     * 查询并缓存指定日期是否为空闲日，已缓存则直接返回。
     * <p>用于跨天预测时按需获取目标日期的真实节假日信息。
     * <p>接口失败时本次按周末估算返回，但<b>不写入缓存</b>，
     * 以便接口恢复后能重新查询到真实结果。
     */
    private static boolean queryHoliday(LocalDate date) {
        Boolean cached = holidayCache.get(date);
        if (cached != null) return cached;

        boolean weekend = isWeekend(date);
        try {
            prefetching.add(date);
            JsonObject data = fetchHolidayData(date);
            if (data != null && data.has("isHoliday")) {
                boolean isHoliday = data.get("isHoliday").getAsBoolean();
                // 调休上班的周末 isHoliday 为 false、isWorkday 为 true，
                // 但规则要求调休周末仍按空闲时段计费，故必须叠加周末判断
                boolean result = isHoliday || weekend;
                holidayCache.put(date, result);
                return result;
            }
        } catch (Exception e) {
            System.err.println("[VerityConfig] 查询 " + date + " 节假日失败，退回本地判断: " + e.getMessage());
        } finally {
            prefetching.remove(date);
        }
        // 接口不可用或返回异常：仅按周末判断，且不缓存以免后续不再重试
        return weekend;
    }

    /** 发起一次 HTTP 请求并解析 data 节点，失败返回 null。 */
    private static JsonObject fetchHolidayData(LocalDate date) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(HOLIDAY_API + date);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() != 200) return null;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JsonObject root = JsonParser.parseString(sb.toString()).getAsJsonObject();
            if (!root.has("data") || !root.get("data").isJsonObject()) return null;
            return root.getAsJsonObject("data");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ---------- 时段判断 ----------

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    /** 指定日期是否为节假日或周末；未缓存时按周末估算，不阻塞主线程。 */
    private static boolean isHolidayOrWeekend(LocalDate date) {
        Boolean cached = holidayCache.get(date);
        if (cached != null) return cached;
        return isWeekend(date);
    }

    /**
     * 预取指定时刻之后若干天的节假日信息，供跨天预测使用。
     * <p>在后台线程串行执行，避免一次并发发起过多请求。
     */
    private static void prefetchRange(LocalDate from, int days) {
        CompletableFuture.runAsync(() -> {
            for (int i = 1; i <= days; i++) {
                queryHoliday(from.plusDays(i));
            }
        });
    }

    /** 当前是否处于高峰时段。 */
    public static boolean isPeak() {
        return isPeakAt(LocalDateTime.now());
    }

    /** 判断指定时刻是否处于高峰时段。 */
    public static boolean isPeakAt(LocalDateTime moment) {
        LocalDate date = moment.toLocalDate();

        // 周末与法定节假日全天空闲（含调休上班的周末）
        if (isHolidayOrWeekend(date)) return false;

        LocalTime time = moment.toLocalTime();
        boolean morning = !time.isBefore(MORNING_START) && time.isBefore(MORNING_END);
        boolean afternoon = !time.isBefore(AFTERNOON_START) && time.isBefore(AFTERNOON_END);
        return morning || afternoon;
    }

    /**
     * 计算下一个时段切换时刻。
     * <p>搜索中若遇到未缓存的日期，会在后台预取该日数据，本次仍按周末估算；
     * 进入存档时已预取未来若干天，因此正常情况不会走到这个兜底。
     *
     * @param targetPeak true 找下一个高峰起点，false 找下一个谷期起点
     */
    private static LocalDateTime nextBoundary(LocalDateTime from, boolean targetPeak) {
        // 以分钟为步长向未来搜索，最多 8 天，足以覆盖任意节假日连休
        LocalDateTime cursor = from.withSecond(0).withNano(0).plusMinutes(1);
        int limit = 8 * 24 * 60;
        boolean currentPeak = isPeakAt(cursor.minusMinutes(1));
        LocalDate scannedDate = from.toLocalDate();
        for (int i = 0; i < limit; i++) {
            // 搜索跨入新日期时按需预取，避免未缓存日期被按周末估算而误判
            LocalDate cursorDate = cursor.toLocalDate();
            if (!cursorDate.equals(scannedDate)) {
                scannedDate = cursorDate;
                prefetch(cursorDate);
            }
            boolean peak = isPeakAt(cursor);
            // 状态发生跨越：谷->峰 得到高峰起点，峰->谷 得到谷期起点
            if (peak != currentPeak) {
                if (peak == targetPeak) return cursor;
                currentPeak = peak;
            }
            cursor = cursor.plusMinutes(1);
        }
        return from;
    }

    /** 预取单个日期的数据；已缓存或已在请求中时直接跳过。 */
    private static void prefetch(LocalDate date) {
        if (holidayCache.containsKey(date)) return;
        if (!prefetching.add(date)) return;
        CompletableFuture.runAsync(() -> queryHoliday(date));
    }

    /**
     * 预取从明天起若干天的数据，供跨天预测尽早拿到准确值。
     * <p>进入存档时调用，可覆盖"周末谷期到下周一高峰"这类跨天场景。
     * <p>串行请求，避免一次性向接口发起过多并发连接。
     */
    public static void prefetchUpcoming(int days) {
        LocalDate today = LocalDate.now();
        bulkPrefetching = true;
        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 1; i <= days; i++) {
                    LocalDate date = today.plusDays(i);
                    if (holidayCache.containsKey(date)) continue;
                    queryHoliday(date);
                }
            } finally {
                bulkPrefetching = false;
            }
        });
    }

    /** 下一个谷期起点时刻，用于峰期提示。 */
    public static LocalDateTime nextValleyStart() {
        return nextBoundary(LocalDateTime.now(), false);
    }

    /** 下一个高峰起点时刻，用于谷期提示。 */
    public static LocalDateTime nextPeakStart() {
        return nextBoundary(LocalDateTime.now(), true);
    }

    /** 将时刻格式化为"月份-日期 时:分"。 */
    public static String format(LocalDateTime moment) {
        return moment.format(TIME_FORMAT);
    }

    /** 当天数据是否已就绪（无论来自接口还是退回本地）。 */
    public static boolean isReady() {
        return holidayCache.containsKey(LocalDate.now());
    }

    /** 是否仍有日期正在后台请求中，用于推迟依赖跨天预测的提示。 */
    public static boolean isPrefetching() {
        return bulkPrefetching || !prefetching.isEmpty();
    }
}
