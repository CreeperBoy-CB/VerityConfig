package com.cb2495.verityconfig.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
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

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

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

    /** 节假日查询接口域名。 */
    private static final String HOLIDAY_HOST = "api.apisbo.com";
    /** 批量查询路径，POST 请求体为 {"dates":["YYYY-MM-DD", ...]}。 */
    private static final String BATCH_PATH = "/holidays/batch";
    /** 每次请求取多少天；一次可覆盖春节等最长假期，且仍只发一次请求。 */
    private static final int PREFETCH_DAYS = 30;

    /** 连接超时（毫秒）。IPv6 不通时首个请求会在此时间后失败并转入 IPv4 重试。 */
    private static final int CONNECT_TIMEOUT_MS = 5000;
    /** 读取超时（毫秒）。 */
    private static final int READ_TIMEOUT_MS = 5000;

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
    /** 当天已尝试过请求的标记，避免接口失败后每 tick 重复发起请求。 */
    private static volatile LocalDate todayAttempted = null;
    /** 是否有批量请求正在进行中。 */
    private static volatile boolean bulkPrefetching = false;
    /** 常规连接失败、IPv4 重试成功后置位，后续请求直接走 IPv4。 */
    private static volatile boolean preferIpv4 = false;

    // ---------- 查询与缓存 ----------

    /**
     * 拉取当天起 {@link #PREFETCH_DAYS} 天的节假日数据并写入缓存。
     * <p>接口支持批量查询，因此无论多少天都只发一次请求。
     * <p>当天只尝试一次：接口失败后不再重复请求，直接沿用周末判断。
     * 跨天后 {@code todayAttempted} 不再匹配，会重新拉取。
     */
    public static void refreshIfNeeded() {
        LocalDate today = LocalDate.now();
        if (holidayCache.containsKey(today)) return;
        if (today.equals(todayAttempted)) return;
        todayAttempted = today;

        CompletableFuture.runAsync(() -> fetchRangeFrom(today));
    }

    /**
     * 批量拉取 {@code start} 起若干天的数据并写入缓存。
     * <p>网络请求应在后台线程调用；失败时静默退回（缓存不写入，
     * 判断时按周末估算）。
     *
     * @param start 起始日期
     */
    private static void fetchRangeFrom(LocalDate start) {
        if (bulkPrefetching) return;
        bulkPrefetching = true;
        try {
            String body = fetchBatch(start, PREFETCH_DAYS);
            if (body == null) return;
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!root.has("data") || !root.get("data").isJsonArray()) return;
            for (JsonElement element : root.getAsJsonArray("data")) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                if (!item.has("date") || !item.has("isHoliday")) continue;
                LocalDate date;
                try {
                    date = LocalDate.parse(item.get("date").getAsString());
                } catch (Exception ignored) {
                    continue;
                }
                boolean isHoliday = item.get("isHoliday").getAsBoolean();
                // 调休上班的周末 isHoliday 为 false、isWorkday 为 true，
                // 但规则要求调休周末仍按空闲时段计费，故必须叠加周末判断
                holidayCache.put(date, isHoliday || isWeekend(date));
            }
        } catch (Exception e) {
            System.err.println("[VerityConfig] 查询节假日失败，退回本地判断: " + e.getMessage());
        } finally {
            bulkPrefetching = false;
        }
    }

    /**
     * 发起批量查询请求，返回响应正文；失败返回 null。
     * <p>先按常规方式连接；若失败（常见于本机 IPv6 不通而域名解析优先返回
     * IPv6 地址，表现为 Connect/Read timed out），再改用 IPv4 地址重试。
     * <p>一旦 IPv4 重试成功过，后续请求直接走 IPv4，避免每次都白等一次超时。
     */
    private static String fetchBatch(LocalDate start, int days) throws Exception {
        JsonArray dates = new JsonArray();
        for (int i = 0; i < days; i++) {
            dates.add(start.plusDays(i).toString());
        }
        JsonObject payload = new JsonObject();
        payload.add("dates", dates);
        String json = payload.toString();

        if (preferIpv4) {
            try {
                return postViaIpv4(HOLIDAY_HOST, BATCH_PATH, json);
            } catch (Exception e) {
                // IPv4 也失败时退回常规方式再试一次
                return post(HOLIDAY_HOST, BATCH_PATH, json);
            }
        }

        Exception firstFailure;
        try {
            return post(HOLIDAY_HOST, BATCH_PATH, json);
        } catch (Exception e) {
            firstFailure = e;
        }
        // 常规方式失败，改用 IPv4 直连重试；SNI 与证书校验仍使用域名
        try {
            String body = postViaIpv4(HOLIDAY_HOST, BATCH_PATH, json);
            preferIpv4 = true;
            return body;
        } catch (Exception e) {
            System.err.println("[VerityConfig] IPv4 重试仍失败: " + e.getMessage());
            throw firstFailure;
        }
    }

    /** 常规 HTTP POST 请求，返回响应正文。 */
    private static String post(String host, String path, String json) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("https://" + host + path).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            try (OutputStream out = conn.getOutputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() != 200) return null;
            return readAll(conn.getInputStream());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 用 IPv4 地址直连并发起 HTTPS POST 请求。
     * <p>先以 IPv4 地址建立普通 socket，再用域名包装成 SSL socket，
     * 使 SNI 与证书校验都针对域名，从而保持证书校验有效。
     */
    private static String postViaIpv4(String host, String path, String json) throws IOException {
        InetAddress ipv4 = null;
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address instanceof Inet4Address) {
                ipv4 = address;
                break;
            }
        }
        if (ipv4 == null) throw new IOException("域名无 IPv4 地址");

        byte[] bodyBytes = json.getBytes(StandardCharsets.UTF_8);
        Socket plain = new Socket();
        SSLSocket ssl = null;
        try {
            plain.connect(new InetSocketAddress(ipv4, 443), CONNECT_TIMEOUT_MS);
            // getDefault() 的声明返回类型是 SocketFactory，
            // 需转型后才能调用 SSLSocketFactory 的 createSocket(Socket, ...) 重载
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            ssl = (SSLSocket) factory.createSocket(plain, host, 443, true);
            ssl.setSoTimeout(READ_TIMEOUT_MS);
            ssl.startHandshake();

            // 该接口返回 Content-Length 且 Connection: close，无需处理分块编码
            String header = "POST " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Accept: application/json\r\n"
                    + "Content-Length: " + bodyBytes.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream out = ssl.getOutputStream();
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(bodyBytes);
            out.flush();

            String raw = readAll(ssl.getInputStream());
            int headerEnd = raw.indexOf("\r\n\r\n");
            if (headerEnd < 0) return null;
            String statusLine = raw.substring(0, raw.indexOf("\r\n"));
            if (!statusLine.contains(" 200 ")) return null;
            return raw.substring(headerEnd + 4);
        } finally {
            if (ssl != null) {
                try { ssl.close(); } catch (IOException ignored) {}
            } else {
                try { plain.close(); } catch (IOException ignored) {}
            }
        }
    }

    /** 读取流中的全部文本。 */
    private static String readAll(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
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
     * <p>搜索中若遇到未缓存的日期，会在后台补取，本次仍按周末估算；
     * 进入存档时已预取未来 {@link #PREFETCH_DAYS} 天，正常不会走到这个兜底。
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
            // 搜索跨入新日期时按需补取，避免未缓存日期被按周末估算而误判
            LocalDate cursorDate = cursor.toLocalDate();
            if (!cursorDate.equals(scannedDate)) {
                scannedDate = cursorDate;
                prefetchFrom(cursorDate);
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

    /**
     * 后台补取 {@code from} 起若干天的数据；已缓存或已在请求中时跳过。
     * <p>供跨天预测兜底使用，数据缺失时才真正发起请求。
     */
    private static void prefetchFrom(LocalDate from) {
        if (holidayCache.containsKey(from)) return;
        if (bulkPrefetching) return;
        CompletableFuture.runAsync(() -> fetchRangeFrom(from));
    }

    /** 下一个谷期起点时刻，用于峰期提示。 */
    public static LocalDateTime nextValleyStart() {
        return nextValleyStart(LocalDateTime.now());
    }

    /** 下一个谷期起点时刻；指定基准时刻，供测试指令使用。 */
    public static LocalDateTime nextValleyStart(LocalDateTime from) {
        return nextBoundary(from, false);
    }

    /** 下一个高峰起点时刻，用于谷期提示。 */
    public static LocalDateTime nextPeakStart() {
        return nextPeakStart(LocalDateTime.now());
    }

    /** 下一个高峰起点时刻；指定基准时刻，供测试指令使用。 */
    public static LocalDateTime nextPeakStart(LocalDateTime from) {
        return nextBoundary(from, true);
    }

    /** 将时刻格式化为"月份-日期 时:分"。 */
    public static String format(LocalDateTime moment) {
        return moment.format(TIME_FORMAT);
    }

    /** 当天数据是否已就绪（无论来自接口还是退回本地）。 */
    public static boolean isReady() {
        return isReady(LocalDate.now());
    }

    /** 指定日期的数据是否已就绪；指定日期，供测试指令使用。 */
    public static boolean isReady(LocalDate date) {
        return holidayCache.containsKey(date);
    }

    /**
     * 同步查询指定日期的节假日数据，供测试指令在提示前拿到准确结果。
     * <p>会批量取该日期起 {@link #PREFETCH_DAYS} 天，因此阻塞调用线程，
     * 仅供手动触发的测试使用，不可放进每 tick 的路径。
     */
    public static void queryNow(LocalDate date) {
        fetchRangeFrom(date);
    }

    /** 是否仍有请求正在进行中，用于推迟依赖跨天预测的提示。 */
    public static boolean isPrefetching() {
        return bulkPrefetching;
    }
}
