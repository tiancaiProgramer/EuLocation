package com.ux5.hyperos3.elemeisland;

/** Exact verified framework variant only; preserve every other original result. */
final class XmsfPolicy {
    static boolean allow(boolean original, String pkg, String version, long code, boolean miui, String region) {
        return original || (XmsfCompat.PACKAGE.equals(pkg) && "6.2.67-G".equals(version)
                && code == 60002067L && miui && "China".equals(region));
    }
    static String chatHost(String original, boolean verifiedChina) { return verifiedChina ? "app.chat.xiaomi.net" : original; }
    static String registrationUrl(String original, boolean encrypted, boolean verifiedChina) {
        return verifiedChina ? "https://register.xmpush.xiaomi.com/pass/v2/register" + (encrypted ? "/encrypt" : "") : original;
    }
    private XmsfPolicy() { }
}
