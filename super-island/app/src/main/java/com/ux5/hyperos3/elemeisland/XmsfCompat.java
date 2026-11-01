package com.ux5.hyperos3.elemeisland;

/** XMSF stays selectable in LSPosed; its app code is left untouched. */
final class XmsfCompat {
    static final String PACKAGE = "com.xiaomi.xmsf";

    private XmsfCompat() { }

    static boolean isProcess(String process) {
        return process != null
                && (PACKAGE.equals(process) || process.startsWith(PACKAGE + ":"));
    }
}
