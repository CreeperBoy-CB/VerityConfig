package com.cb2495.verityconfig.util;

public final class PlatformUtils {
    private PlatformUtils() {}

    public static boolean isWindows() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return osName.contains("windows");
    }
}