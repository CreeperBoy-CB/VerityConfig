package com.cb2495.verityconfig.client;

import com.cb2495.verityconfig.config.VerityConfigManager;
import com.cb2495.verityconfig.util.Log;

import net.minecraftforge.common.ForgeConfigSpec;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Verity 配置项（{@code ForgeConfigSpec}）的读取层。
 * <p>Verity 的配置全部写在 {@code varmite.verity.VerityConfig} 里：每项是一个
 * {@code public static final ForgeConfigSpec.xxxValue} 字段，{@code SPEC} 负责整份存取。
 * 本模组不复制那份清单——那是另一个模组的内部实现，抄一份就要跟着它一起改，
 * 改漏了会读到错的默认值或者把配置写坏。
 * <p>Verity 没装时 {@link #spec()} 返回 {@code null}，调用方据此退回原界面。
 */
public final class VeritySpec {

    private VeritySpec() {}

    /** Verity 的配置类。 */
    private static final String CONFIG_CLASS = "varmite.verity.VerityConfig";

    /** 配置类的 {@code SPEC} 字段；取到它才说明 Verity 装了而且配置已初始化。 */
    private static Field specField;
    /** 找过一次就不再找。 */
    private static boolean probed;
    /** 全部配置项字段，按声明顺序。 */
    private static List<Field> valueFields = List.of();
    /** 字段名 -> 配置项。 */
    private static Map<String, Field> byName;

    private static synchronized void ensureProbed() {
        if (probed) return;
        probed = true;
        try {
            Class<?> configClass = Class.forName(CONFIG_CLASS);
            Field field = configClass.getDeclaredField("SPEC");
            field.setAccessible(true);
            List<Field> values = new ArrayList<>();
            for (Field candidate : configClass.getDeclaredFields()) {
                if (!Modifier.isStatic(candidate.getModifiers())) continue;
                if (!ForgeConfigSpec.ConfigValue.class.isAssignableFrom(candidate.getType())) continue;
                candidate.setAccessible(true);
                values.add(candidate);
            }
            specField = field;
            valueFields = List.copyOf(values);
            Log.info("已接入 Verity 配置，共 " + values.size() + " 项");
        } catch (ClassNotFoundException | NoSuchFieldException | RuntimeException e) {
            specField = null;
            valueFields = List.of();
            Log.info("未找到 Verity 配置类，配置界面接管已跳过");
        }
    }

    /** Verity 的 {@code ForgeConfigSpec}；取不到（没装）时返回 null。 */
    public static ForgeConfigSpec spec() {
        ensureProbed();
        if (specField == null) return null;
        try {
            return specField.get(null) instanceof ForgeConfigSpec spec ? spec : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    // ============================================================
    // 按字段名取配置项
    // ============================================================

    /** 按字段名（如 {@code AI_PROVIDER}）取配置项字段；不存在时返回 null。 */
    public static Field field(String name) {
        if (spec() == null) return null;
        Map<String, Field> map;
        synchronized (VeritySpec.class) {
            if (byName == null) {
                Map<String, Field> built = new LinkedHashMap<>();
                for (Field field : valueFields) built.put(field.getName(), field);
                byName = built;
            }
            map = byName;
        }
        return map.get(name);
    }

    /** 该配置项是否存在。 */
    public static boolean available(String name) {
        return field(name) != null;
    }

    /**
     * 读一个配置项的当前值；读不到时返回 null。
     * <p>参数是 {@code VerityConfig} 里的字段名（如 {@code AI_PROVIDER}），
     * 不是 toml 里的键名。
     */
    public static Object get(String name) {
        Field field = field(name);
        if (field == null) return null;
        try {
            Object entry = field.get(null);
            return entry instanceof ForgeConfigSpec.ConfigValue<?> value ? value.get() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** 读一个布尔配置项的当前值；读不到时返回 false。 */
    public static boolean bool(String name) {
        return Boolean.TRUE.equals(get(name));
    }

    /**
     * 写一个配置项。
     * <p>只改内存里的值，不写盘——写盘统一放在界面的 {@code save} 回调里，
     * 这样点「取消」就不会留下半套改动。
     */
    public static void set(String name, Object value) {
        Field field = field(name);
        if (field == null || value == null) return;
        try {
            Object entry = field.get(null);
            if (entry instanceof ForgeConfigSpec.ConfigValue<?> configValue) {
                setUnchecked(configValue, value);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.warn("写入 Verity 配置项 " + name + " 失败: " + e);
        }
    }

    /** {@code ConfigValue#set} 的形参是通配符，按原始类型调用即可。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setUnchecked(ForgeConfigSpec.ConfigValue entry, Object value) {
        ((ForgeConfigSpec.ConfigValue) entry).set(value);
    }

    /**
     * 保存整份配置。
     * <p>由 Forge 自己维护 toml 的注释与结构，所以这里只是让它落盘一次；
     * Verity 监听 {@code ModConfigEvent.Reloading}，保存后会自动重载，无需重启。
     */
    public static void save(ForgeConfigSpec spec) {
        if (spec == null) return;
        try {
            spec.save();
            Log.info("已保存 Verity 配置");
        } catch (RuntimeException e) {
            Log.error("保存 Verity 配置失败: " + e);
        }
    }

    // ============================================================
    // 布尔项自动枚举
    // ============================================================

    /**
     * 界面上还没有单独列过的布尔项，按 toml 键名升序。
     * <p>Verity 以后新增开关时，即使我们没来得及写中文名，它也会出现在界面上，
     * 而不是被悄悄漏掉。
     * <p>{@code excludedFields} 是字段名，用来排除已经单独列过的项，避免同一个开关出现两次。
     */
    public static List<Field> extraBooleans(ForgeConfigSpec spec, List<String> excludedFields) {
        List<Field> out = new ArrayList<>();
        for (Field field : valueFields) {
            if (field.getType() != ForgeConfigSpec.BooleanValue.class) continue;
            if (excludedFields.contains(field.getName())) continue;
            out.add(field);
        }
        out.sort(Comparator.comparing(VeritySpec::keyOf));
        return out;
    }

    /** 配置项在 toml 里的键名；读不到时退回字段名的小写形式。 */
    public static String key(String name) {
        Field field = field(name);
        return field == null ? name.toLowerCase(Locale.ROOT) : keyOf(field);
    }

    /**
     * 枚举配置项的元素类型；不是枚举项时返回 {@code null}。
     * <p>用来把内置模板里的字符串形默认值（如 {@code "OLLAMA"}）还原成枚举常量。
     */
    public static Class<?> enumType(String name) {
        Field field = field(name);
        if (field == null) return null;
        if (!ForgeConfigSpec.EnumValue.class.isAssignableFrom(field.getType())) return null;
        try {
            Object entry = field.get(null);
            if (entry instanceof ForgeConfigSpec.ConfigValue<?> value) {
                Object current = value.get();
                if (current instanceof Enum<?> e) return e.getDeclaringClass();
                return null;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 取不到类型就按「没有类型信息」处理
        }
        return null;
    }

    /**
     * 一个配置项在内置模板里的默认值；模板里没有这一项时返回 {@code null}。
     * <p>返回的已经是该配置项对应的类型（布尔、整数、枚举常量或字符串），
     * 可以直接交给 {@link #set(String, Object)}。
     * <p>模板里的值只是文本，所以按配置项的实际类型逐一转换——直接把字符串塞进
     * {@code EnumValue#set} 会因为类型不符而写坏配置。
     */
    public static Object templateDefault(String name) {
        String raw = VerityConfigManager.templateDefaults().get(key(name));
        if (raw == null) return null;
        try {
            if (ForgeConfigSpec.BooleanValue.class.isAssignableFrom(typeOf(name))) {
                return "true".equalsIgnoreCase(raw);
            }
            if (ForgeConfigSpec.ConfigValue.class.isAssignableFrom(typeOf(name))) {
                Object current = get(name);
                if (current instanceof Enum<?>) {
                    Class<?> enumType = enumType(name);
                    if (enumType == null) return null;
                    String text = unquote(raw);
                    for (Object constant : enumType.getEnumConstants()) {
                        if (((Enum<?>) constant).name().equalsIgnoreCase(text)) return constant;
                    }
                    return null;
                }
                if (current instanceof Integer) {
                    return Integer.parseInt(unquote(raw));
                }
                return unquote(raw);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    /** 配置项字段的声明类型（{@code BooleanValue} / {@code EnumValue} / {@code ConfigValue}）。 */
    private static Class<?> typeOf(String name) {
        Field field = field(name);
        return field == null ? ForgeConfigSpec.ConfigValue.class : field.getType();
    }

    /** 去掉 toml 文本值两端的引号。 */
    private static String unquote(String raw) {
        String text = raw.trim();
        if (text.length() >= 2 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    /** 配置项在 toml 里的键名；读不到时退回字段名的小写形式。 */
    private static String keyOf(Field field) {
        try {
            Object entry = field.get(null);
            if (entry instanceof ForgeConfigSpec.ConfigValue<?> configValue) {
                List<String> path = configValue.getPath();
                if (path != null && !path.isEmpty()) {
                    return path.get(path.size() - 1);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 退回字段名
        }
        return field.getName().toLowerCase(Locale.ROOT);
    }
}
